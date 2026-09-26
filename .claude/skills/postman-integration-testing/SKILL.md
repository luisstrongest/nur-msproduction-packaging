---
name: postman-integration-testing
description: Convenciones obligatorias para escribir pruebas de integracion de la API con Postman y Newman en este proyecto (coleccion postman/bc4-integracion.postman_collection.json). Cargalo antes de crear o modificar cualquier request o script de test de la coleccion, cuando haya que decidir en que carpeta va un escenario, como encadenar ids entre requests, o por que una coleccion que pasa la primera vez falla la segunda.
---

# Pruebas de integracion con Postman en este proyecto

Estas son las reglas del repositorio, no preferencias generales. Los requests que escribas
deben ser indistinguibles de los que ya existen en
`postman/bc4-integracion.postman_collection.json`.

## 1. Que cuenta como prueba de integracion aqui

Una peticion HTTP real contra el microservicio **levantado de verdad**, con su base de
datos real, comprobando la respuesta completa. Nada se mockea.

La frontera con las pruebas unitarias (ver skill `java-unit-testing`) es nitida:

| Lo prueban los unitarios | Lo prueba SOLO la integracion |
| :----------------------- | :---------------------------- |
| Reglas de negocio del agregado | Enrutamiento HTTP y codigos de estado |
| Orquestacion del servicio (con mocks) | Serializacion / deserializacion JSON |
| Mapeo dominio <-> entidad (con mock del repo) | El viaje real por JPA hasta la base de datos |
| Que el handler traduce una excepcion (con MockMvc a mano) | Que la excepcion **llega al cliente** con ese codigo |

Corolario practico: **si un escenario se puede comprobar sin levantar nada, no va aqui.**
Una invariante de dominio pura ya tiene su test unitario; repetirla por HTTP es coste sin
informacion nueva. Lo que si va aqui es la invariante *vista desde fuera*: que devuelve 409
y que el estado no queda corrupto.

## 2. Estructura de la coleccion

La coleccion se agrupa por **flujo de negocio**, no por endpoint (requisito del Proyecto
Final: al menos dos flujos completos, agrupados por flujo):

```
BC4 - Pruebas de integracion
├── 01 - Flujo A: Produccion en cocina       -> orden generada ... orden COMPLETADA
├── 02 - Flujo B: Envasado, etiquetado y     -> paquete ... LISTO, visible para BC5,
│        entrega a BC5                          y la orden completada con el paquete
└── 03 - Flujo C: Rechazos                   -> errores esperados, con su codigo y su ApiError
```

Reglas de colocacion:

- Un **flujo** es un recorrido de negocio que empieza y termina en un estado con sentido
  para el usuario (orden COMPLETADA, paquete LISTO y entregable). Un escenario nuevo del
  camino feliz se añade al flujo al que pertenece; si recorre otra parte del dominio, es
  una carpeta `0N - Flujo X` nueva, con su propio rango de fechas.
- Un **caso de error nunca se intercala** en un flujo de exito: rompe el encadenado de los
  requests que vienen detras. Va en `03 - Flujo C: Rechazos`.
- Cada carpeta es **autocontenida**. Si un escenario de error necesita un estado previo
  (una orden EN_PREPARACION, un paquete sin envasar), la carpeta lo construye con sus
  propios requests `[setup]`. Nunca se depende de que se haya ejecutado la otra carpeta.
- El nombre del request dice el escenario y, en los errores, el codigo esperado:
  `C.5 Etiquetar un paquete sin envasar -> 409`. No `POST etiquetar (error)`.
- Numeracion `<flujo>.<paso>` en el nombre (`A.4`, `B.0a [setup]`, `C.7`) para que el orden
  se lea de un vistazo. El orden real es el del array `item`, no el alfabetico.
- Las variables encadenadas llevan el sufijo de su flujo (`ordenAId`, `ordenBId`,
  `paqueteBId`, `fechaC`): dos flujos nunca comparten una variable de estado.

## 3. Variables: que va en cada sitio

| Ambito | Que va | Ejemplo |
| :----- | :----- | :------ |
| **Environment** (`local.postman_environment.json`) | Lo que cambia de una maquina a otra | `baseUrl` |
| **Collection variables** | Lo que se encadena durante la corrida | `ordenAId`, `paqueteBId`, `fechaA` (con el sufijo de su flujo) |
| **Literal en el request** | Datos de prueba fijos que nunca colisionan | `PAC-1001`, `REC-001` |

Nunca metas `baseUrl` en el request. Nunca dejes un id de una corrida anterior escrito en
el JSON: los ids se capturan y se leen con `pm.collectionVariables`.

## 4. Repetibilidad: la regla que mas se incumple

**La coleccion debe pasar dos veces seguidas contra la misma base de datos.** Si solo pasa
con la base recien creada, no es una prueba: es una demo.

En este dominio la trampa concreta es la regla *"una sola orden de produccion por fecha"*
(`existePorFecha` -> 409). Con una fecha fija, la segunda corrida falla. Por eso la fecha
se genera en el **pre-request**:

```js
// Fecha unica por ejecucion: la regla de negocio permite UNA orden por fecha.
const dias = Math.floor(Math.random() * 4000) + 1;
const fecha = new Date(Date.now() + dias * 86400000).toISOString().slice(0, 10);
pm.collectionVariables.set('fechaA', fecha);
```

Cada flujo usa un rango de dias **disjunto** (A `1..4000`, B `4001..8000`, C `8001..12000`)
para que sus escenarios no choquen entre si dentro de la misma corrida. Un flujo nuevo toma
el siguiente rango libre.

Comprobacion obligatoria antes de dar nada por bueno:

```powershell
.\tools\validate-integration.ps1 -Mode local -KeepUp   # primera vuelta
.\tools\validate-integration.ps1 -Mode none            # segunda, misma BD
```

## 5. Aserciones

Minimo por request: **el codigo HTTP y algo del cuerpo**. Un `pm.test('200 OK')` solo
comprueba que la aplicacion esta encendida.

```js
pm.test('Responde 200 OK', function () {
    pm.response.to.have.status(200);
});

const res = pm.response.json();

pm.test('El paquete pasa a ETIQUETADO', function () {
    pm.expect(res.estado).to.eql('ETIQUETADO');
});
```

Que asertar, por tipo de escenario:

- **Transicion de estado**: el estado nuevo **y** que lo que no debia cambiar no cambio
  (el otro item sigue PENDIENTE, la etiqueta se conserva).
- **Creacion**: el id devuelto, el estado inicial, y los campos derivados que calcula el
  dominio (`totalPorciones`, el formato de `direccion`, el patron del codigo `PKG-XXXXXXXX`).
- **Persistencia**: un `GET` posterior que relea el recurso. Es lo unico que demuestra que
  el mapeo JPA no perdio nada por el camino.
- **Error**: status + `error` + un trozo del `mensaje` que identifique la regla violada, y
  cuando aplique, un `GET` que confirme que **el estado no quedo a medias**.

Usa comparaciones de valor (`to.eql`) antes que de existencia (`to.exist`). `to.exist`
pasa con casi cualquier cosa.

## 6. Encadenado entre requests

Se captura en el bloque `test` del request que crea el recurso:

```js
const res = pm.response.json();
pm.collectionVariables.set('ordenAId', res.id);
```

Y se consume en la URL del siguiente: `{{baseUrl}}/api/v1/ordenes/{{ordenAId}}/completar`.

Dos restricciones del dominio que condicionan el orden (ver `bc4-api-contract`):

1. El paquete se crea **antes** de completar la orden: una orden COMPLETADA es inmutable.
2. Etiquetar exige haber envasado; marcar listo exige haber etiquetado.

## 7. Editar el JSON a mano

La coleccion se versiona como JSON con indentacion de 2 espacios. Al editarla:

- Los scripts van en `script.exec` como **array de lineas**, una por linea de codigo.
  Sin `\n` dentro de una sola cadena.
- El cuerpo va en `request.body.raw` como cadena JSON escapada.
- Valida siempre despues de editar, antes de ejecutar:

  ```bash
  node -e "JSON.parse(require('fs').readFileSync('postman/bc4-integracion.postman_collection.json','utf8')); console.log('JSON valido')"
  ```

Si prefieres editar en la aplicacion Postman, exporta como *Collection v2.1* al mismo
archivo y revisa el diff: Postman añade `_postman_id` y reordena claves.

## 8. Comandos

Desde la raiz del repositorio (`ddd_clean/`):

```powershell
.\tools\validate-integration.ps1                 # Docker + PostgreSQL real (por defecto)
.\tools\validate-integration.ps1 -Mode local     # jar + H2 en memoria, sin Docker
.\tools\validate-integration.ps1 -Mode none      # la app ya esta levantada
.\tools\validate-integration.ps1 -KeepUp         # deja el ambiente arriba al terminar
```

Newman directo (con el ambiente ya levantado):

```bash
npx --yes --registry=https://registry.npmjs.org newman run \
    postman/bc4-integracion.postman_collection.json \
    -e postman/local.postman_environment.json

# solo una carpeta
... --folder "03 - Flujo C: Rechazos (errores esperados)"
```

El `--registry` explicito no es decorativo: si el npm de la maquina apunta a un registro
privado con el token caducado, `npx` falla con `E401` por un motivo que no tiene nada que
ver con las pruebas.

## Referencias

- `references/plantillas.md` — esqueletos copiables de request (creacion, transicion,
  consulta, error, setup).
- Ejemplos reales que ya pasan en verde: `postman/bc4-integracion.postman_collection.json`.
- Contrato de la API: skill `bc4-api-contract`.
- Como levantar el ambiente y leer los resultados: `postman/README.md`.
