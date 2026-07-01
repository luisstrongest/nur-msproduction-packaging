package com.nurticenter.produccion.domain.model.paquete;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * VALUE OBJECT - CodigoIdentificacion
 *
 * Codigo unico que identifica un PaqueteEtiquetado en todo el sistema.
 * Se genera a partir del PaqueteId para garantizar unicidad.
 * Inmutable e inalterable una vez generado.
 *
 * Nota: la verificacion de unicidad global corresponde a la infraestructura
 * (repositorio). El dominio garantiza que el codigo se genera a partir del Id.
 */
public record CodigoIdentificacion(String valor, LocalDateTime generadoEn) {

    public CodigoIdentificacion {
        Objects.requireNonNull(valor,       "El valor del codigo es obligatorio.");
        Objects.requireNonNull(generadoEn,  "La fecha de generacion es obligatoria.");
        if (valor.isBlank())
            throw new IllegalArgumentException("El codigo no puede estar vacio.");
    }

    /**
     * Genera un codigo unico derivado del PaqueteId.
     * Formato: "PKG-{primeros 8 chars del UUID en mayusculas}"
     * Ejemplo: "PKG-3F2A1B4C"
     */
    public static CodigoIdentificacion generar(PaqueteId paqueteId) {
        String sufijo = paqueteId.valor()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return new CodigoIdentificacion("PKG-" + sufijo, LocalDateTime.now());
    }

    /** Factory para reconstruir desde persistencia. */
    public static CodigoIdentificacion de(String valor, LocalDateTime generadoEn) {
        return new CodigoIdentificacion(valor, generadoEn);
    }
}
