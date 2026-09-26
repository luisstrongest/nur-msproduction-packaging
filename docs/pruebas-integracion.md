# Entorno de pruebas de integración asistido por IA

> Generación de pruebas de integración de API con agentes de IA **verificable**: quién las
> escribe, con qué criterio, y cómo se demuestra que lo generado vale.

Es el segundo piso del entorno descrito en [`entorno-pruebas-ai.md`](entorno-pruebas-ai.md).
Allí el circuito genera pruebas **unitarias** (unidades aisladas, todo mockeado); aquí
genera pruebas **de integración**: peticiones HTTP reales contra el microservicio levantado
y una base de datos real.

El problema que resuelve es el mismo, con una trampa distinta. En las unitarias, el fallo
típico de una IA es el test que nunca podría romperse. En las de integración es el request
que **solo comprueba que la aplicación está encendida** (`pm.test('200 OK')`) y la colección
que **solo pasa la primera vez**.

> El tercer piso son las **pruebas de contrato** con Pact
> ([`pruebas-contrato.md`](pruebas-contrato.md)): aquí se comprueba que BC4 funciona; allí,
> que BC5 puede consumirlo.

## 1. Qué añade este nivel

Las 163 pruebas unitarias mockean el repositorio, el bus de eventos y el servicio de
aplicación. Eso deja cuatro capas sin verificar de punta a punta:

| Capa | Por qué el test unitario no la ve | Request que la cubre |
| :--- | :-------------------------------- | :------------------- |
| Enrutamiento HTTP y códigos de estado | El controller se instancia a mano, sin Spring MVC | Todos |
| Serialización JSON | Se comprueban objetos, no el cuerpo emitido | `etiqueta: null` hasta ETIQUETADO |
| Validación `@Valid` de los DTOs | No hay binding real | `C.1` y `C.2` → 400 con `detalles[]` |
| Persistencia real (JPA + BD) | El repositorio es un mock | los `GET` que releen el recurso |

La frontera es explícita y está escrita en los skills: **si un escenario se puede comprobar
sin levantar nada, no va aquí**. Repetir por HTTP una invariante de dominio que ya cubre un
test unitario es coste de ejecución sin información nueva.

## 2. Las piezas

| Pieza | Dónde | Rol |
| :---- | :---- | :-- |
| **Ambiente** | `docker-compose.integration.yml`, `application-it.yml` | *Contra qué se prueba*: BD efímera, aislada |
| **Colección** | `postman/bc4-integracion.postman_collection.json` | *Qué se prueba*: 3 flujos, 32 requests, 92 aserciones |
| **Flujos en Java** | `src/test/java/.../integration/Flujo*IT.java` | Los flujos A y B en el lenguaje del proyecto, más los eventos de dominio |
| **Agentes** | `.claude/agents/` | *Quién trabaja*: `integration-test-writer` escribe, `integration-test-reviewer` audita |
| **Skills** | `.claude/skills/` | *Qué sabe*: contrato de la API y convenciones, versionados |
| **Gate + CI** | `tools/validate-integration.*`, `.github/workflows/integration-tests.yml` | *Cómo se comprueba*: un comando, un exit code |

```
       /test-integration <escenario>
              │
              ▼
     ┌──────────────────────┐  carga  ┌─────────────────────────┐
     │ integration-test-    │◄────────┤ skill bc4-api-contract  │
     │      writer          │         │ skill postman-integr... │
     │ escribe + ejecuta    │         └─────────────────────────┘
     └────────┬─────────────┘
              │ requests
              ▼
     ┌──────────────────────┐  carga  ┌─────────────────────────┐
     │ integration-test-    │◄────────┤ skill integration-test- │
     │     reviewer         │         │        review           │
     │ dictamen             │         └─────────────────────────┘
     └────────┬─────────────┘
              │ APROBADO
              ▼
     ┌──────────────────────────────────────────────────────────┐
     │  tools\validate-integration.ps1     (exit 0 = aceptado)   │
     │  ambiente · health · Newman · repetibilidad              │
     └────────┬─────────────────────────────────────────────────┘
              │ push
              ▼
        GitHub Actions — el mismo gate, PostgreSQL real, máquina limpia
```

## 3. El ambiente

Dos modos, el mismo gate:

- **Docker (referencia)** — `docker-compose.integration.yml`: PostgreSQL 16 con los datos
  en `tmpfs` y `ddl-auto: create-drop`, más la app construida desde el `Dockerfile` del
  microservicio. La app no arranca hasta que la BD pasa su healthcheck. Puertos separados
  de los de desarrollo (`55432` para Postgres).
- **Local (rápido)** — perfil `it` con H2 en memoria. Arranca en ~50 s sin Docker. Es lo
  que se usa para iterar; el CI lo reutiliza apuntándolo a su PostgreSQL de servicio por
  variables de entorno.

La propiedad importante en los dos casos: **la base de datos nace vacía y muere con la
ejecución**. Sin eso, los resultados dependen de lo que hubiera antes.

## 4. Agentes

### `integration-test-writer`

Escribe requests y sus aserciones. Lo que lo diferencia de pedirle una colección a un chat:

- **Carga el contrato antes de escribir** (`bc4-api-contract`): endpoints, DTOs y el mapeo
  excepción → HTTP están versionados en el repositorio, así que no inventa campos ni rutas.
- **Ejecuta lo que escribe**, y además **dos veces seguidas** contra la misma base de datos.
  Regla dura: *nunca entrega un request que no haya visto pasar, ni una colección que solo
  pase la primera vez*.
- **No adapta la aserción al comportamiento observado.** Si espera 409 y recibe 500, para y
  lo reporta como bug. Cambiar el test a `500` para verlo verde es el fallo más peligroso
  de un agente autónomo escribiendo pruebas de integración — el equivalente aquí de tocar
  `src/main` para que el test pase.

### `integration-test-reviewer`

Audita. **No tiene `Write` ni `Edit` a propósito**: su salida es un dictamen
(`APROBADO` / `APROBADO CON OBSERVACIONES` / `RECHAZADO`) y quien corrige es el escritor.

Su regla central es la traducción de la *regla del mutante* a este nivel:

> Si el repositorio dejara de guardar, o el handler devolviera 500 en vez de 409,
> ¿fallaría alguna aserción? Si no, el request solo está probando que Tomcat responde.

## 5. Skills

| Skill | Contenido |
| :---- | :-------- |
| `bc4-api-contract` | El contrato real de la API: 15 endpoints, DTOs de entrada con sus validaciones y mensajes, DTOs de salida con los formatos que genera el dominio (`PKG-XXXXXXXX`, el formato de impresión de la dirección), la tabla **excepción → código HTTP**, las 11 reglas de negocio observables y las máquinas de estado. Es lo que evita que el agente invente. |
| `postman-integration-testing` | Convenciones de la colección: una carpeta por flujo de negocio, qué variable va en qué ámbito, encadenado de ids, **repetibilidad**, qué asertar según el tipo de escenario, y cómo editar el JSON sin romperlo. Con `references/plantillas.md` (7 esqueletos copiables). |
| `spring-integration-testing` | Convenciones de los flujos en Java: `@SpringBootTest` + MockMvc con el perfil `it`, una clase por flujo, fechas propias por clase porque el contexto (y su H2) se comparte, relectura por `GET` y verificación de eventos con `@RecordApplicationEvents`. |
| `integration-test-review` | Lista de rechazo en tres niveles (7 bloqueantes / 8 graves / 5 menores) y formato del dictamen. |

## 6. El gate

`tools/validate-integration.ps1` (Windows) y `.sh` (CI y Git Bash) hacen lo mismo en cinco
pasos, con exit codes distinguibles:

```powershell
.\tools\validate-integration.ps1              # Docker + PostgreSQL real
.\tools\validate-integration.ps1 -Mode local  # H2, sin Docker
.\tools\validate-integration.ps1 -Twice       # exige dos vueltas en verde
```

| Exit | Significado |
| ---: | :---------- |
| 0 | validación superada |
| 1 | aserciones fallidas |
| 2 | el ambiente no levantó |
| 3 | falta un prerrequisito (Docker, Node, la colección) |
| 4 | timeout esperando `/actuator/health` |

Dos problemas reales de esta máquina que el gate resuelve en vez de dejar al usuario:

- **El npm local apunta a un registro privado (AWS CodeArtifact) con el token caducado**, y
  `npx newman` falla con `E401` por un motivo que no tiene nada que ver con las pruebas. El
  gate fuerza `--registry=https://registry.npmjs.org`.
- **Windows PowerShell 5.1 devuelve el cuerpo de `/actuator/health` como `byte[]`**, porque
  no reconoce `application/vnd.spring-boot.actuator.v3+json` como texto. Un `-match` directo
  sobre `.Content` nunca casa y el gate se quedaba esperando a una aplicación que ya estaba
  arriba. Se decodifica explícitamente.

## 7. Verificación objetiva: la repetibilidad

En las pruebas unitarias, la verificación que no se puede falsificar es el *mutation
testing*. En las de integración, el equivalente barato y decisivo es **ejecutar dos veces
contra la misma base de datos**:

```powershell
.\tools\validate-integration.ps1 -Mode local -Twice
```

Una colección que solo pasa con la base recién creada no es una prueba: es una demo. Y es
un fallo que la cobertura, el resumen de Newman y la revisión visual **no muestran** — solo
aparece al repetir.

En este dominio la trampa concreta es la regla *"una sola orden de producción por fecha"*
(`existePorFecha` → 409). La colección anterior del proyecto (`postman/` de la carpeta
padre) usa `fechaProduccion` fija: pasa la primera vez y falla la segunda. La colección de
integración genera la fecha en un *pre-request*, con rangos de días disjuntos por flujo
para que no choquen entre sí.

El CI ejecuta el gate con `--twice`, de modo que una colección no repetible no puede
llegar a `main`.

## 8. Proyecto Final: agrupación por flujo y flujos en Java

El Proyecto Final pide que las pruebas de integración cubran **al menos dos flujos
completos** de la aplicación, agrupadas por flujo. La colección de la Actividad 2 tenía un
solo flujo de éxito (que lo hacía todo) y una carpeta de errores, así que se reorganizó:

| Carpeta | Requests | Flujo |
| :------ | -------: | :---- |
| `01 - Flujo A: Produccion en cocina` | 10 | orden generada → consultada por id y por fecha → EN_PREPARACION → ítems preparados → COMPLETADA, verificando los filtros `?estado=` |
| `02 - Flujo B: Envasado, etiquetado y entrega a BC5` | 11 | [setup] orden de 1 ítem → paquete → envasado → etiquetado → LISTO → visible en `GET /paquetes` (la consulta de BC5) → orden COMPLETADA con el paquete |
| `03 - Flujo C: Rechazos` | 11 | la antigua carpeta de errores: 400 / 404 / 409 con su `ApiError` y sin estado a medias |

Cada flujo es **autocontenido** (se lanza solo con `--folder`) y usa su propio rango de
fechas (`1..4000`, `4001..8000`, `8001..12000` días), de modo que la colección sigue siendo
repetible.

Los flujos A y B existen también **en Java**, porque el enunciado admite las dos formas y
cada una ve cosas que la otra no:

| Clase | Tests | Qué añade frente a Postman |
| :---- | ----: | :------------------------- |
| `FlujoProduccionIT` | 2 | El flujo A completo + el rechazo de completar con ítems pendientes. Verifica que se publican **una vez** `OrdenGenerada` y `OrdenCompletada`, y que el rechazo **no** publica `OrdenCompletada`. |
| `FlujoEnvasadoIT` | 2 | El flujo B completo + el rechazo de etiquetar sin envasar. Verifica que `PaqueteListo` lleva el mismo código impreso en la etiqueta y que `OrdenCompletada` referencia al paquete. |

Levantan el contexto completo de Spring (`@SpringBootTest` + MockMvc, perfil `it` con H2) y
recorren controller → servicio → JPA → BD sin mocks. Los eventos de dominio no salen por
HTTP: desde Postman son invisibles, y aquí se capturan con `@RecordApplicationEvents`.

```powershell
cd ms-production-packaging
.\mvnw.cmd -Pintegration verify     # unitarias + umbral + los dos flujos (failsafe)
```

**Falsabilidad comprobada:** se comentó a propósito la publicación de eventos en
`PaqueteApplicationService.marcarListo()` y `FlujoEnvasadoIT` falló (1 de 4 tests); se
restauró la línea y los 4 vuelven a verde. Una prueba que siguiera en verde con esa línea
borrada no estaría verificando la entrega a BC5.

### Resultados medidos (25/09/2026)

```
tools\validate-integration.ps1 -Mode local -Twice -Report
  vuelta 1 - Requests   : 32 ejecutados, 0 fallidos
  vuelta 1 - Aserciones : 92 ejecutadas, 0 fallidas
  vuelta 2 - Requests   : 32 ejecutados, 0 fallidos
  vuelta 2 - Aserciones : 92 ejecutadas, 0 fallidas
  HTML       : docs\reportes\integracion-postman\index.html
OK: validacion de integracion superada.

mvnw -Pintegration (failsafe)
  Tests run: 2  -- FlujoEnvasadoIT
  Tests run: 2  -- FlujoProduccionIT
  Tests run: 4, Failures: 0, Errors: 0, Skipped: 0     BUILD SUCCESS
```

Modo de referencia (Docker, PostgreSQL 16 real y efímero):

```
tools\validate-integration.ps1 -Twice -Report
  Contenedores arriba: bc4-it-db (PostgreSQL efimero) + bc4-it-app
  vuelta 1 - Aserciones : 92 ejecutadas, 0 fallidas
  vuelta 2 - Aserciones : 92 ejecutadas, 0 fallidas
  Bajando el ambiente Docker...
OK: validacion de integracion superada.
```

Reportes versionados: `docs/reportes/integracion-postman/index.html` (Newman htmlextra) y
`docs/reportes/integracion-java/` (failsafe). Todo junto: `tools\run-all-tests.ps1`.

> **Corrección del gate encontrada en el camino:** en esta máquina `java` es el *shim* de
> Oracle (`javapath\java.exe`), que lanza la JVM real como proceso hijo. `Stop-Process`
> mataba solo el shim y la aplicación seguía ocupando el puerto 8082 tras el gate. Ahora se
> usa `taskkill /T` para bajar el árbol completo.

## 9. Resultados medidos en la Actividad 2 (histórico)

> Medición con la colección original de dos carpetas, antes de la reorganización por flujo.

Ejecución del gate sobre `ms-production-packaging` (10/09/2026), en los dos modos:

```
=== 2/5  Ambiente de pruebas ====================================
  Contenedores arriba: bc4-it-db (PostgreSQL efimero) + bc4-it-app

=== 3/5  Esperando /actuator/health =============================
  UP tras 86 s aprox.

=== 5/5  Resumen ================================================
  Requests   : 23 ejecutados, 0 fallidos
  Aserciones : 69 ejecutadas, 0 fallidas

OK: validacion de integracion superada.        exit 0
```

| Modo | Ambiente | Resultado |
| :--- | :------- | :-------- |
| `docker` (referencia) | PostgreSQL 16 efímero + la app desde el `Dockerfile` | 23/23 requests, 69/69 aserciones, exit 0 |
| `local` | jar con perfil `it`, H2 en memoria | 23/23 requests, 69/69 aserciones, exit 0 |
| `local -Twice` | dos vueltas, **misma** base de datos | 69/69 y 69/69, exit 0 |

Reparto de la colección:

| Carpeta | Requests | Qué verifica |
| :------ | -------: | :----------- |
| `01 - Flujo correcto` | 12 | Ciclo end-to-end: orden → preparación → paquete → envasado → etiquetado → listo → orden completada |
| `02 - Flujo incorrecto` | 11 | 3 de preparación del escenario + 6 errores esperados + 2 comprobaciones de que el error no dejó estado corrupto |

Lo que estas 69 aserciones comprueban y las 163 unitarias no podían:

- que un `EtiquetaSinEnvasadoException` llega al cliente como **409**, con el mensaje del
  dominio, y no como un 500;
- que `@Valid` reporta **los dos** campos inválidos de una vez, con su ruta dentro de la
  lista (`items[0].numeroPacientes`);
- que la orden, releída de la base de datos, conserva sus dos ítems, su porción y el id del
  paquete que se creó después (`paqueteIds`);
- que la etiqueta se serializa como `null` hasta el estado ETIQUETADO y a partir de ahí con
  el formato de impresión que construye el dominio;
- que un error de negocio **no persiste nada a medias**: el paquete rechazado sigue en
  `ENVASADO_PENDIENTE` y sin etiqueta.

> **Sobre los tiempos:** la primera ejecución en modo `docker` construye la imagen desde
> cero (imagen base + descarga de dependencias Maven dentro del contenedor) y puede tardar
> bastante en una red lenta. Las siguientes reutilizan la caché de capas: solo se recompila
> si cambia el código. Para iterar, `-Mode local` arranca en menos de un minuto.

### La verificación del propio gate

El gate se comprobó saboteando la colección a propósito: se sustituyó el *pre-request* del
request `1.1` (hoy `A.1`) por una `fechaProduccion` fija (`2026-07-15`) y se ejecutó con `-Twice`.

```
  vuelta 1 - Aserciones : 69 ejecutadas, 0 fallidas
  vuelta 2 - Aserciones : 63 ejecutadas, 36 fallidas
    - [1.1 Generar la orden de produccion] Responde 201 CREATED
      expected response to have status code 201 but got 409
    ...
  La primera vuelta paso y la segunda no: la coleccion NO es repetible.

FALLO: la validacion de integracion no paso.                       exit 1
```

Una única aserción en 409 arrastra 36 fallos, porque el resto del flujo se queda sin
`ordenId`. Restaurado el *pre-request*, las dos vueltas vuelven a 69/69 y exit 0. Es la
evidencia de que el gate detecta el fallo que el resumen en verde de una sola vuelta —y una
revisión visual de la colección— no muestran.

## 10. Guardarraíles

`.claude/settings.json` añade a lo que ya había:

- **`allow`** — los dos scripts de validación, `newman`, `docker compose` limitado al fichero
  de integración, y `curl` contra `localhost:8082`. Lo rutinario no se aprueba una por una,
  que es lo que empuja a aprobar sin leer.
- **`ask`** — editar `src/main/**`, cualquier `pom.xml` y ahora también
  `docker-compose.integration.yml`: el agente de pruebas no cambia el ambiente sin que se vea.
- **`deny`** — `git push`, `rm -rf`, `mvn deploy`, `docker system prune`, leer `.env`.

## 11. Cómo reproducirlo

```powershell
# 1. Abrir Claude Code en la carpeta del repositorio (ddd_clean)
cd "D:\Repositorio Luis\nur_proy_final\ddd_clean"

# 2. Validar el estado actual sin generar nada
.\tools\validate-integration.ps1 -Mode local -Twice
cd ms-production-packaging; .\mvnw.cmd -Pintegration verify; cd ..

# 3. Generar pruebas para un escenario nuevo
#    /test-integration listar paquetes LISTOS pendientes de transferencia
#    -> integration-test-writer escribe y ejecuta
#    -> integration-test-reviewer dictamina
#    -> validate-integration.ps1 decide
```

**Demo reproducible** del circuito, análoga a la de las unitarias:

1. Cambia en la colección la `fechaProduccion` del request `A.1` por una fija (por ejemplo
   `"2026-07-15"`), quitando el script *pre-request*.
2. Ejecuta `.\tools\validate-integration.ps1 -Mode local -Twice`: la primera vuelta pasa y
   **la segunda falla con 409**. El gate devuelve exit 1 y dice explícitamente que la
   colección no es repetible.
3. Restaura el *pre-request* y vuelve a lanzarlo: exit 0.

La diferencia entre los pasos 2 y 3 es la evidencia de que el gate detecta el fallo que una
revisión visual de la colección —y el resumen en verde de la primera vuelta— no muestran.
