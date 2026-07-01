package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se intenta generar la etiqueta de un paquete
 * que aun no fue envasado.
 * Invariante: el envasado debe preceder al etiquetado.
 */
public class EtiquetaSinEnvasadoException extends DomainException {
    public EtiquetaSinEnvasadoException(String message) { super(message); }
}
