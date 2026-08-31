---
description: Genera pruebas unitarias para una clase o paquete y las valida (test-writer -> test-reviewer -> gate)
argument-hint: <Clase|paquete> [--no-review]
---

# /test-unit

Objetivo: **$ARGUMENTS**

Ejecuta el ciclo completo de generacion y validacion de pruebas unitarias de este
proyecto. Si no se indica objetivo, primero localiza las clases con menor cobertura
en `target/site/jacoco/jacoco.csv` y propone las 3 candidatas antes de continuar.

## Paso 1 — Generar

Lanza el subagente **test-writer** con el objetivo indicado. El agente carga el skill
`java-unit-testing`, escribe los tests siguiendo las convenciones del repositorio y
**los ejecuta** antes de devolver nada.

## Paso 2 — Revisar

Salvo que se haya pasado `--no-review`, lanza el subagente **test-reviewer** sobre los
archivos que acaba de escribir test-writer. Su dictamen manda:

- **RECHAZADO** -> devuelve los hallazgos a test-writer y repite. Maximo 2 vueltas;
  si a la segunda sigue rechazado, para y explica el bloqueo al usuario.
- **APROBADO CON OBSERVACIONES** -> aplica las correcciones que sean de una linea,
  y reporta el resto como pendiente.
- **APROBADO** -> sigue.

## Paso 3 — Validar

Ejecuta el gate desde la raiz del microservicio:

```powershell
..\tools\validate-tests.ps1
```

Compila, corre toda la suite y comprueba el umbral de cobertura. **Su exit code es el
criterio de aceptacion**: si no es 0, no esta terminado.

## Paso 4 — Reportar

Un resumen corto al usuario, sin volcar el log de Maven:

- archivos de test creados o ampliados, y cuantos tests nuevos;
- cobertura **antes -> despues** (numeros de `jacoco.csv`, no estimaciones);
- veredicto del test-reviewer;
- que quedo sin cubrir y por que.
