package com.nurticenter.logistica.bc4;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * CLIENTE HTTP de BC4 usado por BC5 (ms-logistic-delivery).
 *
 * Este es el codigo que el pacto protege. Las pruebas de contrato NO prueban un
 * JSON escrito a mano: ejecutan estos metodos contra el servidor mock de Pact, de
 * modo que el pacto refleja lo que el cliente realmente pide y lo que realmente
 * necesita leer de la respuesta.
 *
 * Regla de Postel aplicada al contrato: se envia lo estrictamente necesario y se
 * acepta cualquier campo extra que BC4 añada (ver PaqueteListoDto).
 */
public class ClienteProduccionBC4 {

    private static final String RUTA_PAQUETES = "/api/v1/paquetes";

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;

    public ClienteProduccionBC4(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    /**
     * Paquetes LISTOS pendientes de transferencia a BC5.
     * Es la consulta con la que arranca cada ronda de reparto.
     */
    public List<PaqueteListoDto> listarPendientesDeTransferencia() {
        HttpResponse<String> respuesta = get(RUTA_PAQUETES);
        exigir200(respuesta);
        return leer(respuesta.body(), new TypeReference<List<PaqueteListoDto>>() {});
    }

    /** Paquetes generados por una orden de produccion concreta. */
    public List<PaqueteListoDto> listarPorOrden(String ordenId) {
        String ruta = RUTA_PAQUETES + "?ordenId=" + URLEncoder.encode(ordenId, StandardCharsets.UTF_8);
        HttpResponse<String> respuesta = get(ruta);
        exigir200(respuesta);
        return leer(respuesta.body(), new TypeReference<List<PaqueteListoDto>>() {});
    }

    /**
     * Detalle de un paquete concreto: BC5 lo consulta al escanear la etiqueta
     * antes de cargarlo en el vehiculo.
     *
     * @throws PaqueteNoEncontradoException si BC4 responde 404
     */
    public PaqueteListoDto obtenerPaquete(String paqueteId) {
        HttpResponse<String> respuesta = get(RUTA_PAQUETES + "/" + paqueteId);

        if (respuesta.statusCode() == 404) {
            throw new PaqueteNoEncontradoException(leer(respuesta.body(), new TypeReference<ApiErrorDto>() {}));
        }
        exigir200(respuesta);
        return leer(respuesta.body(), new TypeReference<PaqueteListoDto>() {});
    }

    // -- Infraestructura del cliente ------------------------------------------------

    private HttpResponse<String> get(String ruta) {
        HttpRequest peticion = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + ruta))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            return http.send(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("BC4 no respondio a GET " + ruta, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta a BC4 interrumpida: GET " + ruta, e);
        }
    }

    private void exigir200(HttpResponse<String> respuesta) {
        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException(
                    "BC4 respondio " + respuesta.statusCode() + " a una consulta de paquetes");
        }
    }

    private <T> T leer(String cuerpo, TypeReference<T> tipo) {
        try {
            return json.readValue(cuerpo, tipo);
        } catch (IOException e) {
            throw new UncheckedIOException("Respuesta de BC4 ilegible: " + cuerpo, e);
        }
    }
}
