# Proyecto Final — Capa de testing de `ms-production-packaging` (BC4)

> Entrega individual — Luis Hurtado. Microservicio: **BC4 · Producción y Envasado**
> (Spring Boot 3.4, Java 21, DDD + arquitectura hexagonal).

Este documento es el índice de la entrega: qué pide el enunciado, dónde está la evidencia
y cómo se reproduce.

## 1. Requisito → evidencia

| Requisito del enunciado | Evidencia | Resultado |
| :---------------------- | :-------- | :-------- |
| Capa de **unit tests** | `ms-production-packaging/src/test/java/**/*Test.java` — 18 clases, las tres capas | **163 tests**, 0 fallos |
| **Reporte de code coverage**, mínimo 80 % | [`reportes/coverage-unitarias/index.html`](reportes/coverage-unitarias/index.html) (JaCoCo) | **98.7 % líneas / 99.1 % ramas**; el build falla por debajo de 85 % / 80 % |
| Capa de **integration tests** | Postman: `postman/bc4-integracion.postman_collection.json` · Java: `src/test/java/**/integration/Flujo*IT.java` | Postman **32 requests / 92 aserciones**, 2 vueltas en verde con H2 **y con PostgreSQL real en Docker** · Java **4 tests** |
| Al menos **dos flujos completos**, agrupados por flujo | Flujo A *Producción en cocina* y Flujo B *Envasado y entrega a BC5* (+ Flujo C *Rechazos*) — una carpeta Postman y una clase Java por flujo | ver §3 |
| Reporte de integración | [`reportes/integracion-postman/index.html`](reportes/integracion-postman/index.html) (Newman htmlextra) · [`reportes/integracion-java/`](reportes/integracion-java) (failsafe) | |
| (Opcional) Contract testing con Pact | `contract-testing/bc5-logistic-consumer` + `PaquetesProviderContractIT` | 4/4 interacciones verificadas, falsabilidad comprobada |
| Tests generados → **incluir skills** | [`.claude/skills/`](../.claude/skills) — 9 skills, uno o más por nivel | ver §5 |
| Tests generados → **incluir reglas de proyecto** | [`CLAUDE.md`](../CLAUDE.md) + [`.claude/settings.json`](../.claude/settings.json) + [`.claude/agents/`](../.claude/agents) | ver §5 |

## 2. Cómo se ejecuta

Requisitos: **JDK 21** y **Node.js** (para Newman vía `npx`). Docker es opcional.

```powershell
# desde ddd_clean/ — todos los niveles, un solo comando, reportes en docs/reportes/
.\tools\run-all-tests.ps1
```

```
Nivel                              Resultado Segundos
-----                              --------- --------
1. Unitarias + cobertura           OK             175
2. Integracion Java (MockMvc + H2) OK              42
3. Integracion Postman (Newman)    OK              56
4. Contrato (Pact)                 OK             134

OK: la capa de testing completa esta en verde.
```

Por nivel, si se quiere mostrar uno solo:

| Nivel | Comando | Reporte |
| :---- | :------ | :------ |
| Unitarias + coverage | `cd ms-production-packaging; .\mvnw.cmd verify` | `target/site/jacoco/index.html` |
| Mutation testing | `.\tools\validate-tests.ps1 -Mutation` | `docs/reportes/mutation-pit/index.html` (con `run-all-tests.ps1 -Mutation`) |
| Integración Java | `cd ms-production-packaging; .\mvnw.cmd -Pintegration verify` | `target/failsafe-reports/` |
| Integración Postman | `.\tools\validate-integration.ps1 -Mode local -Twice -Report` | `docs/reportes/integracion-postman/index.html` |
| Contrato | `.\tools\validate-contracts.ps1 -BreakCheck` | el pacto en `src/test/resources/pacts/` + resumen en consola |

En Git Bash / Linux / CI: los mismos scripts con extensión `.sh` (`./tools/run-all-tests.sh`).

## 3. Unit tests

Sin Spring, sin base de datos, sin red. Un doble distinto según la capa:

| Capa | Doble | Cobertura de líneas |
| :--- | :---- | ------------------: |
| Dominio (agregados, VOs, eventos) | ninguno: objetos reales | 99.2 % |
| Aplicación (casos de uso) | `@Mock` de los puertos + un *fake* en memoria para el flujo completo | 100 % |
| Infraestructura (JPA, web, eventos) | `@Mock` de Spring Data, del servicio y del `ApplicationEventPublisher` | 97.8 % |

Las 8 líneas sin cubrir son constructores sin argumentos que existen solo para Hibernate
(`OrdenJpaEntity`, `PaqueteJpaEntity`, `ItemOrdenEmbeddable`) y el constructor con `cause`
de `DomainException`, que ninguna excepción del dominio usa; no tienen lógica.

**La cobertura no basta**: una línea "cubierta" solo se ejecutó, no se comprobó. Por eso
también se corre **mutation testing** con PIT sobre dominio y aplicación: 156 mutantes
generados, **154 muertos (99 %)**. Los 2 supervivientes son `hashCode()` de `Etiqueta` e
`ItemOrden` devolviendo 0, un mutante equivalente para el comportamiento del dominio.

## 4. Integration tests, agrupados por flujo

| Flujo | Postman (carpeta) | Java (clase) | Recorrido |
| :---- | :---------------- | :----------- | :-------- |
| **A — Producción en cocina** | `01 - Flujo A` · 10 requests | `FlujoProduccionIT` | generar orden → consultar por id y por fecha → iniciar preparación → preparar cada ítem → completar → releer COMPLETADA |
| **B — Envasado y entrega a BC5** | `02 - Flujo B` · 11 requests | `FlujoEnvasadoIT` | orden en preparación → crear paquete → envasar → etiquetar → LISTO → Logística lo ve en `GET /paquetes` → completar la orden con el paquete |
| **C — Rechazos** | `03 - Flujo C` · 11 requests | un test de rechazo por clase | 400 de validación, 404, 409 de regla de negocio, y que el error no deja estado a medias |

Por qué en **dos formas**:

- **Postman/Newman** prueba la API como la ve un cliente externo, contra la app levantada
  de verdad. Se ejecuta **dos veces contra la misma base de datos**: una colección que solo
  pasa la primera vez es una demo, no una prueba.
- **Java (MockMvc + `@SpringBootTest`, perfil `it` con H2)** recorre los mismos flujos sin
  mocks y además ve lo que no sale por HTTP: que se publican los **eventos de dominio**
  (`OrdenGenerada`, `OrdenCompletada`, `PaqueteListo`) exactamente una vez y con los datos
  correctos.

Verificación de que los tests verifican: se borró a propósito la publicación del evento en
`marcarListo()` y `FlujoEnvasadoIT` falló; restaurada la línea, volvió a verde.

## 5. Generación con IA: skills y reglas incluidas

Los tests se generaron y revisaron con Claude Code. Todo lo que se usó está versionado:

| Pieza | Dónde | Para qué |
| :---- | :---- | :------- |
| Reglas de proyecto | [`CLAUDE.md`](../CLAUDE.md) | Qué va en cada nivel, qué no se negocia (no tocar `src/main` para que pase un test, no bajar umbrales, verificación objetiva por nivel) |
| Guardarraíles | [`.claude/settings.json`](../.claude/settings.json) | Qué puede hacer el agente sin preguntar, qué pregunta y qué tiene prohibido |
| Skills de unitarias | `java-unit-testing`, `test-coverage-jacoco`, `test-quality-review` | Convenciones, doble por capa, lectura de JaCoCo/PIT, lista de revisión |
| Skills de integración | `bc4-api-contract`, `postman-integration-testing`, `spring-integration-testing`, `integration-test-review` | Contrato real de la API, convenciones de la colección por flujo, de los `Flujo*IT`, y lista de revisión |
| Skills de contrato | `pact-contract-testing`, `pact-contract-review` | Convenciones de Pact y revisión adversarial |
| Subagentes | [`.claude/agents/`](../.claude/agents) | Un **escritor** y un **revisor** (sin permiso de escritura) por nivel |
| Comandos | `/test-unit`, `/test-integration`, `/test-contract` | Encadenan escritor → revisor → gate |
| Gates | `tools/validate-*.ps1` / `.sh`, `tools/run-all-tests.*` | El exit code decide si lo generado se acepta |

Cada nivel tiene una verificación que el resumen en verde no muestra y que por eso no se
puede falsificar: *mutation testing* (unitarias), **dos vueltas** (Postman), **romper el
código a propósito** (Java) y **pacto alterado que debe fallar** (Pact).

## 6. Correcciones hechas durante esta entrega

Al integrar todo en un solo comando aparecieron tres defectos de los gates, que antes no se
veían porque cada nivel se ejecutaba por separado:

1. **La cobertura de las unitarias se inflaba** (98.7 % → 99.7 %) si antes se corrían los
   tests de integración o de contrato. Los perfiles de failsafe tenían `<argLine></argLine>`
   para no enganchar JaCoCo, pero Maven trata el elemento vacío como "sin configurar" y
   volvía a inyectar el agente. Ahora llevan un valor explícito. La cifra que se reporta,
   98.7 %, es la de las unitarias solas.
2. **`validate-tests.ps1 -Mutation` fallaba en Windows PowerShell 5.1** con el mensaje
   engañoso "demasiados mutantes sobrevivieron": `-Djacoco.skip=true` sin comillas se partía
   en dos argumentos y Maven ni siquiera arrancaba PIT. Ahora va entre comillas y el gate
   distingue "PIT no pudo ejecutarse" de "sobrevivieron mutantes".
3. **`validate-integration.ps1 -Mode local` dejaba la app viva** en el puerto 8082: `java`
   es el *shim* de Oracle, que lanza la JVM como proceso hijo, y solo se mataba al shim.
   Ahora se baja el árbol completo con `taskkill /T`.
