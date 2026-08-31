# ms-production-packaging

> **Bounded Context:** BC4 · Producción y preparación
> **Clasificación DDD:** Supporting Domain
> **Stack:** Spring Boot 3.4, Maven, Java 21
> **Alcance de este módulo:** únicamente la capa de dominio (`domain/`) — Java puro, sin anotaciones de framework.

## Propósito

Gestiona la generación de órdenes de trabajo a partir del `CalendarioConsolidado` publicado por BC3, la preparación de porciones en cocina, el envasado y el etiquetado de paquetes listos para transferirse a BC5 (Entrega y Logística).

- Recibe el evento `CalendarioConsolidado` y genera la `OrdenDeProduccion`.
- Calcula la cantidad total de cada receta: `porción × nro. de pacientes`.
- Gestiona el ciclo de vida de la orden: `PENDIENTE → EN_PREPARACION → COMPLETADA`.
- Registra el envasado y genera el `PaqueteEtiquetado` por paciente.
- Emite los eventos de dominio: `OrdenGenerada`, `OrdenCompletada`, `PaqueteListo`.

## Diagrama de clases — Diseño táctico DDD

```mermaid
classDiagram
    direction LR

    %% ===================== AGREGADO: OrdenDeProduccion =====================
    class OrdenDeProduccion {
        <<Aggregate Root>>
        -OrdenId id
        -LocalDate fechaProduccion
        -EstadoOrden estado
        -List~ItemOrden~ items
        -List~String~ paqueteIdsGenerados
        +crear(fechaProduccion, items)$ OrdenDeProduccion
        +iniciarPreparacion()
        +marcarItemPreparado(recetaId)
        +registrarPaqueteGenerado(paqueteId)
        +completar()
        +calcularTotalPorciones() int
        +pullEventos() List
    }

    class OrdenId {
        <<Value Object>>
        +String valor
        +nuevo()$ OrdenId
        +de(valor)$ OrdenId
    }

    class ItemOrden {
        <<Value Object>>
        -String recetaId
        -String nombreReceta
        -int cantidadTotal
        -Porcion porcionIndividual
        -EstadoItem estado
        +marcarPreparado()
    }

    class EstadoOrden {
        <<enumeration>>
        PENDIENTE
        EN_PREPARACION
        COMPLETADA
    }

    class EstadoItem {
        <<enumeration>>
        PENDIENTE
        EN_PREPARACION
        PREPARADO
    }

    %% ===================== AGREGADO: PaqueteEtiquetado =====================
    class PaqueteEtiquetado {
        <<Aggregate Root>>
        -PaqueteId id
        -PacienteId pacienteId
        -String ordenId
        -Etiqueta etiqueta
        -EstadoPaquete estado
        +crear(pacienteId, ordenId)$ PaqueteEtiquetado
        +registrarEnvasado()
        +generarEtiqueta(datos)
        +marcarListo()
        +pullEventos() List
    }

    class PaqueteId {
        <<Value Object>>
        +String valor
        +nuevo()$ PaqueteId
        +de(valor)$ PaqueteId
    }

    class PacienteId {
        <<Value Object>>
        +String valor
        +de(valor)$ PacienteId
    }

    class EstadoPaquete {
        <<enumeration>>
        ENVASADO_PENDIENTE
        ENVASADO
        ETIQUETADO
        LISTO
    }

    class Etiqueta {
        <<Value Object>>
        -String nombrePaciente
        -DireccionImpresa direccion
        -CodigoIdentificacion codigo
        -LocalDateTime fechaGeneracion
        +crear(nombre, direccion, codigo)$ Etiqueta
        +esValida() boolean
    }

    class DireccionImpresa {
        <<Value Object>>
        +String calle
        +String numero
        +String referencia
        +String ciudad
        +estaCompleta() boolean
        +formatoParaEtiqueta() String
    }

    class CodigoIdentificacion {
        <<Value Object>>
        +String valor
        +LocalDateTime generadoEn
        +generar(paqueteId)$ CodigoIdentificacion
    }

    class DatosPaciente {
        <<Value Object>>
        +PacienteId id
        +String nombreCompleto
        +DireccionImpresa direccionDelDia
        +esCompleto() boolean
    }

    %% ===================== SHARED =====================
    class Porcion {
        <<Value Object>>
        +double cantidad
        +UnidadMedida unidad
        +String descripcion
        +multiplicar(numeroPacientes) Porcion
        +formatoCorto() String
    }

    class UnidadMedida {
        <<enumeration>>
        GRAMOS
        MILILITROS
        UNIDADES
    }

    %% ===================== EVENTOS DE DOMINIO =====================
    class DomainEvent {
        <<interface>>
        +agregadoId() String
        +occurredAt() LocalDateTime
        +nombreEvento() String
    }

    class OrdenGenerada {
        <<Domain Event>>
        +String ordenId
        +LocalDate fechaProduccion
        +int totalItems
        +LocalDateTime occurredAt
    }

    class OrdenCompletada {
        <<Domain Event>>
        +String ordenId
        +List~String~ paqueteIds
        +LocalDateTime occurredAt
    }

    class PaqueteListo {
        <<Domain Event>>
        +String paqueteId
        +String pacienteId
        +String codigoIdentificacion
        +LocalDateTime occurredAt
    }

    %% ===================== PUERTOS (REPOSITORIOS) =====================
    class OrdenDeProduccionRepository {
        <<interface>>
        +guardar(orden)
        +buscarPorId(id) Optional
        +buscarPorFecha(fecha) Optional
        +listarPorEstado(estado) List
        +existePorFecha(fecha) boolean
    }

    class PaqueteEtiquetadoRepository {
        <<interface>>
        +guardar(paquete)
        +buscarPorId(id) Optional
        +listarPorOrden(ordenId) List
        +listarPorPacienteYFecha(pacienteId, fecha) List
        +listarListosPendientesDeTransferencia() List
        +listarPorEstado(estado) List
        +existeCodigoIdentificacion(codigo) boolean
    }

    %% ===================== RELACIONES =====================
    OrdenDeProduccion "1" *-- "1..*" ItemOrden : contiene
    OrdenDeProduccion --> OrdenId : identidad
    OrdenDeProduccion --> EstadoOrden : estado
    ItemOrden --> EstadoItem : estado
    ItemOrden --> Porcion : porción individual
    Porcion --> UnidadMedida : unidad

    PaqueteEtiquetado --> PaqueteId : identidad
    PaqueteEtiquetado --> PacienteId : ref. externa BC1
    PaqueteEtiquetado --> EstadoPaquete : estado
    PaqueteEtiquetado "1" *-- "0..1" Etiqueta : contiene
    Etiqueta --> DireccionImpresa : dirección impresa
    Etiqueta --> CodigoIdentificacion : código
    CodigoIdentificacion ..> PaqueteId : se genera a partir de
    DatosPaciente --> PacienteId
    DatosPaciente --> DireccionImpresa
    PaqueteEtiquetado ..> DatosPaciente : recibe (no persiste)

    OrdenDeProduccion ..> PaqueteEtiquetado : referencia solo por Id (String)

    OrdenGenerada ..|> DomainEvent
    OrdenCompletada ..|> DomainEvent
    PaqueteListo ..|> DomainEvent

    OrdenDeProduccion ..> OrdenGenerada : emite
    OrdenDeProduccion ..> OrdenCompletada : emite
    PaqueteEtiquetado ..> PaqueteListo : emite

    OrdenDeProduccionRepository ..> OrdenDeProduccion : persiste
    PaqueteEtiquetadoRepository ..> PaqueteEtiquetado : persiste
```

> Las excepciones de dominio (`OrdenSinItemsException`, `TransicionEstadoInvalidaException`, `EtiquetaInvalidaException`, etc.) se omiten del diagrama para no saturarlo. Están documentadas en la tabla de invariantes más abajo y coubicadas junto al agregado que las lanza.

### Lectura del diagrama

- **Agregados raíz:** `OrdenDeProduccion` y `PaqueteEtiquetado`. Cada uno controla su propio ciclo de vida y solo se referencian entre sí **por Id** (`String`), nunca por objeto — así se preserva la consistencia transaccional de cada agregado.
- **Value Objects de identidad:** `OrdenId`, `PaqueteId` (identidad propia del dominio) y `PacienteId` (referencia opaca a BC1).
- **Value Objects de datos:** `ItemOrden`, `Porcion`, `Etiqueta`, `DireccionImpresa`, `CodigoIdentificacion`, `DatosPaciente` — inmutables y autovalidados en su constructor.
- **Eventos de dominio:** implementan la interfaz `DomainEvent` y se acumulan en el agregado (patrón *pull*); la capa de aplicación los extrae con `pullEventos()` y los publica al bus. El dominio no conoce el bus de mensajes.
- **Puertos de repositorio:** interfaces en `domain/repository`; la implementación concreta (JPA, Mongo, etc.) vive en infraestructura, fuera del alcance de este módulo.

## Estructura de paquetes

```
com.nurticenter.produccion
└── domain
    ├── DomainException.java
    ├── model
    │   ├── orden
    │   │   ├── OrdenDeProduccion.java          ← Agregado raíz
    │   │   ├── OrdenId.java                    ← VO (identidad)
    │   │   ├── ItemOrden.java                  ← VO
    │   │   ├── EstadoOrden.java                ← VO (enum)
    │   │   ├── EstadoItem.java                 ← VO (enum)
    │   │   └── *Exception.java                 ← excepciones coubicadas
    │   └── paquete
    │       ├── PaqueteEtiquetado.java          ← Agregado raíz
    │       ├── PaqueteId.java                  ← VO (identidad)
    │       ├── PacienteId.java                 ← VO (ref. externa BC1)
    │       ├── Etiqueta.java                   ← VO
    │       ├── DireccionImpresa.java           ← VO
    │       ├── CodigoIdentificacion.java       ← VO
    │       ├── DatosPaciente.java              ← VO
    │       ├── EstadoPaquete.java              ← VO (enum)
    │       └── *Exception.java                 ← excepciones coubicadas
    ├── shared
    │   ├── Porcion.java                        ← VO compartido
    │   └── UnidadMedida.java                   ← VO (enum)
    ├── event
    │   ├── DomainEvent.java                    ← Interfaz base
    │   ├── OrdenGenerada.java
    │   ├── OrdenCompletada.java
    │   └── PaqueteListo.java
    └── repository
        ├── OrdenDeProduccionRepository.java    ← Puerto
        └── PaqueteEtiquetadoRepository.java    ← Puerto
```

## Invariantes principales

| # | Invariante | Agregado | Excepción |
|---|------------|----------|-----------|
| 1 | Una orden debe tener al menos un `ItemOrden` | `OrdenDeProduccion` | `OrdenSinItemsException` |
| 2 | Una orden `COMPLETADA` es inmutable | `OrdenDeProduccion` | `OrdenYaCompletadaException` |
| 3 | Solo se completa si todos los ítems están `PREPARADO` | `OrdenDeProduccion` | `ItemsPendientesException` |
| 4 | Las transiciones de estado solo avanzan | Ambos | `TransicionEstadoInvalidaException` |
| 5 | El envasado precede al etiquetado | `PaqueteEtiquetado` | `EtiquetaSinEnvasadoException` |
| 6 | La `Etiqueta` debe ser válida antes de marcar `LISTO` | `PaqueteEtiquetado` | `EtiquetaInvalidaException` |
| 7 | Los `DatosPaciente` deben estar completos | `PaqueteEtiquetado` | `DatosPacienteIncompletosException` |

Documentación completa del dominio: [`.agents/bc4-domain-docs`](../../.agents/bc4-domain-docs).

## Build y pruebas

```bash
cd ms-production-packaging

./mvnw test      # 162 pruebas unitarias + reporte de cobertura JaCoCo
./mvnw verify    # además valida el umbral mínimo de cobertura (85 % líneas)
```

Reporte de cobertura: `target/site/jacoco/index.html` (requiere JDK 21).

**162 pruebas unitarias** cubren las tres capas, sin levantar Spring ni base de datos:

| Capa | Enfoque | Cobertura de líneas |
| :--- | :------ | ------------------: |
| Dominio | Java puro, sin dobles: invariantes, transiciones de estado y eventos | 99.3 % |
| Aplicación | **Mocks de Mockito** de los puertos (`OrdenDeProduccionRepository`, `PaqueteEtiquetadoRepository`, `EventPublisher`) para verificar la orquestación; además un **fake** en memoria para el flujo completo | 100 % |
| Infraestructura | **Mocks** de Spring Data JPA, del `ApplicationEventPublisher` y de los servicios de aplicación (adaptadores, mappers, controllers, manejo de errores) | 97.9 % |

Cobertura global: **98.8 % de líneas / 99.1 % de ramas**.

Detalle de la estrategia, herramientas y mapa de pruebas: [`docs/pruebas-unitarias.md`](../docs/pruebas-unitarias.md).

> Las pruebas de integración (`*IT.java`) están excluidas de `mvn test` a propósito:
> se abordan en la siguiente etapa del proyecto.

### Entorno de pruebas asistido por IA

El repositorio incluye un entorno validado para generar y auditar pruebas con IA:
dos subagentes (`test-writer`, `test-reviewer`), tres skills con las convenciones del
proyecto, un gate reproducible (`tools/validate-tests.ps1` / `.sh`) y verificación
objetiva por *mutation testing* con PIT.

```powershell
# desde la raíz del repositorio (ddd_clean)
.\tools\validate-tests.ps1              # compila + suite + umbral de cobertura
.\tools\validate-tests.ps1 -Mutation    # además, mutation testing
```

Resultado medido: **163 tests**, 98.8 % de líneas y **198/200 mutantes muertos** (99 %).
La primera pasada de PIT detectó 12 huecos de verificación que la cobertura no veía —
entre ellos, que se podía borrar la publicación de eventos de dominio sin que fallara
ningún test.

Guía completa: [`docs/entorno-pruebas-ai.md`](../docs/entorno-pruebas-ai.md).
