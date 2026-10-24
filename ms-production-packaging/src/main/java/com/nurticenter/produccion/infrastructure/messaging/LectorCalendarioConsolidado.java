package com.nurticenter.produccion.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.EntregaCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.RecetaCommand;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * ACL de entrada: traduce el evento bc3.CalendarioConsolidadoCerrado (sobre comun
 * de nur_proy_final/contracts) al comando de aplicacion de BC4.
 *
 * Tolerant reader: solo lee los campos que BC4 usa e ignora el resto, asi un
 * campo nuevo en el contrato no rompe al consumidor. Lo que falta o tiene un
 * formato invalido se rechaza con IllegalArgumentException.
 */
public class LectorCalendarioConsolidado {

    public static final String EVENTO = "bc3.CalendarioConsolidadoCerrado";

    private final ObjectMapper objectMapper;

    public LectorCalendarioConsolidado(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Lo que BC4 necesita del mensaje: identidad del evento, su correlacion y el comando. */
    public record CalendarioRecibido(String eventId, String correlationId, ProgramarProduccionCommand comando) {
    }

    /** eventName del sobre, o "" si el mensaje no es un sobre legible. */
    public String nombreDe(String json) {
        try {
            return objectMapper.readTree(json).path("eventName").asText("");
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    public CalendarioRecibido leer(String json) {
        JsonNode sobre = arbol(json);
        String nombre = texto(sobre, "eventName");
        if (!EVENTO.equals(nombre)) {
            throw new IllegalArgumentException("Se esperaba " + EVENTO + " y llego " + nombre);
        }
        JsonNode data = sobre.path("data");
        var comando = new ProgramarProduccionCommand(
                texto(data, "idCalendario"),
                fecha(data, "fechaEntrega"),
                entregas(data.path("entregas")));
        return new CalendarioRecibido(texto(sobre, "eventId"), texto(sobre, "correlationId"), comando);
    }

    private JsonNode arbol(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("El mensaje no es JSON valido: " + e.getOriginalMessage(), e);
        }
    }

    private static List<EntregaCommand> entregas(JsonNode entregas) {
        if (!entregas.isArray() || entregas.isEmpty()) {
            throw new IllegalArgumentException("El calendario no trae entregas.");
        }
        List<EntregaCommand> resultado = new ArrayList<>();
        for (JsonNode entrega : entregas) {
            JsonNode direccion = entrega.path("direccion");
            resultado.add(new EntregaCommand(
                    texto(entrega, "idCalendarioDia"),
                    texto(entrega, "idSuscripcion"),
                    texto(entrega, "idPaciente"),
                    opcional(direccion, "idDireccion"),
                    numero(direccion, "latitud"),
                    numero(direccion, "longitud"),
                    recetas(entrega.path("recetas"), texto(entrega, "idCalendarioDia"))));
        }
        return resultado;
    }

    private static List<RecetaCommand> recetas(JsonNode recetas, String idCalendarioDia) {
        if (!recetas.isArray() || recetas.isEmpty()) {
            throw new IllegalArgumentException("La entrega " + idCalendarioDia + " no trae recetas.");
        }
        List<RecetaCommand> resultado = new ArrayList<>();
        for (JsonNode receta : recetas) {
            JsonNode porcion = receta.path("porcionGramos");
            if (!porcion.isNumber()) {
                throw new IllegalArgumentException("Falta porcionGramos numerico en " + receta);
            }
            resultado.add(new RecetaCommand(
                    texto(receta, "idReceta"),
                    texto(receta, "nombre"),
                    texto(receta, "hora"),
                    porcion.asDouble()));
        }
        return resultado;
    }

    private static String texto(JsonNode nodo, String campo) {
        String valor = opcional(nodo, campo);
        if (valor == null) {
            throw new IllegalArgumentException("Falta el campo " + campo);
        }
        return valor;
    }

    private static String opcional(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isTextual() && !valor.asText().isBlank() ? valor.asText() : null;
    }

    private static Double numero(JsonNode nodo, String campo) {
        JsonNode valor = nodo.path(campo);
        return valor.isNumber() ? valor.asDouble() : null;
    }

    private static LocalDate fecha(JsonNode nodo, String campo) {
        String valor = texto(nodo, campo);
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha invalida en " + campo + ": " + valor, e);
        }
    }
}
