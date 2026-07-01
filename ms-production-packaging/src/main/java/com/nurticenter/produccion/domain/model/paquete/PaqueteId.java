package com.nurticenter.produccion.domain.model.paquete;

import java.util.Objects;
import java.util.UUID;

/**
 * VALUE OBJECT - PaqueteId
 * Identificador unico e inmutable de un PaqueteEtiquetado.
 */
public record PaqueteId(String valor) {

    public PaqueteId {
        Objects.requireNonNull(valor, "PaqueteId no puede ser nulo.");
        if (valor.isBlank()) throw new IllegalArgumentException("PaqueteId no puede ser vacio.");
    }

    public static PaqueteId nuevo() {
        return new PaqueteId(UUID.randomUUID().toString());
    }

    public static PaqueteId de(String valor) {
        return new PaqueteId(valor);
    }
}
