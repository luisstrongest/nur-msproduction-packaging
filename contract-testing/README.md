# contract-testing — lado consumidor del contrato con BC4

Aquí vive el **consumidor** de las pruebas de contrato: el cliente con el que
`ms-logistic-delivery` (BC5 · Entrega y Logística) consume la API de paquetes de
`ms-production-packaging` (BC4 · Producción y Envasado).

El lado **proveedor** está en el propio microservicio:
`ms-production-packaging/src/test/java/com/nurticenter/produccion/contract`.

```
 BC5 declara lo que necesita  ──►  pacto .json  ──►  BC4 demuestra que lo cumple
   (aquí, contra un mock)          (generado)         (allí, con la app levantada)
```

## Qué hay dentro

```
bc5-logistic-consumer/
├── pom.xml                                  pact-jvm 4.6.21 + pact.rootDir (dónde se publica el pacto)
└── src/
    ├── main/java/com/nurticenter/logistica/bc4/
    │   ├── ClienteProduccionBC4.java        el cliente HTTP real de BC5 — es lo que se prueba
    │   ├── PaqueteListoDto.java             la vista que BC5 tiene del paquete (solo lo que usa)
    │   ├── ApiErrorDto.java                 el cuerpo de error de BC4
    │   └── PaqueteNoEncontradoException.java
    └── test/java/com/nurticenter/logistica/bc4/
        └── PaquetesConsumerPactTest.java    4 interacciones = 4 tests
```

El pacto generado **no se guarda aquí**: se publica directamente en los recursos de
prueba del proveedor, en
`ms-production-packaging/src/test/resources/pacts/ms-logistic-delivery-ms-production-packaging.json`.
Esa copia hace las veces de Pact Broker en este taller.

## Cómo se ejecuta

Lo normal es lanzar el ciclo completo desde la raíz del repositorio (`ddd_clean`):

```powershell
..\tools\validate-contracts.ps1                # consumidor -> pacto -> proveedor
..\tools\validate-contracts.ps1 -BreakCheck    # además, exige que el pacto alterado FALLE
```

Solo este lado (genera el pacto y no lo verifica):

```powershell
cd bc5-logistic-consumer
.\mvnw.cmd -B test
```

Requiere JDK 21. No necesita Docker ni que BC4 esté levantado: las pruebas del consumidor
corren contra el servidor *mock* que Pact levanta.

## Reglas que no se saltan

- El pacto es un **artefacto generado**: no se edita a mano, se regenera.
- Los tests ejercen `ClienteProduccionBC4`, no un `HttpClient` escrito dentro del test.
- Al pacto solo van los campos que BC5 **usa**.
- Si la verificación del proveedor falla, no se relaja la expectativa: o se arregla BC4 o
  se renegocia el contrato.

Guía completa del taller: [`../docs/pruebas-contrato.md`](../docs/pruebas-contrato.md).
Convenciones para escribir interacciones: skill `pact-contract-testing`.
