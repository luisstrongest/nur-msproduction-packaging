package com.nurticenter.produccion.infrastructure.outbox;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * correlationId del evento de integracion que se esta procesando en este hilo.
 *
 * Lo fija el consumidor de un evento entrante (p. ej. bc3.CalendarioConsolidadoCerrado)
 * mientras ejecuta el caso de uso; OutboxEventWriter lo copia en los eventos que
 * ese caso de uso publica, como pide el contrato ("se copia del evento que origino
 * este"). Fuera de un consumidor no hay correlacion activa.
 */
@Component
public class ContextoCorrelacion {

    private final ThreadLocal<String> actual = new ThreadLocal<>();

    public <T> T ejecutarCon(String correlationId, Supplier<T> accion) {
        String previo = actual.get();
        actual.set(correlationId);
        try {
            return accion.get();
        } finally {
            if (previo == null) {
                actual.remove();
            } else {
                actual.set(previo);
            }
        }
    }

    public Optional<String> actual() {
        return Optional.ofNullable(actual.get());
    }
}
