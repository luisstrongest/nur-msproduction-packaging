# Pruebas de contrato con Pact — entorno asistido por IA

> Taller de *contract testing* aplicado al caso de estudio: el contrato entre
> **BC4 · Producción y Envasado** (`ms-production-packaging`) y
> **BC5 · Entrega y Logística** (`ms-logistic-delivery`), verificable con un solo comando.

Es el tercer piso del entorno descrito en
[`entorno-pruebas-ai.md`](entorno-pruebas-ai.md) (unitarias) y
[`pruebas-integracion.md`](pruebas-integracion.md) (integración de API). Los tres
comparten la misma forma —agentes, skills, un gate con exit code y una verificación que
no se puede falsificar— y cambian en qué prueban y en cuál es la trampa típica de ese
nivel.

| Nivel | Qué prueba | Trampa típica de una prueba generada por IA |
| :---- | :--------- | :------------------------------------------ |
| Unitarias | Reglas del agregado, con todo mockeado | El test que nunca podría romperse |
| Integración | BC4 entero: HTTP + JPA + base de datos real | El request que solo comprueba que la app está encendida |
| **Contrato** | Que la respuesta de BC4 contiene **lo que BC5 necesita** | El pacto con el *body* entero en *matchers* de tipo: pasa con cualquier respuesta |

## 1. Qué problema resuelve

Los microservicios del grupo se despliegan por separado. El día que BC4 renombre
`codigoIdentificacion` a `codigo`, sus 163 pruebas unitarias seguirán en verde, su
colección de 23 requests de integración seguirá en verde, y **BC5 dejará de imprimir
etiquetas en producción**. Ninguna prueba de BC4 mira lo que BC5 necesita, porque ninguna
prueba de BC4 sabe que BC5 existe.

Las dos salidas habituales a ese problema son malas:

- **Entorno end-to-end** con los dos servicios levantados: lento, frágil y exige que los
  dos equipos desplieguen a la vez, que es justo lo que los microservicios querían evitar.
- **Documentar la API y confiar**: la documentación no falla el build.

El *contract testing* invierte la dirección. El **consumidor declara lo que necesita**, y
esa declaración se convierte en un artefacto ejecutable —el **pacto**— que el
**proveedor verifica contra sí mismo**, solo, en su propio pipeline. Los dos servicios
nunca se levantan juntos.

```
 BC5 declara lo que necesita  ──►  pacto .json  ──►  BC4 demuestra que lo cumple
   (consumer test, mock)            (generado)         (provider test, app real)
```

**Lo que no resuelve:** el pacto no dice que el dato sea correcto —que el total de
porciones esté bien calculado, que el paquete se haya persistido—. Eso es trabajo de las
unitarias y de las de integración. El pacto dice que los dos servicios **hablan el mismo
idioma**.

## 2. El caso de estudio

De todas las relaciones entre bounded contexts del proyecto, la elegida es la de la
transferencia de paquetes:

```
BC3 Planificación  ──CalendarioConsolidado──►  BC4 Producción y Envasado
                                                      │
                                                      │  GET /api/v1/paquetes
                                                      ▼
                                               BC5 Entrega y Logística
                                              (consulta los paquetes LISTOS,
                                               imprime la guía y reparte)
```

Es la pareja con más riesgo real: BC5 consume la API HTTP de BC4 en cada ronda de
reparto, lee la etiqueta que BC4 genera y decide con el estado del paquete. El contrato
va, por tanto, en el sentido **BC5 (consumidor) → BC4 (proveedor)**.

## 3. Las piezas

| Pieza | Dónde | Rol |
| :---- | :---- | :-- |
| **Cliente del consumidor** | `contract-testing/bc5-logistic-consumer/src/main` | *Lo que se prueba*: el `ClienteProduccionBC4` que BC5 usaría de verdad |
| **Pruebas del consumidor** | `…/src/test/…/PaquetesConsumerPactTest.java` | *Qué se declara*: 4 interacciones |
| **Pacto** | `ms-production-packaging/src/test/resources/pacts/*.json` | *El artefacto*: generado, nunca escrito a mano |
| **Verificación del proveedor** | `…/src/test/…/contract/PaquetesProviderContractIT.java` | *Quién demuestra*: BC4 reproduce cada interacción |
| **Agentes** | `.claude/agents/pact-writer.md`, `pact-reviewer.md` | *Quién trabaja*: uno escribe, otro audita |
| **Skills** | `.claude/skills/pact-contract-testing/`, `pact-contract-review/` | *Qué sabe*: convenciones y lista de rechazo |
| **Gate + CI** | `tools/validate-contracts.*`, `.github/workflows/contract-tests.yml` | *Cómo se comprueba*: un comando, un exit code |

```
       /test-contract <escenario>
              │
              ▼
     ┌──────────────────────┐  carga  ┌──────────────────────────┐
     │      pact-writer     │◄────────┤ skill pact-contract-     │
     │  interacción + @State│         │       testing            │
     │  y ejecuta el gate   │         │ skill bc4-api-contract   │
     └────────┬─────────────┘         └──────────────────────────┘
              │ pacto
              ▼
     ┌──────────────────────┐  carga  ┌──────────────────────────┐
     │     pact-reviewer    │◄────────┤ skill pact-contract-     │
     │      dictamen        │         │        review            │
     └────────┬─────────────┘         └──────────────────────────┘
              │ APROBADO
              ▼
     ┌──────────────────────────────────────────────────────────────┐
     │  tools\validate-contracts.ps1 -BreakCheck   (exit 0 = acepta) │
     │  consumidor · pacto · proveedor · falsabilidad                │
     └────────┬─────────────────────────────────────────────────────┘
              │ push
              ▼
        GitHub Actions — el mismo gate, máquina limpia, y además:
        el pacto regenerado tiene que ser idéntico al commiteado
```

## 4. El contrato: 4 interacciones

Las cuatro salen de lo que BC5 hace de verdad, no de recorrer la API por cobertura:

| # | Petición | Estado previo exigido (`given`) | Respuesta | Qué protege |
| - | :------- | :------------------------------ | :-------- | :---------- |
| 1 | `GET /api/v1/paquetes` | existen 2 paquetes LISTOS pendientes de transferencia a BC5 | 200 · lista (≥2) | La consulta con la que arranca cada ronda de reparto |
| 2 | `GET /api/v1/paquetes/{id}` | existe el paquete LISTO con id `7f3e0f9c-…` | 200 · paquete con etiqueta | El detalle que BC5 lee al escanear antes de cargar |
| 3 | `GET /api/v1/paquetes/{id}` | no existe ningún paquete con id `00000000-…` | 404 · `ApiError` | Que BC5 distinga "no existe" de "BC4 caído" |
| 4 | `GET /api/v1/paquetes?ordenId={id}` | la orden `3c1e5b77-…` tiene 1 paquete ENVASADO sin etiquetar | 200 · lista con `etiqueta: null` | Que BC5 reciba el campo en `null` y **no** transfiera ese paquete |

Dos decisiones de diseño que conviene explicar, porque son las que hacen que el pacto
sirva:

**El consumidor declara menos de lo que la API devuelve.** `PaqueteListoDto` solo tiene
los campos que BC5 usa, y lleva `@JsonIgnoreProperties(ignoreUnknown = true)`. Si BC4
añade un campo nuevo, BC5 no se entera y el pacto no se rompe. Un pacto que congela toda
la respuesta convierte cada cambio interno del proveedor en un fallo falso, y un gate que
da fallos falsos acaba ignorado.

**Las pruebas ejercen el cliente real.** El test no compara dos JSON: llama a
`cliente.obtenerPaquete(...)` contra el servidor *mock* de Pact y comprueba el objeto que
sale (`paquete.esTransferible()`). Así el contrato demuestra que **BC5 puede consumir**
la respuesta, no solo que dos textos coinciden.

## 5. Matchers: dónde el contrato puede romperse

Es la decisión central de toda prueba de contrato, y el punto donde una IA se equivoca
por defecto.

> **Valor exacto solo donde el consumidor decide algo. Matcher de tipo o de formato en
> todo lo demás.**

| Campo | Cómo se declara | Por qué |
| :---- | :-------------- | :------ |
| `estado` | `stringValue("estado", "LISTO")` | BC5 solo recoge los LISTOS: el valor es una decisión |
| `status` del error | `numberValue("status", 404)` | Distingue "no existe" de "BC4 caído" |
| `codigoIdentificacion` | `stringMatcher(…, "PKG-[0-9A-F]{8}")` | BC5 lo imprime: importa el formato, no el valor |
| `fechaGeneracion` | `stringMatcher(…, ISO-8601 con fracción opcional)` | Cambia en cada corrida |
| `id`, `ordenId` | `uuid(…)` | BC5 solo los reenvía |
| `etiqueta` ausente | `nullValue("etiqueta")` | El `null` es parte del contrato |

Los dos fallos son simétricos. **Sobre-especificar** produce un pacto que se rompe cuando
cambia un dato de prueba de BC4. **Sub-especificar** —todo `stringType`— produce un pacto
que **no puede fallar**: pasa con cualquier cadena en cualquier campo. El segundo es el
peligroso, porque se ve exactamente igual de verde que uno bueno. De ahí la sección 7.

## 6. Provider states: lo único que escribe el proveedor

El proveedor no declara expectativas. Solo tiene que saber montar el estado que cada
interacción presupone:

```java
@State("existen 2 paquetes LISTOS pendientes de transferencia a BC5")
void dosPaquetesListos() {
    paquetes.guardar(paqueteListo(UUID.randomUUID().toString(), "PAC-1001", …));
    paquetes.guardar(paqueteListo(UUID.randomUUID().toString(), "PAC-1002", …));
}
```

Tres detalles que no son cosméticos:

1. **El texto se copia letra a letra del `given` del consumidor.** Es la clave que une los
   dos lados. Si no casa, Pact no ejecuta el estado.
2. **La siembra pasa por el puerto de dominio** (`PaqueteEtiquetadoRepository`) y produce
   los valores con formato usando **la regla real** (`CodigoIdentificacion.generar(id)`),
   no un literal `"PKG-7F3E0F9C"`. Si mañana cambia la regla de generación del código, el
   contrato se entera.
3. **La base se vacía antes de cada interacción.** Pact invoca los `@State` en
   `beforeTestExecution`, es decir *después* de los `@BeforeEach`, así que un
   `jpaPaquetes.deleteAll()` en el `@BeforeEach` garantiza el aislamiento. Sin eso, los
   paquetes sembrados para la interacción 2 aparecerían en la lista de la interacción 1 y
   el pacto pasaría por casualidad.

La verificación levanta BC4 de verdad (`@SpringBootTest(webEnvironment = RANDOM_PORT)`)
con el perfil `it` (H2 en memoria): no hace falta Docker.

## 7. Verificación objetiva: la falsabilidad del pacto

Cada nivel del entorno tiene una comprobación que **el resumen en verde no puede
falsificar**:

| Nivel | Lo que no se puede falsificar |
| :---- | :---------------------------- |
| Unitarias | *Mutation testing* con PIT: matar mutantes |
| Integración | Repetibilidad: la colección tiene que pasar **dos veces** contra la misma BD |
| **Contrato** | **Falsabilidad**: el pacto **alterado** tiene que hacer fallar la verificación |

`-BreakCheck` respalda el pacto, cambia a propósito una expectativa (el `estado` del
paquete que BC5 recoge: `LISTO` → `TRANSFERIDO`), vuelve a ejecutar la verificación y
**exige que falle**. Si pasa igual, el contrato no está comprobando el cuerpo de la
respuesta: casi siempre porque todo el *body* son *matchers* de tipo. El gate devuelve
entonces **exit 5** y el pacto original se restaura siempre, también si se interrumpe.

```powershell
.\tools\validate-contracts.ps1 -BreakCheck
```

```
  --- Comprobacion de falsabilidad (-BreakCheck) ---
  Se altera el pacto (estado LISTO -> TRANSFERIDO) y se exige que falle.
  OK: la verificacion fallo con el pacto alterado (1 interacciones rotas).
  Pacto original restaurado.
```

Es el equivalente exacto del mutante que sobrevive: una interacción que no puede fallar
está cubierta y no verifica nada.

En CI hay una segunda comprobación que tampoco se puede falsificar a mano: **el pacto
regenerado tiene que ser idéntico al commiteado**. Si alguien edita el `.json` para que
el proveedor pase, el job se cae con el diff en pantalla.

## 8. El gate

Un solo comando recorre el ciclo completo y devuelve un exit code inequívoco:

```powershell
.\tools\validate-contracts.ps1                  # consumidor -> pacto -> proveedor
.\tools\validate-contracts.ps1 -Only consumer   # solo regenerar el pacto
.\tools\validate-contracts.ps1 -Only provider   # solo verificarlo
.\tools\validate-contracts.ps1 -BreakCheck      # + comprobar que el gate puede fallar
```

```bash
./tools/validate-contracts.sh --break-check     # Git Bash y CI
```

| Exit | Significado |
| :--- | :---------- |
| 0 | Contrato verificado de punta a punta |
| 1 | El proveedor no cumple el contrato |
| 2 | Las pruebas del consumidor fallaron: sin ellas no hay pacto |
| 3 | Entorno inválido (falta JDK 21) |
| 4 | El pacto no existe o no tiene interacciones |
| 5 | **El pacto alterado también pasó**: la verificación no comprueba nada |

El paso 3 del gate imprime el contrato entero —interacciones, estados y códigos— antes de
verificarlo, de modo que el propio log documenta qué se está acordando.

## 9. Resultados medidos

Ejecución completa en la máquina de desarrollo, JDK 21:

```
=== 2/5  Consumidor (genera el pacto) ===========================
  Tests: 4 ejecutados, 0 fallidos, 0 con error

=== 3/5  Pacto publicado ========================================
  ms-logistic-delivery -> ms-production-packaging   (4 interacciones)

    [GET /api/v1/paquetes/7f3e0f9c-…] una consulta del detalle de ese paquete -> 200
        given: existe el paquete LISTO con id 7f3e0f9c-…
    [GET /api/v1/paquetes] una consulta de los paquetes listos para transferir -> 200
        given: existen 2 paquetes LISTOS pendientes de transferencia a BC5
    [GET /api/v1/paquetes?ordenId=3c1e5b77-…] una consulta de los paquetes de una orden -> 200
        given: la orden 3c1e5b77-… tiene 1 paquete ENVASADO sin etiquetar
    [GET /api/v1/paquetes/00000000-…] una consulta del detalle de un paquete que no existe -> 404
        given: no existe ningun paquete con id 00000000-…

=== 4/5  Proveedor (verifica el pacto) ==========================
  Interacciones verificadas: 4 de 4, 0 fallidas

  --- Comprobacion de falsabilidad (-BreakCheck) ---
  OK: la verificacion fallo con el pacto alterado (1 interacciones rotas).

OK: contrato verificado de punta a punta.
```

| Métrica | Valor |
| :------ | :---- |
| Interacciones en el pacto | 4 (3 de éxito + 1 de error) |
| Pruebas del consumidor | 4, todas ejercen el cliente real |
| Interacciones verificadas por el proveedor | 4 de 4 |
| Falsabilidad | Comprobada: el pacto alterado rompe la verificación |
| Tiempo del ciclo completo | ≈ 2 min (consumidor ≈ 7 s · proveedor ≈ 40 s + arranques de Maven) |
| Efecto sobre el gate de unitarias | Ninguno: `mvnw verify` sigue en 163 tests y 99.1 % de líneas |

El último punto no es un detalle. La verificación del contrato vive en un perfil Maven
aparte (`-Pcontract`, `maven-failsafe-plugin`) precisamente para que **no pueda romper el
gate de las pruebas unitarias**: si un día el pacto falta, `./mvnw verify` sigue
funcionando igual.

## 10. Guardarraíles

Lo que el entorno impide, no lo que pide por favor:

- **El pacto es de solo lectura para los agentes.** `.claude/settings.json` deniega
  `Edit`/`Write` sobre `ms-production-packaging/src/test/resources/pacts/**`. Es un
  artefacto generado: editarlo es falsificar el resultado, igual que editar el reporte de
  cobertura.
- **`src/main` del proveedor pide confirmación.** Un fallo de verificación no se arregla
  tocando producción desde una prueba de contrato.
- **El pom pide confirmación.** Cambiar el perfil `contract` cambia lo que el gate
  ejecuta.
- Los agentes tienen el comando del gate preautorizado, así que la ruta cómoda es
  ejecutarlo, no saltárselo.
- El `pact-reviewer` no tiene `Write` ni `Edit`: audita, no corrige.

## 11. Lo que falta (y por qué no está)

Este es un avance del taller, no un despliegue de contract testing en producción. Queda
fuera, a conciencia:

| Pendiente | Qué aportaría | Por qué no está |
| :-------- | :------------ | :-------------- |
| **Pact Broker** | Versionado de contratos, matriz de compatibilidad, `can-i-deploy` | Exige un servicio corriendo y credenciales compartidas; aquí el pacto se publica por sistema de archivos (`pact.rootDir`), que ilustra el mismo ciclo |
| **`can-i-deploy`** | Bloquear el despliegue de BC4 si rompe a un consumidor ya desplegado | Depende del broker |
| **Contrato BC3 → BC4** | El otro lado de BC4: recibe el `CalendarioConsolidado` | El código de BC3 no está en este repositorio; el patrón sería idéntico con los papeles cambiados |
| **Contratos de mensajería** | BC4 emite `PaqueteListo`; BC5 lo consume por RabbitMQ | Pact soporta *message pacts*, pero el evento aún no sale del proceso en BC4 |
| **Interacciones de escritura** | `POST /api/v1/paquetes` desde otro consumidor | Hoy ningún consumidor externo escribe en BC4 |

El consumidor vive en este repositorio (`contract-testing/bc5-logistic-consumer`) porque
el código de `ms-logistic-delivery` es de otro integrante del grupo. En un montaje real,
ese módulo sería parte del repositorio de BC5 y publicaría el pacto al broker; la
verificación del proveedor no cambiaría ni una línea.

## 12. Cómo reproducirlo

```bash
# 1. Abrir Claude Code en la carpeta del repositorio (importante: ddd_clean)
cd ddd_clean

# 2. Validar el estado actual sin generar nada
#    PowerShell:
.\tools\validate-contracts.ps1 -BreakCheck
#    Git Bash:
./tools/validate-contracts.sh --break-check

# 3. Generar una interacción nueva
#    /test-contract el detalle de un paquete que aún no está envasado
#    -> pact-writer escribe la interacción y el @State, y ejecuta el gate
#    -> pact-reviewer dictamina
#    -> validate-contracts.ps1 decide

# 4. Solo regenerar el pacto tras tocar el consumidor
.\tools\validate-contracts.ps1 -Only consumer
```

Prerrequisitos: **JDK 21** y, para la versión `.sh`, **python3** (lee el pacto y los
reportes XML). No hace falta Docker: la verificación del proveedor usa H2 en memoria.
