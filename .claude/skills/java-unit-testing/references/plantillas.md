# Plantillas por capa

Esqueletos copiables. Los ejemplos estan extraidos de tests reales de
`ms-production-packaging` que pasan en verde.

---

## 1. Dominio — sin dobles

```java
package com.nurticenter.produccion.domain.model.orden;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrdenDeProduccion (Aggregate Root)")
class OrdenDeProduccionTest {

    @Nested
    @DisplayName("Ciclo de vida PENDIENTE -> EN_PREPARACION -> COMPLETADA")
    class CicloDeVida {

        @Test
        void noSePuedeCompletarConItemsPendientes() {
            OrdenDeProduccion orden = ordenCon("receta-1", "receta-2");
            orden.iniciarPreparacion();
            orden.marcarItemPreparado("receta-1");

            assertThatThrownBy(orden::completar).isInstanceOf(ItemsPendientesException.class);
        }
    }

    // -- helpers de construccion al final de la clase --
    private OrdenDeProduccion ordenCon(String... recetaIds) { ... }
}
```

Comprobacion de eventos del agregado:

```java
@Test
void crearRegistraEventoOrdenGenerada() {
    OrdenDeProduccion orden = OrdenDeProduccion.crear(LocalDate.now(), List.of(itemDe("receta-1")));

    List<Object> eventos = orden.pullEventos();

    assertThat(eventos).hasSize(1);
    assertThat(eventos.get(0)).isInstanceOf(OrdenGenerada.class);
    assertThat(orden.pullEventos()).isEmpty();   // pullEventos vacia la bandeja
}
```

Value Object con `@ParameterizedTest`:

```java
@ParameterizedTest(name = "cantidad = {0} -> rechazada")
@ValueSource(doubles = {0.0, -1.0, -250.5})
void rechazaCantidadNoPositiva(double cantidad) {
    assertThatThrownBy(() -> new Porcion(cantidad, UnidadMedida.GRAMOS, "Sopa"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positiva");
}
```

---

## 2. Aplicacion — mocks de los puertos

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("OrdenApplicationService (unit test con mocks)")
class OrdenApplicationServiceTest {

    @Mock  private OrdenDeProduccionRepository ordenRepository;
    @Mock  private EventPublisher              eventPublisher;
    @InjectMocks private OrdenApplicationService servicio;

    @Captor private ArgumentCaptor<OrdenDeProduccion> ordenCaptor;
    @Captor private ArgumentCaptor<List<Object>>      eventosCaptor;

    @Test
    @DisplayName("guarda la orden y publica OrdenGenerada")
    void guardaYPublica() {
        given(ordenRepository.existePorFecha(FECHA)).willReturn(false);

        OrdenDeProduccion resultado = servicio.generarOrden(comandoCon("GRAMOS"));

        verify(ordenRepository).guardar(ordenCaptor.capture());
        verify(eventPublisher).publicar(eventosCaptor.capture());
        assertThat(ordenCaptor.getValue()).isSameAs(resultado);
        assertThat(eventosCaptor.getValue()).hasSize(1).first().isInstanceOf(OrdenGenerada.class);
    }

    /** El agregado es REAL: se parte del estado necesario con reconstituir(). */
    private OrdenDeProduccion ordenExistente(EstadoOrden estado, EstadoItem estadoItem) {
        ItemOrden item = ItemOrden.reconstituir("REC-001", "Pure", 12,
                new Porcion(250, UnidadMedida.GRAMOS, "Porcion estandar"), estadoItem);
        return OrdenDeProduccion.reconstituir(
                OrdenId.de(ORDEN_ID), FECHA, estado, List.of(item), List.of());
    }
}
```

**Test obligatorio en cada caso de uso de escritura** — que un fallo del dominio aborte
todo el efecto lateral:

```java
@Test
@DisplayName("Si el dominio rechaza la operacion, no se guarda ni se publica nada")
void unaViolacionDeInvarianteAbortaLaTransaccion() {
    given(ordenRepository.buscarPorId(OrdenId.de(ORDEN_ID)))
            .willReturn(Optional.of(ordenExistente(EstadoOrden.EN_PREPARACION, EstadoItem.PENDIENTE)));

    assertThatThrownBy(() -> servicio.completarOrden(ORDEN_ID))
            .isInstanceOf(ItemsPendientesException.class);

    verify(ordenRepository, never()).guardar(any());
    verifyNoInteractions(eventPublisher);
}
```

Cuando un caso de uso toca **dos agregados**, verifica el orden con `InOrder`:

```java
InOrder enOrden = inOrder(paqueteRepository, ordenRepository);
enOrden.verify(paqueteRepository).guardar(paquete);
enOrden.verify(ordenRepository).guardar(orden);
```

---

## 3. Infraestructura — adaptador de persistencia

```java
@ExtendWith(MockitoExtension.class)
class OrdenPersistenceAdapterTest {

    @Mock  private SpringDataOrdenRepository jpa;
    @InjectMocks private OrdenPersistenceAdapter adaptador;
    @Captor private ArgumentCaptor<OrdenJpaEntity> entidadCaptor;

    @Test
    @DisplayName("Ida y vuelta dominio -> entidad -> dominio sin perdida de datos")
    void idaYVueltaSinPerdidaDeDatos() {
        OrdenDeProduccion original = ordenDeDominio();
        adaptador.guardar(original);
        verify(jpa).save(entidadCaptor.capture());
        given(jpa.findById("orden-1")).willReturn(Optional.of(entidadCaptor.getValue()));

        OrdenDeProduccion recuperada = adaptador.buscarPorId(OrdenId.de("orden-1")).orElseThrow();

        assertThat(recuperada.estado()).isEqualTo(original.estado());
        assertThat(recuperada.items()).isEqualTo(original.items());
        assertThat(recuperada.pullEventos()).isEmpty();   // rehidratar no es un hecho nuevo
    }
}
```

---

## 4. Infraestructura — controller (sin MockMvc)

```java
@ExtendWith(MockitoExtension.class)
class OrdenControllerTest {

    @Mock  private OrdenApplicationService servicio;
    @InjectMocks private OrdenController controller;
    @Captor private ArgumentCaptor<GenerarOrdenCommand> comandoCaptor;

    @Test
    @DisplayName("POST /ordenes traduce el request a comando y devuelve la orden creada")
    void generarTraduceElRequestYDevuelveLaRespuesta() {
        given(servicio.generarOrden(any())).willReturn(orden(EstadoOrden.PENDIENTE));

        OrdenResponse respuesta = controller.generar(request());

        verify(servicio).generarOrden(comandoCaptor.capture());
        assertThat(comandoCaptor.getValue().fechaProduccion()).isEqualTo(FECHA);
        assertThat(respuesta.estado()).isEqualTo("PENDIENTE");
    }
}
```

Los `@InitBinder` tambien se prueban sin Spring:

```java
WebDataBinder binder = new WebDataBinder(null);
controller.initBinder(binder);
PropertyEditor editor = binder.findCustomEditor(EstadoOrden.class, null);

editor.setAsText("  completada ");

assertThat(editor.getValue()).isEqualTo(EstadoOrden.COMPLETADA);
```

---

## 5. Doble en memoria (fake) para flujos completos

```java
/** Doble de prueba del puerto EventPublisher: acumula lo publicado. */
private static class EventPublisherSpy implements EventPublisher {
    private final List<Object> publicados = new ArrayList<>();
    @Override public void publicar(List<Object> eventos) { publicados.addAll(eventos); }
    List<Object> publicados() { return publicados; }
}

@BeforeEach
void setUp() {
    repositorio = new InMemoryOrdenDeProduccionRepository();
    publisher   = new EventPublisherSpy();
    servicio    = new OrdenApplicationService(repositorio, publisher);
}
```

Se usa para encadenar casos de uso y comprobar el **estado** final, no la interaccion.
