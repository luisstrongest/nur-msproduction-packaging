package com.nurticenter.produccion.domain.model.paquete;

import java.util.Objects;

/**
 * VALUE OBJECT - DatosPaciente
 *
 * Contenedor de los datos del paciente necesarios para etiquetar el paquete.
 * Llega desnormalizado en el evento CalendarioConsolidado de BC3.
 *
 * DECISION DE DISENO CRITICA:
 * BC4 no consulta BC1 para obtener nombre ni direccion.
 * BC3 los incluye en el evento para desacoplar temporalmente a BC4 de BC1.
 * Si BC4 tuviera que consultar BC1, un fallo en BC1 paralizaria la produccion.
 *
 * Contiene PacienteId (referencia opaca) + datos minimos para la etiqueta.
 */
public record DatosPaciente(
        PacienteId id,
        String nombreCompleto,
        DireccionImpresa direccionDelDia
) {
    public DatosPaciente {
        Objects.requireNonNull(id,             "PacienteId es obligatorio.");
        Objects.requireNonNull(nombreCompleto, "El nombre del paciente es obligatorio.");
        Objects.requireNonNull(direccionDelDia,"La direccion del dia es obligatoria.");
    }

    /**
     * Valida que los datos son suficientes para generar una Etiqueta valida.
     */
    public boolean esCompleto() {
        return !nombreCompleto.isBlank() && direccionDelDia.estaCompleta();
    }
}
