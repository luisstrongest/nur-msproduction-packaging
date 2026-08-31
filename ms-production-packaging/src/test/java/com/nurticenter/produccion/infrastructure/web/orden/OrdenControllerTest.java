package com.nurticenter.produccion.infrastructure.web.orden;

import com.nurticenter.produccion.application.orden.OrdenApplicationService;
import com.nurticenter.produccion.application.orden.dto.GenerarOrdenCommand;
import com.nurticenter.produccion.domain.model.orden.EstadoItem;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import com.nurticenter.produccion.infrastructure.web.orden.dto.GenerarOrdenRequest;
import com.nurticenter.produccion.infrastructure.web.orden.dto.OrdenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.WebDataBinder;

import java.beans.PropertyEditor;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * TEST UNITARIO CON MOCKS - OrdenController.
 *
 * El controller se instancia con un mock del servicio de aplicacion: no se
 * levanta Spring MVC. Lo que se prueba es la responsabilidad del controller:
 *   - traducir Request -> Command
 *   - delegar en el caso de uso correcto
 *   - traducir el agregado -> Response
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrdenController (unit test con mocks)")
class OrdenControllerTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 7, 15);
    private static final String ORDEN_ID = "orden-1";

    @Mock
    private OrdenApplicationService servicio;

    @InjectMocks
    private OrdenController controller;

    @Captor
    private ArgumentCaptor<GenerarOrdenCommand> comandoCaptor;

    private OrdenDeProduccion orden(EstadoOrden estado) {
        ItemOrden item = ItemOrden.reconstituir(
                "REC-001", "Pure de zapallo", 12,
                new Porcion(250, UnidadMedida.GRAMOS, "Porcion estandar"), EstadoItem.PENDIENTE);
        return OrdenDeProduccion.reconstituir(
                OrdenId.de(ORDEN_ID), FECHA, estado, List.of(item), List.of("paquete-1"));
    }

    private GenerarOrdenRequest request() {
        return new GenerarOrdenRequest(FECHA, List.of(
                new GenerarOrdenRequest.ItemRequest(
                        "REC-001", "Pure de zapallo", 12, 250, "GRAMOS", "Porcion estandar")));
    }

    @Test
    @DisplayName("POST /ordenes traduce el request a comando y devuelve la orden creada")
    void generarTraduceElRequestYDevuelveLaRespuesta() {
        given(servicio.generarOrden(org.mockito.ArgumentMatchers.any()))
                .willReturn(orden(EstadoOrden.PENDIENTE));

        OrdenResponse respuesta = controller.generar(request());

        verify(servicio).generarOrden(comandoCaptor.capture());
        GenerarOrdenCommand comando = comandoCaptor.getValue();
        assertThat(comando.fechaProduccion()).isEqualTo(FECHA);
        assertThat(comando.items()).singleElement().satisfies(item -> {
            assertThat(item.recetaId()).isEqualTo("REC-001");
            assertThat(item.nombreReceta()).isEqualTo("Pure de zapallo");
            assertThat(item.numeroPacientes()).isEqualTo(12);
            assertThat(item.cantidadPorcion()).isEqualTo(250);
            assertThat(item.unidadMedida()).isEqualTo("GRAMOS");
            assertThat(item.descripcionPorcion()).isEqualTo("Porcion estandar");
        });

        assertThat(respuesta.id()).isEqualTo(ORDEN_ID);
        assertThat(respuesta.estado()).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("La respuesta expone los items, el total de porciones y los paquetes")
    void laRespuestaMapeaTodoElAgregado() {
        given(servicio.buscarPorId(ORDEN_ID)).willReturn(orden(EstadoOrden.EN_PREPARACION));

        OrdenResponse respuesta = controller.buscar(ORDEN_ID);

        assertThat(respuesta.fechaProduccion()).isEqualTo(FECHA);
        assertThat(respuesta.estado()).isEqualTo("EN_PREPARACION");
        assertThat(respuesta.totalPorciones()).isEqualTo(12);
        assertThat(respuesta.paqueteIds()).containsExactly("paquete-1");
        assertThat(respuesta.items()).singleElement().satisfies(item -> {
            assertThat(item.recetaId()).isEqualTo("REC-001");
            assertThat(item.cantidadTotal()).isEqualTo(12);
            assertThat(item.estado()).isEqualTo("PENDIENTE");
            assertThat(item.porcionCantidad()).isEqualTo(250);
            assertThat(item.porcionUnidad()).isEqualTo("GRAMOS");
            assertThat(item.porcionDescripcion()).isEqualTo("Porcion estandar");
        });
    }

    @Test
    @DisplayName("GET /ordenes sin filtros lista las PENDIENTES por defecto")
    void listarSinFiltrosUsaPendientePorDefecto() {
        given(servicio.listarPorEstado(EstadoOrden.PENDIENTE))
                .willReturn(List.of(orden(EstadoOrden.PENDIENTE)));

        List<OrdenResponse> respuesta = controller.listar(null, null);

        assertThat(respuesta).singleElement().extracting(OrdenResponse::estado).isEqualTo("PENDIENTE");
        verify(servicio).listarPorEstado(EstadoOrden.PENDIENTE);
    }

    @Test
    @DisplayName("GET /ordenes?estado=... filtra por el estado indicado")
    void listarPorEstado() {
        given(servicio.listarPorEstado(EstadoOrden.COMPLETADA))
                .willReturn(List.of(orden(EstadoOrden.COMPLETADA)));

        assertThat(controller.listar(EstadoOrden.COMPLETADA, null)).hasSize(1);
    }

    @Test
    @DisplayName("GET /ordenes?fecha=... tiene prioridad sobre el filtro de estado")
    void listarPorFechaTienePrioridad() {
        given(servicio.buscarPorFecha(FECHA)).willReturn(orden(EstadoOrden.PENDIENTE));

        List<OrdenResponse> respuesta = controller.listar(EstadoOrden.COMPLETADA, FECHA);

        assertThat(respuesta).singleElement().extracting(OrdenResponse::id).isEqualTo(ORDEN_ID);
        verify(servicio).buscarPorFecha(FECHA);
    }

    @Test
    void iniciarPreparacionDelegaEnElServicio() {
        given(servicio.iniciarPreparacion(ORDEN_ID)).willReturn(orden(EstadoOrden.EN_PREPARACION));

        assertThat(controller.iniciarPreparacion(ORDEN_ID).estado()).isEqualTo("EN_PREPARACION");
        verify(servicio).iniciarPreparacion(ORDEN_ID);
    }

    @Test
    void marcarItemPreparadoDelegaEnElServicio() {
        given(servicio.marcarItemPreparado(ORDEN_ID, "REC-001"))
                .willReturn(orden(EstadoOrden.EN_PREPARACION));

        assertThat(controller.marcarItemPreparado(ORDEN_ID, "REC-001").id()).isEqualTo(ORDEN_ID);
        verify(servicio).marcarItemPreparado(ORDEN_ID, "REC-001");
    }

    @Test
    void completarDelegaEnElServicio() {
        given(servicio.completarOrden(ORDEN_ID)).willReturn(orden(EstadoOrden.COMPLETADA));

        assertThat(controller.completar(ORDEN_ID).estado()).isEqualTo("COMPLETADA");
        verify(servicio).completarOrden(ORDEN_ID);
    }

    @Test
    @DisplayName("El editor registrado acepta el estado como texto en cualquier capitalizacion")
    void elInitBinderNormalizaElEstadoRecibidoComoTexto() {
        WebDataBinder binder = new WebDataBinder(null);
        controller.initBinder(binder);

        PropertyEditor editor = binder.findCustomEditor(EstadoOrden.class, null);
        assertThat(editor).isNotNull();

        editor.setAsText("  completada ");

        assertThat(editor.getValue()).isEqualTo(EstadoOrden.COMPLETADA);
    }
}
