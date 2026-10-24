package com.nurticenter.produccion.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO CON MOCKS - OutboxEventWriter.
 *
 * Los repositorios (outbox y correlaciones) son mocks: se verifica que cada evento
 * de dominio se guarda como fila pendiente con el sobre, la key, el topic y la hora
 * del reloj, y de donde sale su correlationId. Que la fila se guarde en la misma
 * transaccion lo prueba FlujoOutboxIT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventWriter (unit test con mocks)")
class OutboxEventWriterTest {

    private static final Instant AHORA = Instant.parse("2026-10-22T21:15:00Z");
    private static final LocalDateTime OCURRIDO = LocalDateTime.of(2026, 10, 22, 21, 0);

    @Mock
    private SpringDataOutboxRepository repositorio;

    @Mock
    private SpringDataCorrelacionOrdenRepository correlaciones;

    private final ContextoCorrelacion contexto = new ContextoCorrelacion();
    private OutboxEventWriter writer;

    @BeforeEach
    void setUp() {
        writer = new OutboxEventWriter(repositorio, correlaciones, contexto,
                new MapeadorEventoIntegracion(new ObjectMapper(), ZoneOffset.UTC),
                Clock.fixed(AHORA, ZoneOffset.UTC),
                "bc4.produccion.eventos");
    }

    @Test
    @DisplayName("Guarda el evento como fila pendiente del outbox con el sobre completo")
    void guardaElEventoComoFilaPendiente() {
        writer.guardar(paqueteListo());

        OutboxJpaEntity fila = filaGuardada();
        assertThat(fila.getEventName()).isEqualTo("bc4.PaqueteListo");
        assertThat(fila.getTopic()).isEqualTo("bc4.produccion.eventos");
        assertThat(fila.getMessageKey()).isEqualTo("p-1");
        assertThat(fila.getCreadoEn()).isEqualTo(AHORA);
        assertThat(fila.getPublicadoEn()).isNull();
        assertThat(fila.getIntentos()).isZero();
        assertThat(fila.getPayload()).contains("\"eventId\":\"" + fila.getEventId() + "\"");
    }

    @Test
    @DisplayName("Sin evento entrante ni correlacion guardada usa corr-orden-{ordenId}")
    void sinCorrelacionUsaLaDeLaOrden() {
        when(correlaciones.findById("orden-1")).thenReturn(Optional.empty());

        writer.guardar(paqueteListo());

        assertThat(filaGuardada().getPayload()).contains("\"correlationId\":\"corr-orden-orden-1\"");
        verify(correlaciones, never()).save(any());
    }

    @Test
    @DisplayName("Sin evento entrante usa la correlacion guardada antes para la orden")
    void usaLaCorrelacionGuardadaDeLaOrden() {
        when(correlaciones.findById("orden-1"))
                .thenReturn(Optional.of(new CorrelacionOrdenJpaEntity("orden-1", "corr-entrega-2026-10-23")));

        writer.guardar(paqueteListo());

        assertThat(filaGuardada().getPayload()).contains("\"correlationId\":\"corr-entrega-2026-10-23\"");
    }

    @Test
    @DisplayName("Con un evento entrante copia su correlacion y la asocia a la orden")
    void copiaLaCorrelacionEntranteYLaAsocia() {
        when(correlaciones.existsById("orden-1")).thenReturn(false);

        contexto.ejecutarCon("corr-entrega-2026-10-23", () -> {
            writer.guardar(new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO));
            return null;
        });

        assertThat(filaGuardada().getPayload()).contains("\"correlationId\":\"corr-entrega-2026-10-23\"");
        var captor = ArgumentCaptor.forClass(CorrelacionOrdenJpaEntity.class);
        verify(correlaciones).save(captor.capture());
        assertThat(captor.getValue().getOrdenId()).isEqualTo("orden-1");
        assertThat(captor.getValue().getCorrelationId()).isEqualTo("corr-entrega-2026-10-23");
    }

    @Test
    @DisplayName("Si la orden ya tenia correlacion asociada no la vuelve a guardar")
    void noDuplicaLaAsociacion() {
        when(correlaciones.existsById("orden-1")).thenReturn(true);

        contexto.ejecutarCon("corr-entrega-2026-10-23", () -> {
            writer.guardar(paqueteListo());
            return null;
        });

        assertThat(filaGuardada().getPayload()).contains("\"correlationId\":\"corr-entrega-2026-10-23\"");
        verify(correlaciones, never()).save(any());
    }

    private OutboxJpaEntity filaGuardada() {
        var captor = ArgumentCaptor.forClass(OutboxJpaEntity.class);
        verify(repositorio).save(captor.capture());
        return captor.getValue();
    }

    private static PaqueteListo paqueteListo() {
        return new PaqueteListo("p-1", "orden-1", "PAC-1", "PKG-1", "Ana Rojas",
                new DireccionImpresa("Calle Junin", "245", null, "Santa Cruz"), null, OCURRIDO);
    }
}
