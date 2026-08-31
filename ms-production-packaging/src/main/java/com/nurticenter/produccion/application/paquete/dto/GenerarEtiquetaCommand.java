package com.nurticenter.produccion.application.paquete.dto;

/**
 * COMANDO - GenerarEtiquetaCommand
 *
 * Datos del paciente desnormalizados (llegan del CalendarioConsolidado de BC3)
 * necesarios para generar la Etiqueta inmutable del paquete.
 * BC4 no consulta BC1: estos datos vienen incluidos en el comando.
 */
public record GenerarEtiquetaCommand(
        String nombreCompleto,
        String calle,
        String numero,
        String referencia,   // opcional
        String ciudad
) {}
