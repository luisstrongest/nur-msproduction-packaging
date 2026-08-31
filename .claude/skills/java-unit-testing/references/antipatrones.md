# Antipatrones — motivos de rechazo

Cada apartado es un motivo suficiente para rechazar un test en revision. Los ejemplos
"mal" son fallos reales tipicos de tests generados por IA: **compilan, pasan en verde y
no comprueban nada**.

---

## 1. Test sin asercion

```java
// MAL: si generarOrden explota el test falla, pero no se comprueba ningun resultado
@Test
void generarOrden() {
    servicio.generarOrden(comando());
}
```

```java
// BIEN
@Test
void generarOrdenGuardaYPublicaElEvento() {
    given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

    OrdenDeProduccion orden = servicio.generarOrden(comando());

    verify(ordenRepository).guardar(orden);
    verify(eventPublisher).publicar(eventosCaptor.capture());
    assertThat(eventosCaptor.getValue()).first().isInstanceOf(OrdenGenerada.class);
}
```

## 2. Asercion tautologica

```java
// MAL: comprueba Mockito, no el codigo
given(repo.buscarPorId(id)).willReturn(Optional.of(orden));
assertThat(servicio.buscarPorId("orden-1")).isEqualTo(orden);   // trivial si el metodo solo delega
```

Si el metodo **solo delega**, la asercion util es que delego con el argumento correcto
(`verify(repo).buscarPorId(OrdenId.de("orden-1"))`) y que traduce el `Optional.empty()`
a la excepcion esperada. Peor todavia: `assertThat(x).isEqualTo(x)`.

## 3. Mockear la clase bajo prueba

```java
// MAL: el SUT es un mock; no se ejecuta ni una linea de produccion
@Mock private OrdenDeProduccion orden;
given(orden.estado()).willReturn(EstadoOrden.COMPLETADA);
assertThat(orden.estado()).isEqualTo(EstadoOrden.COMPLETADA);
```

El agregado, los VOs, los records y los enums **siempre se construyen de verdad**.

## 4. Mock que nadie usa / stub innecesario

Mockito en modo estricto ya falla con `UnnecessaryStubbingException`. Si aparece,
no se silencia con `lenient()`: se borra el stub porque sobra.

```java
// MAL
@Test
void x() {
    lenient().when(repo.existePorFecha(any())).thenReturn(false);   // tapando el sintoma
    ...
}
```

## 5. `verify` sin comportamiento verificado

```java
// MAL: verifica que se llamo, pero con cualquier cosa
verify(ordenRepository).guardar(any());
```

```java
// BIEN: con QUE se llamo
verify(ordenRepository).guardar(ordenCaptor.capture());
assertThat(ordenCaptor.getValue().estado()).isEqualTo(EstadoOrden.PENDIENTE);
```

## 6. Excepcion capturada en falso

```java
// MAL: si NO lanza la excepcion, el test pasa igual
try {
    orden.completar();
    // sin fail()
} catch (ItemsPendientesException e) {
    assertThat(e).isNotNull();
}
```

```java
// BIEN
assertThatThrownBy(orden::completar).isInstanceOf(ItemsPendientesException.class);
```

Tampoco vale `.isInstanceOf(RuntimeException.class)` cuando existe una excepcion de
dominio concreta: no distingue un fallo esperado de un `NullPointerException` accidental.

## 7. Test acoplado al reloj

```java
// MAL: falla de forma intermitente
assertThat(etiqueta.fechaGeneracion()).isEqualTo(LocalDateTime.now());
```

```java
// BIEN
LocalDateTime antes = LocalDateTime.now();
Etiqueta etiqueta = Etiqueta.crear("Ana Perez", DIRECCION, CODIGO);
assertThat(etiqueta.fechaGeneracion()).isAfterOrEqualTo(antes);
```

## 8. Estado compartido entre tests

Campos `static` mutables, orden de ejecucion asumido, `@TestMethodOrder` para que un
test dependa del anterior. Cada test se construye su propio escenario en `@BeforeEach`
o en un helper.

## 9. Infraestructura disfrazada de test unitario

`@SpringBootTest`, `@DataJpaTest`, `MockMvc`, H2, Testcontainers, `RestTemplate`,
ficheros o `Thread.sleep` dentro de un `*Test.java`. Eso es una prueba de integracion:
va en `*IT.java` y hoy esta fuera de alcance.

## 10. El test que no se ejecuta

- Archivo que no acaba en `Test.java` -> surefire no lo corre.
- Metodo `@Test` `private` o `static`.
- Clase `@Nested` sin ser interna no estatica.
- `@Disabled` sin justificacion escrita.

Un test que no corre es peor que no tener test: da cobertura falsa de seguridad.
Comprueba siempre el contador `Tests run:` despues de añadir tests.

## 11. Un test que prueba tres cosas

```java
// MAL
@Test
void cicloCompletoYValidacionesYEventos() { ... 40 lineas ... }
```

Si el nombre necesita un "y", son varios tests. Cuando falle uno, el nombre debe decir
exactamente que se rompio sin abrir el archivo.

## 12. Cobertura sin comprobacion

Recorrer una linea no es probarla. Sintoma tipico: el test llama al metodo, la cobertura
sube, pero si inviertes la condicion en `src/main` **ningun test falla**. Esto lo detecta
PIT (mutantes supervivientes) y es motivo directo de rechazo.
