package com.nurticenter.produccion.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.model.orden.OrdenSinItemsException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO CON MOCKS - EventosCateringListener.
 *
 * El consumidor es un mock y el ConsumerRecord se construye a mano: se verifica
 * el filtro por eventName y que los rechazos de negocio no detengan el listener
 * mientras los errores tecnicos se propagan para que Kafka reintente.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventosCateringListener (unit test con mocks)")
class EventosCateringListenerTest {

    private static final String CALENDARIO = "{\"eventName\": \"bc3.CalendarioConsolidadoCerrado\"}";

    @Mock
    private ConsumidorCalendarioConsolidado consumidor;

    private EventosCateringListener listener;

    @BeforeEach
    void setUp() {
        listener = new EventosCateringListener(consumidor, new LectorCalendarioConsolidado(new ObjectMapper()));
    }

    @Test
    @DisplayName("Con la cabecera del calendario, lo procesa")
    void procesaElCalendarioPorCabecera() {
        when(consumidor.procesar(CALENDARIO)).thenReturn(true);

        listener.recibir(registro(CALENDARIO, "bc3.CalendarioConsolidadoCerrado"));

        verify(consumidor).procesar(CALENDARIO);
    }

    @Test
    @DisplayName("Sin cabecera, decide por el eventName del sobre")
    void sinCabeceraUsaElSobre() {
        when(consumidor.procesar(CALENDARIO)).thenReturn(false);

        listener.recibir(registro(CALENDARIO, null));

        verify(consumidor).procesar(CALENDARIO);
    }

    @Test
    @DisplayName("Ignora los demas eventos de BC3 aunque el sobre diga otra cosa: manda la cabecera")
    void ignoraOtrosEventosPorCabecera() {
        listener.recibir(registro(CALENDARIO, "bc3.SuscripcionCreada"));

        verify(consumidor, never()).procesar(anyString());
    }

    @Test
    @DisplayName("Sin cabecera y con otro evento en el sobre, lo ignora")
    void ignoraOtrosEventosSinCabecera() {
        listener.recibir(registro("{\"eventName\": \"bc3.SuscripcionPausada\"}", null));

        verify(consumidor, never()).procesar(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"argumento", "estado", "dominio"})
    @DisplayName("Un rechazo de negocio o un mensaje invalido se registra y no detiene el listener")
    void unRechazoNoDetieneElListener(String tipo) {
        RuntimeException rechazo = switch (tipo) {
            case "argumento" -> new IllegalArgumentException("Falta el campo idCalendario");
            case "estado" -> new IllegalStateException("Ya existe una orden");
            default -> new OrdenSinItemsException("sin items");
        };
        when(consumidor.procesar(CALENDARIO)).thenThrow(rechazo);

        assertThatCode(() -> listener.recibir(registro(CALENDARIO, "bc3.CalendarioConsolidadoCerrado")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Un error tecnico se propaga para que el contenedor de Kafka reintente")
    void unErrorTecnicoSePropaga() {
        when(consumidor.procesar(CALENDARIO)).thenThrow(new DataAccessResourceFailureException("BD caida"));

        assertThatThrownBy(() -> listener.recibir(registro(CALENDARIO, "bc3.CalendarioConsolidadoCerrado")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    private static ConsumerRecord<String, String> registro(String valor, String eventName) {
        var registro = new ConsumerRecord<>("bc3.catering.eventos", 0, 42L, "CAL-20261023", valor);
        if (eventName != null) {
            registro.headers().add(EventosCateringListener.CABECERA_EVENTO, eventName.getBytes(StandardCharsets.UTF_8));
        }
        return registro;
    }
}
