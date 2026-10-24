package com.nurticenter.produccion.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.event.DomainEvent;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO - MapeadorEventoIntegracion.
 *
 * Verifica que cada evento de dominio se traduce al sobre comun del contrato
 * (nur_proy_final/contracts): campos del sobre, occurredOn en UTC, correlationId
 * por orden y nombres de "data" del contrato.
 */
@DisplayName("MapeadorEventoIntegracion (unit test)")
class MapeadorEventoIntegracionTest {

    private static final LocalDateTime OCURRIDO = LocalDateTime.of(2026, 10, 22, 21, 15, 0);

    private final ObjectMapper json = new ObjectMapper();
    private final MapeadorEventoIntegracion mapeador = new MapeadorEventoIntegracion(json, ZoneOffset.UTC);

    @Test
    @DisplayName("El sobre trae los 7 campos del contrato con los valores de BC4")
    void elSobreTraeLosCamposDelContrato() throws Exception {
        var mensaje = mapeador.mapear(new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO));

        JsonNode sobre = json.readTree(mensaje.json());
        assertThat(sobre.fieldNames()).toIterable()
                .containsExactly("eventId", "eventName", "eventVersion", "occurredOn", "source", "correlationId", "data");
        assertThat(sobre.path("eventId").asText()).isEqualTo(mensaje.eventId())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(sobre.path("eventName").asText()).isEqualTo("bc4.OrdenGenerada");
        assertThat(sobre.path("eventVersion").asText()).isEqualTo("1.0");
        assertThat(sobre.path("source").asText()).isEqualTo("MsProductionPackaging");
        assertThat(sobre.path("occurredOn").asText()).isEqualTo("2026-10-22T21:15:00Z");
    }

    @Test
    @DisplayName("occurredOn se convierte de la zona del dominio a UTC")
    void occurredOnSeConvierteAUtc() throws Exception {
        var mapeadorBolivia = new MapeadorEventoIntegracion(json, ZoneId.of("America/La_Paz"));

        var mensaje = mapeadorBolivia.mapear(new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO));

        assertThat(json.readTree(mensaje.json()).path("occurredOn").asText()).isEqualTo("2026-10-23T01:15:00Z");
    }

    @Test
    @DisplayName("OrdenGenerada: key = orden y data con idOrdenProduccion, fecha y total de items")
    void mapeaOrdenGenerada() throws Exception {
        var mensaje = mapeador.mapear(new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO));

        JsonNode sobre = json.readTree(mensaje.json());
        assertThat(mensaje.key()).isEqualTo("orden-1");
        assertThat(mensaje.eventName()).isEqualTo("bc4.OrdenGenerada");
        assertThat(sobre.path("correlationId").asText()).isEqualTo("corr-orden-orden-1");
        assertThat(sobre.path("data").path("idOrdenProduccion").asText()).isEqualTo("orden-1");
        assertThat(sobre.path("data").path("fechaProduccion").asText()).isEqualTo("2026-10-22");
        assertThat(sobre.path("data").path("totalItems").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("OrdenCompletada: data con los paquetes y su total")
    void mapeaOrdenCompletada() throws Exception {
        var mensaje = mapeador.mapear(new OrdenCompletada("orden-1", List.of("p-1", "p-2"), OCURRIDO));

        JsonNode data = json.readTree(mensaje.json()).path("data");
        assertThat(mensaje.key()).isEqualTo("orden-1");
        assertThat(data.path("idOrdenProduccion").asText()).isEqualTo("orden-1");
        assertThat(data.path("totalPaquetes").asInt()).isEqualTo(2);
        assertThat(data.path("paquetesIds")).extracting(JsonNode::asText).containsExactly("p-1", "p-2");
    }

    @Test
    @DisplayName("PaqueteListo: key = paquete, pero correlationId de su orden")
    void mapeaPaqueteListoConLaCorrelacionDeSuOrden() throws Exception {
        var mensaje = mapeador.mapear(new PaqueteListo("p-1", "orden-1", "PAC-1024", "PKG-ABC12345", OCURRIDO));

        JsonNode sobre = json.readTree(mensaje.json());
        assertThat(mensaje.key()).isEqualTo("p-1");
        assertThat(sobre.path("correlationId").asText()).isEqualTo("corr-orden-orden-1");
        assertThat(sobre.path("data").path("idPaquete").asText()).isEqualTo("p-1");
        assertThat(sobre.path("data").path("etiqueta").asText()).isEqualTo("PKG-ABC12345");
        assertThat(sobre.path("data").path("idOrdenProduccion").asText()).isEqualTo("orden-1");
        assertThat(sobre.path("data").path("idPaciente").asText()).isEqualTo("PAC-1024");
    }

    @Test
    @DisplayName("Un evento sin mapeo propio viaja igual, con el id del agregado en data")
    void unEventoSinMapeoPropioLlevaElAgregado() throws Exception {
        DomainEvent otro = new EventoDePrueba("agg-9", OCURRIDO);

        var mensaje = mapeador.mapear(otro);

        JsonNode sobre = json.readTree(mensaje.json());
        assertThat(sobre.path("eventName").asText()).isEqualTo("bc4.EventoDePrueba");
        assertThat(sobre.path("data").path("agregadoId").asText()).isEqualTo("agg-9");
    }

    @Test
    @DisplayName("Cada mapeo genera un eventId distinto")
    void cadaMapeoGeneraUnEventIdDistinto() {
        var evento = new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO);

        assertThat(mapeador.mapear(evento).eventId()).isNotEqualTo(mapeador.mapear(evento).eventId());
    }

    @Test
    @DisplayName("Si el JSON no se puede generar, falla con IllegalStateException")
    void fallaSiNoSePuedeSerializar() throws Exception {
        ObjectMapper roto = mock(ObjectMapper.class);
        when(roto.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonGenerationException("roto", (com.fasterxml.jackson.core.JsonGenerator) null));
        var mapeadorRoto = new MapeadorEventoIntegracion(roto, ZoneOffset.UTC);

        assertThatThrownBy(() -> mapeadorRoto.mapear(new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bc4.OrdenGenerada");
    }

    private record EventoDePrueba(String agregadoId, LocalDateTime occurredAt) implements DomainEvent {
    }
}
