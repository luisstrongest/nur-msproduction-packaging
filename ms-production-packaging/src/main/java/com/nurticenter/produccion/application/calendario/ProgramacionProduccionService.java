package com.nurticenter.produccion.application.calendario;

import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.EntregaCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.RecetaCommand;
import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrigenCalendario;
import com.nurticenter.produccion.domain.model.paquete.DestinoEntrega;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import com.nurticenter.produccion.domain.repository.PaqueteEtiquetadoRepository;
import com.nurticenter.produccion.domain.shared.Porcion;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SERVICIO DE APLICACION - Programacion de la produccion desde el calendario
 *
 * Caso de uso disparado por bc3.CalendarioConsolidadoCerrado: BC3 cerro las
 * entregas de un dia y BC4 prepara todo lo necesario para producirlas.
 *
 *   1. ACL Receta -> ItemOrden: agrupa las recetas de todas las entregas; cada
 *      item lleva cuantos pacientes la reciben y la porcion individual.
 *   2. Crea la OrdenDeProduccion para el dia anterior a la entrega.
 *   3. Crea un PaqueteEtiquetado por entrega, con su destino, y lo registra en la orden.
 *
 * Regla heredada: a lo sumo una orden por fecha de produccion.
 */
@Service
public class ProgramacionProduccionService {

    /** La cocina produce el dia anterior a la entrega. */
    static final int DIAS_DE_ANTICIPACION = 1;

    private final OrdenDeProduccionRepository ordenRepository;
    private final PaqueteEtiquetadoRepository paqueteRepository;
    private final EventPublisher eventPublisher;

    public ProgramacionProduccionService(OrdenDeProduccionRepository ordenRepository,
                                         PaqueteEtiquetadoRepository paqueteRepository,
                                         EventPublisher eventPublisher) {
        this.ordenRepository = ordenRepository;
        this.paqueteRepository = paqueteRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public OrdenDeProduccion programar(ProgramarProduccionCommand comando) {
        LocalDate fechaProduccion = comando.fechaEntrega().minusDays(DIAS_DE_ANTICIPACION);
        if (ordenRepository.existePorFecha(fechaProduccion)) {
            throw new IllegalStateException(
                    "Ya existe una orden de produccion para la fecha " + fechaProduccion);
        }

        OrdenDeProduccion orden = OrdenDeProduccion.crearDesdeCalendario(
                fechaProduccion,
                traducirItems(comando.entregas()),
                new OrigenCalendario(comando.idCalendario(), comando.fechaEntrega()));

        for (EntregaCommand entrega : comando.entregas()) {
            PaqueteEtiquetado paquete = PaqueteEtiquetado.crearParaEntrega(
                    PacienteId.de(entrega.idPaciente()),
                    orden.id().valor(),
                    destino(entrega, comando.fechaEntrega()));
            paqueteRepository.guardar(paquete);
            orden.registrarPaqueteGenerado(paquete.id().valor());
        }

        ordenRepository.guardar(orden);
        eventPublisher.publicar(orden.pullEventos());
        return orden;
    }

    /**
     * ACL: una receta que aparece en N entregas es un solo ItemOrden con N pacientes.
     * Si dos entregas piden porciones distintas de la misma receta se rechaza el
     * calendario: el ItemOrden tiene una sola porcion individual y promediarla
     * produciria cantidades equivocadas.
     */
    private static List<ItemOrden> traducirItems(List<EntregaCommand> entregas) {
        Map<String, RecetaCommand> recetas = new LinkedHashMap<>();
        Map<String, Integer> pacientes = new LinkedHashMap<>();
        for (EntregaCommand entrega : entregas) {
            for (RecetaCommand receta : entrega.recetas()) {
                RecetaCommand previa = recetas.putIfAbsent(receta.idReceta(), receta);
                if (previa != null && previa.porcionGramos() != receta.porcionGramos()) {
                    throw new IllegalArgumentException(
                            "La receta " + receta.idReceta() + " llega con porciones distintas ("
                                    + previa.porcionGramos() + " g y " + receta.porcionGramos() + " g).");
                }
                pacientes.merge(receta.idReceta(), 1, Integer::sum);
            }
        }
        return recetas.values().stream()
                .map(r -> new ItemOrden(r.idReceta(), r.nombre(), pacientes.get(r.idReceta()),
                        new Porcion(r.porcionGramos(), UnidadMedida.GRAMOS, "Servir a las " + r.hora())))
                .toList();
    }

    private static DestinoEntrega destino(EntregaCommand entrega, LocalDate fechaEntrega) {
        return new DestinoEntrega(
                entrega.idCalendarioDia(),
                entrega.idSuscripcion(),
                fechaEntrega,
                entrega.idDireccion(),
                entrega.latitud(),
                entrega.longitud(),
                entrega.recetas().size());
    }
}
