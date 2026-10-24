package com.nurticenter.produccion.application.calendario.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * COMANDO - ProgramarProduccionCommand
 *
 * El CalendarioConsolidado que BC3 cierra para un dia de entrega, ya traducido
 * desde el evento de integracion (bc3.CalendarioConsolidadoCerrado) por la
 * infraestructura. Datos planos, sin comportamiento de dominio.
 */
public record ProgramarProduccionCommand(
        String idCalendario,
        LocalDate fechaEntrega,
        List<EntregaCommand> entregas
) {

    /** Una entrega del dia: un paciente, su direccion y las recetas que recibe. */
    public record EntregaCommand(
            String idCalendarioDia,
            String idSuscripcion,
            String idPaciente,
            String idDireccion,
            Double latitud,
            Double longitud,
            List<RecetaCommand> recetas
    ) {}

    /** Una receta de la entrega con la porcion para ese paciente. */
    public record RecetaCommand(
            String idReceta,
            String nombre,
            String hora,
            double porcionGramos
    ) {}
}
