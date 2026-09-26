# Plantillas de request

Esqueletos copiables para el JSON de `postman/bc4-integracion.postman_collection.json`.
Copia, renombra y cambia las aserciones; no inventes una estructura nueva.

Recuerda: `script.exec` es un **array de lineas**, una por linea de codigo.

---

## 1. Creacion de un recurso (POST que devuelve 201)

Captura el id para el resto de la carpeta y comprueba el estado inicial y los campos que
calcula el dominio.

```json
{
  "name": "A.1 Generar la orden de produccion",
  "event": [
    {
      "listen": "prerequest",
      "script": {
        "type": "text/javascript",
        "exec": [
          "const dias = Math.floor(Math.random() * 4000) + 1;",
          "const fecha = new Date(Date.now() + dias * 86400000).toISOString().slice(0, 10);",
          "pm.collectionVariables.set('fechaA', fecha);"
        ]
      }
    },
    {
      "listen": "test",
      "script": {
        "type": "text/javascript",
        "exec": [
          "pm.test('Responde 201 CREATED', function () {",
          "    pm.response.to.have.status(201);",
          "});",
          "",
          "const res = pm.response.json();",
          "pm.collectionVariables.set('ordenAId', res.id);",
          "",
          "pm.test('La orden nace en estado PENDIENTE', function () {",
          "    pm.expect(res.estado).to.eql('PENDIENTE');",
          "});",
          "",
          "pm.test('totalPorciones = pacientes por receta (12 + 8 = 20)', function () {",
          "    pm.expect(res.totalPorciones).to.eql(20);",
          "});"
        ]
      }
    }
  ],
  "request": {
    "method": "POST",
    "header": [{ "key": "Content-Type", "value": "application/json" }],
    "body": {
      "mode": "raw",
      "raw": "{\n  \"fechaProduccion\": \"{{fechaA}}\",\n  \"items\": []\n}",
      "options": { "raw": { "language": "json" } }
    },
    "url": {
      "raw": "{{baseUrl}}/api/v1/ordenes",
      "host": ["{{baseUrl}}"],
      "path": ["api", "v1", "ordenes"]
    },
    "description": "Que simula este request y por que existe."
  },
  "response": []
}
```

---

## 2. Transicion de estado (POST sobre un recurso existente)

Comprueba el estado nuevo **y** que lo que no debia cambiar sigue igual. Esa segunda
asercion es la que caza los efectos colaterales.

```json
"exec": [
  "pm.test('Responde 200 OK', function () {",
  "    pm.response.to.have.status(200);",
  "});",
  "",
  "const res = pm.response.json();",
  "function item(id) {",
  "    return res.items.find(function (i) { return i.recetaId === id; });",
  "}",
  "",
  "pm.test('REC-001 queda PREPARADO', function () {",
  "    pm.expect(item(pm.collectionVariables.get('recetaId')).estado).to.eql('PREPARADO');",
  "});",
  "",
  "pm.test('REC-002 NO se toco: sigue PENDIENTE', function () {",
  "    pm.expect(item(pm.collectionVariables.get('recetaId2')).estado).to.eql('PENDIENTE');",
  "});"
]
```

---

## 3. Consulta de verificacion (GET que relee el recurso)

Es el request que demuestra que el estado **sobrevivio al viaje por JPA**. Sin uno de
estos, la coleccion solo prueba lo que la aplicacion tiene en memoria.

```json
"exec": [
  "pm.test('Responde 200 OK', function () {",
  "    pm.response.to.have.status(200);",
  "});",
  "",
  "const res = pm.response.json();",
  "",
  "pm.test('La orden persistida es la misma que se creo', function () {",
  "    pm.expect(res.id).to.eql(pm.collectionVariables.get('ordenAId'));",
  "    pm.expect(res.fechaProduccion).to.eql(pm.collectionVariables.get('fechaA'));",
  "    pm.expect(res.estado).to.eql('PENDIENTE');",
  "});",
  "",
  "pm.test('El mapeo JPA conservo los dos items y su porcion', function () {",
  "    pm.expect(res.items).to.have.lengthOf(2);",
  "    pm.expect(res.items[0].cantidadTotal).to.eql(12);",
  "});"
]
```

---

## 4. Error esperado

Status + forma del `ApiError` + un trozo del mensaje que identifique la regla violada.
Las tres cosas: el status solo no distingue un 409 del handler de un 409 accidental.

```json
"exec": [
  "pm.test('Responde 409 CONFLICT', function () {",
  "    pm.response.to.have.status(409);",
  "});",
  "",
  "const err = pm.response.json();",
  "",
  "pm.test('El cuerpo es un ApiError con la forma del contrato', function () {",
  "    pm.expect(err).to.have.all.keys('timestamp', 'status', 'error', 'mensaje', 'detalles');",
  "    pm.expect(err.status).to.eql(409);",
  "    pm.expect(err.error).to.eql('Regla de negocio violada');",
  "});",
  "",
  "pm.test('El mensaje explica la invariante violada', function () {",
  "    pm.expect(err.mensaje).to.include('no ha sido envasado');",
  "});"
]
```

Para errores de validacion (`400`), comprueba ademas **que campos** se reportan:

```json
"exec": [
  "pm.test('Informa los DOS campos invalidos, no solo el primero', function () {",
  "    const detalles = err.detalles.join(' | ');",
  "    pm.expect(err.detalles).to.have.lengthOf(2);",
  "    pm.expect(detalles).to.include('fechaProduccion');",
  "    pm.expect(detalles).to.include('items');",
  "});"
]
```

---

## 5. Comprobacion de que el error no dejo estado corrupto

Va justo detras del request de error. Es barata y valiosa: verifica que el rechazo no
persistio nada a medias.

```json
"exec": [
  "pm.test('Responde 200 OK', function () {",
  "    pm.response.to.have.status(200);",
  "});",
  "",
  "const res = pm.response.json();",
  "",
  "pm.test('Sigue en ENVASADO_PENDIENTE y sin etiqueta', function () {",
  "    pm.expect(res.estado).to.eql('ENVASADO_PENDIENTE');",
  "    pm.expect(res.etiqueta).to.be.null;",
  "});"
]
```

---

## 6. Request de setup

Prepara el estado previo que necesita un escenario, dentro de su propia carpeta. Se marca
en el nombre y asierta lo minimo: que la precondicion se cumplio.

```json
"exec": [
  "pm.test('[setup] El paquete auxiliar queda ENVASADO_PENDIENTE', function () {",
  "    pm.response.to.have.status(201);",
  "    pm.expect(pm.response.json().estado).to.eql('ENVASADO_PENDIENTE');",
  "});",
  "",
  "pm.collectionVariables.set('paqueteErrorId', pm.response.json().id);"
]
```

Un setup que falla debe notarse como setup, no como si fallara el escenario: por eso la
asercion lleva el prefijo `[setup]` en el nombre.

---

## 7. URL con query params

```json
"url": {
  "raw": "{{baseUrl}}/api/v1/paquetes?ordenId={{ordenAId}}",
  "host": ["{{baseUrl}}"],
  "path": ["api", "v1", "paquetes"],
  "query": [{ "key": "ordenId", "value": "{{ordenAId}}" }]
}
```

`raw`, `path` y `query` tienen que decir lo mismo: Postman muestra `raw` en la interfaz
pero Newman usa `path` y `query`. Si se contradicen, veras una URL en pantalla y otra
ejecutandose.
