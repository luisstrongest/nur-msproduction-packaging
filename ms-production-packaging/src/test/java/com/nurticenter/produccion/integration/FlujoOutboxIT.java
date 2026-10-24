package com.nurticenter.produccion.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.nurticenter.produccion.infrastructure.outbox.OutboxJpaEntity;
import com.nurticenter.produccion.infrastructure.outbox.SpringDataOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST DE INTEGRACION - Flujo D: publicacion de eventos con transactional outbox.
 *
 * Contexto completo de Spring y H2 (perfil 'it'), nada se mockea. Kafka esta
 * apagado en este perfil, asi que el relay no corre y los eventos quedan
 * PENDIENTES en la tabla outbox: es justo lo que se quiere comprobar.
 *
 *   - la operacion de negocio deja su evento en el outbox, con el sobre del contrato;
 *   - todos los eventos de una orden comparten correlationId;
 *   - una operacion rechazada (409) no deja ningun evento.
 *
 *   ./mvnw -Pintegration verify
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@DisplayName("Flujo D - Eventos de integracion via transactional outbox (integracion)")
class FlujoOutboxIT {

    // Fecha propia de esta clase: el contexto (y su H2) se comparte con los otros flujos.
    private static final LocalDate FECHA = LocalDate.of(2033, 9, 1);

    private static final String ORDEN = """
            {
              "fechaProduccion": "%s",
              "items": [
                {"recetaId": "REC-045", "nombreReceta": "Pechuga a la plancha",
                 "numeroPacientes": 1, "cantidadPorcion": 350,
                 "unidadMedida": "GRAMOS", "descripcionPorcion": "Plato principal"}
              ]
            }
            """;

    private static final String ETIQUETA = """
            {"nombreCompleto": "Juan Perez", "calle": "Av. Cristo Redentor", "numero": "1450",
             "referencia": "Frente al Banco Union", "ciudad": "Santa Cruz"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SpringDataOutboxRepository outbox;

    private final ObjectMapper json = new ObjectMapper();

    private String crearOrden(LocalDate fecha) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/ordenes")
                        .contentType(MediaType.APPLICATION_JSON).content(ORDEN.formatted(fecha)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.id");
    }

    @Test
    @DisplayName("Generar una orden deja OrdenGenerada pendiente en el outbox, con el sobre del contrato")
    void generarUnaOrdenDejaElEventoEnElOutbox() throws Exception {
        String ordenId = crearOrden(FECHA);

        List<OutboxJpaEntity> filas = outbox.findByMessageKeyOrderByIdAsc(ordenId);
        assertThat(filas).singleElement().satisfies(fila -> {
            assertThat(fila.getEventName()).isEqualTo("bc4.OrdenGenerada");
            assertThat(fila.getTopic()).isEqualTo("bc4.produccion.eventos");
            assertThat(fila.getPublicadoEn()).isNull();
        });

        JsonNode sobre = json.readTree(filas.get(0).getPayload());
        assertThat(sobre.path("eventId").asText()).isEqualTo(filas.get(0).getEventId());
        assertThat(sobre.path("eventName").asText()).isEqualTo("bc4.OrdenGenerada");
        assertThat(sobre.path("eventVersion").asText()).isEqualTo("1.0");
        assertThat(sobre.path("occurredOn").asText()).endsWith("Z");
        assertThat(sobre.path("source").asText()).isEqualTo("MsProductionPackaging");
        assertThat(sobre.path("correlationId").asText()).isEqualTo("corr-orden-" + ordenId);
        assertThat(sobre.path("data").path("idOrdenProduccion").asText()).isEqualTo(ordenId);
        assertThat(sobre.path("data").path("fechaProduccion").asText()).isEqualTo(FECHA.toString());
    }

    @Test
    @DisplayName("PaqueteListo queda en el outbox con la key del paquete y el correlationId de su orden")
    void paqueteListoCompartElCorrelationIdDeSuOrden() throws Exception {
        String ordenId = crearOrden(FECHA.plusDays(1));
        mvc.perform(post("/api/v1/ordenes/{id}/iniciar-preparacion", ordenId)).andExpect(status().isOk());
        String paquete = mvc.perform(post("/api/v1/paquetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\": \"PAC-1024\", \"ordenId\": \"%s\"}".formatted(ordenId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paqueteId = JsonPath.read(paquete, "$.id");
        mvc.perform(post("/api/v1/paquetes/{id}/envasar", paqueteId)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/paquetes/{id}/etiquetar", paqueteId)
                .contentType(MediaType.APPLICATION_JSON).content(ETIQUETA)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/paquetes/{id}/marcar-listo", paqueteId)).andExpect(status().isOk());

        List<OutboxJpaEntity> filas = outbox.findByMessageKeyOrderByIdAsc(paqueteId);
        assertThat(filas).singleElement()
                .satisfies(fila -> assertThat(fila.getEventName()).isEqualTo("bc4.PaqueteListo"));
        JsonNode sobre = json.readTree(filas.get(0).getPayload());
        assertThat(sobre.path("correlationId").asText()).isEqualTo("corr-orden-" + ordenId);
        assertThat(sobre.path("data").path("idPaquete").asText()).isEqualTo(paqueteId);
        assertThat(sobre.path("data").path("idPaciente").asText()).isEqualTo("PAC-1024");
        assertThat(sobre.path("data").path("etiqueta").asText()).matches("^PKG-[0-9A-F]{8}$");
    }

    @Test
    @DisplayName("Una operacion rechazada (completar con items pendientes -> 409) no deja eventos")
    void unaOperacionRechazadaNoDejaEventos() throws Exception {
        String ordenId = crearOrden(FECHA.plusDays(2));
        mvc.perform(post("/api/v1/ordenes/{id}/iniciar-preparacion", ordenId)).andExpect(status().isOk());
        long antes = outbox.count();

        mvc.perform(post("/api/v1/ordenes/{id}/completar", ordenId)).andExpect(status().isConflict());

        assertThat(outbox.count()).isEqualTo(antes);
        assertThat(outbox.findByMessageKeyOrderByIdAsc(ordenId))
                .extracting(OutboxJpaEntity::getEventName)
                .containsExactly("bc4.OrdenGenerada");
    }
}
