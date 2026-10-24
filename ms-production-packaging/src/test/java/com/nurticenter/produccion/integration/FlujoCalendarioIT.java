package com.nurticenter.produccion.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.nurticenter.produccion.infrastructure.messaging.ConsumidorCalendarioConsolidado;
import com.nurticenter.produccion.infrastructure.messaging.SpringDataEventoProcesadoRepository;
import com.nurticenter.produccion.infrastructure.outbox.OutboxJpaEntity;
import com.nurticenter.produccion.infrastructure.outbox.SpringDataOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST DE INTEGRACION - Flujo E: BC4 consume bc3.CalendarioConsolidadoCerrado.
 *
 * Contexto completo de Spring y H2 (perfil 'it'), nada se mockea. Kafka esta
 * apagado en este perfil: el mensaje se entrega al consumidor tal como lo
 * entregaria el listener (el JSON del contrato, src/test/resources/contratos,
 * copia de nur_proy_final/contracts), y los eventos que BC4 publica quedan
 * PENDIENTES en el outbox, donde se comprueban.
 *
 *   - el calendario crea la orden y un paquete por entrega;
 *   - el mismo evento repetido no duplica nada (inbox);
 *   - los eventos de BC4 llevan los datos del calendario y su correlationId;
 *   - un calendario rechazado no deja orden, paquetes, eventos ni fila en el inbox.
 *
 *   ./mvnw -Pintegration verify
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@DisplayName("Flujo E - Consumo del CalendarioConsolidado de BC3 (integracion)")
class FlujoCalendarioIT {

    // Fecha propia de esta clase: el contexto (y su H2) se comparte con los otros flujos.
    private static final LocalDate ENTREGA = LocalDate.of(2034, 5, 11);
    private static final LocalDate PRODUCCION = ENTREGA.minusDays(1);

    private static final String ETIQUETA = """
            {"nombreCompleto": "%s", "calle": "Av. Cristo Redentor", "numero": "#1450",
             "referencia": "Frente al Banco Union", "ciudad": "Santa Cruz de la Sierra"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ConsumidorCalendarioConsolidado consumidor;

    @Autowired
    private SpringDataOutboxRepository outbox;

    @Autowired
    private SpringDataEventoProcesadoRepository procesados;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("El calendario programa orden y paquetes, y todo el flujo publica sus eventos con los datos de BC3")
    void flujoCompletoDesdeElCalendario() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String calendario = calendarioDelContrato(eventId, ENTREGA);

        // 1. Llega el calendario: se programa la orden del dia anterior
        assertThat(consumidor.procesar(calendario)).isTrue();
        String orden = mvc.perform(get("/api/v1/ordenes").param("fecha", PRODUCCION.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].items[*].recetaId")
                        .value(containsInAnyOrder("REC-14", "REC-22", "REC-31", "REC-45", "REC-33")))
                .andReturn().getResponse().getContentAsString();
        String ordenId = JsonPath.read(orden, "$[0].id");

        // 2. ... con un paquete por entrega, pendiente de envasar
        String paquetes = mvc.perform(get("/api/v1/paquetes").param("ordenId", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].estado", everyItem(is("ENVASADO_PENDIENTE"))))
                .andExpect(jsonPath("$[*].pacienteId", containsInAnyOrder("PAC-1024", "PAC-1031")))
                .andReturn().getResponse().getContentAsString();
        List<String> paqueteIds = JsonPath.read(paquetes, "$[*].id");
        List<String> pacientes = JsonPath.read(paquetes, "$[*].pacienteId");

        // 3. El mismo evento otra vez (Kafka es at-least-once): no se duplica nada
        assertThat(consumidor.procesar(calendario)).isFalse();
        assertThat(procesados.existsById(eventId)).isTrue();
        mvc.perform(get("/api/v1/paquetes").param("ordenId", ordenId)).andExpect(jsonPath("$", hasSize(2)));
        assertThat(outbox.findByMessageKeyOrderByIdAsc(ordenId))
                .extracting(OutboxJpaEntity::getEventName).containsExactly("bc4.OrdenGenerada");

        // 4. Cocina: preparar la orden, envasar, etiquetar y marcar listos los paquetes
        mvc.perform(post("/api/v1/ordenes/{id}/iniciar-preparacion", ordenId)).andExpect(status().isOk());
        for (String receta : List.of("REC-14", "REC-22", "REC-31", "REC-45", "REC-33")) {
            mvc.perform(post("/api/v1/ordenes/{id}/items/{receta}/preparar", ordenId, receta))
                    .andExpect(status().isOk());
        }
        for (int i = 0; i < paqueteIds.size(); i++) {
            String id = paqueteIds.get(i);
            String nombre = pacientes.get(i).equals("PAC-1024") ? "Juan Perez Rojas" : "Maria Lopez Vaca";
            mvc.perform(post("/api/v1/paquetes/{id}/envasar", id)).andExpect(status().isOk());
            mvc.perform(post("/api/v1/paquetes/{id}/etiquetar", id)
                    .contentType(MediaType.APPLICATION_JSON).content(ETIQUETA.formatted(nombre)))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/paquetes/{id}/marcar-listo", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("LISTO"));
        }
        mvc.perform(post("/api/v1/ordenes/{id}/completar", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));

        // 5. Eventos de la orden: OrdenGenerada y OrdenCompletada, con la correlacion del calendario
        List<OutboxJpaEntity> deLaOrden = outbox.findByMessageKeyOrderByIdAsc(ordenId);
        assertThat(deLaOrden).extracting(OutboxJpaEntity::getEventName)
                .containsExactly("bc4.OrdenGenerada", "bc4.OrdenCompletada");
        JsonNode completada = json.readTree(deLaOrden.get(1).getPayload());
        assertThat(completada.path("correlationId").asText()).isEqualTo("corr-entrega-" + ENTREGA);
        JsonNode datosOrden = completada.path("data");
        assertThat(datosOrden.path("idCalendario").asText()).isEqualTo("CAL-20261023");
        assertThat(datosOrden.path("fechaProduccion").asText()).isEqualTo(PRODUCCION.toString());
        assertThat(datosOrden.path("fechaEntrega").asText()).isEqualTo(ENTREGA.toString());
        assertThat(datosOrden.path("totalRecetasPreparadas").asInt()).isEqualTo(8);
        assertThat(datosOrden.path("totalPaquetes").asInt()).isEqualTo(2);
        assertThat(datosOrden.path("paquetesIds")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrderElementsOf(paqueteIds);
        assertThat(json.readTree(deLaOrden.get(0).getPayload()).path("correlationId").asText())
                .isEqualTo("corr-entrega-" + ENTREGA);

        // 6. PaqueteListo de cada paquete: destino del calendario + lo impreso en la etiqueta
        String paqueteDeJuan = paqueteIds.get(pacientes.indexOf("PAC-1024"));
        List<OutboxJpaEntity> delPaquete = outbox.findByMessageKeyOrderByIdAsc(paqueteDeJuan);
        assertThat(delPaquete).singleElement()
                .satisfies(fila -> assertThat(fila.getEventName()).isEqualTo("bc4.PaqueteListo"));
        JsonNode listo = json.readTree(delPaquete.get(0).getPayload());
        assertThat(listo.path("correlationId").asText()).isEqualTo("corr-entrega-" + ENTREGA);
        JsonNode datos = listo.path("data");
        assertThat(datos.path("idOrdenProduccion").asText()).isEqualTo(ordenId);
        assertThat(datos.path("idCalendarioDia").asText()).isEqualTo("CD-70011");
        assertThat(datos.path("idSuscripcion").asText()).isEqualTo("SUS-501");
        assertThat(datos.path("idPaciente").asText()).isEqualTo("PAC-1024");
        assertThat(datos.path("nombrePaciente").asText()).isEqualTo("Juan Perez Rojas");
        assertThat(datos.path("fechaEntrega").asText()).isEqualTo(ENTREGA.toString());
        assertThat(datos.path("nroPorciones").asInt()).isEqualTo(4);
        assertThat(datos.path("etiqueta").asText()).matches("^PKG-[0-9A-F]{8}$");
        JsonNode direccion = datos.path("direccionEntrega");
        assertThat(direccion.path("idDireccion").asText()).isEqualTo("DIR-58");
        assertThat(direccion.path("direccion").asText()).isEqualTo("Av. Cristo Redentor #1450");
        assertThat(direccion.path("latitud").asDouble()).isEqualTo(-17.7612);
        assertThat(direccion.path("longitud").asDouble()).isEqualTo(-63.1825);
        String paqueteDeMaria = paqueteIds.get(pacientes.indexOf("PAC-1031"));
        assertThat(outbox.findByMessageKeyOrderByIdAsc(paqueteDeMaria)).singleElement()
                .satisfies(fila -> assertThat(fila.getPayload()).contains("\"idCalendarioDia\":\"CD-70012\""));
    }

    @Test
    @DisplayName("Un calendario rechazado (ya hay orden ese dia) no deja paquetes, eventos ni fila en el inbox")
    void unCalendarioRechazadoNoDejaNada() throws Exception {
        LocalDate entrega = ENTREGA.plusDays(2);
        mvc.perform(post("/api/v1/ordenes").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fechaProduccion": "%s", "items": [
                          {"recetaId": "REC-1", "nombreReceta": "Sopa", "numeroPacientes": 1,
                           "cantidadPorcion": 300, "unidadMedida": "GRAMOS", "descripcionPorcion": "Plato"}]}
                        """.formatted(entrega.minusDays(1))))
                .andExpect(status().isCreated());
        long eventosAntes = outbox.count();
        String eventId = UUID.randomUUID().toString();

        assertThatThrownBy(() -> consumidor.procesar(calendarioDelContrato(eventId, entrega)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(entrega.minusDays(1).toString());

        assertThat(procesados.existsById(eventId)).isFalse();
        assertThat(outbox.count()).isEqualTo(eventosAntes);
        mvc.perform(get("/api/v1/ordenes").param("fecha", entrega.minusDays(1).toString()))
                .andExpect(jsonPath("$[0].items[*].recetaId").value(containsInAnyOrder("REC-1")));
    }

    /** El JSON del contrato con un eventId y una fecha de entrega propios de esta prueba. */
    private static String calendarioDelContrato(String eventId, LocalDate entrega) throws Exception {
        String contrato = new ClassPathResource("contratos/bc3.CalendarioConsolidadoCerrado.json")
                .getContentAsString(StandardCharsets.UTF_8);
        return contrato
                .replace("3f2a0014-9c1e-4b7a-a1d2-7e5f0c8b1014", eventId)
                .replace("2026-10-23", entrega.toString());
    }
}
