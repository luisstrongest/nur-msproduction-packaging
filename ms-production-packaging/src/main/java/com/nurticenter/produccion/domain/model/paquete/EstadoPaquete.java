package com.nurticenter.produccion.domain.model.paquete;

/**
 * VALUE OBJECT (enum) - EstadoPaquete
 *
 * Ciclo de vida del PaqueteEtiquetado.
 * Transiciones validas:
 *   ENVASADO_PENDIENTE -> ENVASADO -> ETIQUETADO -> LISTO
 */
public enum EstadoPaquete {
    ENVASADO_PENDIENTE,
    ENVASADO,
    ETIQUETADO,
    LISTO
}
