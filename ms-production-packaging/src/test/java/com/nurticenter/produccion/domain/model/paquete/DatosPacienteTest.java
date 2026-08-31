package com.nurticenter.produccion.domain.model.paquete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - DatosPaciente (datos desnormalizados que llegan de BC3).
 */
@DisplayName("DatosPaciente (Value Object)")
class DatosPacienteTest {

    private static final DireccionImpresa DIRECCION_OK =
            new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz");

    @Test
    void esCompletoCuandoHayNombreYDireccionValida() {
        DatosPaciente datos = new DatosPaciente(PacienteId.de("PAC-1001"), "Ana Perez", DIRECCION_OK);

        assertThat(datos.esCompleto()).isTrue();
        assertThat(datos.id().valor()).isEqualTo("PAC-1001");
    }

    @Test
    void noEsCompletoSiElNombreEstaEnBlanco() {
        DatosPaciente datos = new DatosPaciente(PacienteId.de("PAC-1001"), "   ", DIRECCION_OK);

        assertThat(datos.esCompleto()).isFalse();
    }

    @Test
    void noEsCompletoSiLaDireccionEstaIncompleta() {
        DireccionImpresa sinCiudad = new DireccionImpresa("Av. Mutualista", "450", null, "  ");

        DatosPaciente datos = new DatosPaciente(PacienteId.de("PAC-1001"), "Ana Perez", sinCiudad);

        assertThat(datos.esCompleto()).isFalse();
    }

    @Test
    void rechazaCamposObligatoriosNulos() {
        assertThatThrownBy(() -> new DatosPaciente(null, "Ana Perez", DIRECCION_OK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatosPaciente(PacienteId.de("PAC-1001"), null, DIRECCION_OK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatosPaciente(PacienteId.de("PAC-1001"), "Ana Perez", null))
                .isInstanceOf(NullPointerException.class);
    }
}
