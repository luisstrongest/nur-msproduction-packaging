package com.nurticenter.produccion.infrastructure.messaging;

import com.nurticenter.produccion.domain.DomainException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumidor Kafka del topic de BC3 (bc3.catering.eventos).
 *
 * De todo lo que publica BC3, a BC4 solo le interesa bc3.CalendarioConsolidadoCerrado;
 * el resto se ignora (filtrando por la cabecera eventName, sin parsear el JSON).
 *
 * Un calendario que se rechaza por una regla de negocio o por venir mal formado
 * se registra y se descarta: reintentarlo daria el mismo resultado y bloquearia
 * la particion. Los errores tecnicos (p. ej. la BD caida) se propagan para que
 * el contenedor de Kafka reintente.
 */
@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class EventosCateringListener {

    private static final Logger log = LoggerFactory.getLogger(EventosCateringListener.class);
    static final String CABECERA_EVENTO = "eventName";

    private final ConsumidorCalendarioConsolidado consumidor;
    private final LectorCalendarioConsolidado lector;

    public EventosCateringListener(ConsumidorCalendarioConsolidado consumidor,
                                   LectorCalendarioConsolidado lector) {
        this.consumidor = consumidor;
        this.lector = lector;
    }

    @KafkaListener(topics = "${app.kafka.consumo.catering-topic}", groupId = "${spring.application.name}")
    public void recibir(ConsumerRecord<String, String> registro) {
        String evento = nombreDe(registro);
        if (!LectorCalendarioConsolidado.EVENTO.equals(evento)) {
            log.debug("Ignorado {} (key={})", evento, registro.key());
            return;
        }
        try {
            if (consumidor.procesar(registro.value())) {
                log.info("Calendario {} procesado: orden y paquetes programados", registro.key());
            } else {
                log.info("Calendario {} repetido: ya estaba procesado", registro.key());
            }
        } catch (DomainException | IllegalArgumentException | IllegalStateException e) {
            log.warn("Calendario {} rechazado (partition={}, offset={}): {}",
                    registro.key(), registro.partition(), registro.offset(), e.getMessage());
        }
    }

    /** La cabecera eventName; si el productor no la envio, el campo del sobre. */
    private String nombreDe(ConsumerRecord<String, String> registro) {
        Header cabecera = registro.headers().lastHeader(CABECERA_EVENTO);
        return cabecera != null
                ? new String(cabecera.value(), StandardCharsets.UTF_8)
                : lector.nombreDe(registro.value());
    }
}
