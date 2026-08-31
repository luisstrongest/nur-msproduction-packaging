package com.nurticenter.produccion.application.orden;

import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.application.orden.dto.GenerarOrdenCommand;
import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * SERVICIO DE APLICACION - Orden de Produccion
 *
 * Orquesta los casos de uso del agregado OrdenDeProduccion:
 *   - Generar la orden desde el CalendarioConsolidado (ACL Receta -> ItemOrden)
 *   - Iniciar la preparacion
 *   - Marcar items preparados
 *   - Completar la orden
 *   - Consultas
 *
 * Responsabilidades de esta capa (NO del dominio):
 *   - Coordinar repositorio (carga/guardado) y transacciones
 *   - Publicar los eventos que el agregado acumula
 *   - Traducir DTOs de aplicacion a objetos de dominio (ACL)
 */
@Service
public class OrdenApplicationService {

    private final OrdenDeProduccionRepository ordenRepository;
    private final EventPublisher eventPublisher;

    public OrdenApplicationService(OrdenDeProduccionRepository ordenRepository,
                                   EventPublisher eventPublisher) {
        this.ordenRepository = ordenRepository;
        this.eventPublisher = eventPublisher;
    }

    // -- Casos de uso de escritura --------------------------------------------------

    /**
     * Genera una OrdenDeProduccion a partir del CalendarioConsolidado.
     * Regla: existe a lo sumo una orden por fecha de produccion.
     */
    @Transactional
    public OrdenDeProduccion generarOrden(GenerarOrdenCommand comando) {
        if (ordenRepository.existePorFecha(comando.fechaProduccion())) {
            throw new IllegalStateException(
                    "Ya existe una orden de produccion para la fecha " + comando.fechaProduccion());
        }

        List<ItemOrden> items = comando.items().stream()
                .map(this::traducirItem)   // ACL: item del calendario -> ItemOrden de dominio
                .toList();

        OrdenDeProduccion orden = OrdenDeProduccion.crear(comando.fechaProduccion(), items);
        ordenRepository.guardar(orden);
        eventPublisher.publicar(orden.pullEventos());
        return orden;
    }

    /** Transicion PENDIENTE -> EN_PREPARACION. */
    @Transactional
    public OrdenDeProduccion iniciarPreparacion(String ordenId) {
        OrdenDeProduccion orden = cargar(ordenId);
        orden.iniciarPreparacion();
        ordenRepository.guardar(orden);
        eventPublisher.publicar(orden.pullEventos());
        return orden;
    }

    /** Marca un item (por recetaId) como PREPARADO. */
    @Transactional
    public OrdenDeProduccion marcarItemPreparado(String ordenId, String recetaId) {
        OrdenDeProduccion orden = cargar(ordenId);
        orden.marcarItemPreparado(recetaId);
        ordenRepository.guardar(orden);
        eventPublisher.publicar(orden.pullEventos());
        return orden;
    }

    /** Transicion EN_PREPARACION -> COMPLETADA (requiere todos los items preparados). */
    @Transactional
    public OrdenDeProduccion completarOrden(String ordenId) {
        OrdenDeProduccion orden = cargar(ordenId);
        orden.completar();
        ordenRepository.guardar(orden);
        eventPublisher.publicar(orden.pullEventos());
        return orden;
    }

    // -- Consultas ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public OrdenDeProduccion buscarPorId(String ordenId) {
        return cargar(ordenId);
    }

    @Transactional(readOnly = true)
    public List<OrdenDeProduccion> listarPorEstado(EstadoOrden estado) {
        return ordenRepository.listarPorEstado(estado);
    }

    @Transactional(readOnly = true)
    public OrdenDeProduccion buscarPorFecha(LocalDate fecha) {
        return ordenRepository.buscarPorFecha(fecha)
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe orden de produccion para la fecha " + fecha));
    }

    // -- Helpers --------------------------------------------------------------------

    private OrdenDeProduccion cargar(String ordenId) {
        return ordenRepository.buscarPorId(OrdenId.de(ordenId))
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe una OrdenDeProduccion con id " + ordenId));
    }

    /**
     * ACL: traduce un item del CalendarioConsolidado a un ItemOrden del dominio.
     * cantidadTotal = numero de pacientes que reciben la receta ese dia.
     */
    private ItemOrden traducirItem(GenerarOrdenCommand.ItemCommand item) {
        UnidadMedida unidad = UnidadMedida.valueOf(item.unidadMedida().trim().toUpperCase());
        Porcion porcion = new Porcion(item.cantidadPorcion(), unidad, item.descripcionPorcion());
        return new ItemOrden(item.recetaId(), item.nombreReceta(), item.numeroPacientes(), porcion);
    }
}
