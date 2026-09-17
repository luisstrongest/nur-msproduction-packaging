---
name: pact-contract-testing
description: Convenciones obligatorias para escribir pruebas de contrato con Pact en este proyecto (consumidor bc5-logistic-consumer, proveedor ms-production-packaging). Cargalo antes de crear o modificar cualquier interaccion del pacto, cualquier @State del proveedor, o cuando haya que decidir si algo se comprueba con un matcher de tipo o con un valor exacto, donde se publica el pacto, o por que la verificacion del proveedor pasa sin comprobar nada.
---

# Pruebas de contrato con Pact en este proyecto

Reglas del repositorio, no preferencias generales. Lo que escribas tiene que ser
indistinguible de lo que ya hay en
`contract-testing/bc5-logistic-consumer` y en
`ms-production-packaging/src/test/java/com/nurticenter/produccion/contract`.

## 1. Que cuenta como prueba de contrato aqui

Un acuerdo verificable entre **dos microservicios** sobre la forma de una peticion y
de su respuesta. No prueba logica de negocio: prueba que el proveedor sigue hablando
el idioma que el consumidor entiende.

La frontera con los otros dos niveles es nitida y hay que respetarla:

| Nivel | Que prueba | Que NO prueba |
| :---- | :--------- | :------------ |
| Unitarias (`java-unit-testing`) | Reglas del agregado, orquestacion con mocks | Nada que cruce el proceso |
| Integracion (`postman-integration-testing`) | Que BC4 entero funciona: HTTP + JPA + base de datos real | Si BC5 puede consumir lo que BC4 devuelve |
| **Contrato (este skill)** | Que la respuesta de BC4 contiene **lo que BC5 necesita**, con los tipos y formatos que BC5 asume | Que el dato sea correcto de negocio |

Corolario: **el contrato no es la API completa**. Si BC5 no usa un campo, ese campo no
va al pacto. Un pacto que congela toda la respuesta convierte cualquier cambio interno
de BC4 en un fallo falso, y entonces el equipo empieza a ignorarlo.

## 2. Quien es quien, y donde vive cada cosa

```
contract-testing/bc5-logistic-consumer/          CONSUMIDOR - ms-logistic-delivery (BC5)
  src/main/java/.../ClienteProduccionBC4.java      el cliente HTTP real (esto es lo que se prueba)
  src/main/java/.../PaqueteListoDto.java           la vista que BC5 tiene del paquete
  src/test/java/.../PaquetesConsumerPactTest.java  una interaccion por test

ms-production-packaging/                          PROVEEDOR - ms-production-packaging (BC4)
  src/test/resources/pacts/*.json                  el pacto publicado (generado, NO se edita)
  src/test/java/.../contract/PaquetesProviderContractIT.java   verificacion + @State
```

El flujo es siempre el mismo y va en un solo sentido:

```
 BC5 declara lo que necesita  ->  pacto .json  ->  BC4 demuestra que lo cumple
   (consumer test, mock)         (publicado)       (provider test, app real)
```

**El consumidor manda.** Si el proveedor no cumple, se arregla el proveedor o se
renegocia el contrato con el equipo de BC5; nunca se relaja la expectativa para ver
verde.

## 3. El pacto se genera, nunca se escribe

`ms-logistic-delivery-ms-production-packaging.json` es un **artefacto**, como el jar.
Editarlo a mano es el equivalente de editar el reporte de cobertura: el gate seguiria
en verde y ya no significaria nada. Se regenera ejecutando el consumidor.

Aqui el pacto se publica **copiandolo a los recursos de prueba del proveedor** via
`pact.rootDir` en el pom del consumidor. En un equipo real esa copia la hace un Pact
Broker; el mecanismo cambia, las reglas de este skill no.

## 4. Como se escribe una interaccion del consumidor

Una interaccion = un `@Pact` + un `@Test` que lo ejerce. Plantillas copiables en
[`references/plantillas.md`](references/plantillas.md).

Reglas:

1. **El test ejecuta el cliente real** (`ClienteProduccionBC4`), nunca un `HttpClient`
   escrito dentro del test. Si el pacto no pasa por el codigo que BC5 usa en
   produccion, no demuestra que BC5 pueda consumir la respuesta: demuestra que un JSON
   coincide con otro JSON.

2. **`given(...)` describe el estado del proveedor, no la peticion.**
   `"existen 2 paquetes LISTOS pendientes de transferencia a BC5"`, no
   `"GET paquetes devuelve 200"`. Ese texto es la clave que casa con el `@State` del
   proveedor: se copia **letra a letra**, tildes incluidas (aqui: sin tildes, como todo
   el repositorio).

3. **`uponReceiving(...)` describe la peticion desde el punto de vista del consumidor**,
   en lenguaje de negocio: `"una consulta de los paquetes listos para transferir"`.

4. **Las aserciones del test son sobre el objeto que devuelve el cliente**, no sobre el
   JSON. Se comprueba lo que BC5 hara con el dato:
   `assertThat(paquetes).allMatch(PaqueteListoDto::esTransferible)`.

5. **Un test = una interaccion.** Si el nombre lleva un "y", son dos.

## 5. Matchers: la regla que decide todo

> **Valor exacto solo donde el consumidor DECIDE algo. Matcher de tipo o de formato en
> todo lo demas.**

| Campo | Como se declara | Por que |
| :---- | :-------------- | :------ |
| `estado` | `stringValue("estado", "LISTO")` | BC5 solo recoge los LISTOS: el valor es una decision |
| `status` del error | `numberValue("status", 404)` | BC5 distingue "no existe" de "BC4 caido" |
| `codigoIdentificacion` | `stringMatcher(..., "PKG-[0-9A-F]{8}")` | BC5 lo imprime en la guia: importa el formato, no el valor |
| `fechaGeneracion` | `stringMatcher(..., REGEX_FECHA_HORA)` | Es un `LocalDateTime` serializado; el valor cambia en cada corrida |
| `id`, `ordenId` | `uuid(...)` | BC5 los reenvia; solo necesita que sean UUID |
| `nombrePaciente`, `direccion` | `stringType(...)` | BC5 los pinta tal cual |
| `etiqueta` ausente | `nullValue("etiqueta")` | El null forma parte del contrato: BC5 tiene que saber tratarlo |

Los dos fallos simetricos:

- **Sobre-especificar** (valores exactos en todo): el pacto se rompe cada vez que cambia
  un dato de prueba de BC4. Sintoma: hay que regenerar el pacto por cambios que no
  afectan a BC5.
- **Sub-especificar** (todo `stringType`): la verificacion del proveedor no puede
  fallar. Sintoma: el gate con `-BreakCheck` dice que el pacto alterado tambien pasa
  (exit 5). **Es el fallo tipico de las pruebas de contrato generadas por IA.**

Listas: `newJsonArrayMinLike(n, ...)` exige *al menos* n elementos con la forma dada.
Nunca se fija el tamaño exacto de una lista que el proveedor construye: la cantidad la
decide el estado, y ya la exige el `given`.

## 6. Provider states: la unica parte que escribe el proveedor

```java
@State("existen 2 paquetes LISTOS pendientes de transferencia a BC5")
void dosPaquetesListos() { ... }
```

Reglas:

1. **El texto se copia del `given` del consumidor.** Si no casa, Pact lo dice, pero solo
   si el estado existe en el pacto: revisa el nombre antes de dar nada por bueno.

2. **Siembra por el puerto de dominio** (`PaqueteEtiquetadoRepository.guardar(...)`) con
   `reconstituir(...)`, no por SQL ni tocando entidades JPA a mano. Y los valores que el
   contrato exige en un formato (el codigo `PKG-XXXXXXXX`) se producen con la **misma
   regla de dominio** que usa la aplicacion (`CodigoIdentificacion.generar(id)`), no
   escribiendolos a mano: si la regla cambia, el contrato tiene que enterarse.

3. **La base se vacia antes de cada interaccion** (`@BeforeEach` con `deleteAll()`). Sin
   eso, los paquetes sembrados para una interaccion se cuelan en la lista que devuelve la
   siguiente, y el pacto pasa por casualidad. El orden importa: Pact invoca los `@State`
   en `beforeTestExecution`, es decir **despues** de los `@BeforeEach`.

4. **Un estado que no siembra nada tambien se declara** (el del 404): documenta la
   precondicion y falla si alguien la rompe.

5. **El estado no toca `src/main`.** Si un escenario solo se puede montar cambiando
   produccion, es que el escenario esta mal elegido.

## 7. Como se ejecuta

Desde la raiz del repositorio (`ddd_clean`):

```powershell
.\tools\validate-contracts.ps1                  # consumidor -> pacto -> proveedor
.\tools\validate-contracts.ps1 -Only consumer   # solo regenerar el pacto
.\tools\validate-contracts.ps1 -Only provider   # solo verificarlo
.\tools\validate-contracts.ps1 -BreakCheck      # ademas, comprobar que el gate puede fallar
```

```bash
./tools/validate-contracts.sh --break-check
```

**El exit code es el criterio de aceptacion.** 0 o no esta terminado.
`5` significa que el pacto alterado tambien paso: el contrato no comprueba nada.

Por debajo, si hace falta ejecutar un lado suelto:

```powershell
cd contract-testing\bc5-logistic-consumer ; .\mvnw.cmd -B test
cd ms-production-packaging ; .\mvnw.cmd -B -Pcontract test-compile failsafe:integration-test failsafe:verify
```

La verificacion del proveedor corre con el perfil `it` (H2 en memoria): no necesita
Docker ni PostgreSQL.

## 8. Errores tipicos, y como se ven

| Sintoma | Causa real |
| :------ | :--------- |
| `No such state` / el `@State` no se invoca | El texto del `given` y el del `@State` no son identicos |
| La verificacion pasa con cualquier cambio | Todo el body son matchers de tipo (ver seccion 5) |
| Pasa sola pero falla en el gate | La interaccion depende de datos que dejo otra: falta limpiar la base |
| `Expected a List with at least 2 elements` | El `@State` siembra menos de lo que el `given` promete |
| `BodyMismatch` en `fechaGeneracion` | Se fijo un valor exacto en una fecha, o el regex no admite la fraccion de segundo |
| El pacto no se genera | El test del consumidor fallo: sin test verde no hay pacto, a proposito |

## 9. Lo que NO se hace

- No se edita el `.json` del pacto.
- No se relaja un matcher para que el proveedor pase. Si el proveedor cambio de verdad,
  es un **breaking change**: se para y se reporta.
- No se añaden al contrato campos que BC5 no usa.
- No se toca `src/main` del proveedor desde una prueba de contrato.
- No se mete `@Transactional` en el test de verificacion para "que no queden datos": la
  aplicacion responde en otro hilo y no veria la siembra.
