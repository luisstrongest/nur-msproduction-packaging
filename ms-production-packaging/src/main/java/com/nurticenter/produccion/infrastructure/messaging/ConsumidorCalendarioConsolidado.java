package com.nurticenter.produccion.infrastructure.messaging;

import com.nurticenter.produccion.application.calendario.ProgramacionProduccionService;
import com.nurticenter.produccion.infrastructure.outbox.ContextoCorrelacion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Procesa bc3.CalendarioConsolidadoCerrado de forma IDEMPOTENTE.
 *
 * En una sola transaccion:
 *   1. si el eventId ya esta en el inbox, no hace nada (entrega repetida);
 *   2. programa la produccion (orden + paquetes) copiando el correlationId del
 *      calendario en los eventos que se publican (via outbox);
 *   3. registra el eventId en el inbox.
 *
 * Si el caso de uso falla, se deshace todo, inbox incluido.
 * No depende de Kafka: lo invoca EventosCateringListener y, en las pruebas de
 * integracion, directamente con el JSON del contrato.
 */
@Component
public class ConsumidorCalendarioConsolidado {

    private final LectorCalendarioConsolidado lector;
    private final ProgramacionProduccionService programacion;
    private final SpringDataEventoProcesadoRepository procesados;
    private final ContextoCorrelacion contexto;
    private final Clock reloj;

    public ConsumidorCalendarioConsolidado(LectorCalendarioConsolidado lector,
                                           ProgramacionProduccionService programacion,
                                           SpringDataEventoProcesadoRepository procesados,
                                           ContextoCorrelacion contexto,
                                           Clock reloj) {
        this.lector = lector;
        this.programacion = programacion;
        this.procesados = procesados;
        this.contexto = contexto;
        this.reloj = reloj;
    }

    /** @return false si el evento ya se habia procesado */
    @Transactional
    public boolean procesar(String json) {
        var calendario = lector.leer(json);
        if (procesados.existsById(calendario.eventId())) {
            return false;
        }
        contexto.ejecutarCon(calendario.correlationId(), () -> programacion.programar(calendario.comando()));
        procesados.save(new EventoProcesadoJpaEntity(
                calendario.eventId(), LectorCalendarioConsolidado.EVENTO, reloj.instant()));
        return true;
    }
}
