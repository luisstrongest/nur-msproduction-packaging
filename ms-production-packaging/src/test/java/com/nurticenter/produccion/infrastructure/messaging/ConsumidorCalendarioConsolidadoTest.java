package com.nurticenter.produccion.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.application.calendario.ProgramacionProduccionService;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand;
import com.nurticenter.produccion.infrastructure.outbox.ContextoCorrelacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO CON MOCKS - ConsumidorCalendarioConsolidado.
 *
 * El caso de uso y el inbox son mocks; el lector y el contexto de correlacion son
 * reales. Se verifica la idempotencia y que el caso de uso corre con la
 * correlacion del calendario. Que todo ocurra en una transaccion lo prueba
 * FlujoCalendarioIT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumidorCalendarioConsolidado (unit test con mocks)")
class ConsumidorCalendarioConsolidadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-22T04:00:00Z");
    private static final String EVENT_ID = "3f2a0014-9c1e-4b7a-a1d2-7e5f0c8b1014";
    private static final String CALENDARIO = """
            {"eventId": "%s", "eventName": "bc3.CalendarioConsolidadoCerrado",
             "correlationId": "corr-entrega-2026-10-23",
             "data": {"idCalendario": "CAL-20261023", "fechaEntrega": "2026-10-23", "entregas": [
               {"idCalendarioDia": "CD-70011", "idSuscripcion": "SUS-501", "idPaciente": "PAC-1024",
                "recetas": [{"idReceta": "REC-14", "nombre": "Avena", "hora": "08:00", "porcionGramos": 250}]}]}}
            """.formatted(EVENT_ID);

    @Mock
    private ProgramacionProduccionService programacion;

    @Mock
    private SpringDataEventoProcesadoRepository procesados;

    private final ContextoCorrelacion contexto = new ContextoCorrelacion();
    private ConsumidorCalendarioConsolidado consumidor;

    @BeforeEach
    void setUp() {
        consumidor = new ConsumidorCalendarioConsolidado(
                new LectorCalendarioConsolidado(new ObjectMapper()), programacion, procesados, contexto,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Un calendario nuevo programa la produccion con su correlacion y queda en el inbox")
    void programaYRegistraElEvento() {
        when(procesados.existsById(EVENT_ID)).thenReturn(false);
        var correlacionDuranteElCaso = new AtomicReference<String>();
        when(programacion.programar(any())).thenAnswer(i -> {
            correlacionDuranteElCaso.set(contexto.actual().orElse(null));
            return null;
        });

        boolean procesado = consumidor.procesar(CALENDARIO);

        assertThat(procesado).isTrue();
        assertThat(correlacionDuranteElCaso.get()).isEqualTo("corr-entrega-2026-10-23");
        assertThat(contexto.actual()).isEmpty();
        var comando = ArgumentCaptor.forClass(ProgramarProduccionCommand.class);
        verify(programacion).programar(comando.capture());
        assertThat(comando.getValue().idCalendario()).isEqualTo("CAL-20261023");
        var fila = ArgumentCaptor.forClass(EventoProcesadoJpaEntity.class);
        verify(procesados).save(fila.capture());
        assertThat(fila.getValue().getEventId()).isEqualTo(EVENT_ID);
        assertThat(fila.getValue().getEventName()).isEqualTo("bc3.CalendarioConsolidadoCerrado");
        assertThat(fila.getValue().getProcesadoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("Un calendario ya procesado no vuelve a programar nada")
    void ignoraUnEventoRepetido() {
        when(procesados.existsById(EVENT_ID)).thenReturn(true);

        boolean procesado = consumidor.procesar(CALENDARIO);

        assertThat(procesado).isFalse();
        verifyNoInteractions(programacion);
        verify(procesados, never()).save(any());
    }

    @Test
    @DisplayName("Si el caso de uso rechaza el calendario, no se registra en el inbox")
    void siFallaNoSeRegistra() {
        when(procesados.existsById(EVENT_ID)).thenReturn(false);
        when(programacion.programar(any())).thenThrow(new IllegalStateException("Ya existe una orden"));

        assertThatThrownBy(() -> consumidor.procesar(CALENDARIO))
                .isInstanceOf(IllegalStateException.class);
        verify(procesados, never()).save(any());
        assertThat(contexto.actual()).isEmpty();
    }

    @Test
    @DisplayName("Un mensaje invalido se rechaza antes de consultar el inbox")
    void mensajeInvalido() {
        assertThatThrownBy(() -> consumidor.procesar("{}"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(procesados, programacion);
    }
}
