package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se intenta modificar una OrdenDeProduccion ya COMPLETADA.
 * Invariante: una orden completada es inmutable.
 */
public class OrdenYaCompletadaException extends DomainException {
    public OrdenYaCompletadaException(String message) { super(message); }
}
