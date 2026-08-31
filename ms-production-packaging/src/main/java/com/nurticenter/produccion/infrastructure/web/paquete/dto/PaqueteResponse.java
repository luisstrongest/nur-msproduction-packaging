package com.nurticenter.produccion.infrastructure.web.paquete.dto;

import com.nurticenter.produccion.domain.model.paquete.Etiqueta;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;

import java.time.LocalDateTime;

/**
 * RESPONSE - vista de lectura de un PaqueteEtiquetado.
 */
public record PaqueteResponse(
        String id,
        String pacienteId,
        String ordenId,
        String estado,
        EtiquetaResponse etiqueta   // null hasta el estado ETIQUETADO
) {

    public record EtiquetaResponse(
            String nombrePaciente,
            String direccion,
            String codigoIdentificacion,
            LocalDateTime fechaGeneracion
    ) {
        static EtiquetaResponse desde(Etiqueta etiqueta) {
            return new EtiquetaResponse(
                    etiqueta.nombrePaciente(),
                    etiqueta.direccion().formatoParaEtiqueta(),
                    etiqueta.codigo().valor(),
                    etiqueta.fechaGeneracion());
        }
    }

    public static PaqueteResponse desde(PaqueteEtiquetado paquete) {
        EtiquetaResponse etiqueta = (paquete.etiqueta() != null)
                ? EtiquetaResponse.desde(paquete.etiqueta())
                : null;
        return new PaqueteResponse(
                paquete.id().valor(),
                paquete.pacienteId().valor(),
                paquete.ordenId(),
                paquete.estado().name(),
                etiqueta);
    }
}
