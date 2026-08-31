package com.nurticenter.produccion.infrastructure.web.paquete;

import com.nurticenter.produccion.application.paquete.PaqueteApplicationService;
import com.nurticenter.produccion.application.paquete.dto.CrearPaqueteCommand;
import com.nurticenter.produccion.application.paquete.dto.GenerarEtiquetaCommand;
import com.nurticenter.produccion.domain.model.paquete.CodigoIdentificacion;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.Etiqueta;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import com.nurticenter.produccion.infrastructure.web.paquete.dto.CrearPaqueteRequest;
import com.nurticenter.produccion.infrastructure.web.paquete.dto.GenerarEtiquetaRequest;
import com.nurticenter.produccion.infrastructure.web.paquete.dto.PaqueteResponse;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * TEST UNITARIO CON MOCKS - PaqueteController.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaqueteController (unit test con mocks)")
class PaqueteControllerTest {

    private static final String PAQUETE_ID = "paquete-1";
    private static final String ORDEN_ID = "orden-1";
    private static final String PACIENTE_ID = "PAC-1001";
    private static final LocalDateTime GENERADO_EN = LocalDateTime.of(2026, 7, 15, 8, 30);

    @Mock
    private PaqueteApplicationService servicio;

    @InjectMocks
    private PaqueteController controller;

    @Captor
    private ArgumentCaptor<CrearPaqueteCommand> crearCaptor;

    @Captor
    private ArgumentCaptor<GenerarEtiquetaCommand> etiquetaCaptor;

    private Etiqueta etiqueta() {
        return Etiqueta.reconstituir(
                "Ana Perez",
                new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz"),
                CodigoIdentificacion.de("PKG-ABC12345", GENERADO_EN),
                GENERADO_EN);
    }

    private PaqueteEtiquetado paquete(EstadoPaquete estado, Etiqueta etiqueta) {
        return PaqueteEtiquetado.reconstituir(
                PaqueteId.de(PAQUETE_ID), PacienteId.de(PACIENTE_ID), ORDEN_ID, estado, etiqueta);
    }

    @Test
    @DisplayName("POST /paquetes traduce el request a comando")
    void crearTraduceElRequestAComando() {
        given(servicio.crearPaquete(any()))
                .willReturn(paquete(EstadoPaquete.ENVASADO_PENDIENTE, null));

        PaqueteResponse respuesta = controller.crear(new CrearPaqueteRequest(PACIENTE_ID, ORDEN_ID));

        verify(servicio).crearPaquete(crearCaptor.capture());
        assertThat(crearCaptor.getValue().pacienteId()).isEqualTo(PACIENTE_ID);
        assertThat(crearCaptor.getValue().ordenId()).isEqualTo(ORDEN_ID);

        assertThat(respuesta.id()).isEqualTo(PAQUETE_ID);
        assertThat(respuesta.estado()).isEqualTo("ENVASADO_PENDIENTE");
        assertThat(respuesta.etiqueta()).isNull();   // aun no esta etiquetado
    }

    @Test
    @DisplayName("La respuesta incluye la etiqueta formateada cuando el paquete ya la tiene")
    void laRespuestaIncluyeLaEtiquetaFormateada() {
        given(servicio.buscarPorId(PAQUETE_ID)).willReturn(paquete(EstadoPaquete.LISTO, etiqueta()));

        PaqueteResponse respuesta = controller.buscar(PAQUETE_ID);

        assertThat(respuesta.pacienteId()).isEqualTo(PACIENTE_ID);
        assertThat(respuesta.ordenId()).isEqualTo(ORDEN_ID);
        assertThat(respuesta.estado()).isEqualTo("LISTO");
        assertThat(respuesta.etiqueta()).isNotNull();
        assertThat(respuesta.etiqueta().nombrePaciente()).isEqualTo("Ana Perez");
        assertThat(respuesta.etiqueta().direccion())
                .isEqualTo("Av. Mutualista 450, Ref: Edificio azul - Santa Cruz");
        assertThat(respuesta.etiqueta().codigoIdentificacion()).isEqualTo("PKG-ABC12345");
        assertThat(respuesta.etiqueta().fechaGeneracion()).isEqualTo(GENERADO_EN);
    }

    @Test
    @DisplayName("GET /paquetes?ordenId=... filtra por orden")
    void listarPorOrden() {
        given(servicio.listarPorOrden(ORDEN_ID)).willReturn(List.of(paquete(EstadoPaquete.ENVASADO, null)));

        assertThat(controller.listar(ORDEN_ID, null)).hasSize(1);
        verify(servicio).listarPorOrden(ORDEN_ID);
    }

    @Test
    @DisplayName("GET /paquetes?estado=... filtra por estado")
    void listarPorEstado() {
        given(servicio.listarPorEstado(EstadoPaquete.ENVASADO))
                .willReturn(List.of(paquete(EstadoPaquete.ENVASADO, null)));

        assertThat(controller.listar(null, EstadoPaquete.ENVASADO)).hasSize(1);
        verify(servicio).listarPorEstado(EstadoPaquete.ENVASADO);
    }

    @Test
    @DisplayName("GET /paquetes sin filtros devuelve los LISTOS pendientes de transferir a BC5")
    void listarSinFiltrosDevuelveLosPendientesDeTransferencia() {
        given(servicio.listarListosPendientesDeTransferencia())
                .willReturn(List.of(paquete(EstadoPaquete.LISTO, etiqueta())));

        assertThat(controller.listar(null, null)).singleElement()
                .extracting(PaqueteResponse::estado).isEqualTo("LISTO");
        verify(servicio).listarListosPendientesDeTransferencia();
    }

    @Test
    void envasarDelegaEnElServicio() {
        given(servicio.registrarEnvasado(PAQUETE_ID)).willReturn(paquete(EstadoPaquete.ENVASADO, null));

        assertThat(controller.envasar(PAQUETE_ID).estado()).isEqualTo("ENVASADO");
        verify(servicio).registrarEnvasado(PAQUETE_ID);
    }

    @Test
    @DisplayName("POST /paquetes/{id}/etiquetar traduce el request a comando")
    void etiquetarTraduceElRequestAComando() {
        given(servicio.generarEtiqueta(eq(PAQUETE_ID), any()))
                .willReturn(paquete(EstadoPaquete.ETIQUETADO, etiqueta()));

        PaqueteResponse respuesta = controller.etiquetar(PAQUETE_ID, new GenerarEtiquetaRequest(
                "Ana Perez", "Av. Mutualista", "450", "Edificio azul", "Santa Cruz"));

        verify(servicio).generarEtiqueta(eq(PAQUETE_ID), etiquetaCaptor.capture());
        GenerarEtiquetaCommand comando = etiquetaCaptor.getValue();
        assertThat(comando.nombreCompleto()).isEqualTo("Ana Perez");
        assertThat(comando.calle()).isEqualTo("Av. Mutualista");
        assertThat(comando.numero()).isEqualTo("450");
        assertThat(comando.referencia()).isEqualTo("Edificio azul");
        assertThat(comando.ciudad()).isEqualTo("Santa Cruz");

        assertThat(respuesta.estado()).isEqualTo("ETIQUETADO");
    }

    @Test
    void marcarListoDelegaEnElServicio() {
        given(servicio.marcarListo(PAQUETE_ID)).willReturn(paquete(EstadoPaquete.LISTO, etiqueta()));

        assertThat(controller.marcarListo(PAQUETE_ID).estado()).isEqualTo("LISTO");
        verify(servicio).marcarListo(PAQUETE_ID);
    }

    @Test
    @DisplayName("El editor registrado acepta el estado como texto en cualquier capitalizacion")
    void elInitBinderNormalizaElEstadoRecibidoComoTexto() {
        WebDataBinder binder = new WebDataBinder(null);
        controller.initBinder(binder);

        PropertyEditor editor = binder.findCustomEditor(EstadoPaquete.class, null);
        assertThat(editor).isNotNull();

        editor.setAsText(" envasado_pendiente ");

        assertThat(editor.getValue()).isEqualTo(EstadoPaquete.ENVASADO_PENDIENTE);
    }
}
