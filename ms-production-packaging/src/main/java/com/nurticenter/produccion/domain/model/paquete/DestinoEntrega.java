package com.nurticenter.produccion.domain.model.paquete;

import java.time.LocalDate;
import java.util.Objects;

/**
 * VALUE OBJECT - DestinoEntrega
 *
 * Datos de la entrega del CalendarioConsolidado de BC3 que BC5 (Logistica)
 * necesita para planificar la ruta del paquete: el dia del calendario, la
 * suscripcion, la direccion (id y coordenadas) y cuantas porciones lleva.
 *
 * Como DatosPaciente, llega desnormalizado: BC4 no consulta BC3 ni BC1.
 * Es opcional en el paquete: los paquetes creados a mano (API REST) no lo tienen.
 * idDireccion, latitud y longitud pueden faltar si BC3 no los conoce.
 */
public record DestinoEntrega(
        String idCalendarioDia,
        String idSuscripcion,
        LocalDate fechaEntrega,
        String idDireccion,
        Double latitud,
        Double longitud,
        int nroPorciones
) {
    public DestinoEntrega {
        Objects.requireNonNull(idCalendarioDia, "idCalendarioDia es obligatorio.");
        Objects.requireNonNull(idSuscripcion,   "idSuscripcion es obligatorio.");
        Objects.requireNonNull(fechaEntrega,    "fechaEntrega es obligatoria.");
        if (nroPorciones <= 0)
            throw new IllegalArgumentException("nroPorciones debe ser mayor a cero.");
        if (latitud != null && (latitud < -90 || latitud > 90))
            throw new IllegalArgumentException("latitud fuera de rango: " + latitud);
        if (longitud != null && (longitud < -180 || longitud > 180))
            throw new IllegalArgumentException("longitud fuera de rango: " + longitud);
    }
}
