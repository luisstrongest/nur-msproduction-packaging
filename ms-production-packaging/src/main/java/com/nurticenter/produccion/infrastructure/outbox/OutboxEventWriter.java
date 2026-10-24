package com.nurticenter.produccion.infrastructure.outbox;

import com.nurticenter.produccion.domain.event.DomainEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * TRANSACTIONAL OUTBOX - escritura.
 *
 * Escucha los eventos de dominio que publica SpringEventPublisher (de forma
 * sincrona, en el mismo hilo del caso de uso) y guarda cada uno en la tabla
 * outbox DENTRO de la transaccion del caso de uso:
 *
 *   - si la transaccion hace commit, el agregado y el evento quedan guardados juntos;
 *   - si hace rollback, ninguno de los dos existe (no hay eventos "fantasma");
 *   - si falla el insert del outbox, falla tambien el caso de uso.
 *
 * MANDATORY: un evento publicado fuera de una transaccion es un error de
 * programacion y se rechaza en vez de perderse en silencio.
 *
 * La publicacion en Kafka la hace despues {@link OutboxRelay}.
 */
@Component
public class OutboxEventWriter {

    private final SpringDataOutboxRepository repositorio;
    private final MapeadorEventoIntegracion mapeador;
    private final Clock reloj;
    private final String topic;

    public OutboxEventWriter(SpringDataOutboxRepository repositorio,
                             MapeadorEventoIntegracion mapeador,
                             Clock reloj,
                             @Value("${app.kafka.topic}") String topic) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
        this.reloj = reloj;
        this.topic = topic;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardar(DomainEvent evento) {
        var mensaje = mapeador.mapear(evento);
        repositorio.save(new OutboxJpaEntity(
                mensaje.eventId(),
                mensaje.eventName(),
                topic,
                mensaje.key(),
                mensaje.json(),
                reloj.instant()));
    }
}
