---
name: bc4-api-contract
description: Contrato de la API REST del microservicio BC4 (ms-production-packaging) - endpoints, DTOs de entrada y salida, maquinas de estado de los dos agregados y mapeo de excepciones de dominio a codigos HTTP. Cargalo antes de escribir, revisar o depurar cualquier prueba de integracion, o cuando haya que decidir que codigo de estado debe devolver un escenario.
---

# Contrato de la API - BC4 Produccion y Envasado

Fuente de verdad para las pruebas de integracion. Todo lo de aqui esta verificado contra
el codigo de `ms-production-packaging/src/main/java`. Si la API responde algo distinto de
lo que dice este documento, **no ajustes la prueba**: o el contrato esta desactualizado o
hay un bug, y decidirlo es del usuario.

Base URL por defecto: `http://localhost:8082` · Swagger: `/swagger-ui.html`

## 1. Endpoints

### Ordenes de produccion — `/api/v1/ordenes`

| Metodo | Ruta | Que hace | Exito |
| :----- | :--- | :------- | :---- |
| POST | `/api/v1/ordenes` | Genera la orden desde el CalendarioConsolidado | **201** |
| GET | `/api/v1/ordenes/{id}` | Consulta una orden | 200 |
| GET | `/api/v1/ordenes?estado=PENDIENTE` | Lista por estado (por defecto PENDIENTE) | 200 |
| GET | `/api/v1/ordenes?fecha=2026-07-15` | La orden de una fecha (lista de 0 o 1) | 200 |
| POST | `/api/v1/ordenes/{id}/iniciar-preparacion` | PENDIENTE -> EN_PREPARACION | 200 |
| POST | `/api/v1/ordenes/{id}/items/{recetaId}/preparar` | Marca un item PREPARADO | 200 |
| POST | `/api/v1/ordenes/{id}/completar` | EN_PREPARACION -> COMPLETADA | 200 |

### Paquetes etiquetados — `/api/v1/paquetes`

| Metodo | Ruta | Que hace | Exito |
| :----- | :--- | :------- | :---- |
| POST | `/api/v1/paquetes` | Crea el paquete de un paciente en una orden | **201** |
| GET | `/api/v1/paquetes/{id}` | Consulta un paquete | 200 |
| GET | `/api/v1/paquetes?ordenId={id}` | Paquetes de una orden | 200 |
| GET | `/api/v1/paquetes?estado=LISTO` | Paquetes por estado | 200 |
| GET | `/api/v1/paquetes` | Sin filtros: los LISTOS pendientes de transferir a BC5 | 200 |
| POST | `/api/v1/paquetes/{id}/envasar` | ENVASADO_PENDIENTE -> ENVASADO | 200 |
| POST | `/api/v1/paquetes/{id}/etiquetar` | ENVASADO -> ETIQUETADO | 200 |
| POST | `/api/v1/paquetes/{id}/marcar-listo` | ETIQUETADO -> LISTO | 200 |

`/actuator/health` responde `{"status":"UP"}` — es lo que espera el gate antes de lanzar
la coleccion.

## 2. Cuerpos de entrada

### `POST /api/v1/ordenes` — GenerarOrdenRequest

```json
{
  "fechaProduccion": "2026-07-15",
  "items": [
    {
      "recetaId": "REC-001",
      "nombreReceta": "Pure de zapallo con pollo",
      "numeroPacientes": 12,
      "cantidadPorcion": 250,
      "unidadMedida": "GRAMOS",
      "descripcionPorcion": "Porcion individual estandar"
    }
  ]
}
```

Validaciones (`@Valid`, todas producen **400**):

| Campo | Regla | Mensaje |
| :---- | :---- | :------ |
| `fechaProduccion` | `@NotNull` | `fechaProduccion es obligatoria` |
| `items` | `@NotEmpty` | `La orden debe tener al menos un item` |
| `items[].recetaId` | `@NotBlank` | `recetaId es obligatorio` |
| `items[].nombreReceta` | `@NotBlank` | `nombreReceta es obligatorio` |
| `items[].numeroPacientes` | `@Positive` | `numeroPacientes debe ser mayor a cero` |
| `items[].cantidadPorcion` | `@Positive` | `cantidadPorcion debe ser mayor a cero` |
| `items[].unidadMedida` | `@NotBlank`, uno de `GRAMOS` / `MILILITROS` / `UNIDADES` | `unidadMedida es obligatoria` |
| `items[].descripcionPorcion` | `@NotBlank` | `descripcionPorcion es obligatoria` |

### `POST /api/v1/paquetes` — CrearPaqueteRequest

```json
{ "pacienteId": "PAC-1001", "ordenId": "<id de una orden existente>" }
```

Ambos `@NotBlank`. La orden **debe existir**: si no, 404.

### `POST /api/v1/paquetes/{id}/etiquetar` — GenerarEtiquetaRequest

```json
{
  "nombreCompleto": "Maria Perez",
  "calle": "Av. Mutualista",
  "numero": "450",
  "referencia": "Edificio azul, piso 2",
  "ciudad": "Santa Cruz"
}
```

`referencia` es el unico campo opcional. Los demas son `@NotBlank`.

## 3. Cuerpos de salida

### OrdenResponse

```json
{
  "id": "uuid",
  "fechaProduccion": "2026-07-15",
  "estado": "PENDIENTE | EN_PREPARACION | COMPLETADA",
  "totalPorciones": 20,
  "items": [
    {
      "recetaId": "REC-001",
      "nombreReceta": "...",
      "cantidadTotal": 12,
      "estado": "PENDIENTE | EN_PREPARACION | PREPARADO",
      "porcionCantidad": 250,
      "porcionUnidad": "GRAMOS",
      "porcionDescripcion": "..."
    }
  ],
  "paqueteIds": ["uuid", "..."]
}
```

Campos calculados que conviene asertar porque los produce el dominio:

- `totalPorciones` = suma de `cantidadTotal` de los items, y `cantidadTotal` = el
  `numeroPacientes` enviado para esa receta.
- `paqueteIds` se rellena al crear paquetes sobre la orden, no al crearla.

### PaqueteResponse

```json
{
  "id": "uuid",
  "pacienteId": "PAC-1001",
  "ordenId": "uuid",
  "estado": "ENVASADO_PENDIENTE | ENVASADO | ETIQUETADO | LISTO",
  "etiqueta": null
}
```

`etiqueta` es **null hasta ETIQUETADO**. A partir de ahi:

```json
"etiqueta": {
  "nombrePaciente": "Maria Perez",
  "direccion": "Av. Mutualista 450, Ref: Edificio azul, piso 2 - Santa Cruz",
  "codigoIdentificacion": "PKG-3F2A1B4C",
  "fechaGeneracion": "2026-09-09T23:58:12.34"
}
```

Dos formatos que valen la pena asertar porque los construye el dominio:

- `direccion` = `"{calle} {numero}, Ref: {referencia} - {ciudad}"`. Sin `referencia`, el
  tramo `", Ref: ..."` desaparece: `"{calle} {numero} - {ciudad}"`.
- `codigoIdentificacion` = `PKG-` + los 8 primeros caracteres del UUID del paquete, sin
  guiones y en mayusculas. Patron: `/^PKG-[0-9A-F]{8}$/`.

### ApiError (todas las respuestas de error)

```json
{
  "timestamp": "2026-09-09T23:58:12.34",
  "status": 409,
  "error": "Regla de negocio violada",
  "mensaje": "No se puede completar la orden: hay items sin preparar.",
  "detalles": []
}
```

Las cinco claves estan **siempre** presentes; `detalles` es `[]` salvo en los errores de
validacion, donde trae una entrada `"campo: mensaje"` por cada campo invalido.

## 4. Mapeo excepcion -> codigo HTTP

Lo define `GlobalExceptionHandler`. Es lo que las pruebas de integracion verifican de
punta a punta, y lo que los tests unitarios no pueden garantizar.

| Excepcion | HTTP | `error` |
| :-------- | :--: | :------ |
| `DomainException` y sus hijas | **409** | `Regla de negocio violada` |
| `NoSuchElementException` (agregado inexistente) | **404** | `Recurso no encontrado` |
| `IllegalStateException` (conflicto de aplicacion) | **409** | `Conflicto de estado` |
| `IllegalArgumentException`, `NullPointerException` | **400** | `Datos invalidos` |
| `MethodArgumentNotValidException` (`@Valid`) | **400** | `Validacion fallida` |
| Cualquier otra | 500 | `Error interno` |

Un **500 nunca es un resultado esperado** en esta coleccion. Si aparece, es un hallazgo.

## 5. Reglas de negocio observables desde la API

| # | Regla | Efecto HTTP | Mensaje (fragmento) |
| - | :---- | :---------- | :------------------ |
| 1 | Una sola orden por `fechaProduccion` | 409 `Conflicto de estado` | `Ya existe una orden de produccion para la fecha` |
| 2 | La orden debe tener al menos un item | 400 (validacion) | `La orden debe tener al menos un item` |
| 3 | Solo se completa desde EN_PREPARACION | 409 | `Solo se puede completar una orden EN_PREPARACION` |
| 4 | Solo se completa con todos los items PREPARADO | 409 | `hay items sin preparar` |
| 5 | Una orden COMPLETADA es inmutable | 409 | `No se puede modificar una orden ya completada` / `No se pueden registrar paquetes en una orden completada` |
| 6 | Solo se inicia preparacion desde PENDIENTE | 409 | `Solo se puede iniciar la preparacion desde estado PENDIENTE` |
| 7 | Un item no se marca dos veces | 409 (`IllegalStateException`) | `El item ya esta marcado como PREPARADO` |
| 8 | El envasado precede al etiquetado | 409 | `No se puede etiquetar un paquete que no ha sido envasado` |
| 9 | Solo se marca LISTO desde ETIQUETADO | 409 | `El paquete debe estar ETIQUETADO antes de marcarse LISTO` |
| 10 | Los datos del paciente deben estar completos | 409 | `Los datos del paciente son insuficientes` |
| 11 | El paquete exige una orden existente | 404 | `No existe una OrdenDeProduccion con id` |

## 6. Maquinas de estado

```
OrdenDeProduccion
    PENDIENTE ──iniciar-preparacion──▶ EN_PREPARACION ──completar──▶ COMPLETADA
                                            │                            (inmutable)
                                    items/{recetaId}/preparar
                                    (tambien admitido en PENDIENTE)

ItemOrden
    PENDIENTE ──preparar──▶ PREPARADO        (repetir -> 409)

PaqueteEtiquetado
    ENVASADO_PENDIENTE ──envasar──▶ ENVASADO ──etiquetar──▶ ETIQUETADO ──marcar-listo──▶ LISTO
```

## 7. Restricciones de orden que condicionan cualquier flujo

1. **El paquete se crea antes de completar la orden.** Crear un paquete registra su id en
   la orden (`registrarPaqueteGenerado`), y una orden COMPLETADA lo rechaza con 409.
2. **Cada fecha de produccion se usa una sola vez.** Cualquier flujo que genere ordenes
   debe usar fechas distintas por corrida (ver skill `postman-integration-testing`).
3. Las transiciones **solo avanzan**: no hay endpoint para retroceder de estado.
