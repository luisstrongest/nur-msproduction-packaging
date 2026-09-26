package com.nurticenter.produccion.integration;

import com.jayway.jsonpath.JsonPath;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST DE INTEGRACION - Flujo A: produccion en cocina.
 *
 * Mismo flujo que la carpeta "01 - Flujo A" de la coleccion Postman, pero desde el
 * lenguaje del proyecto: levanta el contexto completo de Spring con el perfil 'it'
 * (H2 en memoria) y recorre la API por HTTP -> controller -> servicio -> JPA -> BD.
 * Nada se mockea.
 *
 *   generar orden -> consultar por id y por fecha -> iniciar preparacion
 *       -> preparar cada item -> completar -> releer COMPLETADA
 *
 * Lo que aporta frente a Postman: ve los eventos de dominio publicados en el bus
 * interno (@RecordApplicationEvents), que desde fuera de la aplicacion no se ven.
 *
 *   ./mvnw -Pintegration verify
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@RecordApplicationEvents
@DisplayName("Flujo A - Produccion en cocina (integracion)")
class FlujoProduccionIT {

    // Fecha propia de esta clase: la regla de negocio admite una orden por fecha y
    // el contexto de Spring (y su H2) se comparte con FlujoEnvasadoIT.
    private static final LocalDate FECHA = LocalDate.of(2031, 3, 10);

    private static final String ORDEN = """
            {
              "fechaProduccion": "%s",
              "items": [
                {"recetaId": "REC-001", "nombreReceta": "Pure de zapallo con pollo",
                 "numeroPacientes": 12, "cantidadPorcion": 250,
                 "unidadMedida": "GRAMOS", "descripcionPorcion": "Porcion individual estandar"},
                {"recetaId": "REC-002", "nombreReceta": "Sopa de verduras",
                 "numeroPacientes": 8, "cantidadPorcion": 300,
                 "unidadMedida": "MILILITROS", "descripcionPorcion": "Racion tibia de 300 ml"}
              ]
            }
            """.formatted(FECHA);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents eventos;

    @Test
    @DisplayName("La orden se genera, se prepara item por item y queda COMPLETADA en la BD")
    void flujoCompletoDeProduccion() throws Exception {
        // 1. Generar la orden desde el calendario consolidado
        String cuerpo = mvc.perform(post("/api/v1/ordenes")
                        .contentType(MediaType.APPLICATION_JSON).content(ORDEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.fechaProduccion").value(FECHA.toString()))
                .andExpect(jsonPath("$.totalPorciones").value(20))
                .andExpect(jsonPath("$.items[*].estado", everyItem(is("PENDIENTE"))))
                .andReturn().getResponse().getContentAsString();
        String ordenId = JsonPath.read(cuerpo, "$.id");

        // 2. Releerla por id y por fecha: el mapeo JPA no perdio nada
        mvc.perform(get("/api/v1/ordenes/{id}", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].nombreReceta").value("Pure de zapallo con pollo"))
                .andExpect(jsonPath("$.items[0].porcionUnidad").value("GRAMOS"))
                .andExpect(jsonPath("$.items[1].cantidadTotal").value(8));

        mvc.perform(get("/api/v1/ordenes").param("fecha", FECHA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ordenId));

        // 3. Iniciar la preparacion: la orden avanza, los items no
        mvc.perform(post("/api/v1/ordenes/{id}/iniciar-preparacion", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"))
                .andExpect(jsonPath("$.items[*].estado", everyItem(is("PENDIENTE"))));

        mvc.perform(get("/api/v1/ordenes").param("estado", "en_preparacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(ordenId)));

        // 4. Preparar cada item: marcar uno no toca el otro
        mvc.perform(post("/api/v1/ordenes/{id}/items/{receta}/preparar", ordenId, "REC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.recetaId == 'REC-001')].estado").value("PREPARADO"))
                .andExpect(jsonPath("$.items[?(@.recetaId == 'REC-002')].estado").value("PENDIENTE"));

        mvc.perform(post("/api/v1/ordenes/{id}/items/{receta}/preparar", ordenId, "REC-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].estado", everyItem(is("PREPARADO"))));

        // 5. Completar y releer: COMPLETADA quedo persistida
        mvc.perform(post("/api/v1/ordenes/{id}/completar", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"));

        mvc.perform(get("/api/v1/ordenes/{id}", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.items[*].estado", everyItem(is("PREPARADO"))));

        // 6. Los eventos de dominio llegaron al bus, una vez cada uno
        List<OrdenGenerada> generadas = eventos.stream(OrdenGenerada.class)
                .filter(e -> e.ordenId().equals(ordenId)).toList();
        assertThat(generadas).singleElement().satisfies(e -> {
            assertThat(e.fechaProduccion()).isEqualTo(FECHA);
            assertThat(e.totalItems()).isEqualTo(2);
        });
        assertThat(eventos.stream(OrdenCompletada.class)
                .filter(e -> e.ordenId().equals(ordenId))).hasSize(1);
    }

    @Test
    @DisplayName("Completar con items sin preparar -> 409 y la orden sigue EN_PREPARACION")
    void completarConItemsPendientesSeRechazaSinCorromperLaOrden() throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/ordenes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ORDEN.replace(FECHA.toString(), FECHA.plusDays(1).toString())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ordenId = JsonPath.read(cuerpo, "$.id");
        mvc.perform(post("/api/v1/ordenes/{id}/iniciar-preparacion", ordenId))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ordenes/{id}/completar", ordenId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Regla de negocio violada"));

        mvc.perform(get("/api/v1/ordenes/{id}", ordenId))
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
        assertThat(eventos.stream(OrdenCompletada.class)
                .filter(e -> e.ordenId().equals(ordenId))).isEmpty();
    }
}
