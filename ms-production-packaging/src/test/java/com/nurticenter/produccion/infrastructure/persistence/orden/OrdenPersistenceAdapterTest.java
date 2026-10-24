package com.nurticenter.produccion.infrastructure.persistence.orden;

import com.nurticenter.produccion.domain.model.orden.EstadoItem;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.model.orden.OrigenCalendario;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * TEST UNITARIO CON MOCKS - OrdenPersistenceAdapter.
 *
 * Se mockea Spring Data JPA (SpringDataOrdenRepository) para probar el
 * ADAPTADOR y su mapeo dominio <-> entidad JPA sin arrancar Hibernate ni una
 * base de datos. Aqui no se testea la BD: se testea la traduccion.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrdenPersistenceAdapter (unit test con mocks)")
class OrdenPersistenceAdapterTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 15);

    @Mock
    private SpringDataOrdenRepository jpa;

    @InjectMocks
    private OrdenPersistenceAdapter adaptador;

    @Captor
    private ArgumentCaptor<OrdenJpaEntity> entidadCaptor;

    private OrdenDeProduccion ordenDeDominio() {
        ItemOrden item = ItemOrden.reconstituir(
                "REC-001", "Pure de zapallo", 12,
                new Porcion(250, UnidadMedida.GRAMOS, "Porcion estandar"), EstadoItem.PREPARADO);
        return OrdenDeProduccion.reconstituir(
                OrdenId.de("orden-1"), FECHA, EstadoOrden.EN_PREPARACION,
                List.of(item), List.of("paquete-1"));
    }

    private OrdenJpaEntity entidadJpa() {
        ItemOrdenEmbeddable item = new ItemOrdenEmbeddable(
                "REC-001", "Pure de zapallo", 12, EstadoItem.PREPARADO,
                250, UnidadMedida.GRAMOS, "Porcion estandar");
        return new OrdenJpaEntity("orden-1", FECHA, EstadoOrden.EN_PREPARACION,
                List.of(item), List.of("paquete-1"));
    }

    @Test
    @DisplayName("guardar() mapea el agregado a la entidad JPA y delega en Spring Data")
    void guardarMapeaElAgregadoAEntidad() {
        adaptador.guardar(ordenDeDominio());

        verify(jpa).save(entidadCaptor.capture());
        OrdenJpaEntity entidad = entidadCaptor.getValue();

        assertThat(entidad.getId()).isEqualTo("orden-1");
        assertThat(entidad.getFechaProduccion()).isEqualTo(FECHA);
        assertThat(entidad.getEstado()).isEqualTo(EstadoOrden.EN_PREPARACION);
        assertThat(entidad.getPaqueteIds()).containsExactly("paquete-1");
        assertThat(entidad.getItems()).hasSize(1);

        ItemOrdenEmbeddable item = entidad.getItems().get(0);
        assertThat(item.getRecetaId()).isEqualTo("REC-001");
        assertThat(item.getNombreReceta()).isEqualTo("Pure de zapallo");
        assertThat(item.getCantidadTotal()).isEqualTo(12);
        assertThat(item.getEstado()).isEqualTo(EstadoItem.PREPARADO);
        assertThat(item.getPorcionCantidad()).isEqualTo(250);
        assertThat(item.getPorcionUnidad()).isEqualTo(UnidadMedida.GRAMOS);
        assertThat(item.getPorcionDescripcion()).isEqualTo("Porcion estandar");
    }

    @Test
    @DisplayName("buscarPorId() reconstituye el agregado desde la entidad, sin eventos")
    void buscarPorIdReconstituyeElAgregado() {
        given(jpa.findById("orden-1")).willReturn(Optional.of(entidadJpa()));

        Optional<OrdenDeProduccion> resultado = adaptador.buscarPorId(OrdenId.de("orden-1"));

        assertThat(resultado).isPresent();
        OrdenDeProduccion orden = resultado.get();
        assertThat(orden.id().valor()).isEqualTo("orden-1");
        assertThat(orden.estado()).isEqualTo(EstadoOrden.EN_PREPARACION);
        assertThat(orden.items().get(0).estado()).isEqualTo(EstadoItem.PREPARADO);
        assertThat(orden.paqueteIds()).containsExactly("paquete-1");
        assertThat(orden.pullEventos()).isEmpty();   // rehidratar no es un hecho nuevo
    }

    @Test
    void buscarPorIdDevuelveVacioSiNoExiste() {
        given(jpa.findById("no-existe")).willReturn(Optional.empty());

        assertThat(adaptador.buscarPorId(OrdenId.de("no-existe"))).isEmpty();
    }

    @Test
    void buscarPorFechaMapeaElResultado() {
        given(jpa.findByFechaProduccion(FECHA)).willReturn(Optional.of(entidadJpa()));

        assertThat(adaptador.buscarPorFecha(FECHA))
                .isPresent()
                .get()
                .extracting(o -> o.id().valor())
                .isEqualTo("orden-1");
    }

    @Test
    void listarPorEstadoMapeaCadaEntidad() {
        given(jpa.findByEstado(EstadoOrden.EN_PREPARACION)).willReturn(List.of(entidadJpa()));

        List<OrdenDeProduccion> ordenes = adaptador.listarPorEstado(EstadoOrden.EN_PREPARACION);

        assertThat(ordenes).hasSize(1);
        assertThat(ordenes.get(0).calcularTotalPorciones()).isEqualTo(12);
    }

    @Test
    void existePorFechaDelegaEnSpringData() {
        given(jpa.existsByFechaProduccion(FECHA)).willReturn(true);

        assertThat(adaptador.existePorFecha(FECHA)).isTrue();
        verify(jpa).existsByFechaProduccion(FECHA);
    }

    @Test
    @DisplayName("Ida y vuelta dominio -> entidad -> dominio sin perdida de datos")
    void idaYVueltaSinPerdidaDeDatos() {
        OrdenDeProduccion original = ordenDeDominio();
        adaptador.guardar(original);
        verify(jpa).save(entidadCaptor.capture());
        given(jpa.findById("orden-1")).willReturn(Optional.of(entidadCaptor.getValue()));

        OrdenDeProduccion recuperada = adaptador.buscarPorId(OrdenId.de("orden-1")).orElseThrow();

        assertThat(recuperada.id()).isEqualTo(original.id());
        assertThat(recuperada.fechaProduccion()).isEqualTo(original.fechaProduccion());
        assertThat(recuperada.estado()).isEqualTo(original.estado());
        assertThat(recuperada.paqueteIds()).isEqualTo(original.paqueteIds());
        assertThat(recuperada.items()).isEqualTo(original.items());
        assertThat(recuperada.items().get(0).estado()).isEqualTo(original.items().get(0).estado());
    }

    @Test
    @DisplayName("Una orden de un calendario guarda y recupera su origen")
    void idaYVueltaConOrigenDelCalendario() {
        var origen = new OrigenCalendario("CAL-20261023", LocalDate.of(2026, 10, 23));
        OrdenDeProduccion original = OrdenDeProduccion.reconstituir(OrdenId.de("orden-1"), FECHA,
                EstadoOrden.PENDIENTE, ordenDeDominio().items(), List.of("paquete-1"), origen);
        adaptador.guardar(original);
        verify(jpa).save(entidadCaptor.capture());
        OrdenJpaEntity entidad = entidadCaptor.getValue();
        given(jpa.findById("orden-1")).willReturn(Optional.of(entidad));

        OrdenDeProduccion recuperada = adaptador.buscarPorId(OrdenId.de("orden-1")).orElseThrow();

        assertThat(entidad.tieneOrigen()).isTrue();
        assertThat(entidad.getIdCalendario()).isEqualTo("CAL-20261023");
        assertThat(entidad.getFechaEntrega()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(recuperada.origen()).contains(origen);
    }

    @Test
    @DisplayName("Una orden creada a mano no llena las columnas del origen")
    void ordenSinOrigenNoLlenaColumnas() {
        adaptador.guardar(ordenDeDominio());

        verify(jpa).save(entidadCaptor.capture());
        assertThat(entidadCaptor.getValue().tieneOrigen()).isFalse();
        assertThat(entidadCaptor.getValue().getFechaEntrega()).isNull();
        assertThat(OrdenEntityMapper.aDominio(entidadCaptor.getValue()).origen()).isEmpty();
    }
}
