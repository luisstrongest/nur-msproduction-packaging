package com.nurticenter.produccion.infrastructure.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxJpaEntity (unit test)")
class OutboxJpaEntityTest {

    private static final Instant CREADO = Instant.parse("2026-10-22T21:00:00Z");

    private final OutboxJpaEntity fila =
            new OutboxJpaEntity("e-1", "bc4.OrdenGenerada", "topic", "orden-1", "{}", CREADO);

    @Test
    @DisplayName("Una fila nueva esta pendiente, sin intentos ni error")
    void unaFilaNuevaEstaPendiente() {
        assertThat(fila.getId()).isNull();
        assertThat(fila.getEventId()).isEqualTo("e-1");
        assertThat(fila.getPayload()).isEqualTo("{}");
        assertThat(fila.getCreadoEn()).isEqualTo(CREADO);
        assertThat(fila.getPublicadoEn()).isNull();
        assertThat(fila.getIntentos()).isZero();
        assertThat(fila.getUltimoError()).isNull();
    }

    @Test
    @DisplayName("Cada fallo suma un intento y guarda el ultimo error")
    void cadaFalloSumaUnIntento() {
        fila.registrarFallo("primero");
        fila.registrarFallo("segundo");

        assertThat(fila.getIntentos()).isEqualTo(2);
        assertThat(fila.getUltimoError()).isEqualTo("segundo");
    }

    @Test
    @DisplayName("Un error mas largo que la columna se recorta a 500 caracteres")
    void unErrorLargoSeRecorta() {
        fila.registrarFallo("x".repeat(OutboxJpaEntity.LARGO_ERROR + 20));

        assertThat(fila.getUltimoError()).hasSize(OutboxJpaEntity.LARGO_ERROR);
    }

    @Test
    @DisplayName("Un error de exactamente 500 caracteres se guarda completo")
    void unErrorDelLargoMaximoNoSeRecorta() {
        fila.registrarFallo("y".repeat(OutboxJpaEntity.LARGO_ERROR));

        assertThat(fila.getUltimoError()).hasSize(OutboxJpaEntity.LARGO_ERROR);
    }

    @Test
    @DisplayName("Un fallo sin mensaje se registra igual")
    void unFalloSinMensajeSeRegistra() {
        fila.registrarFallo(null);

        assertThat(fila.getIntentos()).isEqualTo(1);
        assertThat(fila.getUltimoError()).isNull();
    }

    @Test
    @DisplayName("Al publicarse guarda el momento y limpia el error anterior")
    void alPublicarseLimpiaElError() {
        fila.registrarFallo("broker caido");
        Instant publicado = CREADO.plusSeconds(30);

        fila.marcarPublicado(publicado);

        assertThat(fila.getPublicadoEn()).isEqualTo(publicado);
        assertThat(fila.getUltimoError()).isNull();
        assertThat(fila.getIntentos()).isEqualTo(1);
    }
}
