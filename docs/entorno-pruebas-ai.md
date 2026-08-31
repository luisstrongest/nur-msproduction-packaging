# Entorno de pruebas asistido por IA

> Generación de pruebas unitarias con agentes de IA **verificable**: quién las escribe,
> con qué criterio, y cómo se demuestra que lo generado vale.

El problema que resuelve este entorno: pedirle tests a una IA es fácil, y el resultado
suele ser plausible — compila, pasa en verde, sube la cobertura — sin comprobar nada.
La respuesta aquí no es confiar en el prompt, sino **encerrar al agente en un circuito
con criterio explícito y verificación objetiva**.

## 1. Las cuatro piezas

| Pieza | Dónde | Rol |
| :---- | :---- | :-- |
| **Agentes** | `.claude/agents/` | *Quién* hace el trabajo: `test-writer` escribe, `test-reviewer` audita |
| **Skills** | `.claude/skills/` | *Qué sabe*: las convenciones de este repositorio, versionadas |
| **Gate** | `tools/validate-tests.*` | *Cómo se comprueba*: un comando, un exit code |
| **CI** | `.github/workflows/unit-tests.yml` | *Dónde se confirma*: máquina limpia, sin "en mi equipo funciona" |

```
       /test-unit <Clase>
              │
              ▼
     ┌──────────────────┐   carga    ┌────────────────────────┐
     │   test-writer    │◄───────────┤ skill java-unit-testing │
     │  escribe + corre │            │  + plantillas           │
     └────────┬─────────┘            └────────────────────────┘
              │ tests
              ▼
     ┌──────────────────┐   carga    ┌────────────────────────┐
     │  test-reviewer   │◄───────────┤ skill test-quality-     │
     │  dictamen        │            │       review            │
     └────────┬─────────┘            └────────────────────────┘
              │ APROBADO
              ▼
     ┌──────────────────────────────────────────────────────┐
     │  tools/validate-tests.ps1   (exit 0 = aceptado)       │
     │  compila · suite · cobertura ≥ umbral · [PIT]         │
     └────────┬─────────────────────────────────────────────┘
              │ push
              ▼
        GitHub Actions — el mismo gate en una máquina limpia
```

## 2. Agentes

### `test-writer`

Escribe pruebas unitarias. Herramientas acotadas (`Read`, `Grep`, `Glob`, `Write`,
`Edit`, `Bash`, `Skill`) y un protocolo de ocho pasos. Lo que lo diferencia de "pedirle
tests a un chat":

- **Carga el skill antes de escribir.** No improvisa un estilo: sigue el del repositorio.
- **Enumera los casos antes de codificarlos** (camino feliz, cada invariante, cada rama,
  los límites), de modo que la cobertura sea consecuencia de un análisis, no del azar.
- **Ejecuta lo que escribe.** Regla dura: *nunca entrega un test que no haya visto pasar*.
- **No toca `src/main`.** Si un test falla porque el código de producción tiene un bug,
  para y lo reporta, en lugar de ajustar el código para que el test pase — que es el
  fallo más peligroso de un agente autónomo escribiendo tests.

### `test-reviewer`

Audita lo generado. **No tiene `Write` ni `Edit` a propósito**: su salida es un dictamen
(`APROBADO` / `APROBADO CON OBSERVACIONES` / `RECHAZADO`), y quien corrige es el escritor.
La separación evita que el mismo agente que se equivocó se autoapruebe.

Su criterio central es la *regla del mutante manual*:

> Si rompo a propósito el código que este test dice probar, ¿falla el test?
> Si la respuesta es no, el test no vale — aunque cubra el 100 % de las líneas.

## 3. Skills

Las convenciones viven en el repositorio, no en el prompt: se versionan, se revisan en
un PR y las hereda cualquiera que abra el proyecto.

| Skill | Contenido |
| :---- | :-------- |
| `java-unit-testing` | La **regla del doble por capa** (dominio sin dobles, aplicación con `@Mock` de los puertos, infraestructura mockeando Spring Data / el bus / el servicio), nomenclatura en español, estructura AAA, qué hay que cubrir. Con `references/plantillas.md` (esqueletos copiables por capa) y `references/antipatrones.md` (12 motivos de rechazo con ejemplo mal/bien). |
| `test-coverage-jacoco` | Cómo ejecutar y **leer** la cobertura (`jacoco.csv` en vez del HTML), umbrales vigentes, qué huecos importan y cuáles no, y por qué la cobertura sola no basta. |
| `test-quality-review` | Lista de rechazo en tres niveles (bloqueante / grave / menor) y formato del dictamen. |

## 4. El gate

`tools/validate-tests.ps1` (Windows) y `tools/validate-tests.sh` (CI y Git Bash) ejecutan
exactamente la misma comprobación en cuatro pasos, con exit codes distinguibles:

```powershell
.\tools\validate-tests.ps1              # suite + cobertura
.\tools\validate-tests.ps1 -Mutation    # además, mutation testing
```

| Exit | Significado |
| ---: | :---------- |
| 0 | validación superada |
| 1 | tests fallidos o error de compilación |
| 2 | cobertura por debajo del umbral |
| 3 | entorno inválido (sin JDK 21) |
| 4 | demasiados mutantes supervivientes |

El paso 1 resuelve además un problema real de esta máquina: `JAVA_HOME` apunta a Zulu 17
y el build exige Java 21 (`release version 21 not supported`). El script **autodetecta**
el JDK 21 y lo fija; el mismo valor está en `.claude/settings.json` para las sesiones de
Claude Code. Si tu JDK 21 está en otra ruta, edita esa clave o pasa `-JdkPath`.

## 5. Verificación objetiva: mutation testing

Es la pieza que hace *validado* el entorno. La cobertura se infla con facilidad: basta
llamar al método sin comprobar el resultado. PIT no se deja: modifica el bytecode
(invierte una condición, cambia un límite, elimina una llamada) y **exige que algún test
falle**.

- **Mutante muerto** → hay un test que de verdad comprueba ese comportamiento.
- **Mutante superviviente** → línea ejecutada por los tests pero **no verificada**.
  El informe indica la línea exacta y la mutación que sobrevivió: se traduce directo a
  la aserción que falta.

```bash
./mvnw -B -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
# -> target/pit-reports/index.html
```

Configurado en el `pom.xml` sobre `domain.*` y `application.*` (donde vive la lógica),
con umbral del **80 %** de mutantes muertos.

## 6. Guardarraíles del entorno

`.claude/settings.json`, versionado, delimita lo que un agente puede hacer sin
supervisión:

- **`allow`** — comandos de test (`./mvnw`, los scripts de validación), `git status/diff/log`.
  No hay que aprobar lo rutinario, que es lo que empuja a aprobar sin leer.
- **`ask`** — editar `src/main/**` o cualquier `pom.xml`: el agente de tests **no** cambia
  código de producción ni el build sin que tú lo veas.
- **`deny`** — `git push`, `git reset --hard`, `rm -rf`, `mvn deploy`, leer `.env`.

## 7. Resultados medidos

Ejecución del gate sobre `ms-production-packaging` (31/08/2026):

```
=== 1/4  Entorno ================================================
  Modulo    : ms-production-packaging
  JAVA_HOME : C:\Program Files\Java\jdk-21
  Java      : java version "21.0.9" 2025-10-21 LTS

=== 2/4  Tests unitarios + cobertura ============================
  [INFO] Tests run: 163, Failures: 0, Errors: 0, Skipped: 0

=== 3/4  Cobertura (JaCoCo) =====================================
  Lineas : 98.8 %  (633 / 641)
  Ramas  : 99.1 %  (107 / 108)

OK: validacion superada.        exit 0
```

### El mutation testing encontró lo que la cobertura no veía

Primera ejecución de PIT sobre una suite **al 98.8 % de cobertura y en verde**:

| | Antes | Después |
| :--- | ---: | ---: |
| Mutantes generados | 200 | 200 |
| Mutantes muertos | 188 (94 %) | **198 (99 %)** |
| Supervivientes | 10 | 2 |
| Sin cobertura | 2 | 0 |
| *Test strength* | 95 % | **99 %** |
| Cobertura de líneas | 98.8 % | **98.8 %** |

Los 12 mutantes que sobrevivieron eran huecos reales de verificación:

| Mutación superviviente | Qué significaba |
| :--------------------- | :--------------- |
| `removed call to EventPublisher::publicar` (×4, en `PaqueteApplicationService` y `OrdenApplicationService`) | Se podía **borrar la publicación de eventos** y ningún test se enteraba: BC5 nunca recibiría `PaqueteListo` y la suite seguiría verde. |
| `removed call to OrdenDeProduccionRepository::guardar` | Se podía **borrar el guardado** al completar una orden sin que fallara nada. |
| `replaced return value with null` en `occurredAt()` de `OrdenGenerada`, `OrdenCompletada`, `PaqueteListo` | Nadie comprobaba la marca de tiempo de los eventos. |
| `replaced return value with ""` en `OrdenCompletada::ordenId` | El accessor no estaba verificado por ningún test. |
| `removed call to List::clear` en `PaqueteEtiquetado` | Nadie comprobaba que `pullEventos()` vacía la bandeja → riesgo de publicar el evento dos veces. |

Se añadieron las aserciones que faltaban (`verify(eventPublisher).publicar(...)`,
`verify(ordenRepository).guardar(...)`, `occurredAt()` por rango, y un test del vaciado
de la bandeja). Resultado: **163 tests, 198/200 mutantes muertos**.

**El dato que justifica todo el montaje:** la cobertura de líneas no se movió *ni una
décima* — 98.8 % antes y después. Doce defectos de verificación reales eran invisibles
para la métrica que normalmente se entrega como prueba de calidad.

### Supervivientes aceptados

Quedan 2, ambos `replaced int return with 0` en `hashCode()` de `Etiqueta` e `ItemOrden`.
Devolver siempre 0 es un `hashCode` degenerado pero **válido** (no rompe el contrato con
`equals`), así que no hay test razonable que los mate. Se documentan como aceptados en
lugar de escribir una aserción artificial para subir la cifra.

## 8. Cómo reproducirlo

```powershell
# 1. Abrir Claude Code en la carpeta del repositorio (importante: ddd_clean)
cd "D:\Repositorio Luis\nur_proy_final\ddd_clean"

# 2. Generar y validar tests para una clase
#    /test-unit OrdenDeProduccion
#    -> test-writer escribe y ejecuta
#    -> test-reviewer dictamina
#    -> validate-tests.ps1 decide

# 3. Validar el estado actual sin generar nada
.\tools\validate-tests.ps1 -Mutation
```

**Demo reproducible** para comprobar el circuito de punta a punta:

1. Anota la cobertura actual: `.\tools\validate-tests.ps1`.
2. Borra un archivo de test (por ejemplo `domain/shared/PorcionTest.java`) y vuelve a
   ejecutar el gate: la cobertura baja y, si cruza el umbral, **el gate falla con exit 2**.
3. Ejecuta `/test-unit Porcion`: el agente regenera los tests, los ejecuta y el revisor
   los audita.
4. Vuelve a lanzar el gate: exit 0. El delta de cobertura entre los pasos 2 y 4 es la
   evidencia de que el circuito funciona.

## 9. Nota sobre la ubicación

Claude Code descubre `.claude/` desde la raíz del proyecto abierto. Trabaja con el
repositorio abierto en **`ddd_clean/`** (que es además lo que se entrega) para que
agentes, skills y comandos aparezcan directamente. Si abres la carpeta padre
`nur_proy_final/`, se cargan igualmente pero con prefijo de directorio.

`.claude/settings.local.json` (si existe) es configuración personal y no se versiona;
lo compartido va en `.claude/settings.json`.
