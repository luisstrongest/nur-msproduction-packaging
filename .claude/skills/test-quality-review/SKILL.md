---
name: test-quality-review
description: Checklist de revision adversarial para pruebas unitarias, especialmente las generadas por IA. Cargalo cuando haya que revisar, auditar o dar por buenos tests recien escritos, o cuando la cobertura suba pero haya dudas de que los tests comprueben algo de verdad.
---

# Revision de pruebas unitarias

Premisa: **un test en verde no demuestra nada**. Demuestra que no falla. Esta revision
busca los tests que estan en verde por el motivo equivocado.

Es el control de calidad del codigo de test generado por IA, donde el fallo tipico no es
el test que se rompe (ese se ve), sino el test que **nunca podria romperse**.

## 1. Regla de oro

> Si rompo a proposito el codigo de produccion que este test dice probar,
> **¿falla el test?**

Si la respuesta es "no", el test no vale, aunque cubra el 100 % de las lineas. Aplicala
mentalmente a cada test: invierte un `if`, cambia un `>` por `>=`, borra una linea,
devuelve `null`.

## 2. Lista de rechazo

Recorrela en orden. Cada punto marcado es un hallazgo con archivo, linea y explicacion.

**Bloqueantes (-> RECHAZADO):**

1. Test sin ninguna asercion ni `verify`.
2. La clase bajo prueba es un mock (o un spy de si misma).
3. La asercion es tautologica: comprueba el valor que acaba de stubear, o `x == x`.
4. `try/catch` con la excepcion esperada y **sin** `fail()` en el camino feliz.
5. El test **no se ejecuta**: nombre fuera de `*Test.java`, metodo `private`/`static`,
   `@Nested` estatica, `@Disabled` sin justificacion.
6. Sobrevive al mutante manual del punto 1.

**Graves (-> APROBADO CON OBSERVACIONES, o rechazo si se acumulan):**

7. `verify(mock).metodo(any())` cuando el argumento es justo lo interesante.
8. `assertThatThrownBy(...).isInstanceOf(RuntimeException.class)` existiendo una
   excepcion de dominio concreta.
9. Falta el test de "el fallo del dominio no persiste ni publica nada"
   (`verify(repo, never()).guardar(any())` + `verifyNoInteractions(publisher)`).
10. Ramas sin cubrir de un `if`/`switch` de negocio.
11. Un `@Test` que comprueba tres comportamientos distintos.
12. `lenient()` para silenciar un stub innecesario en vez de borrarlo.
13. Dependencia del reloj (`isEqualTo(LocalDateTime.now())`), del orden de ejecucion o
    de estado `static` mutable.
14. Infraestructura real dentro de un `*Test.java`: Spring, H2, MockMvc, ficheros, red,
    `Thread.sleep`.

**Menores (se anotan, no bloquean):**

15. Nombre que no describe el comportamiento (`test1`, `shouldWork`, `testGenerar`).
16. Copy-paste que pedia `@ParameterizedTest`.
17. Datos de prueba magicos sin helper ni constante con nombre.
18. Ausencia de `@DisplayName` en tests cuyo nombre no se lee solo.

El detalle con ejemplos de cada punto esta en el skill `java-unit-testing`,
`references/antipatrones.md`.

## 3. Verificacion objetiva

Cuando la revision manual deje dudas, o para confirmar hallazgos, usa mutation testing
(ver skill `test-coverage-jacoco`):

```bash
JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw -B -Djacoco.skip=true \
    test-compile org.pitest:pitest-maven:mutationCoverage
```

Cada **mutante superviviente** en `target/pit-reports/index.html` es un hallazgo
confirmado: linea ejecutada por los tests pero no comprobada por ninguno. El informe
dice la linea exacta y que mutacion sobrevivio, lo que traduce directo a la asercion
que falta.

## 4. Formato del dictamen

Veredicto en la primera linea: **APROBADO** / **APROBADO CON OBSERVACIONES** /
**RECHAZADO**. Despues, la tabla de hallazgos ordenada por gravedad:

| # | Archivo:linea | Categoria | Por que no vale | Correccion sugerida |
|---|---------------|-----------|-----------------|---------------------|

Cierra con: (a) uno o dos aciertos concretos del autor, (b) los casos de borde que
siguen sin cubrir.

## 5. Al revisar, no

- No reescribas los tests: el dictamen es tu salida, la correccion es de quien los
  escribio.
- No pidas cobertura por cobertura: getters triviales y constructores de JPA no
  necesitan test.
- No apruebes "porque pasa en verde". Esa es exactamente la trampa que buscas.
