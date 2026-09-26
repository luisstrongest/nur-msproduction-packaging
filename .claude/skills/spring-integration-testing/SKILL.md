---
name: spring-integration-testing
description: Convenciones obligatorias para escribir pruebas de integracion en Java (Spring Boot) de este proyecto, las clases Flujo*IT del paquete com.nurticenter.produccion.integration. Cargalo antes de crear o modificar un test *IT.java que levante el contexto de Spring, cuando haya que decidir si un escenario va en Java o en la coleccion Postman, o cuando un IT pase solo y falle junto a los demas.
---

# Pruebas de integracion en Java en este proyecto

Complementan la coleccion Postman (skill `postman-integration-testing`): **los mismos flujos
de negocio**, escritos en el lenguaje del proyecto. Viven en
`ms-production-packaging/src/test/java/com/nurticenter/produccion/integration/`.

## 1. Cuando va en Java y cuando en Postman

| Va en Postman | Va en Java (`Flujo*IT`) |
| :------------ | :---------------------- |
| Lo que ve un cliente HTTP externo | Lo que **no sale por HTTP**: eventos de dominio publicados |
| Repetibilidad contra una BD que sobrevive entre corridas | Verificacion en cada `mvn verify -Pintegration`, en el IDE, con depurador |
| El detalle de cada rechazo (`ApiError` completo) | El rechazo **y** que no se publico el evento que no tocaba |

Si un escenario solo comprueba lo que devuelve la API, ya esta en Postman: no lo dupliques.
Un IT de Java justifica su existencia verificando algo interno (eventos) ademas del flujo.

## 2. Una clase por flujo

```
integration/
├── FlujoProduccionIT   -> Flujo A: orden generada ... COMPLETADA
└── FlujoEnvasadoIT     -> Flujo B: paquete ... LISTO para BC5, orden completada con el
```

- El test principal de la clase recorre el **flujo completo en un solo metodo**, con los
  pasos numerados en comentarios (`// 1. Generar la orden ...`). Partirlo en metodos
  ordenados con `@Order` crea dependencias ocultas entre tests.
- Como mucho un test adicional por clase para el rechazo mas importante de ese flujo,
  comprobando que el estado **no quedo a medias** y que **no se publico** el evento.
- Nombre del metodo en espanol y descriptivo (`flujoCompletoDeEnvasadoYEntrega`), con
  `@DisplayName` legible en el reporte.

## 3. Esqueleto obligatorio

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")           // H2 en memoria: no necesita Docker ni PostgreSQL
@RecordApplicationEvents        // captura los eventos de dominio publicados
@DisplayName("Flujo X - ... (integracion)")
class FlujoXxxIT {

    // Fecha PROPIA de la clase: ver seccion 4.
    private static final LocalDate FECHA = LocalDate.of(2033, 1, 15);

    @Autowired private MockMvc mvc;
    @Autowired private ApplicationEvents eventos;
    ...
}
```

Prohibido en estas clases: `@MockBean` / `@MockitoBean` y cualquier mock. Si algo se mockea,
es un test unitario y va en su capa (skill `java-unit-testing`).

## 4. Aislamiento: el contexto se comparte

Spring **cachea el contexto** entre clases con la misma configuracion: todas las `Flujo*IT`
comparten la misma aplicacion y **la misma H2**. Consecuencias:

- Cada clase usa **su propia fecha de produccion** (la regla "una orden por fecha" devuelve
  409 si dos clases eligen la misma). Clase nueva = fecha nueva, en otro ano.
- Un segundo test de la misma clase usa `FECHA.plusDays(1)`, nunca `FECHA`.
- Las aserciones sobre listas (`GET /paquetes`, `?estado=`) usan `hasItem(id)` y
  `everyItem(...)`, **nunca** `length()` exacto: otras clases pueden haber dejado filas.
  La excepcion es un filtro que solo puede devolver lo propio (`?fecha=FECHA`,
  `?ordenId=<id propio>`).
- Los eventos se filtran por el id del agregado propio antes de contarlos:
  `eventos.stream(PaqueteListo.class).filter(e -> e.paqueteId().equals(paqueteId))`.

## 5. Que asertar

- En cada paso: el **status** y el **estado** devuelto (`jsonPath("$.estado")`).
- Transiciones: lo que cambio **y** lo que no debia cambiar (el otro item sigue PENDIENTE).
- Persistencia: un `GET` que relea el recurso despues de la escritura.
- Al final del flujo: los eventos de dominio, **cuantos** (`singleElement()`, `hasSize(1)`)
  y **con que datos** (el codigo de la etiqueta en `PaqueteListo`, el paquete en
  `OrdenCompletada`). `isNotEmpty()` no vale: pasa con eventos duplicados.
- Los ids se capturan de la respuesta con `JsonPath.read(cuerpo, "$.id")`; nunca se fijan.

## 6. Verificar que el test verifica

Antes de dar un IT por bueno, **rompe el codigo de produccion a proposito** y comprueba que
falla. Minimo, para cada evento que asertas: comenta su `eventPublisher.publicar(...)` en
el servicio de aplicacion, ejecuta, confirma el rojo y **restaura con `git checkout`** (no
con `Set-Content` de PowerShell 5.1, que anade BOM al fichero).

## 7. Comandos

```powershell
cd ms-production-packaging
.\mvnw.cmd -Pintegration verify                                           # unitarias + ITs
.\mvnw.cmd -B -Pintegration test-compile failsafe:integration-test failsafe:verify   # solo ITs
```

Los ITs no corren con `mvnw test` ni con `mvnw verify` sin perfil: surefire excluye
`*IT.java` y el perfil `integration` los incluye por failsafe **sin** el agente de JaCoCo,
para que el coverage reportado siga siendo el de las unitarias.

Todo el ciclo junto (unitarias, estos ITs, Postman y Pact): `tools\run-all-tests.ps1`.

## Referencias

- Ejemplos reales en verde: `FlujoProduccionIT`, `FlujoEnvasadoIT`.
- Contrato de la API (rutas, DTOs, mapeo excepcion -> HTTP): skill `bc4-api-contract`.
- Los mismos flujos en Postman: skill `postman-integration-testing`.
