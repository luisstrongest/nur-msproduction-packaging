package com.nurticenter.produccion.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Fila de la tabla OUTBOX: un evento de integracion pendiente de publicar.
 *
 * Se inserta en la MISMA transaccion que el agregado que origino el evento
 * ({@link OutboxEventWriter}); el {@link OutboxRelay} la publica en Kafka y
 * solo entonces completa {@code publicadoEn}. publicadoEn == null -> pendiente.
 */
@Entity
@Table(name = "outbox", indexes = @Index(name = "ix_outbox_pendientes", columnList = "publicado_en, id"))
public class OutboxJpaEntity {

    static final int LARGO_ERROR = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "event_name", nullable = false, length = 100)
    private String eventName;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /** Id del agregado: key del mensaje en Kafka. */
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    /** Sobre completo en JSON, tal como se publicara. */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "ultimo_error", length = LARGO_ERROR)
    private String ultimoError;

    protected OutboxJpaEntity() {
        // requerido por JPA
    }

    public OutboxJpaEntity(String eventId, String eventName, String topic, String messageKey,
                           String payload, Instant creadoEn) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.creadoEn = creadoEn;
    }

    public void marcarPublicado(Instant momento) {
        this.publicadoEn = momento;
        this.ultimoError = null;
    }

    public void registrarFallo(String error) {
        this.intentos++;
        this.ultimoError = error != null && error.length() > LARGO_ERROR ? error.substring(0, LARGO_ERROR) : error;
    }

    public Long getId()             { return id; }
    public String getEventId()      { return eventId; }
    public String getEventName()    { return eventName; }
    public String getTopic()        { return topic; }
    public String getMessageKey()   { return messageKey; }
    public String getPayload()      { return payload; }
    public Instant getCreadoEn()    { return creadoEn; }
    public Instant getPublicadoEn() { return publicadoEn; }
    public int getIntentos()        { return intentos; }
    public String getUltimoError()  { return ultimoError; }
}
