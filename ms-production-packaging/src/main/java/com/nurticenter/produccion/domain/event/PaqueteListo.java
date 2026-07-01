package com.nurticenter.produccion.domain.event;

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
 * el paquete fisico con su registro digital sin consultar BC4.
 */
public record PaqueteListo(
        String paqueteId,
        String pacienteId,
        String codigoIdentificacion,
        LocalDateTime occurredAt
) implements DomainEvent {

    @Override
    public String agregadoId() { return paqueteId; }
}
