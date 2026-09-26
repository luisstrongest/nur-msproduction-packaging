---
name: java-unit-testing
description: Convenciones obligatorias para escribir pruebas unitarias Java (JUnit 5 + AssertJ + Mockito) en los microservicios DDD / Clean Architecture de este proyecto. Cargalo antes de crear o modificar cualquier archivo bajo src/test, y cuando haya que decidir que doble de prueba usar (mock, fake, spy) o por que un test no debe levantar Spring.
---

# Pruebas unitarias en este proyecto

Estas son las reglas del repositorio, no preferencias generales. El codigo de test que
escribas debe ser indistinguible del que ya existe en
`ms-production-packaging/src/test/`.

## 1. Que cuenta como prueba unitaria aqui

Una unidad de comportamiento aislada de sus colaboradores externos: **sin Spring, sin
base de datos, sin red, sin ficheros, sin reloj real**. Todo lo externo se sustituye por
un doble.

Prohibido en `*Test.java`:

- `@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest`, `MockMvc`, `@Autowired`
- `Thread.sleep`, ficheros temporales, puertos, Testcontainers
- cualquier cosa que tarde mas de ~100 ms por test

Todo eso pertenece a `*IT.java` (pruebas de integracion), que estan **excluidas** de
`mvn test` por configuracion de surefire. No las escribas salvo peticion explicita; si
te la piden, sus reglas estan en el skill `spring-integration-testing`.

## 2. La regla del doble por capa

Es la decision mas importante y la marca la arquitectura hexagonal:

| Capa | Doble | Por que |
| :--- | :---- | :------ |
| `domain/**` | **ninguno** — objetos reales | El dominio es POJO puro y no tiene dependencias. Si necesitas un mock aqui, el diseño esta mal o estas probando la capa equivocada. |
| `application/**` | **`@Mock`** de los puertos: `*Repository`, `EventPublisher` | El servicio de aplicacion solo depende de interfaces. Se verifica la ORQUESTACION (cargar -> delegar -> guardar -> publicar), no las reglas de negocio. |
| `infrastructure/persistence/**` | **`@Mock`** del repositorio Spring Data | Se prueba el mapeo dominio <-> entidad JPA, no la base de datos. |
| `infrastructure/event/**` | **`@Mock`** de `ApplicationEventPublisher` | Se prueba que el adaptador publique lo que recibe. |
| `infrastructure/web/**` | **`@Mock`** del servicio de aplicacion, controller instanciado **a mano** | Se prueba Request -> Command y agregado -> Response. El enrutamiento HTTP y el JSON son cosa de las pruebas de integracion. |

El agregado de dominio **siempre es real**, incluso en los tests de aplicacion: se
construye con `reconstituir(...)` para partir del estado que haga falta.

## 3. Mock, fake o spy

- **Mock** (Mockito): verificas la **interaccion**. *"se llamo a `guardar()` una vez y a
  `publicar()` con un `OrdenGenerada`"*. Es el caso por defecto en aplicacion e
  infraestructura.
- **Fake** (implementacion real simplificada, p. ej. `InMemoryOrdenDeProduccionRepository`):
  verificas el **estado** despues de encadenar varios casos de uso. Util para flujos
  completos; no lo uses para un unico caso de uso.
- **Spy** hecho a mano: cuando solo necesitas acumular lo que paso (ver
  `OrdenApplicationServiceConDoblesEnMemoriaTest`).

Nunca mockees: la clase bajo prueba, records, enums, `String`, `LocalDate`, VOs del
dominio. Se construyen de verdad.

## 4. Estructura de un test

- Nombre del archivo: `<ClaseBajoPrueba>Test.java`, mismo paquete que la clase.
- Nombres de metodo **en español**, describiendo el comportamiento:
  `noSePuedeCompletarConItemsPendientes()`, no `test1()` ni `shouldWork()`.
- `@DisplayName` en la clase y en los tests cuyo nombre no se lee solo.
- `@Nested` para agrupar por escenario cuando la clase supera ~6 tests.
- Cuerpo en tres bloques (Arrange / Act / Assert) separados por una linea en blanco.
  Sin comentarios `// arrange`.
- `@ParameterizedTest` con `@ValueSource` / `@CsvSource` cuando el mismo comportamiento
  se verifica con varios valores. No copies-pegues el mismo test cambiando un numero.
- Helpers privados al final de la clase para construir datos de prueba.

## 5. Aserciones

Siempre **AssertJ**, nunca `org.junit.jupiter.api.Assertions`:

```java
assertThat(orden.estado()).isEqualTo(EstadoOrden.COMPLETADA);
assertThatThrownBy(orden::completar)
        .isInstanceOf(ItemsPendientesException.class)
        .hasMessageContaining("sin preparar");
```

- Excepciones: comprueba el **tipo concreto del dominio**, no `RuntimeException`.
- Fechas generadas con `now()`: `isAfterOrEqualTo(antes)`, nunca igualdad exacta.
- Eventos: captura con `@Captor` y comprueba tipo **y contenido**.
- Todo test termina con al menos un `assertThat` o un `verify`.

## 6. Que hay que cubrir

Para cada clase, como minimo:

1. el camino feliz;
2. **cada invariante** que la clase protege (una excepcion de dominio = un test);
3. **cada rama** de un `if` / `switch` / operador ternario;
4. los limites: nulo, vacio, cero, negativo, estado terminal;
5. si emite eventos: que se emiten, con que datos, y que `pullEventos()` vacia la bandeja;
6. si reconstituye desde persistencia: que **no** emite eventos.

## 7. Comandos

Desde la raiz del microservicio (`ms-production-packaging/`):

```bash
# JDK 21 obligatorio: si JAVA_HOME apunta a otra version el build falla
JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw -B test           # toda la suite + cobertura
JAVA_HOME="C:/Program Files/Java/jdk-21" ./mvnw -B -Dtest=PorcionTest test   # una clase
```

Gate completo (compila + suite + umbral de cobertura), desde el microservicio:

```powershell
..\tools\validate-tests.ps1
```

## Referencias

- `references/plantillas.md` — esqueletos copiables para cada capa.
- `references/antipatrones.md` — lo que hace que un test sea rechazado.
- Ejemplos reales que ya pasan en verde:
  `ms-production-packaging/src/test/java/com/nurticenter/produccion/`
- Estrategia y cobertura actual: `docs/pruebas-unitarias.md`.
