package com.nurticenter.produccion.application.orden;

import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.application.orden.dto.GenerarOrdenCommand;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.model.orden.EstadoItem;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.ItemsPendientesException;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TEST UNITARIO CON MOCKS - OrdenApplicationService.
 *
 * La capa de aplicacion se prueba AISLADA de la infraestructura:
 *   - OrdenDeProduccionRepository -> mock (no hay base de datos)
 *   - EventPublisher              -> mock (no hay bus de mensajes)
 *
 * El agregado de dominio si es real: lo que se verifica aqui es la
 * ORQUESTACION (cargar, delegar en el dominio, guardar, publicar eventos),
 * no las reglas de negocio (esas ya tienen su propio test de dominio).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrdenApplicationService (unit test con mocks)")
class OrdenApplicationServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 15);
    private static final String ORDEN_ID = "orden-1";

    @Mock
    private OrdenDeProduccionRepository ordenRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrdenApplicationService servicio;

    @Captor
    private ArgumentCaptor<OrdenDeProduccion> ordenCaptor;

    @Captor
    private ArgumentCaptor<List<Object>> eventosCaptor;

    // -- Datos de prueba ---------------------------------------------------------

    private GenerarOrdenCommand comandoCon(String unidadMedida) {
        return new GenerarOrdenCommand(FECHA, List.of(
                new GenerarOrdenCommand.ItemCommand(
                        "REC-001", "Pure de zapallo", 12, 250, unidadMedida, "Porcion estandar")));
    }

    /** Orden ya persistida (reconstituida), como la devolveria el repositorio real. */
    private OrdenDeProduccion ordenExistente(EstadoOrden estado, EstadoItem estadoItem) {
        ItemOrden item = ItemOrden.reconstituir(
                "REC-001", "Pure de zapallo", 12,
                new Porcion(250, UnidadMedida.GRAMOS, "Porcion estandar"), estadoItem);
        return OrdenDeProduccion.reconstituir(
                OrdenId.de(ORDEN_ID), FECHA, estado, List.of(item), List.of());
    }

    // -- generarOrden -------------------------------------------------------------

    @Nested
    @DisplayName("generarOrden()")
    class GenerarOrden {

        @Test
        @DisplayName("guarda la orden y publica OrdenGenerada")
        void guardaYPublica() {
            given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

            OrdenDeProduccion resultado = servicio.generarOrden(comandoCon("GRAMOS"));

            verify(ordenRepository).guardar(ordenCaptor.capture());
            verify(eventPublisher).publicar(eventosCaptor.capture());

            assertThat(ordenCaptor.getValue()).isSameAs(resultado);
            assertThat(resultado.estado()).isEqualTo(EstadoOrden.PENDIENTE);
            assertThat(eventosCaptor.getValue()).hasSize(1).first().isInstanceOf(OrdenGenerada.class);
        }

        @Test
        @DisplayName("ACL: traduce el item del calendario a ItemOrden de dominio")
        void traduceElItemDelCalendario() {
            given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

            OrdenDeProduccion orden = servicio.generarOrden(comandoCon("GRAMOS"));

            ItemOrden item = orden.items().get(0);
            assertThat(item.recetaId()).isEqualTo("REC-001");
            assertThat(item.nombreReceta()).isEqualTo("Pure de zapallo");
            assertThat(item.cantidadTotal()).isEqualTo(12);              // nro. de pacientes
            assertThat(item.porcionIndividual().cantidad()).isEqualTo(250);
            assertThat(item.porcionIndividual().unidad()).isEqualTo(UnidadMedida.GRAMOS);
            assertThat(item.estado()).isEqualTo(EstadoItem.PENDIENTE);
        }

        @Test
        @DisplayName("ACL: la unidad de medida se normaliza (case-insensitive y con espacios)")
        void normalizaLaUnidadDeMedida() {
            given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

            OrdenDeProduccion orden = servicio.generarOrden(comandoCon("  mililitros  "));

            assertThat(orden.items().get(0).porcionIndividual().unidad())
                    .isEqualTo(UnidadMedida.MILILITROS);
        }

        @Test
        @DisplayName("ACL: una unidad de medida desconocida es rechazada")
        void rechazaUnidadDeMedidaDesconocida() {
            given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

            assertThatThrownBy(() -> servicio.generarOrden(comandoCon("ONZAS")))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(ordenRepository, never()).guardar(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Regla: una sola orden por fecha de produccion")
        void noPermiteDosOrdenesEnLaMismaFecha() {
            given(ordenRepository.existePorFecha(FECHA)).willReturn(true);

            assertThatThrownBy(() -> servicio.generarOrden(comandoCon("GRAMOS")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("2026-07-15");

            verify(ordenRepository, never()).guardar(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    // -- Transiciones -------------------------------------------------------------

    @Nested
    @DisplayName("Transiciones de estado")
    class Transiciones {

        @Test
        void iniciarPreparacionDelegaEnElDominioYGuarda() {
            given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID)))
                    .willReturn(Optional.of(ordenExistente(EstadoOrden.PENDIENTE, EstadoItem.PENDIENTE)));

            OrdenDeProduccion resultado = servicio.iniciarPreparacion(ORDEN_ID);

            assertThat(resultado.estado()).isEqualTo(EstadoOrden.EN_PREPARACION);
            verify(ordenRepository).guardar(resultado);
            verify(eventPublisher).publicar(List.of());   // esta transicion no emite eventos
        }

        @Test
        void marcarItemPreparadoDelegaEnElDominioYGuarda() {
            given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID)))
                    .willReturn(Optional.of(ordenExistente(EstadoOrden.EN_PREPARACION, EstadoItem.PENDIENTE)));

            OrdenDeProduccion resultado = servicio.marcarItemPreparado(ORDEN_ID, "REC-001");

            assertThat(resultado.items().get(0).estado()).isEqualTo(EstadoItem.PREPARADO);
            verify(ordenRepository).guardar(resultado);
            verify(eventPublisher).publicar(List.of());
        }

        @Test
        @DisplayName("completarOrden() publica OrdenCompletada")
        void completarPublicaElEvento() {
            given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID)))
                    .willReturn(Optional.of(ordenExistente(EstadoOrden.EN_PREPARACION, EstadoItem.PREPARADO)));

            OrdenDeProduccion resultado = servicio.completarOrden(ORDEN_ID);

            assertThat(resultado.estado()).isEqualTo(EstadoOrden.COMPLETADA);
            verify(ordenRepository).guardar(resultado);
            verify(eventPublisher).publicar(eventosCaptor.capture());
            assertThat(eventosCaptor.getValue()).hasSize(1).first().isInstanceOf(OrdenCompletada.class);
        }

        @Test
        @DisplayName("Si el dominio rechaza la operacion, no se guarda ni se publica nada")
        void unaViolacionDeInvarianteAbortaLaTransaccion() {
            given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID)))
                    .willReturn(Optional.of(ordenExistente(EstadoOrden.EN_PREPARACION, EstadoItem.PENDIENTE)));

            assertThatThrownBy(() -> servicio.completarOrden(ORDEN_ID))
                    .isInstanceOf(ItemsPendientesException.class);

            verify(ordenRepository, never()).guardar(any());
            verifyNoInteractions(eventPublisher);
        }
    }

    // -- Consultas ------------------------------------------------------------------

    @Nested
    @DisplayName("Consultas")
    class Consultas {

        @Test
        void buscarPorIdDevuelveLaOrdenDelRepositorio() {
            OrdenDeProduccion orden = ordenExistente(EstadoOrden.PENDIENTE, EstadoItem.PENDIENTE);
            given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID))).willReturn(Optional.of(orden));

            assertThat(servicio.buscarPorId(ORDEN_ID)).isSameAs(orden);
        }

        @Test
        void buscarPorIdInexistenteLanzaNoSuchElement() {
            given(ordenRepository.buscarPorId(OrdenId.de("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.buscarPorId("no-existe"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("no-existe");
        }

        @Test
        void buscarPorFechaSinOrdenLanzaNoSuchElement() {
            given(ordenRepository.buscarPorFecha(FECHA)).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.buscarPorFecha(FECHA))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void buscarPorFechaDevuelveLaOrdenDelDia() {
            OrdenDeProduccion orden = ordenExistente(EstadoOrden.PENDIENTE, EstadoItem.PENDIENTE);
            given(ordenRepository.buscarPorFecha(FECHA)).willReturn(Optional.of(orden));

            assertThat(servicio.buscarPorFecha(FECHA)).isSameAs(orden);
        }

        @Test
        void listarPorEstadoDelegaEnElRepositorio() {
            OrdenDeProduccion orden = ordenExistente(EstadoOrden.PENDIENTE, EstadoItem.PENDIENTE);
            given(ordenRepository.listarPorEstado(EstadoOrden.PENDIENTE)).willReturn(List.of(orden));

            assertThat(servicio.listarPorEstado(EstadoOrden.PENDIENTE)).containsExactly(orden);
            verify(ordenRepository, times(1)).listarPorEstado(EstadoOrden.PENDIENTE);
        }
    }
}
