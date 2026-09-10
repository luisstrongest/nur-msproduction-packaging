---
description: Genera pruebas de integracion de la API (requests Postman) y las valida (integration-test-writer -> integration-test-reviewer -> gate)
argument-hint: <endpoint|escenario|flujo> [--no-review] [--docker]
---

# /test-integration

Objetivo: **$ARGUMENTS**

Ejecuta el ciclo completo de generacion y validacion de pruebas de integracion de este
proyecto sobre `postman/bc4-integracion.postman_collection.json`.

Si no se indica objetivo, primero lista que endpoints del contrato (`bc4-api-contract`) no
tienen ningun request en la coleccion y propone los 3 escenarios mas valiosos antes de
continuar. Prioriza por riesgo, no por cobertura: un flujo que cruza dos agregados o una
regla de negocio con efecto HTTP vale mas que un GET trivial.

## Paso 1 — Levantar el ambiente

Por defecto usa el modo rapido (H2, sin Docker) para iterar; con `--docker` usa PostgreSQL
real, que es el modo de referencia:

```powershell
.\tools\validate-integration.ps1 -Mode local -KeepUp
```

Deja el ambiente arriba durante toda la sesion para que las vueltas de correccion no paguen
el arranque cada vez. Al terminar el comando, bajalo.

## Paso 2 — Generar

Lanza el subagente **integration-test-writer** con el escenario indicado. El agente carga
los skills `bc4-api-contract` y `postman-integration-testing`, escribe los requests
siguiendo las convenciones del repositorio y **los ejecuta con Newman** antes de devolver
nada.

## Paso 3 — Revisar

Salvo que se haya pasado `--no-review`, lanza el subagente **integration-test-reviewer**
sobre los requests que acaba de escribir. Su dictamen manda:

- **RECHAZADO** -> devuelve los hallazgos a integration-test-writer y repite. Maximo 2
  vueltas; si a la segunda sigue rechazado, para y explica el bloqueo al usuario.
- **APROBADO CON OBSERVACIONES** -> aplica las correcciones de una linea, y reporta el
  resto como pendiente.
- **APROBADO** -> sigue.

## Paso 4 — Validar

Ejecuta el gate completo desde la raiz del repositorio, en el modo de referencia:

```powershell
.\tools\validate-integration.ps1
```

**Su exit code es el criterio de aceptacion**: si no es 0, no esta terminado.

Ademas, comprueba la **repetibilidad** (es el fallo tipico de estas pruebas): la coleccion
tiene que pasar dos veces seguidas contra la misma base de datos.

## Paso 5 — Reportar

Un resumen corto al usuario, sin volcar el log de Newman:

- requests creados o ampliados, y cuantas aserciones nuevas;
- resultado de Newman **antes -> despues** (ejecutadas / fallidas), con numeros de
  `target/newman/report.json`, no estimaciones;
- confirmacion de que la segunda vuelta tambien paso;
- veredicto del integration-test-reviewer;
- que escenarios quedaron sin cubrir y por que.

Si en el camino aparece un **500 donde el contrato dice 4xx**, eso no es un test que
arreglar: es un bug de produccion. Reportalo y no toques `src/main`.
