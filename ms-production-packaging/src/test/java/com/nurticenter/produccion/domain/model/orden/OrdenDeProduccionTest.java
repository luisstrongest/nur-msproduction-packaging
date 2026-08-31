package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - Agregado raiz OrdenDeProduccion.
 *
 * El dominio es POJO puro: estos tests corren sin Spring, sin base de datos y
 * sin mocks. Se verifican invariantes, transiciones de estado y los eventos
 * que el agregado acumula (patron outbox).
 */
@DisplayName("OrdenDeProduccion (Aggregate Root)")
class OrdenDeProduccionTest {

    private ItemOrden itemDe(String recetaId) {
        Porcion porcion = new Porcion(250, UnidadMedida.GRAMOS, "Sopa de verduras").multiplicar(10);
        return new ItemOrden(recetaId, "Sopa de verduras", (int) porcion.cantidad(), porcion);
    }

    private OrdenDeProduccion ordenCon(String... recetaIds) {
        List<ItemOrden> items = java.util.Arrays.stream(recetaIds).map(this::itemDe).toList();
        return OrdenDeProduccion.crear(LocalDate.now(), items);
    }

    // -- Creacion --------------------------------------------------------------

    @Nested
    @DisplayName("Creacion")
    class Creacion {

        @Test
        void noSePuedeCrearSinItems() {
            assertThatThrownBy(() -> OrdenDeProduccion.crear(LocalDate.now(), List.of()))
                    .isInstanceOf(OrdenSinItemsException.class);
        }

        @Test
        void noSePuedeCrearConItemsNulos() {
            assertThatThrownBy(() -> OrdenDeProduccion.crear(LocalDate.now(), null))
                    .isInstanceOf(OrdenSinItemsException.class);
        }

        @Test
        void crearRegistraEventoOrdenGenerada() {
            OrdenDeProduccion orden = OrdenDeProduccion.crear(LocalDate.now(), List.of(itemDe("receta-1")));

            List<Object> eventos = orden.pullEventos();

            assertThat(eventos).hasSize(1);
            assertThat(eventos.get(0)).isInstanceOf(OrdenGenerada.class);
            assertThat(orden.estado()).isEqualTo(EstadoOrden.PENDIENTE);
        }

        @Test
        @DisplayName("El evento OrdenGenerada lleva el id, la fecha y el total de items")
        void elEventoLlevaLosDatosDeLaOrden() {
            LocalDate fecha = LocalDate.of(2026, 7, 15);
            LocalDateTime antes = LocalDateTime.now();

            OrdenDeProduccion orden = OrdenDeProduccion.crear(
                    fecha, List.of(itemDe("receta-1"), itemDe("receta-2")));

            OrdenGenerada evento = (OrdenGenerada) orden.pullEventos().get(0);
            assertThat(evento.ordenId()).isEqualTo(orden.id().valor());
            assertThat(evento.agregadoId()).isEqualTo(orden.id().valor());
            assertThat(evento.fechaProduccion()).isEqualTo(fecha);
            assertThat(evento.totalItems()).isEqualTo(2);
            assertThat(evento.nombreEvento()).isEqualTo("bc4.OrdenGenerada");
            assertThat(evento.occurredAt()).isAfterOrEqualTo(antes);
        }

        @Test
        @DisplayName("pullEventos() vacia la bandeja: no se publica dos veces el mismo evento")
        void pullEventosLimpiaLaBandeja() {
            OrdenDeProduccion orden = ordenCon("receta-1");

            assertThat(orden.pullEventos()).hasSize(1);
            assertThat(orden.pullEventos()).isEmpty();
        }

        @Test
        @DisplayName("reconstituir() NO emite eventos: la orden ya existia")
        void reconstituirNoEmiteEventos() {
            OrdenDeProduccion orden = OrdenDeProduccion.reconstituir(
                    OrdenId.de("orden-1"),
                    LocalDate.of(2026, 7, 15),
                    EstadoOrden.EN_PREPARACION,
                    List.of(itemDe("receta-1")),
                    List.of("paquete-1"));

            assertThat(orden.pullEventos()).isEmpty();
            assertThat(orden.estado()).isEqualTo(EstadoOrden.EN_PREPARACION);
            assertThat(orden.paqueteIds()).containsExactly("paquete-1");
        }
    }

    // -- Ciclo de vida ----------------------------------------------------------

    @Nested
    @DisplayName("Ciclo de vida PENDIENTE -> EN_PREPARACION -> COMPLETADA")
    class CicloDeVida {

        @Test
        void cicloDeVidaCompletoHastaCompletada() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.pullEventos();

            orden.iniciarPreparacion();
            orden.marcarItemPreparado("receta-1");
            orden.completar();

            assertThat(orden.estado()).isEqualTo(EstadoOrden.COMPLETADA);
            assertThat(orden.pullEventos().get(0)).isInstanceOf(OrdenCompletada.class);
        }

        @Test
        void noSePuedeIniciarLaPreparacionDosVeces() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.iniciarPreparacion();

            assertThatThrownBy(orden::iniciarPreparacion)
                    .isInstanceOf(TransicionEstadoInvalidaException.class)
                    .hasMessageContaining("EN_PREPARACION");
        }

        @Test
        void noSePuedeCompletarUnaOrdenPendiente() {
            OrdenDeProduccion orden = ordenCon("receta-1");

            assertThatThrownBy(orden::completar)
                    .isInstanceOf(TransicionEstadoInvalidaException.class);
        }

        @Test
        void noSePuedeCompletarConItemsPendientes() {
            OrdenDeProduccion orden = ordenCon("receta-1", "receta-2");
            orden.iniciarPreparacion();
            orden.marcarItemPreparado("receta-1");

            assertThatThrownBy(orden::completar).isInstanceOf(ItemsPendientesException.class);
        }

        @Test
        void noSePuedeModificarUnaOrdenCompletada() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.iniciarPreparacion();
            orden.marcarItemPreparado("receta-1");
            orden.completar();

            assertThatThrownBy(() -> orden.marcarItemPreparado("receta-1"))
                    .isInstanceOf(OrdenYaCompletadaException.class);
        }

        @Test
        void marcarUnItemInexistenteFalla() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.iniciarPreparacion();

            assertThatThrownBy(() -> orden.marcarItemPreparado("receta-inexistente"))
                    .isInstanceOf(ItemNoEncontradoException.class)
                    .hasMessageContaining("receta-inexistente");
        }

        @Test
        @DisplayName("El evento OrdenCompletada arrastra los paquetes generados")
        void ordenCompletadaIncluyeLosPaquetes() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.registrarPaqueteGenerado("paquete-1");
            orden.registrarPaqueteGenerado("paquete-2");
            orden.iniciarPreparacion();
            orden.marcarItemPreparado("receta-1");
            orden.pullEventos();
            LocalDateTime antes = LocalDateTime.now();

            orden.completar();

            OrdenCompletada evento = (OrdenCompletada) orden.pullEventos().get(0);
            assertThat(evento.paqueteIds()).containsExactly("paquete-1", "paquete-2");
            assertThat(evento.ordenId()).isEqualTo(orden.id().valor());
            assertThat(evento.agregadoId()).isEqualTo(orden.id().valor());
            assertThat(evento.occurredAt()).isAfterOrEqualTo(antes);
        }
    }

    // -- Reglas de negocio adicionales ------------------------------------------

    @Nested
    @DisplayName("Reglas de negocio")
    class Reglas {

        @Test
        void noSePuedenRegistrarPaquetesEnUnaOrdenCompletada() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.iniciarPreparacion();
            orden.marcarItemPreparado("receta-1");
            orden.completar();

            assertThatThrownBy(() -> orden.registrarPaqueteGenerado("paquete-1"))
                    .isInstanceOf(OrdenYaCompletadaException.class);
        }

        @Test
        void calcularTotalPorcionesSumaTodosLosItems() {
            OrdenDeProduccion orden = ordenCon("receta-1", "receta-2");

            // cada item se creo con 250 g x 10 pacientes = 2500 porciones
            assertThat(orden.calcularTotalPorciones()).isEqualTo(5000);
        }

        @Test
        @DisplayName("items() y paqueteIds() devuelven colecciones inmutables")
        void lasColeccionesExpuestasSonInmutables() {
            OrdenDeProduccion orden = ordenCon("receta-1");
            orden.registrarPaqueteGenerado("paquete-1");

            assertThatThrownBy(() -> orden.items().add(itemDe("receta-2")))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> orden.paqueteIds().add("paquete-2"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
