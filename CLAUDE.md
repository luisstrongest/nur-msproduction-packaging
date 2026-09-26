# Reglas del proyecto — ms-production-packaging (BC4)

Reglas que Claude Code carga en cada sesión de este repositorio. Son las que se usaron para
**generar y validar** la capa de testing del Proyecto Final. El detalle de cada nivel está en
sus skills (`.claude/skills/`); aquí va lo que aplica siempre.

## Contexto

- Microservicio Spring Boot 3.4 / Java 21, DDD + arquitectura hexagonal:
  `domain` (POJO puro) → `application` (casos de uso, depende de puertos) →
  `infrastructure` (JPA, web, eventos).
- Código en `ms-production-packaging/`. Consumidor de contrato (BC5) en
  `contract-testing/bc5-logistic-consumer/`.
- JDK 21 obligatorio (`java -version`). En Windows se usa `.\mvnw.cmd`; en Git Bash/CI, `./mvnw`.
- Nombres de tests, métodos y mensajes **en español**, como el resto del código.

## Niveles de prueba: dónde va cada cosa

| Nivel | Archivos | Skill que se carga antes de escribir | Gate |
| :---- | :------- | :----------------------------------- | :--- |
| Unitario | `src/test/**/*Test.java` | `java-unit-testing` (+ `test-coverage-jacoco`) | `tools\validate-tests.ps1` |
| Integración (Java) | `src/test/**/integration/Flujo*IT.java` | `spring-integration-testing` | `mvnw -Pintegration verify` |
| Integración (Postman) | `postman/bc4-integracion.postman_collection.json` | `postman-integration-testing` + `bc4-api-contract` | `tools\validate-integration.ps1 -Mode local -Twice` |
| Contrato (Pact) | `contract-testing/**`, `src/test/**/contract/*ContractIT.java` | `pact-contract-testing` + `bc4-api-contract` | `tools\validate-contracts.ps1 -BreakCheck` |

Todo junto, con los reportes entregables en `docs/reportes/`: `tools\run-all-tests.ps1`.

## Reglas que no se negocian

1. **Un test unitario no levanta nada.** Nada de Spring, BD, red ni reloj real en
   `*Test.java`. El dominio se prueba sin dobles; aplicación e infraestructura con
   `@Mock` de sus puertos. Lo que levanta Spring es `*IT.java`.
2. **Un test de integración no mockea nada.** Ni `@MockBean` en los `Flujo*IT`, ni stubs en
   Postman: la app real contra una BD real (H2 con el perfil `it`, o PostgreSQL).
3. **Las pruebas de integración se agrupan por flujo de negocio**, no por endpoint: Flujo A
   (producción en cocina), Flujo B (envasado y entrega a BC5), Flujo C (rechazos). Cada
   flujo es autocontenido y usa su propio rango de fechas (una orden por fecha).
4. **Nunca se toca `src/main` para que un test pase.** Si el comportamiento no es el
   esperado, se reporta como bug. Tampoco se cambia una aserción para que coincida con lo
   observado (esperar 409 y "arreglarlo" a 500).
5. **Nunca se bajan los umbrales** de JaCoCo (85 % líneas / 80 % ramas) ni de PIT (80 %)
   para que el build pase: se escribe el test que falta.
6. **El pacto no se edita a mano** (`src/test/resources/pacts/**` está en `deny`): lo genera
   el consumidor.
7. **Ningún test generado se da por bueno sin su verificación objetiva**, que es distinta en
   cada nivel porque cada nivel se falsifica de forma distinta:
   - unitarias → *mutation testing* (PIT): la cobertura alta se consigue sin asertar nada;
   - integración Postman → **dos vueltas** contra la misma BD: la colección que solo pasa
     una vez es una demo;
   - integración Java → **romper el código a propósito** (p. ej. comentar la publicación de
     un evento) y ver el rojo; restaurar con `git checkout`, no con `Set-Content` (añade BOM);
   - contrato → `-BreakCheck`: el pacto alterado tiene que hacer fallar la verificación.

## Flujo de generación con IA

Cada nivel tiene un comando que encadena **escritor → revisor → gate**:

| Comando | Escritor | Revisor (sin `Write`/`Edit`) |
| :------ | :------- | :--------------------------- |
| `/test-unit <clase>` | `test-writer` | `test-reviewer` (skill `test-quality-review`) |
| `/test-integration <escenario>` | `integration-test-writer` | `integration-test-reviewer` (skill `integration-test-review`) |
| `/test-contract <interacción>` | `pact-writer` | `pact-reviewer` (skill `pact-contract-review`) |

El revisor dictamina (`APROBADO` / `APROBADO CON OBSERVACIONES` / `RECHAZADO`) y corrige el
escritor. El gate decide: exit 0 o no se acepta.

## Guardarraíles (`.claude/settings.json`)

- Se piden confirmación: editar `src/main/**`, cualquier `pom.xml`,
  `docker-compose.integration.yml`.
- Prohibido: editar el pacto, `git push`, `git reset --hard`, `rm -rf`, `mvn deploy`,
  `docker system prune`, leer `.env`.

## Documentación

- `docs/pruebas-unitarias.md`, `docs/pruebas-integracion.md`, `docs/pruebas-contrato.md`
- `docs/entorno-pruebas-ai.md` — el entorno de generación asistida por IA
- `docs/proyecto-final-testing.md` — la entrega del Proyecto Final: requisito → evidencia
