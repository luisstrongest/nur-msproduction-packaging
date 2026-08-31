# Arquitectura — BC4 · Producción y Empaquetado (`ms-production-packaging`)

> **Bounded Context:** Producción y preparación (Supporting Domain)
> **Stack:** Spring Boot 3.4 · Java 21 · PostgreSQL
> **Estilo:** Domain-Driven Design + Clean Architecture (Hexagonal / Puertos y Adaptadores)

Este documento explica **cómo el microservicio cumple DDD y Clean Architecture** y **dónde se evidencian los patrones de diseño**, con referencias directas al código.

---

## Índice

1. [Visión general en capas](#1-visión-general-en-capas)
2. [La Regla de Dependencia](#2-la-regla-de-dependencia)
3. [Bloques tácticos de DDD](#3-bloques-tácticos-de-ddd)
4. [Puertos y Adaptadores (Hexagonal)](#4-puertos-y-adaptadores-hexagonal)
5. [Catálogo de patrones de diseño](#5-catálogo-de-patrones-de-diseño)
6. [Principios SOLID](#6-principios-solid)
7. [Recorrido de una petición (end-to-end)](#7-recorrido-de-una-petición-end-to-end)
8. [Cómo verificarlo](#8-cómo-verificarlo)

---

## 1. Visión general en capas

La estructura de paquetes **es** la arquitectura. Está partida en las tres capas de Clean Architecture y las dependencias solo apuntan hacia el centro (dominio).

```
com.nurticenter.produccion
├── domain/            ← NÚCLEO. Java puro, CERO imports de Spring/JPA
│   ├── model/
│   │   ├── orden/     OrdenDeProduccion (AR), ItemOrden, OrdenId, EstadoOrden, excepciones…
│   │   └── paquete/   PaqueteEtiquetado (AR), Etiqueta, DireccionImpresa, PacienteId, excepciones…
│   ├── event/         DomainEvent, OrdenGenerada, OrdenCompletada, PaqueteListo
│   ├── repository/    PUERTOS: OrdenDeProduccionRepository, PaqueteEtiquetadoRepository
│   └── shared/        Porcion, UnidadMedida (value objects compartidos)
│
├── application/       ← CASOS DE USO. Orquesta el dominio, no contiene reglas de negocio
│   ├── orden/         OrdenApplicationService (+ dto/GenerarOrdenCommand)
│   ├── paquete/       PaqueteApplicationService (+ dto/Crear…, Generar…)
│   └── event/         PUERTO: EventPublisher
│
└── infrastructure/    ← DETALLES. Depende hacia adentro; implementa los puertos
    ├── web/           Controllers REST, DTOs HTTP, GlobalExceptionHandler
    ├── persistence/   Adaptadores JPA, entidades, mappers (Data Mapper)
    ├── event/         SpringEventPublisher (adaptador del bus)
    └── config/        OpenApiConfig
```

```
   ┌─────────────────────────────────────────────────────────┐
   │  infrastructure/   (web · persistence · event · config)  │
   │      │  implementa puertos          ▲ inyección          │
   │      ▼                              │                    │
   │  ┌─────────────────────────────────────────────────┐    │
   │  │  application/   (casos de uso, orquestación)     │    │
   │  │      │  usa el dominio                           │    │
   │  │      ▼                                           │    │
   │  │  ┌───────────────────────────────────────────┐  │    │
   │  │  │  domain/  (agregados, VOs, eventos,        │  │    │
   │  │  │           puertos)  ← NO depende de nadie  │  │    │
   │  │  └───────────────────────────────────────────┘  │    │
   │  └─────────────────────────────────────────────────┘    │
   └─────────────────────────────────────────────────────────┘
          Las flechas de dependencia SIEMPRE apuntan hacia adentro
```

---

## 2. La Regla de Dependencia

El núcleo de Clean Architecture: **el código de dominio no conoce ningún detalle externo**.

- Nada en `domain/` importa `org.springframework.*`, `jakarta.persistence.*`, Jackson, etc.
- El dominio es **Java puro**: se puede compilar y testear sin levantar Spring ni una base de datos.
- Spring solo aparece en `application/` (`@Service`, `@Transactional`) y en `infrastructure/`.

Verificable en un comando:

```bash
grep -r "org.springframework\|jakarta.persistence" \
     ms-production-packaging/src/main/java/com/nurticenter/produccion/domain/
# → sin resultados
```

---

## 3. Bloques tácticos de DDD

| Bloque | Clases | Evidencia |
|--------|--------|-----------|
| **Aggregate Root** | `OrdenDeProduccion`, `PaqueteEtiquetado` | Constructor **privado** + factory method; encapsulan invariantes; exponen comportamiento, no setters |
| **Entity** | `ItemOrden` | Identidad (`recetaId`) y estado mutable dentro del agregado |
| **Value Object** | `OrdenId`, `PaqueteId`, `PacienteId`, `Porcion`, `Etiqueta`, `DireccionImpresa`, `CodigoIdentificacion` | `record` inmutables, validados en el constructor compacto |
| **Domain Event** | `OrdenGenerada`, `OrdenCompletada`, `PaqueteListo` | `record` inmutables que implementan `DomainEvent` |
| **Repository (puerto)** | `OrdenDeProduccionRepository`, `PaqueteEtiquetadoRepository` | Interfaces **dentro del dominio** |
| **Domain Exception** | `DomainException` + 9 subtipos | Protegen invariantes desde el agregado; se traducen a 409 |
| **Ubiquitous Language** | Todo el código | Métodos con el lenguaje del negocio: `envasar`, `etiquetar`, `marcarListo`, `completar` |

### 3.1 Rich Domain Model — la lógica vive en el agregado

`domain/model/paquete/PaqueteEtiquetado.java` (método `generarEtiqueta`):

```java
public void generarEtiqueta(DatosPaciente datos) {
    if (this.estado != EstadoPaquete.ENVASADO)
        throw new EtiquetaSinEnvasadoException(...);        // invariante de transición
    if (datos == null || !datos.esCompleto())
        throw new DatosPacienteIncompletosException(...);   // invariante de datos
    CodigoIdentificacion codigo = CodigoIdentificacion.generar(this.id);
    this.etiqueta = Etiqueta.crear(datos.nombreCompleto(), datos.direccionDelDia(), codigo);
    this.estado   = EstadoPaquete.ETIQUETADO;
}
```

La regla de negocio **no** está en el controller ni en la base de datos: está en el objeto de dominio. Esto es un *rich model*, no un *anemic model*.

### 3.2 Value Object con comportamiento e inmutabilidad

`domain/shared/Porcion.java`:

```java
public Porcion multiplicar(int numeroPacientes) {
    if (numeroPacientes <= 0) throw new IllegalArgumentException(...);
    return new Porcion(this.cantidad * numeroPacientes, this.unidad, this.descripcion);
}
```

No muta: devuelve una **nueva** `Porcion`. Se valida a sí mismo en el constructor compacto.

### 3.3 Referencias entre agregados por identidad

- `OrdenDeProduccion` guarda `List<String> paqueteIdsGenerados` (solo IDs), no objetos `PaqueteEtiquetado`.
- `PaqueteEtiquetado` referencia al paciente con `PacienteId` opaco (ref. a BC1) y a la orden con `String ordenId`.

Cada agregado es su propia **frontera de consistencia** y unidad transaccional — regla central de DDD.

### 3.4 Eventos de dominio con patrón "pull" (estilo Outbox)

El agregado **crea** el evento pero **no lo publica** (no puede conocer el bus sin violar la Regla de Dependencia). Lo acumula en una lista interna; la capa de aplicación lo extrae con `pullEventos()`:

```java
// PaqueteEtiquetado.java
public List<Object> pullEventos() {
    List<Object> copia = List.copyOf(this.eventos);
    this.eventos.clear();        // evita doble publicación
    return copia;
}
```

---

## 4. Puertos y Adaptadores (Hexagonal)

El patrón estructural dominante. Ejemplo con la persistencia:

```
DOMINIO define el PUERTO                 INFRAESTRUCTURA provee el ADAPTADOR
────────────────────────                 ───────────────────────────────────
domain/repository/                        infrastructure/persistence/orden/
  OrdenDeProduccionRepository  ◄─────────   OrdenPersistenceAdapter
        (interface)                            (@Repository, implements)
                                                     │
                                                     ▼
                                            SpringDataOrdenRepository (JPA)
```

Inversión de Dependencia evidenciada:

- **Puerto en el dominio:** `domain/repository/OrdenDeProduccionRepository.java` — documenta *"el dominio depende de esta abstracción, no de Spring Data, JPA ni ninguna tecnología concreta"*.
- **Adaptador en infraestructura:** `infrastructure/persistence/orden/OrdenPersistenceAdapter.java` → `implements OrdenDeProduccionRepository`.
- **La aplicación depende de la interfaz:** `OrdenApplicationService` recibe `OrdenDeProduccionRepository` por constructor, nunca la implementación concreta.

Cambiar PostgreSQL por otra tecnología solo toca `infrastructure/persistence/`; el dominio y la aplicación no se enteran.

El mismo patrón aplica a los eventos: puerto `application/event/EventPublisher` ← adaptador `infrastructure/event/SpringEventPublisher`.

---

## 5. Catálogo de patrones de diseño

| Patrón | Tipo | Dónde | Evidencia |
|--------|------|-------|-----------|
| **Factory Method** | Creacional | `OrdenDeProduccion.crear()`, `PaqueteEtiquetado.crear()`, `OrdenId.nuevo()/.de()` | Constructor privado + método estático que garantiza objetos válidos |
| **Repository** | DDD / Enterprise | `domain/repository/*` | Persistencia abstraída como colección de dominio |
| **Adapter (Ports & Adapters)** | Estructural | `OrdenPersistenceAdapter`, `PaquetePersistenceAdapter`, `SpringEventPublisher` | Adaptan tecnología concreta al puerto de dominio |
| **Data Mapper** | Enterprise | `OrdenEntityMapper`, `PaqueteEntityMapper` | Traducen dominio ↔ entidad JPA; mantienen *"el dominio libre de anotaciones de persistencia"* |
| **DTO** | Enterprise | `GenerarOrdenRequest`, `OrdenResponse`, `PaqueteResponse`… | Separan el modelo HTTP del modelo de dominio |
| **Command** | Comportamiento | `GenerarOrdenCommand`, `CrearPaqueteCommand`, `GenerarEtiquetaCommand` | Encapsulan la intención de un caso de uso |
| **Anti-Corruption Layer (ACL)** | DDD | `OrdenApplicationService.traducirItem()` | Traduce el `CalendarioConsolidado` de BC3 a `ItemOrden` de BC4 |
| **State (máquina de estados)** | Comportamiento | `EstadoOrden`, `EstadoPaquete` + guardas de transición | Cada método valida el estado origen antes de transicionar |
| **Observer / Publish–Subscribe** | Comportamiento | Eventos de dominio + `ApplicationEventPublisher` | `SpringEventPublisher` publica al bus interno |
| **Domain Events + Outbox ("pull")** | DDD | `pullEventos()` en ambos agregados | El agregado acumula; la aplicación extrae y publica |
| **Facade / Application Service** | Enterprise | `OrdenApplicationService`, `PaqueteApplicationService` | Orquestan: cargar → operar dominio → guardar → publicar |
| **Dependency Injection** | Estructural | Constructores en todos los `@Service` / `@Repository` | Inyección por constructor (inmutable y testeable) |

### Ciclos de vida como máquinas de estado

```
OrdenDeProduccion:   PENDIENTE ──► EN_PREPARACION ──► COMPLETADA
                                      (todos los items PREPARADO)

PaqueteEtiquetado:   ENVASADO_PENDIENTE ──► ENVASADO ──► ETIQUETADO ──► LISTO
```

Cada transición está protegida por una guarda que lanza una `DomainException` si el estado origen no es válido.

---

## 6. Principios SOLID

| Principio | Evidencia en el proyecto |
|-----------|--------------------------|
| **S** · Single Responsibility | `OrdenDeProduccion` gestiona la orden; el mapper solo mapea; el controller solo traduce HTTP |
| **O** · Open/Closed | Agregar un evento = implementar `DomainEvent`, sin modificar los existentes |
| **L** · Liskov Substitution | Cualquier `DomainException` se maneja uniformemente en `GlobalExceptionHandler` |
| **I** · Interface Segregation | Los repositorios exponen **solo** los métodos que el dominio usa; sin `findAll`/`deleteById` heredados |
| **D** · Dependency Inversion | Dominio y aplicación dependen de puertos; la infraestructura provee los adaptadores |

---

## 7. Recorrido de una petición (end-to-end)

Ejemplo: **etiquetar un paquete** (`POST /api/v1/paquetes/{id}/etiquetar`).

```
1. infrastructure/web       PaqueteController.etiquetar()
                            recibe GenerarEtiquetaRequest (DTO HTTP) → GenerarEtiquetaCommand
                                     │
2. application              PaqueteApplicationService.generarEtiqueta()
                            carga el agregado por el PUERTO repository,
                            invoca comportamiento de dominio, guarda, publica eventos
                                     │
3. domain                   PaqueteEtiquetado.generarEtiqueta()
                            valida invariantes y cambia de estado  ← REGLA DE NEGOCIO
                                     │
4. infrastructure/persist.  PaquetePersistenceAdapter + PaqueteEntityMapper
                            traducen el agregado a entidad JPA y persisten
                                     │
5. infrastructure/event     SpringEventPublisher publica los eventos al bus
```

Cada caso de uso de escritura sigue el mismo esqueleto (ver `OrdenApplicationService`):

```java
@Transactional
public OrdenDeProduccion generarOrden(GenerarOrdenCommand comando) {
    // ... guardas de aplicación (unicidad por fecha)
    List<ItemOrden> items = comando.items().stream()
            .map(this::traducirItem)                       // ACL
            .toList();
    OrdenDeProduccion orden = OrdenDeProduccion.crear(...); // dominio (Factory + evento)
    ordenRepository.guardar(orden);                        // puerto de persistencia
    eventPublisher.publicar(orden.pullEventos());          // puerto de eventos
    return orden;
}
```

**Conclusión:** el dominio es el centro y no depende de nadie; web, JPA y mensajería son adaptadores intercambiables en el borde.

---

## 8. Cómo verificarlo

| Afirmación | Cómo demostrarla |
|------------|------------------|
| El dominio no depende de frameworks | `grep -r "springframework\|jakarta.persistence" .../domain/` → vacío |
| Los agregados protegen invariantes | Intentar una transición inválida por REST → responde **409** (ver colección Postman, *Flujo D*) |
| Los puertos están en el dominio | Las interfaces `*Repository` viven en `domain/repository/`, sus `implements` en `infrastructure/` |
| El modelo de dominio ≠ modelo de persistencia | Comparar `OrdenDeProduccion` (sin anotaciones) vs `OrdenJpaEntity` (`@Entity`) y su `OrdenEntityMapper` |
| Los eventos se desacoplan del bus | El agregado crea el evento; solo el `ApplicationService` llama a `eventPublisher.publicar()` |

---

### Documentación relacionada

- `.agents/bc4-domain-docs/` — documentación detallada de la capa de dominio (agregados, value objects, eventos, reglas de negocio).
- Colección Postman `postman/ms-production-packaging.postman_collection.json` — flujos A/B/C y casos de error (Flujo D) para demostrar las invariantes en vivo.
