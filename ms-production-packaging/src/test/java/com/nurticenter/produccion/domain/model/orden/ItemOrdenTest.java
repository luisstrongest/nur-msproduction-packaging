package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - ItemOrden (Value Object con estado propio).
 */
@DisplayName("ItemOrden")
class ItemOrdenTest {

    private static final Porcion PORCION = new Porcion(250, UnidadMedida.GRAMOS, "Porcion estandar");

    @Test
    void seCreaEnEstadoPendiente() {
        ItemOrden item = new ItemOrden("REC-001", "Pure de zapallo", 12, PORCION);

        assertThat(item.estado()).isEqualTo(EstadoItem.PENDIENTE);
        assertThat(item.recetaId()).isEqualTo("REC-001");
        assertThat(item.nombreReceta()).isEqualTo("Pure de zapallo");
        assertThat(item.cantidadTotal()).isEqualTo(12);
        assertThat(item.porcionIndividual()).isEqualTo(PORCION);
    }

    @ParameterizedTest(name = "cantidadTotal = {0} -> rechazada")
    @ValueSource(ints = {0, -5})
    void rechazaCantidadTotalNoPositiva(int cantidad) {
        assertThatThrownBy(() -> new ItemOrden("REC-001", "Pure", cantidad, PORCION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cantidadTotal");
    }

    @Test
    void rechazaRecetaIdNulo() {
        assertThatThrownBy(() -> new ItemOrden(null, "Pure", 1, PORCION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaNombreRecetaNulo() {
        assertThatThrownBy(() -> new ItemOrden("REC-001", null, 1, PORCION))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaPorcionNula() {
        assertThatThrownBy(() -> new ItemOrden("REC-001", "Pure", 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void marcarPreparadoAvanzaElEstado() {
        ItemOrden item = new ItemOrden("REC-001", "Pure", 12, PORCION);

        item.marcarPreparado();

        assertThat(item.estado()).isEqualTo(EstadoItem.PREPARADO);
    }

    @Test
    void noSePuedeMarcarPreparadoDosVeces() {
        ItemOrden item = new ItemOrden("REC-001", "Pure", 12, PORCION);
        item.marcarPreparado();

        assertThatThrownBy(item::marcarPreparado)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REC-001");
    }

    @Test
    @DisplayName("reconstituir() preserva el estado que venia de la base de datos")
    void reconstituirPreservaEstado() {
        ItemOrden item = ItemOrden.reconstituir("REC-001", "Pure", 12, PORCION, EstadoItem.PREPARADO);

        assertThat(item.estado()).isEqualTo(EstadoItem.PREPARADO);
    }

    @Test
    @DisplayName("reconstituir() exige un estado no nulo")
    void reconstituirRechazaEstadoNulo() {
        assertThatThrownBy(() -> ItemOrden.reconstituir("REC-001", "Pure", 12, PORCION, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("La identidad del item dentro de la orden es el recetaId")
    void igualdadPorRecetaId() {
        ItemOrden a = new ItemOrden("REC-001", "Pure", 12, PORCION);
        ItemOrden mismoRecetaId = new ItemOrden("REC-001", "Otro nombre", 99, PORCION);
        ItemOrden otro = new ItemOrden("REC-002", "Pure", 12, PORCION);

        assertThat(a).isEqualTo(a)
                .isEqualTo(mismoRecetaId)
                .hasSameHashCodeAs(mismoRecetaId)
                .isNotEqualTo(otro)
                .isNotEqualTo("no es un ItemOrden");
    }
}
