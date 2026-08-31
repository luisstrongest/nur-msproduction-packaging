package com.nurticenter.produccion.domain.model.paquete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - DireccionImpresa (copia inmutable de la direccion de BC3).
 */
@DisplayName("DireccionImpresa (Value Object)")
class DireccionImpresaTest {

    @Test
    void rechazaCalleNula() {
        assertThatThrownBy(() -> new DireccionImpresa(null, "450", "ref", "Santa Cruz"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaNumeroNulo() {
        assertThatThrownBy(() -> new DireccionImpresa("Av. Mutualista", null, "ref", "Santa Cruz"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaCiudadNula() {
        assertThatThrownBy(() -> new DireccionImpresa("Av. Mutualista", "450", "ref", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("La referencia es opcional: puede ser null")
    void laReferenciaEsOpcional() {
        DireccionImpresa direccion = new DireccionImpresa("Av. Mutualista", "450", null, "Santa Cruz");

        assertThat(direccion.estaCompleta()).isTrue();
        assertThat(direccion.referencia()).isNull();
    }

    @ParameterizedTest(name = "calle=[{0}] numero=[{1}] ciudad=[{2}] -> completa={3}")
    @CsvSource(nullValues = "NULL", value = {
            "Av. Mutualista, 450, Santa Cruz, true",
            "'            ', 450, Santa Cruz, false",
            "Av. Mutualista, '  ', Santa Cruz, false",
            "Av. Mutualista, 450, '        ', false"
    })
    void estaCompletaExigeCalleNumeroYCiudad(String calle, String numero, String ciudad, boolean esperado) {
        DireccionImpresa direccion = new DireccionImpresa(calle, numero, "Edificio azul", ciudad);

        assertThat(direccion.estaCompleta()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("formatoParaEtiqueta() incluye la referencia cuando existe")
    void formatoConReferencia() {
        DireccionImpresa direccion =
                new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz");

        assertThat(direccion.formatoParaEtiqueta())
                .isEqualTo("Av. Mutualista 450, Ref: Edificio azul - Santa Cruz");
    }

    @Test
    @DisplayName("formatoParaEtiqueta() omite la referencia si es nula o vacia")
    void formatoSinReferencia() {
        assertThat(new DireccionImpresa("Av. Mutualista", "450", null, "Santa Cruz").formatoParaEtiqueta())
                .isEqualTo("Av. Mutualista 450 - Santa Cruz");
        assertThat(new DireccionImpresa("Av. Mutualista", "450", "   ", "Santa Cruz").formatoParaEtiqueta())
                .isEqualTo("Av. Mutualista 450 - Santa Cruz");
    }
}
