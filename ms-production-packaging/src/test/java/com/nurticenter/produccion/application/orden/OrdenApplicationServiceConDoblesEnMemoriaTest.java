package com.nurticenter.produccion.application.orden;

import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.application.orden.dto.GenerarOrdenCommand;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.repository.InMemoryOrdenDeProduccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO CON DOBLES EN MEMORIA (fakes) - OrdenApplicationService.
 *
 * Complementa a {@link OrdenApplicationServiceTest}, que usa mocks de Mockito:
 *
 *   - MOCK  -> se verifica la INTERACCION ("se llamo a guardar() una vez").
 *   - FAKE  -> implementacion real pero simplificada (un HashMap en lugar de
 *              PostgreSQL); se verifica el ESTADO resultante.
 *
 * El fake permite recorrer un flujo completo de varios casos de uso encadenados
 * sin levantar Spring ni una base de datos.
 */
@DisplayName("OrdenApplicationService (unit test con dobles en memoria)")
class OrdenApplicationServiceConDoblesEnMemoriaTest {

    /** Doble de prueba del puerto EventPublisher: acumula lo publicado. */
    private static class EventPublisherSpy implements EventPublisher {
        private final List<Object> publicados = new ArrayList<>();

        @Override
        public void publicar(List<Object> eventos) {
            publicados.addAll(eventos);
        }

        List<Object> publicados() {
            return publicados;
        }
    }

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 15);

    private InMemoryOrdenDeProduccionRepository repositorio;
    private EventPublisherSpy publisher;
    private OrdenApplicationService servicio;

    @BeforeEach
    void setUp() {
        repositorio = new InMemoryOrdenDeProduccionRepository();
        publisher = new EventPublisherSpy();
        servicio = new OrdenApplicationService(repositorio, publisher);
    }

    private GenerarOrdenCommand comando() {
        return new GenerarOrdenCommand(FECHA, List.of(
                new GenerarOrdenCommand.ItemCommand(
                        "REC-001", "Pure de zapallo", 12, 250, "GRAMOS", "Porcion estandar"),
                new GenerarOrdenCommand.ItemCommand(
                        "REC-002", "Jugo de naranja", 8, 200, "MILILITROS", "Vaso individual")));
    }

    @Test
    @DisplayName("Flujo completo: generar -> preparar items -> completar")
    void flujoCompletoDeLaOrden() {
        OrdenDeProduccion generada = servicio.generarOrden(comando());
        String ordenId = generada.id().valor();

        servicio.iniciarPreparacion(ordenId);
        servicio.marcarItemPreparado(ordenId, "REC-001");
        servicio.marcarItemPreparado(ordenId, "REC-002");
        OrdenDeProduccion completada = servicio.completarOrden(ordenId);

        assertThat(completada.estado()).isEqualTo(EstadoOrden.COMPLETADA);
        assertThat(servicio.buscarPorId(ordenId).estado()).isEqualTo(EstadoOrden.COMPLETADA);
        assertThat(publisher.publicados())
                .hasSize(2)
                .hasAtLeastOneElementOfType(OrdenGenerada.class)
                .hasAtLeastOneElementOfType(OrdenCompletada.class);
    }

    @Test
    @DisplayName("El estado queda realmente persistido en el repositorio entre casos de uso")
    void elEstadoPersisteEntreCasosDeUso() {
        String ordenId = servicio.generarOrden(comando()).id().valor();

        servicio.iniciarPreparacion(ordenId);

        assertThat(servicio.buscarPorFecha(FECHA).estado()).isEqualTo(EstadoOrden.EN_PREPARACION);
        assertThat(servicio.listarPorEstado(EstadoOrden.EN_PREPARACION)).hasSize(1);
        assertThat(servicio.listarPorEstado(EstadoOrden.COMPLETADA)).isEmpty();
    }

    @Test
    @DisplayName("Regla: no se puede generar una segunda orden para la misma fecha")
    void noSePuedeDuplicarLaOrdenDelDia() {
        servicio.generarOrden(comando());

        assertThatThrownBy(() -> servicio.generarOrden(comando()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(servicio.listarPorEstado(EstadoOrden.PENDIENTE)).hasSize(1);
    }
}
