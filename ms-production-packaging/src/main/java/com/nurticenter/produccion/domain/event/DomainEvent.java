package com.nurticenter.produccion.domain.event;

import java.time.LocalDateTime;

/**
 * INTERFAZ BASE - DomainEvent
 *
 * Contrato minimo que deben cumplir todos los eventos de dominio del BC4.
 *
 * Principio OCP: se pueden agregar nuevos eventos sin modificar esta interfaz.
 * Principio ISP: la interfaz es minima; cada evento agrega sus propios datos.
 */
public interface DomainEvent {

    /**
     * Identificador unico del agregado que origino el evento.
     * Permite a los consumidores correlacionar el evento con su fuente.
     */
    String agregadoId();

    /**
     * Momento exacto en que ocurrio el hecho en el dominio.
     * No es el momento de publicacion al bus (eso lo agrega la infraestructura).
     */
    LocalDateTime occurredAt();

    /**
     * Nombre descriptivo del evento para logging y routing en el bus.
     * Convenio: "bc4.{nombreEvento}" - ej. "bc4.OrdenGenerada"
     */
    default String nombreEvento() {
        return "bc4." + this.getClass().getSimpleName();
    }
}
