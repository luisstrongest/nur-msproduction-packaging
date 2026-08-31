package com.nurticenter.produccion.infrastructure.web.paquete.dto;

import com.nurticenter.produccion.application.paquete.dto.GenerarEtiquetaCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * REQUEST - datos del paciente (desnormalizados) para generar la etiqueta.
 */
public record GenerarEtiquetaRequest(

        @Schema(example = "Maria Perez")
        @NotBlank(message = "nombreCompleto es obligatorio")
        String nombreCompleto,

        @Schema(example = "Av. Mutualista")
        @NotBlank(message = "calle es obligatoria")
        String calle,

        @Schema(example = "450")
        @NotBlank(message = "numero es obligatorio")
        String numero,

        @Schema(example = "Edificio azul, piso 2", nullable = true)
        String referencia,

        @Schema(example = "Santa Cruz")
        @NotBlank(message = "ciudad es obligatoria")
        String ciudad
) {
    public GenerarEtiquetaCommand aComando() {
        return new GenerarEtiquetaCommand(nombreCompleto, calle, numero, referencia, ciudad);
    }
}
