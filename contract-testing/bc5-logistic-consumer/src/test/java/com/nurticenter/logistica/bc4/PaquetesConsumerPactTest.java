package com.nurticenter.logistica.bc4;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonArrayMinLike;
import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PRUEBAS DE CONTRATO - lado CONSUMIDOR.
 *
 * Consumidor : ms-logistic-delivery (BC5 - Entrega y Logistica)
 * Proveedor  : ms-production-packaging (BC4 - Produccion y Envasado)
 *
 * Cada test describe UNA expectativa del consumidor sobre la API de BC4, la
 * ejecuta contra el servidor mock de Pact usando el cliente real
 * ({@link ClienteProduccionBC4}) y, si pasa, contribuye una interaccion al pacto
 * ms-logistic-delivery-ms-production-packaging.json.
 *
 * Lo que aqui se decide no es "que devuelve BC4", sino "que necesita BC5 para
 * funcionar": por eso se usan matchers de tipo/formato en todo lo que BC5 solo
 * reenvia, y valores exactos solo donde BC5 toma decisiones (el estado LISTO, el
 * codigo HTTP, el formato del codigo de identificacion que imprime en la guia).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "ms-production-packaging", pactVersion = PactSpecVersion.V3)
class PaquetesConsumerPactTest {

    private static final String CONSUMIDOR = "ms-logistic-delivery";

    /** Ids fijos del contrato: el proveedor los siembra en su @State homonimo. */
    private static final String PAQUETE_LISTO_ID       = "7f3e0f9c-4c2a-4f0a-9d21-1a2b3c4d5e6f";
    private static final String PAQUETE_INEXISTENTE_ID = "00000000-0000-0000-0000-000000000000";
    private static final String ORDEN_ID               = "3c1e5b77-9a48-4d5e-8b1c-2f9d0e7a6b54";

    private static final Map<String, String> JSON = Map.of("Content-Type", "application/json");

    /** ISO-8601 local con fraccion de segundo opcional: asi serializa Jackson un LocalDateTime. */
    private static final String REGEX_FECHA_HORA =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?";

    /** Regla de dominio de BC4: PKG- mas los 8 primeros caracteres del UUID, en mayusculas. */
    private static final String REGEX_CODIGO = "PKG-[0-9A-F]{8}";

    // =================================================================================
    // Interaccion 1 - la consulta con la que BC5 arranca cada ronda de reparto
    // =================================================================================

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact pactoPaquetesPendientes(PactDslWithProvider builder) {
        return builder
                .given("existen 2 paquetes LISTOS pendientes de transferencia a BC5")
                .uponReceiving("una consulta de los paquetes listos para transferir")
                    .path("/api/v1/paquetes")
                    .method("GET")
                    .headers(Map.of("Accept", "application/json"))
                .willRespondWith()
                    .status(200)
                    .headers(JSON)
                    .body(newJsonArrayMinLike(2, array -> array.object(paquete -> {
                        paquete.uuid("id");
                        paquete.stringType("pacienteId", "PAC-1001");
                        paquete.uuid("ordenId");
                        // Valor exacto: BC5 solo recoge paquetes LISTOS. Si BC4
                        // empezara a devolver aqui otros estados, el pacto se cae.
                        paquete.stringValue("estado", "LISTO");
                        paquete.object("etiqueta", etiqueta -> {
                            etiqueta.stringType("nombrePaciente", "Maria Perez");
                            etiqueta.stringType("direccion",
                                    "Av. Mutualista 450, Ref: Edificio azul, piso 2 - Santa Cruz");
                            etiqueta.stringMatcher("codigoIdentificacion", REGEX_CODIGO, "PKG-3F2A1B4C");
                            etiqueta.stringMatcher("fechaGeneracion", REGEX_FECHA_HORA,
                                    "2026-09-17T08:15:30.123");
                        });
                    })).build())
                .toPact();
    }

    @Test
    @DisplayName("1. BC5 obtiene la lista de paquetes listos y todos son transferibles")
    @PactTestFor(pactMethod = "pactoPaquetesPendientes")
    void listaPaquetesPendientesDeTransferencia(MockServer mockServer) {
        ClienteProduccionBC4 cliente = new ClienteProduccionBC4(mockServer.getUrl());

        List<PaqueteListoDto> paquetes = cliente.listarPendientesDeTransferencia();

        assertThat(paquetes).hasSize(2);
        assertThat(paquetes).allMatch(PaqueteListoDto::esTransferible);
        assertThat(paquetes.get(0).etiqueta().codigoIdentificacion()).matches(REGEX_CODIGO);
        assertThat(paquetes.get(0).etiqueta().direccion()).isNotBlank();
        assertThat(paquetes.get(0).etiqueta().fechaGeneracion()).isNotNull();
    }

    // =================================================================================
    // Interaccion 2 - el detalle que BC5 lee al escanear la etiqueta antes de cargar
    // =================================================================================

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact pactoDetalleDePaqueteListo(PactDslWithProvider builder) {
        return builder
                .given("existe el paquete LISTO con id " + PAQUETE_LISTO_ID)
                .uponReceiving("una consulta del detalle de ese paquete")
                    .path("/api/v1/paquetes/" + PAQUETE_LISTO_ID)
                    .method("GET")
                    .headers(Map.of("Accept", "application/json"))
                .willRespondWith()
                    .status(200)
                    .headers(JSON)
                    .body(newJsonBody(paquete -> {
                        // Valor exacto: es el paquete que BC5 pidio, no otro.
                        paquete.stringValue("id", PAQUETE_LISTO_ID);
                        paquete.stringType("pacienteId", "PAC-1001");
                        paquete.stringValue("ordenId", ORDEN_ID);
                        paquete.stringValue("estado", "LISTO");
                        paquete.object("etiqueta", etiqueta -> {
                            etiqueta.stringType("nombrePaciente", "Maria Perez");
                            etiqueta.stringType("direccion",
                                    "Av. Mutualista 450, Ref: Edificio azul, piso 2 - Santa Cruz");
                            etiqueta.stringMatcher("codigoIdentificacion", REGEX_CODIGO, "PKG-7F3E0F9C");
                            etiqueta.stringMatcher("fechaGeneracion", REGEX_FECHA_HORA,
                                    "2026-09-17T08:15:30.123");
                        });
                    }).build())
                .toPact();
    }

    @Test
    @DisplayName("2. BC5 lee el detalle del paquete que va a cargar en el vehiculo")
    @PactTestFor(pactMethod = "pactoDetalleDePaqueteListo")
    void obtieneElDetalleDeUnPaqueteListo(MockServer mockServer) {
        ClienteProduccionBC4 cliente = new ClienteProduccionBC4(mockServer.getUrl());

        PaqueteListoDto paquete = cliente.obtenerPaquete(PAQUETE_LISTO_ID);

        assertThat(paquete.id()).isEqualTo(PAQUETE_LISTO_ID);
        assertThat(paquete.ordenId()).isEqualTo(ORDEN_ID);
        assertThat(paquete.esTransferible()).isTrue();
        assertThat(paquete.etiqueta().codigoIdentificacion()).matches(REGEX_CODIGO);
    }

    // =================================================================================
    // Interaccion 3 - el contrato del error: un 404 tiene que ser distinguible
    // =================================================================================

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact pactoPaqueteInexistente(PactDslWithProvider builder) {
        return builder
                .given("no existe ningun paquete con id " + PAQUETE_INEXISTENTE_ID)
                .uponReceiving("una consulta del detalle de un paquete que no existe")
                    .path("/api/v1/paquetes/" + PAQUETE_INEXISTENTE_ID)
                    .method("GET")
                    .headers(Map.of("Accept", "application/json"))
                .willRespondWith()
                    .status(404)
                    .headers(JSON)
                    .body(newJsonBody(error -> {
                        error.stringMatcher("timestamp", REGEX_FECHA_HORA, "2026-09-17T08:15:30.123");
                        // Exactos los tres campos con los que BC5 decide: sin ellos no
                        // puede distinguir "paquete inexistente" de "BC4 caido".
                        error.numberValue("status", 404);
                        error.stringValue("error", "Recurso no encontrado");
                        error.stringMatcher("mensaje",
                                "No existe un PaqueteEtiquetado con id .+",
                                "No existe un PaqueteEtiquetado con id " + PAQUETE_INEXISTENTE_ID);
                        error.array("detalles", detalles -> {
                            // vacio: solo trae entradas en los errores de validacion (400)
                        });
                    }).build())
                .toPact();
    }

    @Test
    @DisplayName("3. BC5 distingue un paquete inexistente por el cuerpo ApiError del 404")
    @PactTestFor(pactMethod = "pactoPaqueteInexistente")
    void distingueUnPaqueteInexistente(MockServer mockServer) {
        ClienteProduccionBC4 cliente = new ClienteProduccionBC4(mockServer.getUrl());

        assertThatThrownBy(() -> cliente.obtenerPaquete(PAQUETE_INEXISTENTE_ID))
                .isInstanceOf(PaqueteNoEncontradoException.class)
                .hasMessageContaining("No existe un PaqueteEtiquetado con id")
                .extracting(e -> ((PaqueteNoEncontradoException) e).error())
                .satisfies(error -> {
                    assertThat(error.status()).isEqualTo(404);
                    assertThat(error.error()).isEqualTo("Recurso no encontrado");
                    assertThat(error.detalles()).isEmpty();
                });
    }

    // =================================================================================
    // Interaccion 4 - los paquetes de una orden, incluidos los que aun no tienen etiqueta
    // =================================================================================

    @Pact(consumer = CONSUMIDOR)
    public RequestResponsePact pactoPaquetesDeUnaOrden(PactDslWithProvider builder) {
        return builder
                .given("la orden " + ORDEN_ID + " tiene 1 paquete ENVASADO sin etiquetar")
                .uponReceiving("una consulta de los paquetes de una orden de produccion")
                    .path("/api/v1/paquetes")
                    .query("ordenId=" + ORDEN_ID)
                    .method("GET")
                    .headers(Map.of("Accept", "application/json"))
                .willRespondWith()
                    .status(200)
                    .headers(JSON)
                    .body(newJsonArrayMinLike(1, array -> array.object(paquete -> {
                        paquete.uuid("id");
                        paquete.stringType("pacienteId", "PAC-2002");
                        paquete.stringValue("ordenId", ORDEN_ID);
                        paquete.stringValue("estado", "ENVASADO");
                        // El contrato incluye explicitamente el null: BC5 tiene que
                        // recibir el campo aunque el paquete aun no este etiquetado.
                        paquete.nullValue("etiqueta");
                    })).build())
                .toPact();
    }

    @Test
    @DisplayName("4. BC5 recibe los paquetes sin etiqueta de una orden y no los transfiere")
    @PactTestFor(pactMethod = "pactoPaquetesDeUnaOrden")
    void noTransfiereLosPaquetesSinEtiqueta(MockServer mockServer) {
        ClienteProduccionBC4 cliente = new ClienteProduccionBC4(mockServer.getUrl());

        List<PaqueteListoDto> paquetes = cliente.listarPorOrden(ORDEN_ID);

        assertThat(paquetes).hasSize(1);
        assertThat(paquetes.get(0).ordenId()).isEqualTo(ORDEN_ID);
        assertThat(paquetes.get(0).etiqueta()).isNull();
        assertThat(paquetes.get(0).esTransferible()).isFalse();
    }
}
