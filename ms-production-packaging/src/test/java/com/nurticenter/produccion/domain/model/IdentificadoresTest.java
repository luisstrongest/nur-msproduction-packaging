package com.nurticenter.produccion.domain.model;

import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - Identificadores del dominio (OrdenId, PaqueteId, PacienteId).
 *
 * Los tres son Value Objects que envuelven un String para evitar confundir
 * un id con otro (tiny types). Comparten las mismas invariantes.
 */
@DisplayName("Identificadores del dominio")
class IdentificadoresTest {

    @Test
    @DisplayName("nuevo() genera un UUID distinto en cada llamada")
    void generanIdentificadoresUnicos() {
        OrdenId orden1 = OrdenId.nuevo();
        OrdenId orden2 = OrdenId.nuevo();
        PaqueteId paquete1 = PaqueteId.nuevo();

        assertThat(orden1).isNotEqualTo(orden2);
        assertThat(UUID.fromString(orden1.valor())).isNotNull();
        assertThat(UUID.fromString(paquete1.valor())).isNotNull();
    }

    @Test
    @DisplayName("de() reconstruye el id desde persistencia")
    void reconstruyenDesdeUnValorExistente() {
        assertThat(OrdenId.de("orden-1").valor()).isEqualTo("orden-1");
        assertThat(PaqueteId.de("paquete-1").valor()).isEqualTo("paquete-1");
        assertThat(PacienteId.de("PAC-1001").valor()).isEqualTo("PAC-1001");
    }

    @Test
    void rechazanValorNulo() {
        assertThatThrownBy(() -> OrdenId.de(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PaqueteId.de(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PacienteId.de(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazanValorVacio() {
        assertThatThrownBy(() -> OrdenId.de("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrdenId");
        assertThatThrownBy(() -> PaqueteId.de(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PaqueteId");
        assertThatThrownBy(() -> PacienteId.de(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PacienteId");
    }

    @Test
    @DisplayName("Dos ids con el mismo valor son iguales (semantica de Value Object)")
    void igualdadPorValor() {
        assertThat(OrdenId.de("orden-1")).isEqualTo(OrdenId.de("orden-1"))
                .hasSameHashCodeAs(OrdenId.de("orden-1"));
        assertThat(PacienteId.de("PAC-1")).isEqualTo(PacienteId.de("PAC-1"));
    }
}
