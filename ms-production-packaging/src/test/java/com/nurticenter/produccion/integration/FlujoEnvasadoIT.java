package com.nurticenter.produccion.integration;

import com.jayway.jsonpath.JsonPath;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.PaqueteListo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TEST DE INTEGRACION - Flujo B: envasado, etiquetado y entrega a BC5.
 *
 * Mismo flujo que la carpeta "02 - Flujo B" de la coleccion Postman, con el contexto
 * completo de Spring y H2 (perfil 'it'). Nada se mockea.
 *
 *   orden EN_PREPARACION -> crear paquete -> la orden lo registra -> envasar
 *       -> etiquetar -> LISTO -> aparece en los listos para BC5 -> completar la orden
 *
 * El paquete se crea ANTES de completar la orden: una orden COMPLETADA es inmutable.
 *
 *   ./mvnw -Pintegration verify
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@RecordApplicationEvents
@DisplayName("Flujo B - Envasado, etiquetado y entrega a BC5 (integracion)")
class FlujoEnvasadoIT {

    // Fecha propia de esta clase: el contexto (y su H2) se comparte con FlujoProduccionIT.
    private static final LocalDate FECHA = LocalDate.of(2032, 6, 20);

    private static final String ORDEN = """
            {
              "fechaProduccion": "%s",
              "items": [
                {"recetaId": "REC-010", "nombreReceta": "Guiso de lentejas",
                 "numeroPacientes": 5, "cantidadPorcion": 350,
                 "unidadMedida": "GRAMOS", "descripcionPorcion": "Porcion de almuerzo"}
              ]
            }
            """;

    private static final String ETIQUETA = """
            {
              "nombreCompleto": "Maria Perez",
              "calle": "Av. Mutualista",
              "numero": "450",
              "referencia": "Edificio azul, piso 2",
              "ciudad": "Santa Cruz"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationEvents eventos;

    private String ordenEnPreparacion(LocalDate fecha) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/ordenes")
                        .contentType(MediaType.APPLICATION_JSON).content(ORDEN.formatted(fecha)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ordenId = JsonPath.read(cuerpo, "$.id");
        mvc.perform(post("/api/v1/ordenes/{id}/iniciar-preparacion", ordenId))
                .andExpect(status().isOk());
        return ordenId;
    }

    private String crearPaquete(String ordenId) throws Exception {
        String cuerpo = mvc.perform(post("/api/v1/paquetes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\": \"PAC-1001\", \"ordenId\": \"%s\"}".formatted(ordenId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ENVASADO_PENDIENTE"))
                .andExpect(jsonPath("$.ordenId").value(ordenId))
                .andExpect(jsonPath("$.etiqueta").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.id");
    }

    @Test
    @DisplayName("El paquete se envasa, se etiqueta, queda LISTO para BC5 y la orden se completa con el")
    void flujoCompletoDeEnvasadoYEntrega() throws Exception {
        String ordenId = ordenEnPreparacion(FECHA);

        // 1. Crear el paquete: la orden registra su referencia
        String paqueteId = crearPaquete(ordenId);
        mvc.perform(get("/api/v1/ordenes/{id}", ordenId))
                .andExpect(jsonPath("$.paqueteIds", hasItem(paqueteId)));

        // 2. Envasar, etiquetar y marcar listo
        mvc.perform(post("/api/v1/paquetes/{id}/envasar", paqueteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ENVASADO"))
                .andExpect(jsonPath("$.etiqueta").value(nullValue()));

        mvc.perform(post("/api/v1/paquetes/{id}/etiquetar", paqueteId)
                        .contentType(MediaType.APPLICATION_JSON).content(ETIQUETA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ETIQUETADO"))
                .andExpect(jsonPath("$.etiqueta.nombrePaciente").value("Maria Perez"))
                .andExpect(jsonPath("$.etiqueta.direccion")
                        .value("Av. Mutualista 450, Ref: Edificio azul, piso 2 - Santa Cruz"))
                .andExpect(jsonPath("$.etiqueta.codigoIdentificacion", matchesPattern("^PKG-[0-9A-F]{8}$")));

        String listo = mvc.perform(post("/api/v1/paquetes/{id}/marcar-listo", paqueteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"))
                .andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(listo, "$.etiqueta.codigoIdentificacion");

        // 3. BC5 lo ve entre los listos para transferir (GET sin filtros)
        mvc.perform(get("/api/v1/paquetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(paqueteId)))
                .andExpect(jsonPath("$[*].estado", everyItem(is("LISTO"))));

        // 4. Preparar el item y completar la orden, que conserva su paquete
        mvc.perform(post("/api/v1/ordenes/{id}/items/{receta}/preparar", ordenId, "REC-010"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/ordenes/{id}/completar", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.paqueteIds", hasItem(paqueteId)));

        // 5. Releer desde la BD: el paquete quedo LISTO y con su etiqueta
        mvc.perform(get("/api/v1/paquetes").param("ordenId", ordenId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("LISTO"))
                .andExpect(jsonPath("$[0].etiqueta.codigoIdentificacion").value(codigo));

        // 6. Eventos de dominio: PaqueteListo con el codigo impreso, y la orden
        //    completada lleva la referencia del paquete
        assertThat(eventos.stream(PaqueteListo.class)
                .filter(e -> e.paqueteId().equals(paqueteId)))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.pacienteId()).isEqualTo("PAC-1001");
                    assertThat(e.codigoIdentificacion()).isEqualTo(codigo);
                });
        assertThat(eventos.stream(OrdenCompletada.class)
                .filter(e -> e.ordenId().equals(ordenId)))
                .singleElement()
                .satisfies(e -> assertThat(e.paqueteIds()).containsExactly(paqueteId));
    }

    @Test
    @DisplayName("Etiquetar sin envasar -> 409 y el paquete sigue ENVASADO_PENDIENTE sin etiqueta")
    void etiquetarSinEnvasarSeRechazaSinCorromperElPaquete() throws Exception {
        String paqueteId = crearPaquete(ordenEnPreparacion(FECHA.plusDays(1)));

        mvc.perform(post("/api/v1/paquetes/{id}/etiquetar", paqueteId)
                        .contentType(MediaType.APPLICATION_JSON).content(ETIQUETA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Regla de negocio violada"));

        mvc.perform(get("/api/v1/paquetes/{id}", paqueteId))
                .andExpect(jsonPath("$.estado").value("ENVASADO_PENDIENTE"))
                .andExpect(jsonPath("$.etiqueta").value(nullValue()));
        assertThat(eventos.stream(PaqueteListo.class)
                .filter(e -> e.paqueteId().equals(paqueteId))).isEmpty();
    }
}
