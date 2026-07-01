package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando se intenta marcar LISTO un paquete con etiqueta invalida o ausente.
 * Invariante: un paquete sin etiqueta valida no puede transferirse a BC5.
 */
public class EtiquetaInvalidaException extends DomainException {
    public EtiquetaInvalidaException(String message) { super(message); }
}
