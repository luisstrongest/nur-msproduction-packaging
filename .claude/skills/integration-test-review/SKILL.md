---
name: integration-test-review
description: Checklist de revision adversarial para pruebas de integracion de API (coleccion Postman / Newman), especialmente las generadas por IA. Cargalo cuando haya que revisar, auditar o dar por buenos requests recien escritos, o cuando la coleccion pase en verde pero haya dudas de que compruebe algo mas que el enrutamiento.
---

# Revision de pruebas de integracion

Premisa: **una coleccion en verde no demuestra que la API funcione**. Demuestra que N
peticiones no explotaron. Esta revision busca los requests que estarian en verde aunque
media aplicacion estuviera rota.

Es el control de calidad de las pruebas de integracion generadas por IA, donde el fallo
tipico no es el request que se rompe (ese se ve), sino el request que **no puede romperse**:
un `pm.test('200 OK')` que pasa mientras el servicio devuelve basura.

## 1. Regla de oro

> Si rompo a proposito la capa que este request dice probar,
> **¿falla alguna asercion?**

Aplicala a cada request con tres averias concretas, que son las que la integracion existe
para detectar:

| Averia simulada | Que request deberia caerse |
| :-------------- | :------------------------- |
| El repositorio deja de guardar (`guardar()` vacio) | El `GET` de verificacion posterior |
| El handler devuelve 500 en vez de 409 | Los requests de la carpeta de errores |
| El mapper JPA pierde la lista de items | El `GET` que relee la orden |

Si ninguna asercion se cae ante las tres, la coleccion solo esta probando que Tomcat
responde.

## 2. Lista de rechazo

Recorrela en orden. Cada punto marcado es un hallazgo con request, categoria y explicacion.

**Bloqueantes (-> RECHAZADO):**

1. Request sin bloque `test`, o con `exec` vacio. Cuenta como ejecutado en el resumen y no
   comprueba nada.
2. La unica asercion es el codigo de estado. No prueba la aplicacion, prueba que esta
   encendida.
3. **La coleccion no es repetible**: pasa contra una base recien creada y falla en la
   segunda vuelta. Causa habitual aqui: una `fechaProduccion` fija chocando con la regla
   de una orden por fecha.
4. Una carpeta **no es autocontenida**: la de errores depende de que se haya ejecutado
   antes la del flujo correcto.
5. Se espera un codigo HTTP que **contradice el contrato** (`bc4-api-contract`): un 500
   dado por bueno, o un 400 donde el handler devuelve 409.
6. Ids o fechas de una corrida anterior escritos a mano en el JSON.
7. La asercion es tautologica: compara la respuesta consigo misma, o comprueba una
   variable que el propio script acaba de fijar.

**Graves (-> APROBADO CON OBSERVACIONES, o rechazo si se acumulan):**

8. Error verificado solo por el status, sin mirar `error` ni `mensaje`: no distingue el
   409 del handler de un 409 llegado por otro camino.
9. `to.exist` / `to.not.be.null` donde se podia comparar el valor exacto.
10. Transicion de estado sin comprobar que **lo que no debia cambiar** no cambio.
11. No hay ningun `GET` de verificacion tras las escrituras: la persistencia queda sin
    probar, que es media razon de ser de estas pruebas.
12. Un request que comprueba tres escenarios distintos.
13. Escenario de error sin la comprobacion de que el estado **no quedo corrupto**.
14. Datos de prueba que colisionan entre carpetas dentro de la misma corrida.
15. `console.log` como sustituto de una asercion.

**Menores (se anotan, no bloquean):**

16. Nombre que no describe el escenario (`POST orden 2`, `test error`).
17. El error esperado no aparece en el nombre del request (`... -> 409`).
18. Copy-paste de un request identico cambiando un literal, sin necesidad.
19. Falta la descripcion que explica **por que** existe ese request.
20. `raw`, `path` y `query` de la URL se contradicen (Postman muestra uno, Newman ejecuta
    otro).

## 3. Verificacion objetiva

La revision manual no basta: hay dos comprobaciones que se ejecutan y dan un si o un no.

**Repetibilidad** — la que mas hallazgos produce:

```powershell
.\tools\validate-integration.ps1 -Mode local -KeepUp   # primera vuelta
.\tools\validate-integration.ps1 -Mode none            # segunda, misma base de datos
```

Las dos tienen que dar exit 0. Si la segunda falla, es un hallazgo bloqueante confirmado,
no una opinion.

**Aislamiento por flujo** — cada carpeta (flujo) debe pasar sola:

```bash
npx --yes --registry=https://registry.npmjs.org newman run \
    postman/bc4-integracion.postman_collection.json \
    -e postman/local.postman_environment.json \
    --folder "03 - Flujo C: Rechazos (errores esperados)"
```

El equivalente del mutation testing de las pruebas unitarias es, aqui, **preguntarse que
capa queda sin verificar**. Una coleccion de solo status codes deja fuera persistencia,
mapeo y serializacion — exactamente las tres capas que los tests unitarios mockean y que
esta coleccion existe para cubrir.

## 4. Formato del dictamen

Veredicto en la primera linea: **APROBADO** / **APROBADO CON OBSERVACIONES** /
**RECHAZADO**. Despues, la tabla de hallazgos ordenada por gravedad:

| # | Request | Categoria | Por que no vale | Correccion sugerida |
|---|---------|-----------|-----------------|---------------------|

Cierra con: (a) uno o dos aciertos concretos del autor, (b) los escenarios que siguen sin
cubrir, separando los que importan de los que serian cobertura por cobertura.

## 5. Al revisar, no

- No reescribas los requests: el dictamen es tu salida, la correccion es de quien los
  escribio.
- No pidas un request por endpoint. La integracion se paga en tiempo de ejecucion: cubrir
  el flujo principal y los errores que representan una regla vale mas que 40 requests
  triviales.
- No exijas por HTTP lo que ya cubre un test unitario. Duplicar una invariante de dominio
  en la coleccion no añade informacion, solo tiempo de suite.
- No apruebes "porque pasa en verde". Esa es exactamente la trampa que buscas.
