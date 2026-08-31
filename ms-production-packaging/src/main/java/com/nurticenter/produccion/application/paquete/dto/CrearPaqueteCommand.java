package com.nurticenter.produccion.application.paquete.dto;

/**
 * COMANDO - CrearPaqueteCommand
 *
 * Solicita la creacion de un PaqueteEtiquetado para un paciente,
 * asociado a una OrdenDeProduccion existente.
 */
public record CrearPaqueteCommand(
        String pacienteId,
        String ordenId
) {}
