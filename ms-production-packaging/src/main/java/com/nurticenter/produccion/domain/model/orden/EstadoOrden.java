package com.nurticenter.produccion.domain.model.orden;

/**
 * VALUE OBJECT (enum) - EstadoOrden
 *
 * Ciclo de vida de la OrdenDeProduccion.
 * Las transiciones validas son:
 *   PENDIENTE -> EN_PREPARACION -> COMPLETADA
 * No se permiten retrocesos ni saltos.
 */
public enum EstadoOrden {
    PENDIENTE,
    EN_PREPARACION,
    COMPLETADA
}
