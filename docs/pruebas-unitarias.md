# Pruebas unitarias — ms-production-packaging (BC4)

> Estado: **solo pruebas unitarias**. Las pruebas de integración quedan para la
> siguiente etapa y están excluidas de la ejecución (ver §7).

## 1. Cómo ejecutarlas

```bash
cd ddd_clean/ms-production-packaging

./mvnw test                     # ejecuta los tests y genera el reporte de cobertura
./mvnw verify                   # además valida el umbral mínimo de cobertura
```

Reporte de cobertura (HTML):

```
target/site/jacoco/index.html
```

> **Requisito:** JDK 21. Si `JAVA_HOME` apunta a otra versión, el build falla con
> `release version 21 not supported`. En ese caso:
> `JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw test`

## 2. Qué es una prueba unitaria en este proyecto

Una prueba unitaria verifica **una unidad de comportamiento aislada de sus
colaboradores externos**: sin Spring, sin base de datos, sin red y sin ficheros.
Todo lo que está fuera de la unidad se reemplaza por un **doble de prueba**.

La arquitectura hexagonal del microservicio hace esto posible: cada capa depende
de *puertos* (interfaces), nunca de tecnología concreta, así que en el test el
puerto se sustituye por un mock.

```
              ┌──────────────────────────────────────────────┐
   Test  ───► │  Unidad bajo prueba (SUT)                    │
              │  p.ej. OrdenApplicationService               │
              └───────────────┬──────────────────────────────┘
                              │ depende de PUERTOS (interfaces)
              ┌───────────────▼──────────────────────────────┐
              │  Mocks:  OrdenDeProduccionRepository          │
              │          EventPublisher                       │
              │  (no hay PostgreSQL ni bus de eventos real)   │
              └──────────────────────────────────────────────┘
```

## 3. Herramientas

| Herramienta | Versión | Uso |
| :---------- | :------ | :-- |
| JUnit 5 (Jupiter) | via `spring-boot-starter-test` | motor de pruebas, `@Nested`, `@ParameterizedTest` |
| AssertJ | via `spring-boot-starter-test` | aserciones fluidas (`assertThat`, `assertThatThrownBy`) |
| Mockito | via `spring-boot-starter-test` | mocks, `@Mock`, `@InjectMocks`, `@Captor`, `verify`, `InOrder` |
| JaCoCo | 0.8.12 | medición de cobertura (`prepare-agent`, `report`, `check`) |

Ninguna prueba usa `@SpringBootTest`: **no se levanta el contexto de Spring**.

## 4. Mapa de pruebas

### 4.1 Capa de dominio — sin dobles (Java puro)

El dominio no tiene dependencias, así que se prueba directamente su estado.

| Clase de prueba | Qué verifica |
| :-------------- | :----------- |
| `OrdenDeProduccionTest` | invariantes del agregado, transiciones `PENDIENTE → EN_PREPARACION → COMPLETADA`, eventos `OrdenGenerada` / `OrdenCompletada`, inmutabilidad de las colecciones expuestas |
| `PaqueteEtiquetadoTest` | ciclo `ENVASADO_PENDIENTE → ENVASADO → ETIQUETADO → LISTO`, generación de la etiqueta, evento `PaqueteListo` |
| `ItemOrdenTest` | validaciones del VO, `marcarPreparado()` idempotencia, identidad por `recetaId` |
| `PorcionTest` | invariantes, `multiplicar()` (cálculo de la ACL), `formatoCorto()` |
| `EtiquetaTest` | inmutabilidad, `esValida()`, igualdad por código |
| `DireccionImpresaTest` | campos obligatorios, `estaCompleta()`, `formatoParaEtiqueta()` |
| `CodigoIdentificacionTest` | derivación determinista `PaqueteId → PKG-XXXXXXXX` |
| `DatosPacienteTest` | `esCompleto()` como precondición del etiquetado |
| `IdentificadoresTest` | `OrdenId`, `PaqueteId`, `PacienteId` (tiny types) |

### 4.2 Capa de aplicación — **con mocks**

| Clase de prueba | Dobles usados | Qué verifica |
| :-------------- | :------------ | :----------- |
| `OrdenApplicationServiceTest` | **mocks** de `OrdenDeProduccionRepository` y `EventPublisher` | orquestación: cargar → delegar en el dominio → guardar → publicar; traducción ACL del `GenerarOrdenCommand`; que una violación de invariante **no** persista ni publique nada |
| `PaqueteApplicationServiceTest` | **mocks** de los dos repositorios y del `EventPublisher` | coordinación entre dos agregados (paquete + orden) verificando el **orden** de las llamadas con `InOrder` |
| `OrdenApplicationServiceConDoblesEnMemoriaTest` | **fake** (`InMemoryOrdenDeProduccionRepository`) + **spy** propio del `EventPublisher` | flujo completo encadenando varios casos de uso, verificando el *estado* resultante |

Contraste didáctico entre los dos estilos:

- **Mock** (Mockito) → se verifica la **interacción**: *«se llamó a `guardar()` una vez y a `publicar()` con un `OrdenGenerada`»*.
- **Fake / Spy** (hecho a mano) → se verifica el **estado**: *«después del flujo, la orden recuperada del repositorio está `COMPLETADA`»*.

Ejemplo de mock + `ArgumentCaptor` (`OrdenApplicationServiceTest`):

```java
@Mock  private OrdenDeProduccionRepository ordenRepository;
@Mock  private EventPublisher              eventPublisher;
@InjectMocks private OrdenApplicationService servicio;
@Captor private ArgumentCaptor<List<Object>> eventosCaptor;

@Test
void guardaYPublica() {
    given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

    OrdenDeProduccion resultado = servicio.generarOrden(comandoCon("GRAMOS"));

    verify(ordenRepository).guardar(ordenCaptor.capture());
    verify(eventPublisher).publicar(eventosCaptor.capture());
    assertThat(eventosCaptor.getValue()).hasSize(1).first().isInstanceOf(OrdenGenerada.class);
}
```

### 4.3 Capa de infraestructura — **con mocks**

| Clase de prueba | Dobles usados | Qué verifica |
| :-------------- | :------------ | :----------- |
| `OrdenPersistenceAdapterTest` | **mock** de `SpringDataOrdenRepository` | mapeo agregado ↔ entidad JPA, ida y vuelta sin pérdida de datos, `Optional.empty()` cuando no existe |
| `PaquetePersistenceAdapterTest` | **mock** de `SpringDataPaqueteRepository` | aplanado de la `Etiqueta` en columnas y su reconstitución; caso «paquete aún sin etiqueta» |
| `SpringEventPublisherTest` | **mock** de `ApplicationEventPublisher` | publica cada evento en orden (`InOrder`); tolera lista vacía o nula |
| `OrdenControllerTest` | **mock** de `OrdenApplicationService` | traducción *Request → Command → Response*, filtros de la consulta, editor de enums del `@InitBinder` |
| `PaqueteControllerTest` | **mock** de `PaqueteApplicationService` | ídem para el agregado paquete |
| `GlobalExceptionHandlerTest` | **mock** de `MethodArgumentNotValidException` | mapeo excepción → código HTTP (409 / 404 / 400 / 500) |

Los controllers se prueban **instanciándolos directamente** con el servicio
mockeado, no con `MockMvc`: así siguen siendo pruebas unitarias puras. La
verificación del enrutamiento HTTP, la serialización JSON y las anotaciones
`@Valid` corresponde a las pruebas de integración de la siguiente etapa.

## 5. Cobertura de código (JaCoCo)

Resultado de la última ejecución (`./mvnw test`) — **163 pruebas, 0 fallos**:

| Métrica | Cobertura |
| :------ | --------: |
| Líneas | **98.8 %** (633/641) |
| Ramas | **99.1 %** |
| Instrucciones | **99.1 %** |
| Métodos | **98.2 %** |

Por capa:

| Capa | Líneas | Ramas |
| :--- | -----: | ----: |
| Dominio | 99.3 % | 98.8 % |
| Aplicación | 100 % | 100 % |
| Infraestructura | 97.9 % | 100 % |

Las 8 líneas sin cubrir son constructores sin argumentos exigidos por JPA
(`OrdenJpaEntity`, `ItemOrdenEmbeddable`, `PaqueteJpaEntity`) y un constructor
sobrecargado de `DomainException` que hoy no se usa: código sin comportamiento
propio, cuya prueba no aportaría valor.

Umbral configurado en el `pom.xml` (falla el build si baja): **85 % de líneas y
80 % de ramas**, validado en la fase `verify`.

## 6. Calidad de las pruebas: mutation testing (PIT)

La cobertura dice qué líneas se **ejecutaron**, no cuáles se **comprobaron**. Para
medir lo segundo, PIT introduce mutantes en el código (invierte condiciones, cambia
límites, elimina llamadas) y exige que algún test falle:

```bash
./mvnw -B -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
# -> target/pit-reports/index.html
```

Se aplica sobre `domain.*` y `application.*`, con umbral del **80 %** de mutantes
muertos. La primera ejecución detectó 12 mutantes supervivientes: aserciones que
faltaban sobre la publicación de eventos, sobre `occurredAt` y sobre el vaciado de la
bandeja de eventos del paquete. Al cerrarlos, la cobertura de líneas **no se movió**
(98.8 % antes y después) pero las pruebas pasaron a comprobar de verdad ese código —
exactamente lo que la cobertura sola no distingue.

Detalle del entorno de generación y validación de pruebas con IA:
[`entorno-pruebas-ai.md`](entorno-pruebas-ai.md).

## 7. Qué queda fuera a propósito

`maven-surefire-plugin` está configurado para ejecutar únicamente `**/*Test.java`
y excluir `**/*IT.java`. La clase `ProduccionApplicationIT` (que levanta el
contexto de Spring y necesita datasource) queda por tanto **fuera de `mvn test`**
y se habilitará junto con el resto de pruebas de integración:

- pruebas de repositorio con `@DataJpaTest` + H2 o Testcontainers,
- pruebas de la capa web con `@WebMvcTest` / `@SpringBootTest` + `MockMvc`,
- prueba end-to-end del flujo orden → paquete → evento `PaqueteListo`.
