package com.nurticenter.produccion.application.orden.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * COMANDO - GenerarOrdenCommand
 *
 * Representa la intencion de generar una OrdenDeProduccion a partir del
 * CalendarioConsolidado publicado por BC3.
 *
 * Es un DTO de la capa de aplicacion: datos planos, sin comportamiento de dominio.
 * La capa de aplicacion (ACL) lo traduce a los ItemOrden del dominio.
 */
public record GenerarOrdenCommand(
        LocalDate fechaProduccion,
        List<ItemCommand> items
) {

    /**
     * Item del calendario consolidado: una receta con la porcion por paciente
     * y el numero de pacientes que la reciben ese dia.
     */
    public record ItemCommand(
            String recetaId,
            String nombreReceta,
            int numeroPacientes,
            double cantidadPorcion,
            String unidadMedida,   // GRAMOS | MILILITROS | UNIDADES
            String descripcionPorcion
    ) {}
}
