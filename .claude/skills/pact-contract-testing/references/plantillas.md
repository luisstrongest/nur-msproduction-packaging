# Plantillas copiables - Pact

Todo lo de aqui esta extraido de las pruebas que ya pasan el gate
(`tools/validate-contracts.ps1`). Copia, cambia los nombres y ejecuta.

## 1. Interaccion de exito - recurso unico (consumidor)

```java
@Pact(consumer = CONSUMIDOR)
public RequestResponsePact pactoDetalleDePaqueteListo(PactDslWithProvider builder) {
    return builder
            .given("existe el paquete LISTO con id " + PAQUETE_LISTO_ID)   // <- lo replica el @State
            .uponReceiving("una consulta del detalle de ese paquete")
                .path("/api/v1/paquetes/" + PAQUETE_LISTO_ID)
                .method("GET")
                .headers(Map.of("Accept", "application/json"))
            .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(newJsonBody(paquete -> {
                    paquete.stringValue("id", PAQUETE_LISTO_ID);       // exacto: es EL recurso pedido
                    paquete.stringType("pacienteId", "PAC-1001");      // tipo: BC5 solo lo reenvia
                    paquete.stringValue("estado", "LISTO");            // exacto: BC5 decide con el
                    paquete.object("etiqueta", etiqueta -> {
                        etiqueta.stringType("nombrePaciente", "Maria Perez");
                        etiqueta.stringMatcher("codigoIdentificacion", REGEX_CODIGO, "PKG-7F3E0F9C");
                        etiqueta.stringMatcher("fechaGeneracion", REGEX_FECHA_HORA, "2026-09-17T08:15:30.123");
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
    assertThat(paquete.esTransferible()).isTrue();
}
```

## 2. Interaccion de exito - coleccion

```java
.willRespondWith()
    .status(200)
    .headers(Map.of("Content-Type", "application/json"))
    .body(newJsonArrayMinLike(2, array -> array.object(paquete -> {   // al menos 2, misma forma
        paquete.uuid("id");
        paquete.stringValue("estado", "LISTO");
        paquete.object("etiqueta", etiqueta -> { ... });
    })).build())
```

Con query string:

```java
.uponReceiving("una consulta de los paquetes de una orden de produccion")
    .path("/api/v1/paquetes")
    .query("ordenId=" + ORDEN_ID)
    .method("GET")
```

Campo que viaja en null (parte del contrato, no un olvido):

```java
paquete.nullValue("etiqueta");   // BC5 tiene que recibir el campo aunque no haya etiqueta
```

## 3. Interaccion de error (ApiError)

```java
.given("no existe ningun paquete con id " + PAQUETE_INEXISTENTE_ID)
.uponReceiving("una consulta del detalle de un paquete que no existe")
    .path("/api/v1/paquetes/" + PAQUETE_INEXISTENTE_ID)
    .method("GET")
.willRespondWith()
    .status(404)
    .headers(Map.of("Content-Type", "application/json"))
    .body(newJsonBody(error -> {
        error.stringMatcher("timestamp", REGEX_FECHA_HORA, "2026-09-17T08:15:30.123");
        error.numberValue("status", 404);
        error.stringValue("error", "Recurso no encontrado");
        error.stringMatcher("mensaje", "No existe un PaqueteEtiquetado con id .+",
                            "No existe un PaqueteEtiquetado con id " + PAQUETE_INEXISTENTE_ID);
        error.array("detalles", detalles -> { /* vacio fuera de los 400 de validacion */ });
    }).build())
```

El test correspondiente comprueba que el **cliente traduce el error**, no el JSON:

```java
assertThatThrownBy(() -> cliente.obtenerPaquete(PAQUETE_INEXISTENTE_ID))
        .isInstanceOf(PaqueteNoEncontradoException.class)
        .hasMessageContaining("No existe un PaqueteEtiquetado con id");
```

## 4. Constantes del contrato (consumidor)

```java
/** ISO-8601 local con fraccion de segundo opcional: asi serializa Jackson un LocalDateTime. */
private static final String REGEX_FECHA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?";

/** Regla de dominio de BC4: PKG- mas los 8 primeros caracteres del UUID, en mayusculas. */
private static final String REGEX_CODIGO = "PKG-[0-9A-F]{8}";
```

## 5. Esqueleto del proveedor

```java
@Provider("ms-production-packaging")
@PactFolder("src/test/resources/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class PaquetesProviderContractIT {

    @LocalServerPort private int puerto;
    @Autowired private PaqueteEtiquetadoRepository paquetes;      // siembra por el puerto
    @Autowired private SpringDataPaqueteRepository jpaPaquetes;   // solo para vaciar

    @BeforeEach
    void prepararEntorno(PactVerificationContext context) {
        jpaPaquetes.deleteAll();                                  // aislamiento entre interacciones
        context.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void verificarInteraccionDelContrato(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
```

## 6. Provider states

```java
@State("existen 2 paquetes LISTOS pendientes de transferencia a BC5")
void dosPaquetesListos() {
    paquetes.guardar(paqueteListo(UUID.randomUUID().toString(), "PAC-1001",
            UUID.randomUUID().toString(), "Maria Perez"));
    paquetes.guardar(paqueteListo(UUID.randomUUID().toString(), "PAC-1002",
            UUID.randomUUID().toString(), "Juan Gutierrez"));
}

@State("no existe ningun paquete con id 00000000-0000-0000-0000-000000000000")
void ningunPaqueteConEseId() {
    // La base ya quedo vacia en @BeforeEach. Se declara igualmente: documenta la
    // precondicion y falla si alguien la rompe mas adelante.
}
```

Helper de siembra: el estado se construye con las **reglas de dominio reales**.

```java
private PaqueteEtiquetado paqueteListo(String paqueteId, String pacienteId,
                                       String ordenId, String nombrePaciente) {
    PaqueteId id = PaqueteId.de(paqueteId);
    Etiqueta etiqueta = Etiqueta.reconstituir(
            nombrePaciente,
            new DireccionImpresa("Av. Mutualista", "450", "Edificio azul, piso 2", "Santa Cruz"),
            CodigoIdentificacion.generar(id),                 // <- la regla, no un literal
            LocalDateTime.of(2026, 9, 17, 8, 15, 30, 123_000_000));

    return PaqueteEtiquetado.reconstituir(
            id, PacienteId.de(pacienteId), ordenId, EstadoPaquete.LISTO, etiqueta);
}
```

## 7. Imports que siempre hacen falta

Consumidor:

```java
import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;

import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonArrayMinLike;
import static au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody;
```

Proveedor:

```java
import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
```

## 8. Comprobacion final (no es opcional)

```powershell
.\tools\validate-contracts.ps1 -BreakCheck
```

Exit 0 y la linea `Falsabilidad: comprobada`. Si sale **exit 5**, la interaccion que
acabas de escribir no puede fallar: revisa los matchers antes de reportar nada.
