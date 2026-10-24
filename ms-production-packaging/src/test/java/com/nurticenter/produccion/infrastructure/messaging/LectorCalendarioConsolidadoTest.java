package com.nurticenter.produccion.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.EntregaCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.RecetaCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - LectorCalendarioConsolidado (ACL de entrada).
 *
 * El JSON de prueba sigue el contrato bc3.CalendarioConsolidadoCerrado de
 * nur_proy_final/contracts, con campos extra que el lector debe ignorar.
 */
@DisplayName("LectorCalendarioConsolidado (unit test)")
class LectorCalendarioConsolidadoTest {

    private static final String CALENDARIO = """
            {
              "eventId": "3f2a0014-9c1e-4b7a-a1d2-7e5f0c8b1014",
              "eventName": "bc3.CalendarioConsolidadoCerrado",
              "eventVersion": "1.0",
              "occurredOn": "2026-10-22T03:59:59Z",
              "source": "MsCateringSubscription",
              "correlationId": "corr-entrega-2026-10-23",
              "campoNuevo": "se ignora",
              "data": {
                "idCalendario": "CAL-20261023",
                "fechaEntrega": "2026-10-23",
                "totalEntregas": 2,
                "entregas": [
                  {
                    "idCalendarioDia": "CD-70011", "idSuscripcion": "SUS-501", "idPaciente": "PAC-1024",
                    "nombrePaciente": "Juan Perez Rojas",
                    "direccion": {"idDireccion": "DIR-58", "direccion": "Av. Cristo Redentor #1450",
                                  "latitud": -17.7612, "longitud": -63.1825},
                    "recetas": [
                      {"idReceta": "REC-14", "nombre": "Avena cocida", "hora": "08:00", "porcionGramos": 250},
                      {"idReceta": "REC-45", "nombre": "Pechuga a la plancha", "hora": "19:30", "porcionGramos": 350}
                    ]
                  },
                  {
                    "idCalendarioDia": "CD-70012", "idSuscripcion": "SUS-502", "idPaciente": "PAC-1031",
                    "direccion": {"idDireccion": "", "latitud": "sin dato"},
                    "recetas": [
                      {"idReceta": "REC-14", "nombre": "Avena cocida", "hora": "08:00", "porcionGramos": 250}
                    ]
                  }
                ]
              }
            }
            """;

    private final LectorCalendarioConsolidado lector = new LectorCalendarioConsolidado(new ObjectMapper());

    @Test
    @DisplayName("Lee la identidad del evento, su correlacion y el calendario")
    void leeElSobreYElCalendario() {
        var recibido = lector.leer(CALENDARIO);

        assertThat(recibido.eventId()).isEqualTo("3f2a0014-9c1e-4b7a-a1d2-7e5f0c8b1014");
        assertThat(recibido.correlationId()).isEqualTo("corr-entrega-2026-10-23");
        assertThat(recibido.comando().idCalendario()).isEqualTo("CAL-20261023");
        assertThat(recibido.comando().fechaEntrega()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(recibido.comando().entregas()).hasSize(2);
    }

    @Test
    @DisplayName("Traduce cada entrega con su direccion y sus recetas")
    void traduceLasEntregas() {
        EntregaCommand primera = lector.leer(CALENDARIO).comando().entregas().get(0);

        assertThat(primera).isEqualTo(new EntregaCommand("CD-70011", "SUS-501", "PAC-1024", "DIR-58",
                -17.7612, -63.1825, java.util.List.of(
                        new RecetaCommand("REC-14", "Avena cocida", "08:00", 250),
                        new RecetaCommand("REC-45", "Pechuga a la plancha", "19:30", 350))));
    }

    @Test
    @DisplayName("Una direccion sin id o con coordenadas no numericas queda sin esos datos")
    void direccionIncompletaQuedaEnNull() {
        EntregaCommand segunda = lector.leer(CALENDARIO).comando().entregas().get(1);

        assertThat(segunda.idDireccion()).isNull();
        assertThat(segunda.latitud()).isNull();
        assertThat(segunda.longitud()).isNull();
    }

    @Test
    @DisplayName("nombreDe() devuelve el eventName del sobre, o vacio si no es JSON")
    void nombreDelEvento() {
        assertThat(lector.nombreDe(CALENDARIO)).isEqualTo("bc3.CalendarioConsolidadoCerrado");
        assertThat(lector.nombreDe("{\"data\": {}}")).isEmpty();
        assertThat(lector.nombreDe("no es json")).isEmpty();
    }

    @Test
    @DisplayName("Rechaza otro evento")
    void rechazaOtroEvento() {
        String otro = CALENDARIO.replace("bc3.CalendarioConsolidadoCerrado", "bc3.SuscripcionCreada");

        assertThatThrownBy(() -> lector.leer(otro))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bc3.SuscripcionCreada");
    }

    @Test
    @DisplayName("Rechaza un mensaje que no es JSON")
    void rechazaJsonInvalido() {
        assertThatThrownBy(() -> lector.leer("{no es json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON");
    }

    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(delimiter = '|', value = {
            "\"eventId\": \"3f2a0014-9c1e-4b7a-a1d2-7e5f0c8b1014\"|\"eventId\": \"\"|Falta el campo eventId",
            "\"correlationId\": \"corr-entrega-2026-10-23\"|\"x\": 1|Falta el campo correlationId",
            "\"idCalendario\": \"CAL-20261023\"|\"idCalendario\": 7|Falta el campo idCalendario",
            "\"fechaEntrega\": \"2026-10-23\"|\"fechaEntrega\": \"23/10/2026\"|Fecha invalida en fechaEntrega",
            "\"idPaciente\": \"PAC-1031\"|\"idPaciente\": null|Falta el campo idPaciente",
            "\"hora\": \"19:30\"|\"hora\": \"\"|Falta el campo hora",
            "\"porcionGramos\": 350|\"porcionGramos\": \"350\"|Falta porcionGramos numerico"
    })
    @DisplayName("Rechaza un calendario con campos obligatorios ausentes o invalidos")
    void rechazaCamposInvalidos(String original, String alterado, String mensaje) {
        String roto = CALENDARIO.replace(original, alterado);

        assertThatThrownBy(() -> lector.leer(roto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(mensaje);
    }

    @Test
    @DisplayName("Rechaza un calendario sin entregas")
    void rechazaSinEntregas() {
        String sinEntregas = """
                {"eventId": "e-1", "eventName": "bc3.CalendarioConsolidadoCerrado", "correlationId": "c-1",
                 "data": {"idCalendario": "CAL-1", "fechaEntrega": "2026-10-23", "entregas": []}}
                """;

        assertThatThrownBy(() -> lector.leer(sinEntregas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no trae entregas");
    }

    @Test
    @DisplayName("Rechaza una entrega sin recetas")
    void rechazaEntregaSinRecetas() {
        String sinRecetas = """
                {"eventId": "e-1", "eventName": "bc3.CalendarioConsolidadoCerrado", "correlationId": "c-1",
                 "data": {"idCalendario": "CAL-1", "fechaEntrega": "2026-10-23", "entregas": [
                   {"idCalendarioDia": "CD-1", "idSuscripcion": "SUS-1", "idPaciente": "PAC-1"}]}}
                """;

        assertThatThrownBy(() -> lector.leer(sinRecetas))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CD-1 no trae recetas");
    }
}
