package com.nurticenter.produccion.infrastructure.web.paquete.dto;

import com.nurticenter.produccion.application.paquete.dto.CrearPaqueteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * REQUEST - crear un paquete etiquetado para un paciente dentro de una orden.
 */
public record CrearPaqueteRequest(

        @Schema(example = "PAC-1001")
        @NotBlank(message = "pacienteId es obligatorio")
        String pacienteId,

        @Schema(example = "id de una orden existente")
        @NotBlank(message = "ordenId es obligatorio")
        String ordenId
) {
    public CrearPaqueteCommand aComando() {
        return new CrearPaqueteCommand(pacienteId, ordenId);
    }
}
