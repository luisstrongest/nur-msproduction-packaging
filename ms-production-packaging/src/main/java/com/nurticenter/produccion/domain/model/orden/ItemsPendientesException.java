package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se intenta completar una orden con items en estado PENDIENTE.
 * Invariante: todos los items deben estar PREPARADOS antes de completar la orden.
 */
public class ItemsPendientesException extends DomainException {
    public ItemsPendientesException(String message) { super(message); }
}
