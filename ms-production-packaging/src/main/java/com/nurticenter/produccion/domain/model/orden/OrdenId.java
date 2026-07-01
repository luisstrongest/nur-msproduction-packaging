package com.nurticenter.produccion.domain.model.orden;

import java.util.Objects;
import java.util.UUID;

/**
 * VALUE OBJECT - OrdenId
 * Identificador unico e inmutable de una OrdenDeProduccion.
 * Wrappear UUID en un VO evita confusiones entre distintos tipos de Id en el dominio.
 */
public record OrdenId(String valor) {

    public OrdenId {
        Objects.requireNonNull(valor, "OrdenId no puede ser nulo.");
        if (valor.isBlank()) throw new IllegalArgumentException("OrdenId no puede ser vacio.");
    }

    /** Factory: genera un Id nuevo aleatorio. */
    public static OrdenId nuevo() {
        return new OrdenId(UUID.randomUUID().toString());
    }

    /** Factory: reconstruye desde persistencia. */
    public static OrdenId de(String valor) {
        return new OrdenId(valor);
    }
}
