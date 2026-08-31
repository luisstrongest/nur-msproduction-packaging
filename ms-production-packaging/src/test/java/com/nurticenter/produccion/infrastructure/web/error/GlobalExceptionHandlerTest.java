package com.nurticenter.produccion.infrastructure.web.error;

import com.nurticenter.produccion.domain.model.orden.OrdenSinItemsException;
import com.nurticenter.produccion.domain.model.paquete.EtiquetaSinEnvasadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * TEST UNITARIO - GlobalExceptionHandler.
 *
 * El handler se instancia directamente (no hace falta MockMvc ni el contexto de
 * Spring) y se verifica el mapeo excepcion -> codigo HTTP. La excepcion de
 * validacion si se mockea, porque construirla a mano exige un MethodParameter.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler (unit test)")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private MethodArgumentNotValidException validacionEx;

    @Mock
    private BindingResult bindingResult;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("DomainException -> 409 CONFLICT")
    void excepcionesDeDominioSonConflicto() {
        ResponseEntity<ApiError> respuesta =
                handler.handleDomain(new OrdenSinItemsException("Una orden debe tener items."));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().status()).isEqualTo(409);
        assertThat(respuesta.getBody().error()).isEqualTo("Regla de negocio violada");
        assertThat(respuesta.getBody().mensaje()).isEqualTo("Una orden debe tener items.");
        assertThat(respuesta.getBody().detalles()).isEmpty();
        assertThat(respuesta.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Cualquier subclase de DomainException recibe el mismo tratamiento")
    void otraExcepcionDeDominioTambienEsConflicto() {
        ResponseEntity<ApiError> respuesta =
                handler.handleDomain(new EtiquetaSinEnvasadoException("Falta envasar."));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("NoSuchElementException -> 404 NOT FOUND")
    void agregadoInexistenteEsNotFound() {
        ResponseEntity<ApiError> respuesta =
                handler.handleNotFound(new NoSuchElementException("No existe la orden orden-1"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody().error()).isEqualTo("Recurso no encontrado");
    }

    @Test
    @DisplayName("IllegalStateException -> 409 CONFLICT")
    void conflictoDeEstadoDeAplicacion() {
        ResponseEntity<ApiError> respuesta =
                handler.handleIllegalState(new IllegalStateException("Ya existe una orden."));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().error()).isEqualTo("Conflicto de estado");
    }

    @Test
    @DisplayName("IllegalArgumentException / NullPointerException -> 400 BAD REQUEST")
    void datosInvalidosSonBadRequest() {
        assertThat(handler.handleBadInput(new IllegalArgumentException("unidad invalida"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleBadInput(new NullPointerException("campo obligatorio"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Errores de @Valid -> 400 con el detalle campo a campo")
    void validacionDeDtosDevuelveElDetalle() {
        given(validacionEx.getBindingResult()).willReturn(bindingResult);
        given(bindingResult.getFieldErrors()).willReturn(List.of(
                new FieldError("generarOrdenRequest", "fechaProduccion", "fechaProduccion es obligatoria"),
                new FieldError("generarOrdenRequest", "items", "La orden debe tener al menos un item")));

        ResponseEntity<ApiError> respuesta = handler.handleValidation(validacionEx);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().error()).isEqualTo("Validacion fallida");
        assertThat(respuesta.getBody().detalles()).containsExactly(
                "fechaProduccion: fechaProduccion es obligatoria",
                "items: La orden debe tener al menos un item");
    }

    @Test
    @DisplayName("Cualquier otra excepcion -> 500 INTERNAL SERVER ERROR")
    void excepcionNoContempladaEsErrorInterno() {
        ResponseEntity<ApiError> respuesta = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().error()).isEqualTo("Error interno");
    }

    @Test
    @DisplayName("ApiError.de() normaliza una lista de detalles nula")
    void apiErrorNormalizaDetallesNulos() {
        ApiError error = ApiError.de(400, "Datos invalidos", "mensaje", null);

        assertThat(error.detalles()).isEmpty();
    }
}
