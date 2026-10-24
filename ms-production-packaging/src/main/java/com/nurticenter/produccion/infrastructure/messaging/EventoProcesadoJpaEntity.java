package com.nurticenter.produccion.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * INBOX - evento de integracion ya procesado.
 *
 * Kafka entrega at-least-once: el mismo evento puede llegar dos veces. Su eventId
 * se guarda en la misma transaccion que el efecto del evento; si vuelve a llegar,
 * se ignora.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesadoJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesadoJpaEntity() {
        // requerido por JPA
    }

    public EventoProcesadoJpaEntity(String eventId, String eventName, Instant procesadoEn) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.procesadoEn = procesadoEn;
    }

    public String getEventId()     { return eventId; }
    public String getEventName()   { return eventName; }
    public Instant getProcesadoEn() { return procesadoEn; }
}
