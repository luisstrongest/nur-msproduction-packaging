package com.nurticenter.produccion.infrastructure.web.orden;

import com.nurticenter.produccion.application.orden.OrdenApplicationService;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.infrastructure.web.orden.dto.GenerarOrdenRequest;
import com.nurticenter.produccion.infrastructure.web.orden.dto.OrdenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller REST del agregado OrdenDeProduccion.
 */
@RestController
@RequestMapping("/api/v1/ordenes")
@Tag(name = "Ordenes de Produccion",
        description = "Ciclo de vida de la orden: PENDIENTE -> EN_PREPARACION -> COMPLETADA")
public class OrdenController {

    private final OrdenApplicationService servicio;

    public OrdenController(OrdenApplicationService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Generar una orden de produccion (desde el CalendarioConsolidado)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenResponse generar(@Valid @RequestBody GenerarOrdenRequest request) {
        OrdenDeProduccion orden = servicio.generarOrden(request.aComando());
        return OrdenResponse.desde(orden);
    }

    @Operation(summary = "Consultar una orden por id")
    @GetMapping("/{id}")
    public OrdenResponse buscar(@PathVariable String id) {
        return OrdenResponse.desde(servicio.buscarPorId(id));
    }

    @Operation(summary = "Listar ordenes por estado, o buscar la orden de una fecha")
    @GetMapping
    public List<OrdenResponse> listar(
            @RequestParam(required = false) EstadoOrden estado,
            @RequestParam(required = false) LocalDate fecha) {
        if (fecha != null) {
            return List.of(OrdenResponse.desde(servicio.buscarPorFecha(fecha)));
        }
        EstadoOrden filtro = (estado != null) ? estado : EstadoOrden.PENDIENTE;
        return servicio.listarPorEstado(filtro).stream()
                .map(OrdenResponse::desde)
                .toList();
    }

    @Operation(summary = "Iniciar la preparacion (PENDIENTE -> EN_PREPARACION)")
    @PostMapping("/{id}/iniciar-preparacion")
    public OrdenResponse iniciarPreparacion(@PathVariable String id) {
        return OrdenResponse.desde(servicio.iniciarPreparacion(id));
    }

    @Operation(summary = "Marcar un item como preparado")
    @PostMapping("/{id}/items/{recetaId}/preparar")
    public OrdenResponse marcarItemPreparado(@PathVariable String id, @PathVariable String recetaId) {
        return OrdenResponse.desde(servicio.marcarItemPreparado(id, recetaId));
    }

    @Operation(summary = "Completar la orden (EN_PREPARACION -> COMPLETADA)")
    @PostMapping("/{id}/completar")
    public OrdenResponse completar(@PathVariable String id) {
        return OrdenResponse.desde(servicio.completarOrden(id));
    }

    /** Permite pasar el estado como texto (case-insensitive) en el query param. */
    @org.springframework.web.bind.annotation.InitBinder
    public void initBinder(org.springframework.web.bind.WebDataBinder binder) {
        binder.registerCustomEditor(EstadoOrden.class,
                new java.beans.PropertyEditorSupport() {
                    @Override
                    public void setAsText(String text) {
                        setValue(EstadoOrden.valueOf(text.trim().toUpperCase()));
                    }
                });
    }
}
