package com.nurticenter.produccion.domain.event;

import com.nurticenter.produccion.domain.model.paquete.DestinoEntrega;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;

import java.time.LocalDateTime;

/**
 * EVENTO DE DOMINIO - PaqueteListo
 *
 * Hecho: un PaqueteEtiquetado paso a estado LISTO.
 *
 * Publicado por: PaqueteEtiquetado#marcarListo()
 * Consumidores esperados:
 *   - BC5 (Logistica): puede pre-registrar el paquete antes de recibir OrdenCompletada.
 *   - Auditoria: trazabilidad a nivel de paquete individual.
 *
 * Incluye el codigoIdentificacion para que BC5 pueda correlacionar
 * el paquete fisico con su registro digital sin consultar BC4, y la orden a la
 * que pertenece para correlacionarlo con el resto de eventos de esa orden.
 *
 * Lleva lo impreso en la etiqueta (nombre y direccion): es lo que se entrega.
 * destino es null si el paquete no vino de un CalendarioConsolidado de BC3.
 */
public record PaqueteListo(
        String paqueteId,
        String ordenId,
        String pacienteId,
        String codigoIdentificacion,
        String nombrePaciente,
        DireccionImpresa direccion,
        DestinoEntrega destino,
        LocalDateTime occurredAt
) implements DomainEvent {

    @Override
    public String agregadoId() { return paqueteId; }
}
