package com.nurticenter.produccion.infrastructure.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO CON MOCKS - OutboxRelay.
 *
 * Repositorio y KafkaTemplate son mocks. Se verifica la regla central del
 * outbox: una fila solo se marca publicada cuando Kafka CONFIRMA el envio; si
 * falla, queda pendiente con su error y los eventos posteriores del mismo
 * agregado esperan (orden por agregado).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelay (unit test con mocks)")
class OutboxRelayTest {

    private static final Instant AHORA = Instant.parse("2026-10-22T21:15:00Z");
    private static final int LOTE = 50;

    @Mock
    private SpringDataOutboxRepository repositorio;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay(long esperaEnvioMs) {
        return new OutboxRelay(repositorio, kafkaTemplate, Clock.fixed(AHORA, ZoneOffset.UTC), LOTE, esperaEnvioMs, 7);
    }

    private static OutboxJpaEntity fila(String eventId, String key) {
        return new OutboxJpaEntity(eventId, "bc4.PaqueteListo", "bc4.produccion.eventos", key,
                "{\"eventId\":\"" + eventId + "\"}", AHORA.minusSeconds(5));
    }

    private static CompletableFuture<SendResult<String, String>> confirmado() {
        return CompletableFuture.completedFuture(null);
    }

    @Nested
    @DisplayName("Publicacion")
    class Publicacion {

        @Test
        @DisplayName("Publica cada pendiente con su topic, key, sobre y cabecera eventName, y la marca publicada")
        void publicaYMarcaPublicada() {
            OutboxJpaEntity pendiente = fila("e-1", "p-1");
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of(pendiente));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(confirmado());

            int publicadas = relay(5000).publicarPendientes();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
            verify(kafkaTemplate).send(captor.capture());
            ProducerRecord<String, String> registro = captor.getValue();
            assertThat(registro.topic()).isEqualTo("bc4.produccion.eventos");
            assertThat(registro.key()).isEqualTo("p-1");
            assertThat(registro.value()).isEqualTo("{\"eventId\":\"e-1\"}");
            assertThat(new String(registro.headers().lastHeader(OutboxRelay.CABECERA_NOMBRE).value(), StandardCharsets.UTF_8))
                    .isEqualTo("bc4.PaqueteListo");
            assertThat(publicadas).isEqualTo(1);
            assertThat(pendiente.getPublicadoEn()).isEqualTo(AHORA);
            assertThat(pendiente.getUltimoError()).isNull();
        }

        @Test
        @DisplayName("Sin pendientes no envia nada")
        void sinPendientesNoEnviaNada() {
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of());

            int publicadas = relay(5000).publicarPendientes();

            assertThat(publicadas).isZero();
            verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        }
    }

    @Nested
    @DisplayName("Fallos: nada se pierde")
    class Fallos {

        @Test
        @DisplayName("Si Kafka rechaza el envio, la fila queda pendiente con el intento y el error")
        void siKafkaRechazaQuedaPendiente() {
            OutboxJpaEntity pendiente = fila("e-1", "p-1");
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of(pendiente));
            when(kafkaTemplate.send(any(ProducerRecord.class)))
                    .thenReturn(CompletableFuture.failedFuture(new KafkaException("broker caido")));

            int publicadas = relay(5000).publicarPendientes();

            assertThat(publicadas).isZero();
            assertThat(pendiente.getPublicadoEn()).isNull();
            assertThat(pendiente.getIntentos()).isEqualTo(1);
            assertThat(pendiente.getUltimoError()).contains("broker caido");
        }

        @Test
        @DisplayName("Si Kafka no confirma a tiempo, la fila queda pendiente")
        void siKafkaNoConfirmaATiempoQuedaPendiente() {
            OutboxJpaEntity pendiente = fila("e-1", "p-1");
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of(pendiente));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

            relay(1).publicarPendientes();

            assertThat(pendiente.getPublicadoEn()).isNull();
            assertThat(pendiente.getUltimoError()).startsWith("TimeoutException");
        }

        @Test
        @DisplayName("Si el envio falla al instante (metadata no disponible), la fila queda pendiente")
        void siElEnvioLanzaExcepcionQuedaPendiente() {
            OutboxJpaEntity pendiente = fila("e-1", "p-1");
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of(pendiente));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new KafkaException("sin metadata"));

            relay(5000).publicarPendientes();

            assertThat(pendiente.getPublicadoEn()).isNull();
            assertThat(pendiente.getUltimoError()).isEqualTo("KafkaException: sin metadata");
        }

        @Test
        @DisplayName("Un fallo detiene los eventos posteriores del MISMO agregado, no los de otros")
        void unFalloDetieneSoloAlMismoAgregado() {
            OutboxJpaEntity primeroDeP1 = fila("e-1", "p-1");
            OutboxJpaEntity deP2 = fila("e-2", "p-2");
            OutboxJpaEntity segundoDeP1 = fila("e-3", "p-1");
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of(primeroDeP1, deP2, segundoDeP1));
            when(kafkaTemplate.send(any(ProducerRecord.class)))
                    .thenReturn(CompletableFuture.failedFuture(new KafkaException("fallo")))
                    .thenReturn(confirmado());

            int publicadas = relay(5000).publicarPendientes();

            assertThat(publicadas).isEqualTo(1);
            assertThat(primeroDeP1.getPublicadoEn()).isNull();
            assertThat(deP2.getPublicadoEn()).isEqualTo(AHORA);
            assertThat(segundoDeP1.getPublicadoEn()).isNull();
            assertThat(segundoDeP1.getIntentos()).isZero();
            verify(kafkaTemplate, times(2)).send(any(ProducerRecord.class));
        }

        @Test
        @DisplayName("Si el hilo se interrumpe, registra el fallo, corta el lote y conserva la interrupcion")
        void siElHiloSeInterrumpeCortaElLote() {
            OutboxJpaEntity primera = fila("e-1", "p-1");
            OutboxJpaEntity segunda = fila("e-2", "p-2");
            when(repositorio.bloquearPendientes(LOTE)).thenReturn(List.of(primera, segunda));
            when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

            Thread.currentThread().interrupt();
            try {
                relay(5000).publicarPendientes();

                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            assertThat(primera.getUltimoError()).startsWith("Interrumpido");
            assertThat(segunda.getIntentos()).isZero();
            verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
        }
    }

    @Nested
    @DisplayName("Limpieza")
    class Limpieza {

        @Test
        @DisplayName("Borra las publicadas con mas antiguedad que la retencion (7 dias)")
        void borraLasPublicadasFueraDeLaRetencion() {
            when(repositorio.borrarPublicadosAntesDe(Instant.parse("2026-10-15T21:15:00Z"))).thenReturn(4);

            assertThat(relay(5000).limpiarPublicados()).isEqualTo(4);
        }

        @Test
        @DisplayName("Si no hay nada que borrar devuelve cero")
        void sinNadaQueBorrarDevuelveCero() {
            when(repositorio.borrarPublicadosAntesDe(any())).thenReturn(0);

            assertThat(relay(5000).limpiarPublicados()).isZero();
        }
    }
}
