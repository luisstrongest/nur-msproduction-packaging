package com.nurticenter.produccion.infrastructure.event;

import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.domain.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ADAPTADOR DE SALIDA - implementa el puerto EventPublisher.
 *
 * Publica los eventos de dominio en el bus interno de Spring
 * ({@link ApplicationEventPublisher}) y los registra en el log.
 *
 * En un despliegue real, aqui se enrutarian a Kafka / RabbitMQ para
 * que otros bounded contexts (BC5) los consuman.
 */
@Component
public class SpringEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringEventPublisher.class);

    private final ApplicationEventPublisher springPublisher;

    public SpringEventPublisher(ApplicationEventPublisher springPublisher) {
        this.springPublisher = springPublisher;
    }

    @Override
    public void publicar(List<Object> eventos) {
        if (eventos == null || eventos.isEmpty()) {
            return;
        }
        for (Object evento : eventos) {
            if (evento instanceof DomainEvent de) {
                log.info("Publicando evento de dominio [{}] agregado={} occurredAt={}",
                        de.nombreEvento(), de.agregadoId(), de.occurredAt());
            } else {
                log.info("Publicando evento [{}]", evento.getClass().getSimpleName());
            }
            springPublisher.publishEvent(evento);
        }
    }
}
