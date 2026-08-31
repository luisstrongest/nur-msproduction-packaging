package com.nurticenter.produccion.infrastructure.web.orden.dto;

import com.nurticenter.produccion.application.orden.dto.GenerarOrdenCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.List;

/**
 * REQUEST - generar una orden de produccion (simula el CalendarioConsolidado de BC3).
 */
public record GenerarOrdenRequest(

        @Schema(example = "2026-07-15")
        @NotNull(message = "fechaProduccion es obligatoria")
        LocalDate fechaProduccion,

        @NotEmpty(message = "La orden debe tener al menos un item")
        @Valid
        List<ItemRequest> items
) {

    public record ItemRequest(

            @Schema(example = "REC-001")
            @NotBlank(message = "recetaId es obligatorio")
            String recetaId,

            @Schema(example = "Pure de zapallo con pollo")
            @NotBlank(message = "nombreReceta es obligatorio")
            String nombreReceta,

            @Schema(example = "12", description = "Numero de pacientes que reciben esta receta")
            @Positive(message = "numeroPacientes debe ser mayor a cero")
            int numeroPacientes,

            @Schema(example = "250")
            @Positive(message = "cantidadPorcion debe ser mayor a cero")
            double cantidadPorcion,

            @Schema(example = "GRAMOS", allowableValues = {"GRAMOS", "MILILITROS", "UNIDADES"})
            @NotBlank(message = "unidadMedida es obligatoria")
            String unidadMedida,

            @Schema(example = "Porcion individual estandar")
            @NotBlank(message = "descripcionPorcion es obligatoria")
            String descripcionPorcion
    ) {}

    /** Traduce el request web al comando de la capa de aplicacion. */
    public GenerarOrdenCommand aComando() {
        List<GenerarOrdenCommand.ItemCommand> itemCommands = items.stream()
                .map(i -> new GenerarOrdenCommand.ItemCommand(
                        i.recetaId(), i.nombreReceta(), i.numeroPacientes(),
                        i.cantidadPorcion(), i.unidadMedida(), i.descripcionPorcion()))
                .toList();
        return new GenerarOrdenCommand(fechaProduccion, itemCommands);
    }
}
