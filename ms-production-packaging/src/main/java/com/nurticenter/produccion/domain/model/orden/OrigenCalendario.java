package com.nurticenter.produccion.domain.model.orden;

import java.time.LocalDate;
import java.util.Objects;

/**
 * VALUE OBJECT - OrigenCalendario
 *
 * Referencia al CalendarioConsolidado de BC3 que origino la orden: el id del
 * calendario (referencia opaca) y el dia en que se entregan los paquetes.
 *
 * Es opcional en la orden: las ordenes creadas a mano (API REST) no lo tienen.
 */
public record OrigenCalendario(String idCalendario, LocalDate fechaEntrega) {

    public OrigenCalendario {
        Objects.requireNonNull(idCalendario, "idCalendario es obligatorio.");
        Objects.requireNonNull(fechaEntrega, "fechaEntrega es obligatoria.");
        if (idCalendario.isBlank())
            throw new IllegalArgumentException("idCalendario no puede estar vacio.");
    }
}
