package com.nurticenter.produccion.infrastructure.web.error;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cuerpo estandar de respuesta de error de la API.
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        List<String> detalles
) {
    public static ApiError de(int status, String error, String mensaje, List<String> detalles) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje,
                detalles == null ? List.of() : detalles);
    }
}
