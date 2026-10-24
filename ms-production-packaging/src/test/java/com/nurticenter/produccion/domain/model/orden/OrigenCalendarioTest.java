package com.nurticenter.produccion.domain.model.orden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrigenCalendario (value object)")
class OrigenCalendarioTest {

    private static final LocalDate ENTREGA = LocalDate.of(2026, 10, 23);

    @Test
    @DisplayName("Guarda el calendario y la fecha de entrega; dos iguales son el mismo valor")
    void guardaSusDatosYSeComparaPorValor() {
        var origen = new OrigenCalendario("CAL-20261023", ENTREGA);

        assertThat(origen.idCalendario()).isEqualTo("CAL-20261023");
        assertThat(origen.fechaEntrega()).isEqualTo(ENTREGA);
        assertThat(origen).isEqualTo(new OrigenCalendario("CAL-20261023", ENTREGA));
    }

    @Test
    void exigeElIdDelCalendario() {
        assertThatThrownBy(() -> new OrigenCalendario(null, ENTREGA))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idCalendario");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rechazaUnIdDeCalendarioVacio(String vacio) {
        assertThatThrownBy(() -> new OrigenCalendario(vacio, ENTREGA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacio");
    }

    @Test
    void exigeLaFechaDeEntrega() {
        assertThatThrownBy(() -> new OrigenCalendario("CAL-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fechaEntrega");
    }
}
