# Pruebas de integración — BC4 (ms-production-packaging)

Pruebas de la API REST contra el microservicio **realmente levantado** y una base de datos
real. Cubren lo que las pruebas unitarias no pueden ver porque lo mockean: enrutamiento
HTTP, serialización JSON, validación de DTOs, mapeo de excepciones a códigos de estado y
el viaje completo por JPA hasta la base de datos.

| Archivo | Qué es |
| :------ | :----- |
| `bc4-integracion.postman_collection.json` | La colección: 3 flujos, 32 requests, 92 aserciones |
| `local.postman_environment.json` | Environment con `baseUrl` |

---

## 1. Levantar el ambiente y lanzar las pruebas (un comando)

Desde la raíz del repositorio (`ddd_clean/`):

```powershell
.\tools\validate-integration.ps1
```

Eso levanta el ambiente, espera a que la app responda, ejecuta la colección y baja el
ambiente. En Git Bash o Linux: `./tools/validate-integration.sh`.

### Modos

| Comando | Qué levanta | Cuándo usarlo |
| :------ | :---------- | :------------ |
| `.\tools\validate-integration.ps1` | Docker: PostgreSQL 16 + la app | Modo de referencia (mismo motor que producción) |
| `.\tools\validate-integration.ps1 -Mode local` | El jar con perfil `it` y H2 en memoria | Iterar rápido, sin Docker |
| `.\tools\validate-integration.ps1 -Mode none` | Nada: la app ya está levantada | Repetir la colección sin reiniciar |

Opciones útiles: `-KeepUp` deja el ambiente arriba al terminar; `-Twice` ejecuta la
colección **dos veces contra la misma base de datos** y exige que las dos pasen (es la
comprobación de repetibilidad); `-Report` genera además el reporte HTML entregable en
`docs/reportes/integracion-postman/index.html`.

### Requisitos

- **JDK 21** (`java -version` debe decir 21).
- **Node.js** — el gate ejecuta Newman con `npx`. Alternativa: `npm install -g newman`.
- **Docker Desktop** solo para el modo por defecto.

En ambos modos la base de datos es **efímera**: se crea vacía y se destruye al terminar.

### Salida esperada

```
=== 4/5  Coleccion Postman (Newman) =============================
  ...
=== 5/5  Resumen ================================================
  Requests   : 32 ejecutados, 0 fallidos
  Aserciones : 92 ejecutadas, 0 fallidas
  Reporte    : ms-production-packaging\target\newman\report.json

OK: validacion de integracion superada.
```

| Exit code | Significado |
| --------: | :---------- |
| 0 | validación superada |
| 1 | aserciones fallidas |
| 2 | el ambiente no levantó |
| 3 | falta un prerrequisito (Docker, Node, la colección) |
| 4 | timeout esperando `/actuator/health` |

---

## 2. Ejecutarlas desde Postman (interfaz gráfica)

1. Levantar el ambiente y dejarlo arriba:
   `.\tools\validate-integration.ps1 -Mode local -KeepUp`
2. En Postman: **Import** → los dos archivos de esta carpeta.
3. Seleccionar el environment **BC4 - Integración local** (arriba a la derecha).
4. Botón derecho sobre la colección → **Run collection** → **Run**.

Importante: **ejecutar la colección entera y en orden**. Los requests se encadenan (el id
de la orden que crea el primero lo usan los siguientes); lanzar uno suelto falla.

Para bajar el ambiente después: `docker compose -f docker-compose.integration.yml down -v`
(modo Docker) o cerrar el proceso `java` del modo local.

---

## 3. Qué se prueba: un flujo de negocio por carpeta

La colección se agrupa por **flujo**, no por endpoint. Cada carpeta es un flujo completo y
**autocontenido** (construye su propio escenario), así que se puede lanzar sola:
`newman run ... --folder "02 - Flujo B: Envasado, etiquetado y entrega a BC5"`.

| Carpeta | Requests | Flujo |
| :------ | -------: | :---- |
| `01 - Flujo A: Produccion en cocina` | 10 | De la orden generada a la orden `COMPLETADA` |
| `02 - Flujo B: Envasado, etiquetado y entrega a BC5` | 11 | Del paquete del paciente a `LISTO` y visible para Logística |
| `03 - Flujo C: Rechazos` | 11 | Los errores esperados de los dos flujos anteriores |

### `01 - Flujo A` — producción en cocina (10 requests)

```
generar orden → consultar por id → buscar por fecha → iniciar preparación
    → aparece entre las EN_PREPARACION → preparar item 1 → preparar item 2
    → completar → releer COMPLETADA → aparece entre las COMPLETADA
```

Verifica el estado devuelto en cada transición, que `totalPorciones` se calcula bien
(12 + 8 = 20), que marcar un ítem no toca el otro, y que los filtros `?fecha=` y
`?estado=` de la API devuelven lo que deben. Los `GET` intercalados releen el recurso de la
base de datos: son los que demuestran que el mapeo JPA no pierde nada.

### `02 - Flujo B` — envasado, etiquetado y entrega a BC5 (11 requests)

```
[setup] orden de 1 ítem EN_PREPARACION → crear paquete → la orden lo registra
    → envasar → etiquetar → marcar LISTO → BC5 lo ve entre los listos para transferir
    → preparar el ítem → completar la orden con el paquete → listar paquetes de la orden
```

Comprueba que la etiqueta sale con el formato del dominio
(`Av. Mutualista 450, Ref: ... - Santa Cruz` y código `PKG-XXXXXXXX`), que `GET /paquetes`
sin filtros (la consulta de Logística) solo ofrece paquetes `LISTO` e incluye éste con sus
datos de entrega, y que la orden termina `COMPLETADA` con la referencia del paquete.

> El paquete se crea **antes** de completar la orden: una orden `COMPLETADA` es inmutable.

### `03 - Flujo C` — rechazos (3 de preparación + 8 de verificación)

Cada error comprueba el código HTTP **y** el cuerpo `ApiError`:

| Escenario | Esperado |
| :-------- | :------- |
| Orden sin fecha y sin ítems | **400** con los dos campos en `detalles` |
| Ítem con `numeroPacientes = 0` | **400** señalando el campo dentro de la lista |
| Segunda orden para la misma fecha | **409** `Conflicto de estado` |
| Consultar una orden inexistente | **404** `Recurso no encontrado` |
| Etiquetar un paquete sin envasar | **409** `Regla de negocio violada` |
| Completar una orden con ítems sin preparar | **409** `Regla de negocio violada` |

Además, tras los dos errores de negocio se relee el recurso para confirmar que **el rechazo
no dejó estado a medias**.

### Los mismos flujos, en Java

Los flujos A y B están también como pruebas de integración en el lenguaje del proyecto
(`FlujoProduccionIT`, `FlujoEnvasadoIT`), que además verifican los eventos de dominio
publicados. Ver [`../docs/pruebas-integracion.md`](../docs/pruebas-integracion.md).

---

## 4. Repetibilidad

La colección debe pasar **dos veces seguidas contra la misma base de datos**:

```powershell
.\tools\validate-integration.ps1 -Mode local -Twice
```

Es la propiedad que más fácil se rompe. Aquí el riesgo concreto es la regla *"una sola
orden de producción por fecha"*: con una fecha fija, la segunda vuelta devolvería 409. Por
eso las fechas se generan aleatorias en un script *pre-request*, con rangos disjuntos para
cada flujo (`1..4000`, `4001..8000` y `8001..12000` días).

---

## 5. Problemas frecuentes

| Síntoma | Causa y solución |
| :------ | :--------------- |
| `exit 3` — "no hay forma de ejecutar Newman" | Falta Node.js. Instálalo, o `npm install -g newman`. |
| `npm error code E401` | Tu npm apunta a un registro privado con el token caducado. El gate ya fuerza `--registry=https://registry.npmjs.org`; si lo lanzas a mano, añade ese flag. |
| `exit 4` — timeout de health | El puerto 8082 ya está ocupado por otra instancia. Ciérrala, o usa `-BaseUrl` y `$env:IT_PORT` con otro puerto. |
| `exit 2` en modo Docker | Docker Desktop no está arrancado. O usa `-Mode local`. |
| 409 "Ya existe una orden para la fecha" al ejecutar a mano | Estás reutilizando una fecha. Ejecuta la colección completa: el pre-request genera una nueva. |
| Un request suelto falla en Postman | Los requests se encadenan. Ejecuta la colección entera con el Runner. |

---

## 6. Generar más pruebas con los agentes

El repositorio incluye un entorno de generación asistida por IA para estas pruebas:

```
/test-integration <endpoint o escenario>
```

Lanza `integration-test-writer` (escribe los requests y los ejecuta), después
`integration-test-reviewer` (dictamen adversarial) y finalmente el gate. Detalle completo
en [`../docs/pruebas-integracion.md`](../docs/pruebas-integracion.md).
