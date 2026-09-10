# Pruebas de integración — BC4 (ms-production-packaging)

Pruebas de la API REST contra el microservicio **realmente levantado** y una base de datos
real. Cubren lo que las pruebas unitarias no pueden ver porque lo mockean: enrutamiento
HTTP, serialización JSON, validación de DTOs, mapeo de excepciones a códigos de estado y
el viaje completo por JPA hasta la base de datos.

| Archivo | Qué es |
| :------ | :----- |
| `bc4-integracion.postman_collection.json` | La colección: 23 requests, 69 aserciones |
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
comprobación de repetibilidad).

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
  Requests   : 23 ejecutados, 0 fallidos
  Aserciones : 69 ejecutadas, 0 fallidas
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

## 3. Qué se prueba

### `01 - Flujo correcto` — 12 requests, el ciclo completo de BC4

```
generar orden → consultar → iniciar preparación → preparar item 1 → preparar item 2
    → crear paquete → verificar que la orden lo registró → envasar → etiquetar
    → marcar listo → completar orden → listar paquetes de la orden
```

No comprueba solo códigos 200: verifica el estado devuelto en cada transición, que
`totalPorciones` se calcula bien (12 + 8 = 20), que la etiqueta sale con el formato del
dominio (`Av. Mutualista 450, Ref: ... - Santa Cruz` y código `PKG-XXXXXXXX`), y que la
orden acaba registrando el id del paquete. Los `GET` intercalados releen el recurso de la
base de datos: son los que demuestran que el mapeo JPA no pierde nada.

> El paquete se crea **antes** de completar la orden: una orden `COMPLETADA` es inmutable.

### `02 - Flujo incorrecto` — 11 requests (3 de preparación + 8 de verificación)

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

La carpeta es autocontenida: construye su propio escenario, así que puede ejecutarse sola.

---

## 4. Repetibilidad

La colección debe pasar **dos veces seguidas contra la misma base de datos**:

```powershell
.\tools\validate-integration.ps1 -Mode local -Twice
```

Es la propiedad que más fácil se rompe. Aquí el riesgo concreto es la regla *"una sola
orden de producción por fecha"*: con una fecha fija, la segunda vuelta devolvería 409. Por
eso las fechas se generan aleatorias en un script *pre-request*, con rangos distintos para
cada carpeta.

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
