package com.nurticenter.produccion.domain.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * EVENTO DE DOMINIO - OrdenGenerada
 *
 * Hecho: una OrdenDeProduccion fue creada exitosamente.
 *
 * Publicado por: OrdenDeProduccion#crear()
 * Consumidores esperados:
 *   - Notificaciones: avisa al encargado de cocina que hay una nueva orden.
 *   - Auditoria: registra el inicio de la jornada de produccion.
 *
 * Contiene solo los datos necesarios para los consumidores;
 * NO expone la lista de items ni los detalles internos de la orden.
 */
public record OrdenGenerada(
        String ordenId,
        LocalDate fechaProduccion,
        int totalItems,
        LocalDateTime occurredAt
) implements DomainEvent {

    @Override
    public String agregadoId() { return ordenId; }
}
