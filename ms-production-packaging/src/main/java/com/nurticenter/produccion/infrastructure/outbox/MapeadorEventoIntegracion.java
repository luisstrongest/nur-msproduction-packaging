package com.nurticenter.produccion.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.event.DomainEvent;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.event.PaqueteListo;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL de salida: traduce un evento de dominio al SOBRE COMUN de integracion
 * (nur_proy_final/contracts/README.md). El dominio no conoce este formato.
 *
 *   eventId, eventName, eventVersion, occurredOn (UTC), source, correlationId, data
 *
 * correlationId: todos los eventos de una orden comparten "corr-orden-{ordenId}".
 */
public class MapeadorEventoIntegracion {

    static final String VERSION = "1.0";
    static final String ORIGEN = "MsProductionPackaging";
    static final String PREFIJO_CORRELACION = "corr-orden-";

    private final ObjectMapper objectMapper;
    private final ZoneId zonaDelDominio;

    /**
     * @param zonaDelDominio zona en la que el dominio registra occurredAt
     *                       (LocalDateTime), para convertirlo a UTC
     */
    public MapeadorEventoIntegracion(ObjectMapper objectMapper, ZoneId zonaDelDominio) {
        this.objectMapper = objectMapper;
        this.zonaDelDominio = zonaDelDominio;
    }

    /** Mensaje listo para guardar en el outbox. */
    public record MensajeIntegracion(String eventId, String eventName, String key, String json) {
    }

    record Sobre(String eventId, String eventName, String eventVersion, String occurredOn,
                 String source, String correlationId, Map<String, Object> data) {
    }

    public MensajeIntegracion mapear(DomainEvent evento) {
        String eventId = UUID.randomUUID().toString();
        var sobre = new Sobre(
                eventId,
                evento.nombreEvento(),
                VERSION,
                evento.occurredAt().atZone(zonaDelDominio).toInstant().toString(),
                ORIGEN,
                PREFIJO_CORRELACION + ordenDe(evento),
                datos(evento));
        try {
            return new MensajeIntegracion(eventId, evento.nombreEvento(), evento.agregadoId(),
                    objectMapper.writeValueAsString(sobre));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar " + evento.nombreEvento(), e);
        }
    }

    private static String ordenDe(DomainEvent evento) {
        return evento instanceof PaqueteListo p ? p.ordenId() : evento.agregadoId();
    }

    /** Campos de "data" con los nombres del contrato de cada evento. */
    private Map<String, Object> datos(DomainEvent evento) {
        var data = new LinkedHashMap<String, Object>();
        switch (evento) {
            case OrdenGenerada e -> {
                data.put("idOrdenProduccion", e.ordenId());
                data.put("fechaProduccion", e.fechaProduccion().toString());
                data.put("totalItems", e.totalItems());
            }
            case OrdenCompletada e -> {
                data.put("idOrdenProduccion", e.ordenId());
                data.put("totalPaquetes", e.paqueteIds().size());
                data.put("paquetesIds", e.paqueteIds());
            }
            case PaqueteListo e -> {
                data.put("idPaquete", e.paqueteId());
                data.put("etiqueta", e.codigoIdentificacion());
                data.put("idOrdenProduccion", e.ordenId());
                data.put("idPaciente", e.pacienteId());
            }
            default -> data.put("agregadoId", evento.agregadoId());
        }
        return data;
    }
}
