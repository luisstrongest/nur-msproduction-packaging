package com.nurticenter.produccion.application.paquete;

import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.application.paquete.dto.CrearPaqueteCommand;
import com.nurticenter.produccion.application.paquete.dto.GenerarEtiquetaCommand;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import com.nurticenter.produccion.domain.model.orden.EstadoItem;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.model.paquete.CodigoIdentificacion;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.Etiqueta;
import com.nurticenter.produccion.domain.model.paquete.EtiquetaSinEnvasadoException;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import com.nurticenter.produccion.domain.repository.PaqueteEtiquetadoRepository;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TEST UNITARIO CON MOCKS - PaqueteApplicationService.
 *
 * Se mockean los dos repositorios y el publicador de eventos. El caso de uso
 * "crear paquete" toca DOS agregados (paquete + orden), asi que aqui se verifica
 * que la orquestacion entre ambos ocurre en el orden correcto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaqueteApplicationService (unit test con mocks)")
class PaqueteApplicationServiceTest {

    private static final String ORDEN_ID = "orden-1";
    private static final String PAQUETE_ID = "3f2a1b4c-9d8e-4f10-b111-222233334444";
    private static final String PACIENTE_ID = "PAC-1001";

    @Mock
    private PaqueteEtiquetadoRepository paqueteRepository;

    @Mock
    private OrdenDeProduccionRepository ordenRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private PaqueteApplicationService servicio;

    @Captor
    private ArgumentCaptor<List<Object>> eventosCaptor;

    // -- Datos de prueba -----------------------------------------------------------

    private OrdenDeProduccion ordenExistente() {
        ItemOrden item = ItemOrden.reconstituir(
                "REC-001", "Pure de zapallo", 12,
                new Porcion(250, UnidadMedida.GRAMOS, "Porcion estandar"), EstadoItem.PENDIENTE);
        return OrdenDeProduccion.reconstituir(
                OrdenId.de(ORDEN_ID), LocalDate.of(2026, 7, 15),
                EstadoOrden.EN_PREPARACION, List.of(item), List.of());
    }

    private PaqueteEtiquetado paqueteEn(EstadoPaquete estado, Etiqueta etiqueta) {
        return PaqueteEtiquetado.reconstituir(
                PaqueteId.de(PAQUETE_ID), PacienteId.de(PACIENTE_ID), ORDEN_ID, estado, etiqueta);
    }

    private Etiqueta etiquetaValida() {
        return Etiqueta.reconstituir(
                "Ana Perez",
                new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz"),
                CodigoIdentificacion.de("PKG-3F2A1B4C", LocalDateTime.now()),
                LocalDateTime.now());
    }

    private GenerarEtiquetaCommand comandoEtiqueta() {
        return new GenerarEtiquetaCommand(
                "Ana Perez", "Av. Mutualista", "450", "Edificio azul", "Santa Cruz");
    }

    // -- crearPaquete -----------------------------------------------------------------

    @Nested
    @DisplayName("crearPaquete()")
    class CrearPaquete {

        @Test
        @DisplayName("crea el paquete y registra su referencia en la orden")
        void creaElPaqueteYLoVinculaALaOrden() {
            OrdenDeProduccion orden = ordenExistente();
            given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID))).willReturn(Optional.of(orden));

            PaqueteEtiquetado paquete =
                    servicio.crearPaquete(new CrearPaqueteCommand(PACIENTE_ID, ORDEN_ID));

            assertThat(paquete.estado()).isEqualTo(EstadoPaquete.ENVASADO_PENDIENTE);
            assertThat(paquete.pacienteId().valor()).isEqualTo(PACIENTE_ID);
            assertThat(paquete.ordenId()).isEqualTo(ORDEN_ID);
            assertThat(orden.paqueteIds()).containsExactly(paquete.id().valor());

            // El paquete se persiste ANTES de actualizar la orden que lo referencia.
            InOrder enOrden = inOrder(paqueteRepository, ordenRepository);
            enOrden.verify(paqueteRepository).guardar(paquete);
            enOrden.verify(ordenRepository).guardar(orden);

            // Crear no emite eventos, pero la bandeja se vacia igualmente hacia el bus.
            verify(eventPublisher).publicar(List.of());
        }

        @Test
        @DisplayName("si la orden no existe, no se crea ningun paquete")
        void fallaSiLaOrdenNoExiste() {
            given(ordenRepository.buscarPorId(OrdenId.de("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    servicio.crearPaquete(new CrearPaqueteCommand(PACIENTE_ID, "no-existe")))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("no-existe");

            verifyNoInteractions(paqueteRepository);
            verifyNoInteractions(eventPublisher);
            verify(ordenRepository, never()).guardar(any());
        }
    }

    // -- Transiciones -------------------------------------------------------------------

    @Nested
    @DisplayName("Ciclo de vida del paquete")
    class CicloDeVida {

        @Test
        void registrarEnvasadoAvanzaElEstadoYGuarda() {
            given(paqueteRepository.buscarPorId(PaqueteId.de(PAQUETE_ID)))
                    .willReturn(Optional.of(paqueteEn(EstadoPaquete.ENVASADO_PENDIENTE, null)));

            PaqueteEtiquetado resultado = servicio.registrarEnvasado(PAQUETE_ID);

            assertThat(resultado.estado()).isEqualTo(EstadoPaquete.ENVASADO);
            verify(paqueteRepository).guardar(resultado);
            verify(eventPublisher).publicar(List.of());
        }

        @Test
        @DisplayName("generarEtiqueta() arma DatosPaciente con el comando y el pacienteId del paquete")
        void generaLaEtiquetaConLosDatosDelComando() {
            given(paqueteRepository.buscarPorId(PaqueteId.de(PAQUETE_ID)))
                    .willReturn(Optional.of(paqueteEn(EstadoPaquete.ENVASADO, null)));

            PaqueteEtiquetado resultado = servicio.generarEtiqueta(PAQUETE_ID, comandoEtiqueta());

            assertThat(resultado.estado()).isEqualTo(EstadoPaquete.ETIQUETADO);
            assertThat(resultado.etiqueta().nombrePaciente()).isEqualTo("Ana Perez");
            assertThat(resultado.etiqueta().direccion().formatoParaEtiqueta())
                    .isEqualTo("Av. Mutualista 450, Ref: Edificio azul - Santa Cruz");
            assertThat(resultado.etiqueta().codigo().valor()).isEqualTo("PKG-3F2A1B4C");
            verify(paqueteRepository).guardar(resultado);
            verify(eventPublisher).publicar(List.of());
        }

        @Test
        @DisplayName("si el dominio rechaza la etiqueta, no se guarda ni se publica nada")
        void noEtiquetaUnPaqueteSinEnvasar() {
            given(paqueteRepository.buscarPorId(PaqueteId.de(PAQUETE_ID)))
                    .willReturn(Optional.of(paqueteEn(EstadoPaquete.ENVASADO_PENDIENTE, null)));

            assertThatThrownBy(() -> servicio.generarEtiqueta(PAQUETE_ID, comandoEtiqueta()))
                    .isInstanceOf(EtiquetaSinEnvasadoException.class);

            verify(paqueteRepository, never()).guardar(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("marcarListo() publica el evento PaqueteListo para BC5")
        void marcarListoPublicaElEvento() {
            given(paqueteRepository.buscarPorId(PaqueteId.de(PAQUETE_ID)))
                    .willReturn(Optional.of(paqueteEn(EstadoPaquete.ETIQUETADO, etiquetaValida())));

            PaqueteEtiquetado resultado = servicio.marcarListo(PAQUETE_ID);

            assertThat(resultado.estado()).isEqualTo(EstadoPaquete.LISTO);
            verify(paqueteRepository).guardar(resultado);
            verify(eventPublisher).publicar(eventosCaptor.capture());

            assertThat(eventosCaptor.getValue()).hasSize(1).first().isInstanceOf(PaqueteListo.class);
            PaqueteListo evento = (PaqueteListo) eventosCaptor.getValue().get(0);
            assertThat(evento.paqueteId()).isEqualTo(PAQUETE_ID);
            assertThat(evento.pacienteId()).isEqualTo(PACIENTE_ID);
            assertThat(evento.codigoIdentificacion()).isEqualTo("PKG-3F2A1B4C");
        }

        @Test
        void operarSobreUnPaqueteInexistenteLanzaNoSuchElement() {
            given(paqueteRepository.buscarPorId(PaqueteId.de("no-existe"))).willReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.registrarEnvasado("no-existe"))
                    .isInstanceOf(NoSuchElementException.class)
                    .hasMessageContaining("no-existe");
        }
    }

    // -- Consultas ------------------------------------------------------------------------

    @Nested
    @DisplayName("Consultas")
    class Consultas {

        @Test
        void buscarPorIdDevuelveElPaquete() {
            PaqueteEtiquetado paquete = paqueteEn(EstadoPaquete.ENVASADO, null);
            given(paqueteRepository.buscarPorId(PaqueteId.de(PAQUETE_ID))).willReturn(Optional.of(paquete));

            assertThat(servicio.buscarPorId(PAQUETE_ID)).isSameAs(paquete);
        }

        @Test
        void listarPorOrdenDelegaEnElRepositorio() {
            PaqueteEtiquetado paquete = paqueteEn(EstadoPaquete.LISTO, etiquetaValida());
            given(paqueteRepository.listarPorOrden(ORDEN_ID)).willReturn(List.of(paquete));

            assertThat(servicio.listarPorOrden(ORDEN_ID)).containsExactly(paquete);
        }

        @Test
        void listarPorEstadoDelegaEnElRepositorio() {
            PaqueteEtiquetado paquete = paqueteEn(EstadoPaquete.ENVASADO, null);
            given(paqueteRepository.listarPorEstado(EstadoPaquete.ENVASADO)).willReturn(List.of(paquete));

            assertThat(servicio.listarPorEstado(EstadoPaquete.ENVASADO)).containsExactly(paquete);
        }

        @Test
        void listarListosPendientesDeTransferenciaDelegaEnElRepositorio() {
            PaqueteEtiquetado paquete = paqueteEn(EstadoPaquete.LISTO, etiquetaValida());
            given(paqueteRepository.listarListosPendientesDeTransferencia()).willReturn(List.of(paquete));

            assertThat(servicio.listarListosPendientesDeTransferencia()).containsExactly(paquete);
        }
    }
}
