package com.nurticenter.produccion.application.event;

import java.util.List;

/**
 * PUERTO DE SALIDA - EventPublisher
 *
 * Abstraccion que la capa de aplicacion usa para publicar los eventos de dominio
 * que los agregados acumulan (patron outbox: {@code agregado.pullEventos()}).
 *
 * Principio DIP: la aplicacion define el contrato; la infraestructura decide
 * la tecnologia concreta (bus interno de Spring, Kafka, RabbitMQ, log, etc.).
 */
public interface EventPublisher {

    /**
     * Publica una lista de eventos de dominio.
     * @param eventos eventos acumulados por un agregado (nunca null; puede ser vacia)
     */
    void publicar(List<Object> eventos);
}
