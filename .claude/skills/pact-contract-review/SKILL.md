---
name: pact-contract-review
description: Checklist de revision adversarial para pruebas de contrato Pact (interacciones del consumidor y verificacion del proveedor), especialmente las generadas por IA. Cargalo cuando haya que revisar, auditar o dar por buenas interacciones recien escritas, cuando la verificacion del proveedor pase en verde pero haya dudas de que compruebe algo, o cuando el gate falle con exit 5 (pacto falsificable).
---

# Revision de pruebas de contrato

Premisa: **un pacto verificado en verde no demuestra que los dos servicios se
entiendan**. Demuestra que N respuestas encajaron con N plantillas que puede que no
exijan nada. Esta revision busca las interacciones que estarian en verde aunque el
proveedor devolviera casi cualquier cosa.

Es el control de calidad de las pruebas de contrato generadas por IA, donde el fallo
dominante no es la interaccion que se rompe (esa se ve), sino la que **no puede
romperse**: un body entero declarado con `stringType` pasa con cualquier cadena.

## 1. Regla de oro

> Si el proveedor cambiara la respuesta de forma que **rompiera a BC5**,
> ¿fallaria la verificacion?

Aplicala con averias concretas, que son las que el contract testing existe para
detectar antes del despliegue:

| Cambio en el proveedor | Que interaccion deberia caerse |
| :--------------------- | :----------------------------- |
| Renombrar `codigoIdentificacion` a `codigo` | Todas las que leen la etiqueta |
| Devolver `estado: "TRANSFERIDO"` donde antes iba `LISTO` | La lista de pendientes y el detalle |
| Cambiar el 404 por un 200 con lista vacia | La interaccion de error |
| Serializar la fecha como epoch millis | Las que exigen el formato ISO |
| Quitar el campo `etiqueta` cuando es null | La de los paquetes de una orden |

Si ninguna se cae ante ninguna, el pacto solo esta probando que el servicio responde.

El gate tiene la version automatica de esta pregunta:

```powershell
.\tools\validate-contracts.ps1 -BreakCheck
```

**Exit 5 es un hallazgo bloqueante**: el pacto alterado tambien paso.

## 2. Lista de rechazo

**Bloqueantes (-> RECHAZADO):**

1. **Body sin un solo valor exacto.** Todo `stringType`/`uuid`/`numberType`: la
   verificacion no puede fallar por contenido. Tiene que haber al menos un campo con
   el que el consumidor *decide* declarado con `stringValue` / `numberValue`.
2. **El test del consumidor no usa el cliente real.** Si construye un `HttpClient`
   dentro del test, el pacto no demuestra que BC5 pueda consumir la respuesta.
3. **El test del consumidor no asierta nada sobre el resultado**, o solo comprueba
   `isNotNull()`. El servidor mock devuelve exactamente lo que se le dijo: sin
   aserciones sobre el objeto deserializado, el test es una tautologia.
4. **`given` y `@State` no coinciden letra a letra.** El estado no se aplica y la
   interaccion pasa con la base en el estado que hubiera quedado.
5. **El `@State` no siembra el estado que promete** (dice "2 paquetes LISTOS" y siembra
   uno, o siembra uno en otro estado).
6. **No se limpia la base entre interacciones.** Sin `deleteAll()` en `@BeforeEach`, una
   interaccion pasa gracias a los datos de la anterior: el pacto deja de ser verificable
   por separado y el orden de los tests se vuelve significativo.
7. **El pacto `.json` fue editado a mano.** Es un artefacto generado; editarlo es
   falsificar el resultado. Se detecta porque no se reproduce al regenerarlo.
8. **La expectativa se relajo para que el proveedor pase.** Si el commit cambia un
   `stringValue` por un `stringType` justo despues de un fallo de verificacion, eso no
   es una correccion: es tapar un breaking change.

**Observaciones (-> APROBADO CON OBSERVACIONES):**

9. Campos en el pacto que el consumidor no usa (sobre-especificacion): cada uno es un
   falso positivo futuro.
10. Valores exactos donde bastaba el formato (una fecha concreta, un UUID concreto que
    no es el recurso pedido).
11. `given` que describe la peticion en vez del estado del proveedor
    (`"GET devuelve 200"`).
12. Nombres de interaccion que no dicen el escenario de negocio.
13. Tamaño exacto de lista donde bastaba `minArrayLike`.
14. Estados duplicados que dicen lo mismo con otras palabras: multiplican el codigo de
    siembra del proveedor sin añadir cobertura.
15. Ningun caso de error en el pacto. Un contrato que solo cubre el camino feliz deja
    fuera justo lo que el consumidor no sabe manejar.

## 3. Comprobaciones que hay que ejecutar, no razonar

1. **El ciclo completo, con falsabilidad:**

   ```powershell
   .\tools\validate-contracts.ps1 -BreakCheck
   ```

   Exit 0 y la linea `Falsabilidad: comprobada`.

2. **Reproducibilidad del pacto.** Regenerarlo no debe cambiarlo:

   ```powershell
   .\tools\validate-contracts.ps1 -Only consumer
   git -C . diff --stat ms-production-packaging/src/test/resources/pacts
   ```

   Si aparece un diff sin que nadie haya tocado el consumidor, hay algo no
   determinista en el pacto (una fecha real, un UUID aleatorio como valor exacto).

3. **Aislamiento entre interacciones.** El numero de tests del proveedor debe ser
   exactamente el numero de interacciones del pacto. Si el gate dice
   "Interacciones verificadas: 3 de 4", hay una interaccion que Pact no ejecuto: en la
   practica es como si no existiera.

4. **Averia manual dirigida** (la mas informativa, y no la hace el gate): cambia una
   linea del proveedor que el contrato dice cubrir — por ejemplo, devolver
   `null` en `etiqueta` para un paquete LISTO — y comprueba que la verificacion se cae.
   Deshaz el cambio despues. Nunca lo dejes en el arbol de trabajo.

## 4. Formato del dictamen

Veredicto en una linea: **APROBADO** / **APROBADO CON OBSERVACIONES** / **RECHAZADO**.
Despues:

| # | Interaccion / @State | Categoria | Por que no vale | Correccion sugerida |
|---|----------------------|-----------|-----------------|---------------------|

Ordena por gravedad. Cierra con lo que si esta bien resuelto (uno o dos puntos
concretos) y con lo que falta por cubrir, separando lo que importa de lo que seria
contrato por contrato.

## 5. Criterios de veredicto

- **RECHAZADO**: cualquier bloqueante de la seccion 2, o el gate devuelve distinto de 0
  (incluido el exit 5 de falsabilidad).
- **APROBADO CON OBSERVACIONES**: las interacciones son validas y el gate pasa, pero hay
  sobre-especificacion, escenarios de error sin cubrir o nombres que no describen el
  escenario.
- **APROBADO**: cada interaccion se caeria ante un cambio que rompiera al consumidor, los
  estados siembran lo que prometen, el pacto se regenera identico y el gate pasa con
  `-BreakCheck`.

No suavices el veredicto. Un contrato falsamente aprobado es peor que no tener contrato:
da permiso para desplegar.
