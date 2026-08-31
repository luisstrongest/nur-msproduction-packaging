package com.nurticenter.produccion.infrastructure.web.error;

import com.nurticenter.produccion.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Traduce las excepciones del dominio y de la capa web a respuestas HTTP coherentes.
 *
 * Mapeo:
 *   - DomainException (violacion de invariante/transicion)  -> 409 CONFLICT
 *   - NoSuchElementException (agregado inexistente)         -> 404 NOT FOUND
 *   - IllegalStateException (conflicto de estado de app)    -> 409 CONFLICT
 *   - IllegalArgumentException / NullPointerException (VO)  -> 400 BAD REQUEST
 *   - Validacion de DTOs (@Valid)                           -> 400 BAD REQUEST
 *   - Cualquier otra                                        -> 500 INTERNAL SERVER ERROR
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException ex) {
        return build(HttpStatus.CONFLICT, "Regla de negocio violada", ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException ex) {
        return build(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex) {
        return build(HttpStatus.CONFLICT, "Conflicto de estado", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<ApiError> handleBadInput(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, "Datos invalidos", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ApiError error = ApiError.de(
                HttpStatus.BAD_REQUEST.value(),
                "Validacion fallida",
                "La solicitud contiene campos invalidos",
                detalles);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ex.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String error, String mensaje) {
        return ResponseEntity.status(status)
                .body(ApiError.de(status.value(), error, mensaje, List.of()));
    }
}
