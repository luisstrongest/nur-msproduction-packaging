package com.nurticenter.produccion.domain.model.paquete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - CodigoIdentificacion.
 */
@DisplayName("CodigoIdentificacion (Value Object)")
class CodigoIdentificacionTest {

    @Test
    @DisplayName("generar() deriva el codigo del PaqueteId: PKG-{8 chars en mayusculas}")
    void generaElCodigoDesdeElPaqueteId() {
        PaqueteId id = PaqueteId.de("3f2a1b4c-9d8e-4f10-b111-222233334444");

        CodigoIdentificacion codigo = CodigoIdentificacion.generar(id);

        assertThat(codigo.valor()).isEqualTo("PKG-3F2A1B4C");
        assertThat(codigo.generadoEn()).isNotNull();
    }

    @Test
    @DisplayName("El mismo PaqueteId produce siempre el mismo codigo (determinista)")
    void esDeterministaParaElMismoId() {
        PaqueteId id = PaqueteId.nuevo();

        assertThat(CodigoIdentificacion.generar(id).valor())
                .isEqualTo(CodigoIdentificacion.generar(id).valor());
    }

    @Test
    void rechazaValorNulo() {
        assertThatThrownBy(() -> new CodigoIdentificacion(null, LocalDateTime.now()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaValorVacio() {
        assertThatThrownBy(() -> new CodigoIdentificacion("  ", LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede estar vacio");
    }

    @Test
    void rechazaFechaNula() {
        assertThatThrownBy(() -> new CodigoIdentificacion("PKG-ABC12345", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("de() reconstruye el codigo tal cual estaba persistido")
    void reconstruyeDesdePersistencia() {
        LocalDateTime generadoEn = LocalDateTime.of(2026, 7, 15, 8, 0);

        CodigoIdentificacion codigo = CodigoIdentificacion.de("PKG-ABC12345", generadoEn);

        assertThat(codigo.valor()).isEqualTo("PKG-ABC12345");
        assertThat(codigo.generadoEn()).isEqualTo(generadoEn);
    }
}
