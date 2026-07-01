package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se busca un ItemOrden por RecetaId y no existe en la orden.
 */
public class ItemNoEncontradoException extends DomainException {
    public ItemNoEncontradoException(String message) { super(message); }
}
