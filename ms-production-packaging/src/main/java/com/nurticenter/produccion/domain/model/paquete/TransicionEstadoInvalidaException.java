package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se intenta una transicion de estado no permitida.
 * Aplica tanto a OrdenDeProduccion como a PaqueteEtiquetado.
 *
 * Ejemplos:
 *   - Intentar completar una orden PENDIENTE (debe pasar por EN_PREPARACION primero)
 *   - Intentar etiquetar un paquete que no fue envasado
 */
public class TransicionEstadoInvalidaException extends DomainException {
    public TransicionEstadoInvalidaException(String message) { super(message); }
}
