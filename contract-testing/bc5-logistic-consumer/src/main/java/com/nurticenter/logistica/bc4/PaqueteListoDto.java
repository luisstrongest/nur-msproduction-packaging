package com.nurticenter.logistica.bc4;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Vista que BC5 (Entrega y Logistica) tiene de un paquete de BC4.
 *
 * Es deliberadamente MAS PEQUEÑA que la respuesta de BC4: el consumidor solo
 * declara los campos que realmente usa para planificar la ruta de entrega.
 * De ahi el @JsonIgnoreProperties(ignoreUnknown = true): si BC4 añade campos
 * nuevos, BC5 no se rompe. Esa tolerancia es la que permite que el pacto
 * exija solo lo necesario en vez de congelar toda la respuesta del proveedor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaqueteListoDto(
        String id,
        String pacienteId,
        String ordenId,
        String estado,
        EtiquetaDto etiqueta
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EtiquetaDto(
            String nombrePaciente,
            String direccion,
            String codigoIdentificacion,
            LocalDateTime fechaGeneracion
    ) {}

    /** Un paquete solo se transfiere a BC5 cuando esta LISTO y tiene etiqueta. */
    public boolean esTransferible() {
        return "LISTO".equals(estado)
                && etiqueta != null
                && etiqueta.direccion() != null
                && !etiqueta.direccion().isBlank();
    }
}
