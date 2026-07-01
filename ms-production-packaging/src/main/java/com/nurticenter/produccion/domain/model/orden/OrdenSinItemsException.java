package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se intenta crear una OrdenDeProduccion sin items.
 * Invariante: una orden debe tener al menos un ItemOrden.
 */
public class OrdenSinItemsException extends DomainException {
    public OrdenSinItemsException(String message) { super(message); }
}
