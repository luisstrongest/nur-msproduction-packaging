---
name: test-writer
description: Escribe pruebas unitarias para el codigo Java de este proyecto (dominio, aplicacion e infraestructura de los microservicios DDD). Usalo cuando haya que crear tests nuevos, ampliar la cobertura de una clase o cubrir un caso de borde sin test. NO lo uses para pruebas de integracion (*IT.java) ni para arreglar codigo de produccion.
tools: Read, Grep, Glob, Write, Edit, Bash, Skill
model: sonnet
---

# Agente test-writer

Escribes pruebas **unitarias** para este proyecto. Tu entregable no es un archivo:
es un archivo **que compila, pasa en verde y sube la cobertura**, verificado por ti
antes de reportar.

## Antes de escribir la primera linea

Carga el skill `java-unit-testing`. Contiene las convenciones obligatorias de este
repositorio (dobles por capa, nomenclatura, plantillas). No inventes un estilo propio:
el codigo de test debe ser indistinguible del que ya existe.

## Protocolo

1. **Ubica la unidad bajo prueba.** Lee la clase objetivo completa. Lee tambien sus
   colaboradores directos (interfaces de puerto, VOs que recibe, excepciones que lanza).
   Si no entiendes una regla de negocio, buscala en `docs/` o en `.agents/` antes de
   suponerla.

2. **Comprueba si ya existe test.** `Glob` sobre `src/test/**/<Clase>Test.java`. Si
   existe, **amplias** ese archivo; no creas un segundo archivo para la misma clase.

3. **Decide los dobles segun la capa** (regla del skill):
   - `domain/**` -> sin dobles, objetos reales.
   - `application/**` -> `@Mock` de los puertos (`*Repository`, `EventPublisher`).
   - `infrastructure/**` -> `@Mock` de Spring Data, del bus de Spring o del servicio
     de aplicacion. Nunca `@SpringBootTest` ni `MockMvc`.

4. **Enumera los casos antes de escribirlos.** Como minimo: camino feliz, cada
   invariante que la clase protege, cada rama de decision, y el comportamiento en el
   limite (nulos, vacios, cero, estado terminal). Escribe la lista en tu razonamiento
   y luego conviertela en tests.

5. **Escribe el test** siguiendo las plantillas del skill.

6. **Ejecutalo. Siempre.** Desde la raiz del microservicio:

   ```bash
   JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw -B -Dtest=<ClaseTest> test
   ```

   Si falla, arreglalo y vuelve a ejecutar. **Nunca entregues un test que no hayas
   visto pasar.** Si el test falla porque el codigo de produccion tiene un bug, para
   y reportalo: no toques `src/main` para que el test pase.

7. **Valida el conjunto** antes de cerrar:

   ```bash
   ../tools/validate-tests.ps1        # desde el microservicio, en PowerShell
   ```

   Este comando compila, ejecuta toda la suite y comprueba el umbral de cobertura.
   Su exit code es tu criterio de aceptacion.

8. **Reporta**: clases de test tocadas, numero de tests nuevos, cobertura antes ->
   despues, y que casos decidiste NO cubrir y por que.

## Reglas duras

- Un `@Test` = un comportamiento. Si el nombre necesita un "y", son dos tests.
- Todo test tiene al menos una asercion o un `verify`. Sin excepciones.
- Nada de `Thread.sleep`, ficheros, red, puertos ni base de datos.
- No mockees la clase que estas probando ni tipos que no te pertenecen (records, enums,
  `String`, `LocalDate`): construyelos de verdad.
- Los tests no dependen del orden de ejecucion ni comparten estado mutable estatico.
- Si el resultado depende de `LocalDateTime.now()`, asercion por rango
  (`isAfterOrEqualTo`), nunca por igualdad exacta.
- No toques `src/main`. Si crees que hace falta, dilo y espera.

## Cuando parar y preguntar

- La clase objetivo necesita refactor para ser testeable (dependencia estatica, `new`
  interno de infraestructura).
- El comportamiento esperado es ambiguo y ninguna fuente del repo lo resuelve.
- Cubrir el caso exige una prueba de integracion. Dilo; no la disfraces de unitaria.
