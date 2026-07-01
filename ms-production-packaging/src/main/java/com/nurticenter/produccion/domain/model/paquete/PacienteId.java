package com.nurticenter.produccion.domain.model.paquete;

import java.util.Objects;

/**
 * VALUE OBJECT - PacienteId
 *
 * Referencia opaca al Paciente que vive en BC1.
 * BC4 solo necesita este Id para:
 *   1. Vincularlo al PaqueteEtiquetado.
 *   2. Incluirlo en el evento PaqueteListo para que BC5 pueda rastrearlo.
 *
 * BC4 NUNCA consulta BC1 para obtener datos del paciente.
 * Los datos necesarios (nombre, direccion) llegan desnormalizados
 * en el evento CalendarioConsolidado -> DatosPaciente.
 */
public record PacienteId(String valor) {

    public PacienteId {
        Objects.requireNonNull(valor, "PacienteId no puede ser nulo.");
        if (valor.isBlank()) throw new IllegalArgumentException("PacienteId no puede ser vacio.");
    }

    public static PacienteId de(String valor) {
        return new PacienteId(valor);
    }
}
