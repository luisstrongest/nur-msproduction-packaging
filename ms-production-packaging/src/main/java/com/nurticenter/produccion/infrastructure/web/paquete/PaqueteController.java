package com.nurticenter.produccion.infrastructure.web.paquete;

import com.nurticenter.produccion.application.paquete.PaqueteApplicationService;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.infrastructure.web.paquete.dto.CrearPaqueteRequest;
import com.nurticenter.produccion.infrastructure.web.paquete.dto.GenerarEtiquetaRequest;
import com.nurticenter.produccion.infrastructure.web.paquete.dto.PaqueteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.beans.PropertyEditorSupport;
import java.util.List;

/**
 * Controller REST del agregado PaqueteEtiquetado.
 */
@RestController
@RequestMapping("/api/v1/paquetes")
@Tag(name = "Paquetes Etiquetados",
        description = "Ciclo de vida: ENVASADO_PENDIENTE -> ENVASADO -> ETIQUETADO -> LISTO")
public class PaqueteController {

    private final PaqueteApplicationService servicio;

    public PaqueteController(PaqueteApplicationService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Crear un paquete para un paciente dentro de una orden")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaqueteResponse crear(@Valid @RequestBody CrearPaqueteRequest request) {
        return PaqueteResponse.desde(servicio.crearPaquete(request.aComando()));
    }

    @Operation(summary = "Consultar un paquete por id")
    @GetMapping("/{id}")
    public PaqueteResponse buscar(@PathVariable String id) {
        return PaqueteResponse.desde(servicio.buscarPorId(id));
    }

    @Operation(summary = "Listar paquetes por orden o por estado")
    @GetMapping
    public List<PaqueteResponse> listar(
            @RequestParam(required = false) String ordenId,
            @RequestParam(required = false) EstadoPaquete estado) {
        if (ordenId != null) {
            return servicio.listarPorOrden(ordenId).stream().map(PaqueteResponse::desde).toList();
        }
        if (estado != null) {
            return servicio.listarPorEstado(estado).stream().map(PaqueteResponse::desde).toList();
        }
        // Sin filtros: devuelve los paquetes LISTOS pendientes de transferencia a BC5.
        return servicio.listarListosPendientesDeTransferencia().stream()
                .map(PaqueteResponse::desde).toList();
    }

    @Operation(summary = "Registrar envasado (ENVASADO_PENDIENTE -> ENVASADO)")
    @PostMapping("/{id}/envasar")
    public PaqueteResponse envasar(@PathVariable String id) {
        return PaqueteResponse.desde(servicio.registrarEnvasado(id));
    }

    @Operation(summary = "Generar etiqueta (ENVASADO -> ETIQUETADO)")
    @PostMapping("/{id}/etiquetar")
    public PaqueteResponse etiquetar(@PathVariable String id,
                                     @Valid @RequestBody GenerarEtiquetaRequest request) {
        return PaqueteResponse.desde(servicio.generarEtiqueta(id, request.aComando()));
    }

    @Operation(summary = "Marcar listo (ETIQUETADO -> LISTO). Emite PaqueteListo")
    @PostMapping("/{id}/marcar-listo")
    public PaqueteResponse marcarListo(@PathVariable String id) {
        return PaqueteResponse.desde(servicio.marcarListo(id));
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(EstadoPaquete.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(EstadoPaquete.valueOf(text.trim().toUpperCase()));
            }
        });
    }
}
