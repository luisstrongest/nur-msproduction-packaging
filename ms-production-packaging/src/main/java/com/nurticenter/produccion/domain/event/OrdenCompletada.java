package com.nurticenter.produccion.domain.event;

import com.nurticenter.produccion.domain.model.orden.OrigenCalendario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * EVENTO DE DOMINIO - OrdenCompletada
 *
 * Hecho: la OrdenDeProduccion fue completada; todos los paquetes estan listos.
 *
 * Publicado por: OrdenDeProduccion#completar()
 * Consumidores esperados:
 *   - BC5 (Logistica): recibe la lista de paqueteIds para iniciar la asignacion de rutas.
 *   - Administracion: actualiza el estado de la jornada de produccion.
 *
 * La lista de paqueteIds permite a BC5 consultar los paquetes
 * sin necesidad de conocer la estructura de la OrdenDeProduccion.
 *
 * origen es null si la orden no vino de un CalendarioConsolidado de BC3.
 * totalRecetasPreparadas = suma de las porciones de todos los items.
 */
public record OrdenCompletada(
        String ordenId,
        LocalDate fechaProduccion,
        OrigenCalendario origen,
        int totalRecetasPreparadas,
        List<String> paqueteIds,
        LocalDateTime occurredAt
) implements DomainEvent {

    public OrdenCompletada {
        // Defensa: lista inmutable en el evento
        paqueteIds = List.copyOf(paqueteIds);
    }

    @Override
    public String agregadoId() { return ordenId; }
}
