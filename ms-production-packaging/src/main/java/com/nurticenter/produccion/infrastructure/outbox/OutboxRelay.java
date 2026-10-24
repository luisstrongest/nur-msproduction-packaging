package com.nurticenter.produccion.infrastructure.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TRANSACTIONAL OUTBOX - publicacion (relay / polling publisher).
 *
 * Cada {@code app.outbox.poll-delay-ms} toma un lote de filas pendientes
 * (bloqueadas con SKIP LOCKED, asi varias replicas no se pisan), las publica en
 * Kafka esperando la confirmacion del broker (acks=all) y SOLO entonces las
 * marca como publicadas. Si Kafka no responde, la fila queda pendiente con su
 * error y se reintenta en el siguiente ciclo: el evento nunca se pierde.
 *
 * Orden por agregado: si falla un evento, los siguientes del mismo agregado
 * (misma key) esperan al proximo ciclo para no adelantarse.
 *
 * Garantia: at-least-once. Si el proceso cae entre la confirmacion de Kafka y
 * el commit, la fila se vuelve a publicar; los consumidores descartan
 * duplicados por eventId.
 */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class OutboxRelay {

    static final String CABECERA_NOMBRE = "eventName";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final SpringDataOutboxRepository repositorio;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock reloj;
    private final int tamanoLote;
    private final long esperaEnvioMs;
    private final Duration retencion;

    public OutboxRelay(SpringDataOutboxRepository repositorio,
                       KafkaTemplate<String, String> kafkaTemplate,
                       Clock reloj,
                       @Value("${app.outbox.batch-size:50}") int tamanoLote,
                       @Value("${app.outbox.send-timeout-ms:5000}") long esperaEnvioMs,
                       @Value("${app.outbox.retention-days:7}") int diasRetencion) {
        this.repositorio = repositorio;
        this.kafkaTemplate = kafkaTemplate;
        this.reloj = reloj;
        this.tamanoLote = tamanoLote;
        this.esperaEnvioMs = esperaEnvioMs;
        this.retencion = Duration.ofDays(diasRetencion);
    }

    /** @return cuantas filas se publicaron en este ciclo */
    @Scheduled(fixedDelayString = "${app.outbox.poll-delay-ms:1000}")
    @Transactional
    public int publicarPendientes() {
        List<OutboxJpaEntity> pendientes = repositorio.bloquearPendientes(tamanoLote);
        Set<String> agregadosDetenidos = new HashSet<>();
        int publicadas = 0;

        for (OutboxJpaEntity fila : pendientes) {
            if (agregadosDetenidos.contains(fila.getMessageKey())) {
                continue;
            }
            try {
                enviar(fila);
                fila.marcarPublicado(reloj.instant());
                publicadas++;
                log.info("[outbox] publicado {} key={} eventId={}", fila.getEventName(), fila.getMessageKey(), fila.getEventId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fila.registrarFallo("Interrumpido: " + e.getMessage());
                break;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                fila.registrarFallo(e.getClass().getSimpleName() + ": " + e.getMessage());
                agregadosDetenidos.add(fila.getMessageKey());
                log.warn("[outbox] no se pudo publicar {} key={} (intento {}); se reintentara: {}",
                        fila.getEventName(), fila.getMessageKey(), fila.getIntentos(), e.getMessage());
            }
        }
        return publicadas;
    }

    /** Borra las filas ya publicadas que superan la retencion (una vez por hora). */
    @Scheduled(fixedDelayString = "${app.outbox.cleanup-delay-ms:3600000}")
    @Transactional
    public int limpiarPublicados() {
        int borradas = repositorio.borrarPublicadosAntesDe(reloj.instant().minus(retencion));
        if (borradas > 0) {
            log.info("[outbox] {} eventos publicados eliminados por retencion", borradas);
        }
        return borradas;
    }

    private void enviar(OutboxJpaEntity fila) throws InterruptedException, ExecutionException, TimeoutException {
        var registro = new ProducerRecord<String, String>(fila.getTopic(), fila.getMessageKey(), fila.getPayload());
        registro.headers().add(CABECERA_NOMBRE, fila.getEventName().getBytes(StandardCharsets.UTF_8));
        // Espera la confirmacion del broker: sin ella la fila no se marca publicada
        kafkaTemplate.send(registro).get(esperaEnvioMs, TimeUnit.MILLISECONDS);
    }
}
