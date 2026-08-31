---
name: test-coverage-jacoco
description: Medir e interpretar la cobertura de pruebas de los microservicios Java de este proyecto con JaCoCo y PIT (mutation testing). Cargalo cuando haya que ejecutar la suite, reportar cobertura, comparar el antes y el despues de añadir tests, decidir si un hueco de cobertura importa, o diagnosticar por que falla el gate de calidad.
---

# Cobertura: JaCoCo y PIT

## 1. Requisito previo: JDK 21

`JAVA_HOME` en esta maquina apunta a Zulu 17 y el build falla con
`release version 21 not supported`. Fijalo en cada invocacion:

```bash
JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw -B test
```

En PowerShell: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"`.

## 2. Comandos

Desde `ms-production-packaging/`:

| Objetivo | Comando |
| :------- | :------ |
| Suite + reporte de cobertura | `./mvnw -B test` |
| Suite + **umbral** (falla si baja) | `./mvnw -B verify` |
| Una sola clase | `./mvnw -B -Dtest=PorcionTest test` |
| Mutation testing | `./mvnw -B -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage` |
| Gate completo (lo que valida el CI) | `..\tools\validate-tests.ps1` |

Salidas:

- `target/site/jacoco/index.html` — cobertura navegable (rojo = linea no ejecutada).
- `target/site/jacoco/jacoco.csv` — la misma informacion en texto: **usa este para
  reportar numeros**, no el HTML.
- `target/pit-reports/index.html` — mutantes muertos / supervivientes.

## 3. Leer `jacoco.csv` sin abrir el HTML

Columnas relevantes: `PACKAGE, CLASS, LINE_MISSED, LINE_COVERED, BRANCH_MISSED, BRANCH_COVERED`.

```bash
python -c "
import csv,io
rows=list(csv.DictReader(io.open('target/site/jacoco/jacoco.csv',encoding='utf-8')))
lm=sum(int(r['LINE_MISSED']) for r in rows); lc=sum(int(r['LINE_COVERED']) for r in rows)
bm=sum(int(r['BRANCH_MISSED']) for r in rows); bc=sum(int(r['BRANCH_COVERED']) for r in rows)
print('lineas %.1f%% (%d/%d)  ramas %.1f%%'%(100*lc/(lm+lc),lc,lm+lc,100*bc/(bm+bc)))
for r in sorted(rows,key=lambda r:-int(r['LINE_MISSED']))[:10]:
    if int(r['LINE_MISSED']): print(' ',r['CLASS'],'missed',r['LINE_MISSED'])
"
```

## 4. Umbrales configurados

En `pom.xml`, ejecucion `jacoco:check` de la fase `verify`:

- **85 %** de lineas (BUNDLE)
- **80 %** de ramas (BUNDLE)

PIT: `mutationThreshold` **80 %**, sobre `domain.*` y `application.*`.

Bajar un umbral para que el build pase **no es una opcion**: o se escribe el test que
falta, o se documenta por que ese codigo no debe cubrirse.

## 5. Interpretar los huecos

No toda linea sin cubrir es un test que falta. Clasifica antes de actuar:

| Hueco | Que hacer |
| :---- | :-------- |
| Rama de una regla de negocio | **Escribir el test.** Prioridad maxima. |
| Excepcion de dominio sin test | **Escribir el test.** Cada invariante necesita el suyo. |
| Constructor sin argumentos exigido por JPA (`protected XJpaEntity()`) | Ignorar: no tiene comportamiento. |
| Getter/record accessor trivial | Ignorar si ya se ejerce indirectamente. |
| `main()`, `@Configuration` | Ya excluido en el `pom.xml`. |

## 6. La cobertura miente; los mutantes no

Una linea "cubierta" solo significa que se ejecuto, no que se comprobo. PIT inyecta
mutantes (invierte condiciones, cambia limites, elimina llamadas) y exige que algun
test falle.

- **Mutante muerto** = hay un test que realmente comprueba ese comportamiento.
- **Mutante superviviente** = codigo ejecutado por los tests pero **no verificado**.
  Es un hallazgo accionable: senala exactamente que asercion falta.

Cuando reportes la calidad de unos tests generados, da **las dos cifras**: cobertura de
lineas y % de mutantes muertos. La primera sola es facil de inflar.

## 7. Como reportar

Siempre en formato antes -> despues, con numeros del `jacoco.csv`, no estimados:

```
Cobertura: 91.2 % -> 98.8 % de lineas (633/641), ramas 99.1 %
Mutantes:  muertos 168/175 (96 %), 7 supervivientes en OrdenDeProduccion
Suite:     162 tests, 0 fallos
```
