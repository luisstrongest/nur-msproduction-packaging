package com.nurticenter.produccion.application.paquete;

import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.application.paquete.dto.CrearPaqueteCommand;
import com.nurticenter.produccion.application.paquete.dto.GenerarEtiquetaCommand;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.model.paquete.DatosPaciente;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import com.nurticenter.produccion.domain.repository.PaqueteEtiquetadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * SERVICIO DE APLICACION - Paquete Etiquetado
 *
 * Orquesta los casos de uso del agregado PaqueteEtiquetado:
 *   ENVASADO_PENDIENTE -> ENVASADO -> ETIQUETADO -> LISTO
 *
 * Ademas, al crear un paquete, registra su referencia en la OrdenDeProduccion
 * (regla: la orden guarda los ids de los paquetes que genera).
 */
@Service
public class PaqueteApplicationService {

    private final PaqueteEtiquetadoRepository paqueteRepository;
    private final OrdenDeProduccionRepository ordenRepository;
    private final EventPublisher eventPublisher;

    public PaqueteApplicationService(PaqueteEtiquetadoRepository paqueteRepository,
                                     OrdenDeProduccionRepository ordenRepository,
                                     EventPublisher eventPublisher) {
        this.paqueteRepository = paqueteRepository;
        this.ordenRepository = ordenRepository;
        this.eventPublisher = eventPublisher;
    }

    // -- Casos de uso de escritura --------------------------------------------------

    /**
     * Crea un PaqueteEtiquetado para un paciente y lo vincula a su orden.
     * La orden debe existir; se registra la referencia del paquete en ella.
     */
    @Transactional
    public PaqueteEtiquetado crearPaquete(CrearPaqueteCommand comando) {
        OrdenDeProduccion orden = ordenRepository.buscarPorId(OrdenId.de(comando.ordenId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe una OrdenDeProduccion con id " + comando.ordenId()));

        PaqueteEtiquetado paquete = PaqueteEtiquetado.crear(
                PacienteId.de(comando.pacienteId()), comando.ordenId());
        paqueteRepository.guardar(paquete);

        // La orden lleva el registro de los paquetes generados.
        orden.registrarPaqueteGenerado(paquete.id().valor());
        ordenRepository.guardar(orden);

        eventPublisher.publicar(paquete.pullEventos());
        return paquete;
    }

    /** Transicion ENVASADO_PENDIENTE -> ENVASADO. */
    @Transactional
    public PaqueteEtiquetado registrarEnvasado(String paqueteId) {
        PaqueteEtiquetado paquete = cargar(paqueteId);
        paquete.registrarEnvasado();
        paqueteRepository.guardar(paquete);
        eventPublisher.publicar(paquete.pullEventos());
        return paquete;
    }

    /** Transicion ENVASADO -> ETIQUETADO (genera la etiqueta inmutable). */
    @Transactional
    public PaqueteEtiquetado generarEtiqueta(String paqueteId, GenerarEtiquetaCommand comando) {
        PaqueteEtiquetado paquete = cargar(paqueteId);

        DireccionImpresa direccion = new DireccionImpresa(
                comando.calle(), comando.numero(), comando.referencia(), comando.ciudad());
        DatosPaciente datos = new DatosPaciente(
                paquete.pacienteId(), comando.nombreCompleto(), direccion);

        paquete.generarEtiqueta(datos);
        paqueteRepository.guardar(paquete);
        eventPublisher.publicar(paquete.pullEventos());
        return paquete;
    }

    /** Transicion ETIQUETADO -> LISTO (emite PaqueteListo). */
    @Transactional
    public PaqueteEtiquetado marcarListo(String paqueteId) {
        PaqueteEtiquetado paquete = cargar(paqueteId);
        paquete.marcarListo();
        paqueteRepository.guardar(paquete);
        eventPublisher.publicar(paquete.pullEventos());
        return paquete;
    }

    // -- Consultas ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PaqueteEtiquetado buscarPorId(String paqueteId) {
        return cargar(paqueteId);
    }

    @Transactional(readOnly = true)
    public List<PaqueteEtiquetado> listarPorOrden(String ordenId) {
        return paqueteRepository.listarPorOrden(ordenId);
    }

    @Transactional(readOnly = true)
    public List<PaqueteEtiquetado> listarPorEstado(EstadoPaquete estado) {
        return paqueteRepository.listarPorEstado(estado);
    }

    @Transactional(readOnly = true)
    public List<PaqueteEtiquetado> listarListosPendientesDeTransferencia() {
        return paqueteRepository.listarListosPendientesDeTransferencia();
    }

    // -- Helpers --------------------------------------------------------------------

    private PaqueteEtiquetado cargar(String paqueteId) {
        return paqueteRepository.buscarPorId(PaqueteId.de(paqueteId))
                .orElseThrow(() -> new NoSuchElementException(
                        "No existe un PaqueteEtiquetado con id " + paqueteId));
    }
}
