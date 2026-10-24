package com.nurticenter.produccion.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.event.DomainEvent;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import com.nurticenter.produccion.domain.model.orden.OrigenCalendario;
import com.nurticenter.produccion.domain.model.paquete.DestinoEntrega;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * ACL de salida: traduce un evento de dominio al SOBRE COMUN de integracion
 * (nur_proy_final/contracts/README.md). El dominio no conoce este formato.
 *
 *   eventId, eventName, eventVersion, occurredOn, source, correlationId, data
 *
 * Toda fecha con hora se publica en hora de Bolivia (-04:00), como pide el contrato.
 *
 * Los campos de "data" llevan los nombres del contrato. Los que dependen del
 * calendario de BC3 (idCalendario, idCalendarioDia, coordenadas...) viajan en
 * null cuando la orden o el paquete se crearon a mano por la API REST.
 */
public class MapeadorEventoIntegracion {

    static final String VERSION = "1.0";
    static final String ORIGEN = "MsProductionPackaging";
    static final String PREFIJO_CORRELACION = "corr-orden-";
    /** Zona de las fechas publicadas: Bolivia, sin horario de verano. */
    static final ZoneId ZONA_PUBLICADA = ZoneId.of("America/La_Paz");

    private final ObjectMapper objectMapper;
    private final ZoneId zonaDelDominio;

    /**
     * @param zonaDelDominio zona en la que el dominio registra occurredAt
     *                       (LocalDateTime), para convertirlo a hora de Bolivia
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

    public MensajeIntegracion mapear(DomainEvent evento, String correlationId) {
        String eventId = UUID.randomUUID().toString();
        var sobre = new Sobre(
                eventId,
                evento.nombreEvento(),
                VERSION,
                conZona(evento.occurredAt()),
                ORIGEN,
                correlationId,
                datos(evento));
        try {
            return new MensajeIntegracion(eventId, evento.nombreEvento(), evento.agregadoId(),
                    objectMapper.writeValueAsString(sobre));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar " + evento.nombreEvento(), e);
        }
    }

    /** Orden a la que pertenece el evento: todos los eventos de una orden comparten correlacion. */
    public static String ordenDe(DomainEvent evento) {
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
                Optional<OrigenCalendario> origen = Optional.ofNullable(e.origen());
                data.put("idOrdenProduccion", e.ordenId());
                data.put("idCalendario", origen.map(OrigenCalendario::idCalendario).orElse(null));
                data.put("fechaProduccion", e.fechaProduccion().toString());
                data.put("fechaEntrega", origen.map(o -> o.fechaEntrega().toString()).orElse(null));
                data.put("totalRecetasPreparadas", e.totalRecetasPreparadas());
                data.put("totalPaquetes", e.paqueteIds().size());
                data.put("paquetesIds", e.paqueteIds());
                data.put("fechaCierre", conZona(e.occurredAt()));
            }
            case PaqueteListo e -> {
                Optional<DestinoEntrega> destino = Optional.ofNullable(e.destino());
                data.put("idPaquete", e.paqueteId());
                data.put("etiqueta", e.codigoIdentificacion());
                data.put("idOrdenProduccion", e.ordenId());
                data.put("idCalendarioDia", del(destino, DestinoEntrega::idCalendarioDia));
                data.put("idSuscripcion", del(destino, DestinoEntrega::idSuscripcion));
                data.put("idPaciente", e.pacienteId());
                data.put("nombrePaciente", e.nombrePaciente());
                data.put("fechaEntrega", del(destino, d -> d.fechaEntrega().toString()));
                data.put("direccionEntrega", direccionEntrega(e, destino));
                data.put("nroPorciones", del(destino, DestinoEntrega::nroPorciones));
            }
            default -> data.put("agregadoId", evento.agregadoId());
        }
        return data;
    }

    /** Lo impreso en la etiqueta + id y coordenadas de la direccion del calendario. */
    private static Map<String, Object> direccionEntrega(PaqueteListo e, Optional<DestinoEntrega> destino) {
        var direccion = new LinkedHashMap<String, Object>();
        direccion.put("idDireccion", del(destino, DestinoEntrega::idDireccion));
        direccion.put("direccion", e.direccion().calle() + " " + e.direccion().numero());
        direccion.put("referencia", e.direccion().referencia());
        direccion.put("ciudad", e.direccion().ciudad());
        direccion.put("latitud", del(destino, DestinoEntrega::latitud));
        direccion.put("longitud", del(destino, DestinoEntrega::longitud));
        return direccion;
    }

    private static <T> T del(Optional<DestinoEntrega> destino, Function<DestinoEntrega, T> campo) {
        return destino.map(campo).orElse(null);
    }

    /** Fecha y hora en hora de Bolivia, p. ej. 2026-10-22T21:40:00-04:00. */
    private String conZona(LocalDateTime momento) {
        return momento.truncatedTo(ChronoUnit.SECONDS).atZone(zonaDelDominio)
                .withZoneSameInstant(ZONA_PUBLICADA)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
