package com.nurticenter.produccion.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContextoCorrelacion (unit test)")
class ContextoCorrelacionTest {

    private final ContextoCorrelacion contexto = new ContextoCorrelacion();

    @Test
    @DisplayName("Fuera de ejecutarCon() no hay correlacion activa")
    void sinCorrelacionFuera() {
        assertThat(contexto.actual()).isEmpty();
    }

    @Test
    @DisplayName("Dentro de ejecutarCon() la correlacion esta activa y se devuelve el resultado")
    void activaDentroYDevuelveElResultado() {
        String visto = contexto.ejecutarCon("corr-1", () -> contexto.actual().orElseThrow());

        assertThat(visto).isEqualTo("corr-1");
        assertThat(contexto.actual()).isEmpty();
    }

    @Test
    @DisplayName("Al anidar, al salir se restaura la correlacion exterior")
    void anidadoRestauraLaExterior() {
        String exterior = contexto.ejecutarCon("corr-exterior", () -> {
            contexto.ejecutarCon("corr-interior", () -> null);
            return contexto.actual().orElseThrow();
        });

        assertThat(exterior).isEqualTo("corr-exterior");
        assertThat(contexto.actual()).isEmpty();
    }

    @Test
    @DisplayName("Si la accion falla, la correlacion se limpia igual")
    void seLimpiaAunqueFalle() {
        assertThatThrownBy(() -> contexto.ejecutarCon("corr-1", () -> {
            throw new IllegalStateException("fallo");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(contexto.actual()).isEmpty();
    }
}
