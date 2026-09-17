package com.nurticenter.logistica.bc4;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cuerpo de error estandar de BC4. BC5 lo necesita para distinguir un paquete
 * inexistente (404) de una caida del servicio, y para registrar el motivo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiErrorDto(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        List<String> detalles
) {}
