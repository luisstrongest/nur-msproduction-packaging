package com.nurticenter.produccion.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * TEST UNITARIO CON MOCKS - OutboxEventWriter.
 *
 * El repositorio del outbox es un mock: se verifica que cada evento de dominio
 * se guarda como fila pendiente con el sobre, la key, el topic y la hora del reloj.
 * Que la fila se guarde en la misma transaccion lo prueba FlujoOutboxIT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventWriter (unit test con mocks)")
class OutboxEventWriterTest {

    private static final Instant AHORA = Instant.parse("2026-10-22T21:15:00Z");

    @Mock
    private SpringDataOutboxRepository repositorio;

    @Test
    @DisplayName("Guarda el evento como fila pendiente del outbox con el sobre completo")
    void guardaElEventoComoFilaPendiente() {
        var writer = new OutboxEventWriter(repositorio,
                new MapeadorEventoIntegracion(new ObjectMapper(), ZoneOffset.UTC),
                Clock.fixed(AHORA, ZoneOffset.UTC),
                "bc4.produccion.eventos");

        writer.guardar(new PaqueteListo("p-1", "orden-1", "PAC-1", "PKG-1", LocalDateTime.of(2026, 10, 22, 21, 0)));

        var captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(repositorio).save(captor.capture());
        OutboxJpaEntity fila = captor.getValue();
        assertThat(fila.getEventName()).isEqualTo("bc4.PaqueteListo");
        assertThat(fila.getTopic()).isEqualTo("bc4.produccion.eventos");
        assertThat(fila.getMessageKey()).isEqualTo("p-1");
        assertThat(fila.getCreadoEn()).isEqualTo(AHORA);
        assertThat(fila.getPublicadoEn()).isNull();
        assertThat(fila.getIntentos()).isZero();
        assertThat(fila.getPayload())
                .contains("\"eventId\":\"" + fila.getEventId() + "\"")
                .contains("\"correlationId\":\"corr-orden-orden-1\"");
    }
}
