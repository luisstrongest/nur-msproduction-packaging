package com.nurticenter.produccion.domain.model.paquete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - Etiqueta (Value Object inmutable impreso en el paquete).
 */
@DisplayName("Etiqueta (Value Object)")
class EtiquetaTest {

    private static final DireccionImpresa DIRECCION =
            new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz");

    private static final CodigoIdentificacion CODIGO =
            CodigoIdentificacion.de("PKG-ABC12345", LocalDateTime.of(2026, 7, 15, 8, 0));

    @Test
    void crearGuardaLosDatosYSellaLaFechaDeGeneracion() {
        LocalDateTime antes = LocalDateTime.now();

        Etiqueta etiqueta = Etiqueta.crear("Ana Perez", DIRECCION, CODIGO);

        assertThat(etiqueta.nombrePaciente()).isEqualTo("Ana Perez");
        assertThat(etiqueta.direccion()).isEqualTo(DIRECCION);
        assertThat(etiqueta.codigo()).isEqualTo(CODIGO);
        assertThat(etiqueta.fechaGeneracion()).isAfterOrEqualTo(antes);
        assertThat(etiqueta.esValida()).isTrue();
    }

    @Test
    void rechazaNombreNulo() {
        assertThatThrownBy(() -> Etiqueta.crear(null, DIRECCION, CODIGO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaNombreVacio() {
        assertThatThrownBy(() -> Etiqueta.crear("   ", DIRECCION, CODIGO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede estar vacio");
    }

    @Test
    void rechazaDireccionNula() {
        assertThatThrownBy(() -> Etiqueta.crear("Ana Perez", null, CODIGO))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rechazaCodigoNulo() {
        assertThatThrownBy(() -> Etiqueta.crear("Ana Perez", DIRECCION, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("esValida() es false si la direccion esta incompleta")
    void noEsValidaConDireccionIncompleta() {
        DireccionImpresa incompleta = new DireccionImpresa("  ", "450", null, "Santa Cruz");

        Etiqueta etiqueta = Etiqueta.reconstituir("Ana Perez", incompleta, CODIGO, LocalDateTime.now());

        assertThat(etiqueta.esValida()).isFalse();
    }

    @Test
    @DisplayName("esValida() es false si falta el nombre o el codigo")
    void noEsValidaSinNombreNiCodigo() {
        LocalDateTime ahora = LocalDateTime.now();

        assertThat(Etiqueta.reconstituir("  ", DIRECCION, CODIGO, ahora).esValida()).isFalse();
        assertThat(Etiqueta.reconstituir(null, DIRECCION, CODIGO, ahora).esValida()).isFalse();
        assertThat(Etiqueta.reconstituir("Ana Perez", null, CODIGO, ahora).esValida()).isFalse();
        assertThat(Etiqueta.reconstituir("Ana Perez", DIRECCION, null, ahora).esValida()).isFalse();
    }

    @Test
    @DisplayName("reconstituir() preserva la fecha original de generacion")
    void reconstituirPreservaLaFecha() {
        LocalDateTime original = LocalDateTime.of(2026, 7, 15, 8, 30);

        Etiqueta etiqueta = Etiqueta.reconstituir("Ana Perez", DIRECCION, CODIGO, original);

        assertThat(etiqueta.fechaGeneracion()).isEqualTo(original);
    }

    @Test
    @DisplayName("Dos etiquetas son iguales si comparten el codigo de identificacion")
    void igualdadPorCodigo() {
        Etiqueta a = Etiqueta.reconstituir("Ana Perez", DIRECCION, CODIGO, LocalDateTime.now());
        Etiqueta mismoCodigo = Etiqueta.reconstituir("Otro nombre", DIRECCION, CODIGO, LocalDateTime.now());
        Etiqueta otroCodigo = Etiqueta.reconstituir("Ana Perez", DIRECCION,
                CodigoIdentificacion.de("PKG-ZZZZ9999", LocalDateTime.now()), LocalDateTime.now());

        assertThat(a).isEqualTo(a)
                .isEqualTo(mismoCodigo)
                .hasSameHashCodeAs(mismoCodigo)
                .isNotEqualTo(otroCodigo)
                .isNotEqualTo("no es una Etiqueta");
    }
}
