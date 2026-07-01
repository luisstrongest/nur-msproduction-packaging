package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdenDeProduccionTest {

    private ItemOrden itemDe(String recetaId) {
        Porcion porcion = new Porcion(250, UnidadMedida.GRAMOS, "Sopa de verduras").multiplicar(10);
        return new ItemOrden(recetaId, "Sopa de verduras", (int) porcion.cantidad(), porcion);
    }

    @Test
    void noSePuedeCrearSinItems() {
        assertThatThrownBy(() -> OrdenDeProduccion.crear(LocalDate.now(), List.of()))
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
    void cicloDeVidaCompletoHastaCompletada() {
        OrdenDeProduccion orden = OrdenDeProduccion.crear(LocalDate.now(), List.of(itemDe("receta-1")));
        orden.pullEventos();

        orden.iniciarPreparacion();
        orden.marcarItemPreparado("receta-1");
        orden.completar();

        assertThat(orden.estado()).isEqualTo(EstadoOrden.COMPLETADA);
        assertThat(orden.pullEventos().get(0)).isInstanceOf(OrdenCompletada.class);
    }

    @Test
    void noSePuedeCompletarConItemsPendientes() {
        OrdenDeProduccion orden = OrdenDeProduccion.crear(LocalDate.now(), List.of(itemDe("receta-1")));
        orden.iniciarPreparacion();

        assertThatThrownBy(orden::completar).isInstanceOf(ItemsPendientesException.class);
    }

    @Test
    void noSePuedeModificarUnaOrdenCompletada() {
        OrdenDeProduccion orden = OrdenDeProduccion.crear(LocalDate.now(), List.of(itemDe("receta-1")));
        orden.iniciarPreparacion();
        orden.marcarItemPreparado("receta-1");
        orden.completar();

        assertThatThrownBy(() -> orden.marcarItemPreparado("receta-1"))
                .isInstanceOf(OrdenYaCompletadaException.class);
    }
}
