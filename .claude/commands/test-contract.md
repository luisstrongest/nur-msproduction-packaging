---
description: Genera pruebas de contrato Pact entre BC5 (consumidor) y BC4 (proveedor) y las valida (pact-writer -> pact-reviewer -> gate con falsabilidad)
argument-hint: <llamada|campo|escenario de error> [--no-review] [--only consumer|provider]
---

# /test-contract

Objetivo: **$ARGUMENTS**

Ejecuta el ciclo completo de generacion y validacion de pruebas de contrato entre
`ms-logistic-delivery` (consumidor, BC5) y `ms-production-packaging` (proveedor, BC4).

Si no se indica objetivo, primero lista que llamadas del cliente
(`contract-testing/bc5-logistic-consumer/.../ClienteProduccionBC4.java`) no tienen
interaccion en el pacto, y que respuestas del contrato de la API (`bc4-api-contract`)
BC5 consume sin tenerlas cubiertas. Propone los 3 escenarios mas valiosos antes de
seguir. Prioriza por **riesgo de ruptura**, no por cobertura: un campo que BC5 usa para
decidir vale mas que uno que solo reenvia, y un caso de error vale mas que otro camino
feliz.

## Paso 1 — Situacion de partida

Mira que hay ya en el pacto antes de tocar nada:

```powershell
.\tools\validate-contracts.ps1 -Only provider
```

Devuelve las interacciones actuales, sus estados y si hoy estan todas verificadas. Ese
es el "antes" contra el que se compara al final.

## Paso 2 — Generar

Lanza el subagente **pact-writer** con el escenario indicado. Carga los skills
`pact-contract-testing` y `bc4-api-contract`, escribe la interaccion en el consumidor
(ejerciendo el cliente real), añade el `@State` correspondiente en el proveedor y
**ejecuta el gate** antes de devolver nada.

## Paso 3 — Revisar

Salvo que se haya pasado `--no-review`, lanza el subagente **pact-reviewer** sobre lo que
acaba de escribirse. Su dictamen manda:

- **RECHAZADO** -> devuelve los hallazgos a pact-writer y repite. Maximo 2 vueltas; si a
  la segunda sigue rechazado, para y explica el bloqueo al usuario.
- **APROBADO CON OBSERVACIONES** -> aplica las correcciones de una linea y reporta el
  resto como pendiente.
- **APROBADO** -> sigue.

## Paso 4 — Validar

El gate completo, con la comprobacion de falsabilidad:

```powershell
.\tools\validate-contracts.ps1 -BreakCheck
```

**Su exit code es el criterio de aceptacion**: si no es 0, no esta terminado.

- `exit 1` — el proveedor no cumple el contrato.
- `exit 4` — el pacto no se genero: las pruebas del consumidor no pasaron.
- `exit 5` — **el pacto alterado tambien paso**: la interaccion no puede fallar. No es un
  detalle de estilo, es el gate diciendo que no esta comprobando nada.

Comprueba ademas que el pacto se **regenera identico** (`-Only consumer` y luego
`git diff` sobre `ms-production-packaging/src/test/resources/pacts`): si cambia sin que
nadie haya tocado el consumidor, hay algo no determinista en el contrato.

## Paso 5 — Reportar

Un resumen corto, sin volcar el log de Maven:

- interacciones creadas o reforzadas, y estados nuevos en el proveedor;
- resultado del gate **antes -> despues** (interacciones verificadas / fallidas), con los
  numeros que imprime el script, no estimaciones;
- confirmacion de la linea `Falsabilidad: comprobada`;
- veredicto del pact-reviewer;
- que escenarios quedaron sin cubrir y por que.

Si en el camino la verificacion falla porque **el proveedor cambio**, eso no es una
expectativa que relajar: es un breaking change para BC5. Reportalo, no toques `src/main`
y no ablandes el matcher.
