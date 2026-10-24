package com.nurticenter.produccion.infrastructure.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurticenter.produccion.domain.event.DomainEvent;
import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import com.nurticenter.produccion.domain.model.orden.OrigenCalendario;
import com.nurticenter.produccion.domain.model.paquete.DestinoEntrega;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
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
 * (nur_proy_final/contracts): campos del sobre, fechas en hora de Bolivia, el correlationId
 * recibido y los nombres de "data" del contrato, con y sin datos del calendario de BC3.
 */
@DisplayName("MapeadorEventoIntegracion (unit test)")
class MapeadorEventoIntegracionTest {

    private static final LocalDateTime OCURRIDO = LocalDateTime.of(2026, 10, 22, 21, 15, 0);
    private static final String CORRELACION = "corr-entrega-2026-10-23";
    private static final DireccionImpresa DIRECCION =
            new DireccionImpresa("Av. Cristo Redentor", "#1450", "Frente al Banco Union", "Santa Cruz");
    private static final DestinoEntrega DESTINO = new DestinoEntrega(
            "CD-70011", "SUS-501", LocalDate.of(2026, 10, 23), "DIR-58", -17.7612, -63.1825, 4);

    private final ObjectMapper json = new ObjectMapper();
    private final MapeadorEventoIntegracion mapeador = new MapeadorEventoIntegracion(json, ZoneOffset.UTC);

    @Test
    @DisplayName("El sobre trae los 7 campos del contrato con los valores de BC4 y la correlacion recibida")
    void elSobreTraeLosCamposDelContrato() throws Exception {
        var mensaje = mapeador.mapear(ordenGenerada(), CORRELACION);

        JsonNode sobre = json.readTree(mensaje.json());
        assertThat(sobre.fieldNames()).toIterable()
                .containsExactly("eventId", "eventName", "eventVersion", "occurredOn", "source", "correlationId", "data");
        assertThat(sobre.path("eventId").asText()).isEqualTo(mensaje.eventId())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        assertThat(sobre.path("eventName").asText()).isEqualTo("bc4.OrdenGenerada");
        assertThat(sobre.path("eventVersion").asText()).isEqualTo("1.0");
        assertThat(sobre.path("source").asText()).isEqualTo("MsProductionPackaging");
        assertThat(sobre.path("occurredOn").asText()).isEqualTo("2026-10-22T17:15:00-04:00");
        assertThat(sobre.path("correlationId").asText()).isEqualTo(CORRELACION);
    }

    @Test
    @DisplayName("occurredOn queda en hora de Bolivia sea cual sea la zona del dominio")
    void occurredOnQuedaEnHoraDeBolivia() throws Exception {
        var mapeadorBolivia = new MapeadorEventoIntegracion(json, ZoneId.of("America/La_Paz"));
        var mapeadorMadrid = new MapeadorEventoIntegracion(json, ZoneId.of("Europe/Madrid"));

        var enBolivia = mapeadorBolivia.mapear(ordenGenerada(), CORRELACION);
        var enMadrid = mapeadorMadrid.mapear(ordenGenerada(), CORRELACION);

        assertThat(json.readTree(enBolivia.json()).path("occurredOn").asText()).isEqualTo("2026-10-22T21:15:00-04:00");
        assertThat(json.readTree(enMadrid.json()).path("occurredOn").asText()).isEqualTo("2026-10-22T15:15:00-04:00");
    }

    @Test
    @DisplayName("OrdenGenerada: key = orden y data con idOrdenProduccion, fecha y total de items")
    void mapeaOrdenGenerada() throws Exception {
        var mensaje = mapeador.mapear(ordenGenerada(), CORRELACION);

        JsonNode data = json.readTree(mensaje.json()).path("data");
        assertThat(mensaje.key()).isEqualTo("orden-1");
        assertThat(mensaje.eventName()).isEqualTo("bc4.OrdenGenerada");
        assertThat(data.fieldNames()).toIterable()
                .containsExactly("idOrdenProduccion", "fechaProduccion", "totalItems");
        assertThat(data.path("idOrdenProduccion").asText()).isEqualTo("orden-1");
        assertThat(data.path("fechaProduccion").asText()).isEqualTo("2026-10-22");
        assertThat(data.path("totalItems").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("OrdenCompletada de un calendario: data con todos los campos del contrato")
    void mapeaOrdenCompletadaDeUnCalendario() throws Exception {
        var mapeadorBolivia = new MapeadorEventoIntegracion(json, ZoneId.of("America/La_Paz"));
        var evento = new OrdenCompletada("orden-1", LocalDate.of(2026, 10, 22),
                new OrigenCalendario("CAL-20261023", LocalDate.of(2026, 10, 23)), 8,
                List.of("p-1", "p-2"), LocalDateTime.of(2026, 10, 22, 21, 40, 0, 123_000_000));

        var mensaje = mapeadorBolivia.mapear(evento, CORRELACION);

        JsonNode data = json.readTree(mensaje.json()).path("data");
        assertThat(mensaje.key()).isEqualTo("orden-1");
        assertThat(data.fieldNames()).toIterable().containsExactly(
                "idOrdenProduccion", "idCalendario", "fechaProduccion", "fechaEntrega",
                "totalRecetasPreparadas", "totalPaquetes", "paquetesIds", "fechaCierre");
        assertThat(data.path("idOrdenProduccion").asText()).isEqualTo("orden-1");
        assertThat(data.path("idCalendario").asText()).isEqualTo("CAL-20261023");
        assertThat(data.path("fechaProduccion").asText()).isEqualTo("2026-10-22");
        assertThat(data.path("fechaEntrega").asText()).isEqualTo("2026-10-23");
        assertThat(data.path("totalRecetasPreparadas").asInt()).isEqualTo(8);
        assertThat(data.path("totalPaquetes").asInt()).isEqualTo(2);
        assertThat(data.path("paquetesIds")).extracting(JsonNode::asText).containsExactly("p-1", "p-2");
        assertThat(data.path("fechaCierre").asText()).isEqualTo("2026-10-22T21:40:00-04:00");
    }

    @Test
    @DisplayName("OrdenCompletada creada a mano: los campos del calendario viajan en null")
    void mapeaOrdenCompletadaSinCalendario() throws Exception {
        var evento = new OrdenCompletada("orden-1", LocalDate.of(2026, 10, 22), null, 5, List.of(), OCURRIDO);

        JsonNode data = json.readTree(mapeador.mapear(evento, CORRELACION).json()).path("data");

        assertThat(data.has("idCalendario")).isTrue();
        assertThat(data.path("idCalendario").isNull()).isTrue();
        assertThat(data.path("fechaEntrega").isNull()).isTrue();
        assertThat(data.path("totalPaquetes").asInt()).isZero();
        assertThat(data.path("fechaCierre").asText()).isEqualTo("2026-10-22T17:15:00-04:00");
    }

    @Test
    @DisplayName("PaqueteListo de un calendario: key = paquete y data con destino, etiqueta y direccion")
    void mapeaPaqueteListoDeUnCalendario() throws Exception {
        var evento = new PaqueteListo("p-1", "orden-1", "PAC-1024", "PKG-ABC12345", "Juan Perez Rojas",
                DIRECCION, DESTINO, OCURRIDO);

        var mensaje = mapeador.mapear(evento, CORRELACION);

        JsonNode data = json.readTree(mensaje.json()).path("data");
        assertThat(mensaje.key()).isEqualTo("p-1");
        assertThat(data.fieldNames()).toIterable().containsExactly(
                "idPaquete", "etiqueta", "idOrdenProduccion", "idCalendarioDia", "idSuscripcion",
                "idPaciente", "nombrePaciente", "fechaEntrega", "direccionEntrega", "nroPorciones");
        assertThat(data.path("idPaquete").asText()).isEqualTo("p-1");
        assertThat(data.path("etiqueta").asText()).isEqualTo("PKG-ABC12345");
        assertThat(data.path("idOrdenProduccion").asText()).isEqualTo("orden-1");
        assertThat(data.path("idCalendarioDia").asText()).isEqualTo("CD-70011");
        assertThat(data.path("idSuscripcion").asText()).isEqualTo("SUS-501");
        assertThat(data.path("idPaciente").asText()).isEqualTo("PAC-1024");
        assertThat(data.path("nombrePaciente").asText()).isEqualTo("Juan Perez Rojas");
        assertThat(data.path("fechaEntrega").asText()).isEqualTo("2026-10-23");
        assertThat(data.path("nroPorciones").asInt()).isEqualTo(4);
        JsonNode direccion = data.path("direccionEntrega");
        assertThat(direccion.fieldNames()).toIterable()
                .containsExactly("idDireccion", "direccion", "referencia", "ciudad", "latitud", "longitud");
        assertThat(direccion.path("idDireccion").asText()).isEqualTo("DIR-58");
        assertThat(direccion.path("direccion").asText()).isEqualTo("Av. Cristo Redentor #1450");
        assertThat(direccion.path("referencia").asText()).isEqualTo("Frente al Banco Union");
        assertThat(direccion.path("ciudad").asText()).isEqualTo("Santa Cruz");
        assertThat(direccion.path("latitud").asDouble()).isEqualTo(-17.7612);
        assertThat(direccion.path("longitud").asDouble()).isEqualTo(-63.1825);
    }

    @Test
    @DisplayName("PaqueteListo creado a mano: sin destino, pero con lo impreso en la etiqueta")
    void mapeaPaqueteListoSinDestino() throws Exception {
        var evento = new PaqueteListo("p-1", "orden-1", "PAC-1024", "PKG-ABC12345", "Juan Perez",
                DIRECCION, null, OCURRIDO);

        JsonNode data = json.readTree(mapeador.mapear(evento, CORRELACION).json()).path("data");

        assertThat(data.path("idCalendarioDia").isNull()).isTrue();
        assertThat(data.path("idSuscripcion").isNull()).isTrue();
        assertThat(data.path("fechaEntrega").isNull()).isTrue();
        assertThat(data.path("nroPorciones").isNull()).isTrue();
        assertThat(data.path("nombrePaciente").asText()).isEqualTo("Juan Perez");
        assertThat(data.path("direccionEntrega").path("idDireccion").isNull()).isTrue();
        assertThat(data.path("direccionEntrega").path("latitud").isNull()).isTrue();
        assertThat(data.path("direccionEntrega").path("direccion").asText()).isEqualTo("Av. Cristo Redentor #1450");
    }

    @Test
    @DisplayName("La orden de un PaqueteListo es su ordenId; la de los eventos de orden, su agregado")
    void ordenDeCadaEvento() {
        var paquete = new PaqueteListo("p-1", "orden-7", "PAC-1", "PKG-1", "Ana", DIRECCION, null, OCURRIDO);

        assertThat(MapeadorEventoIntegracion.ordenDe(paquete)).isEqualTo("orden-7");
        assertThat(MapeadorEventoIntegracion.ordenDe(ordenGenerada())).isEqualTo("orden-1");
    }

    @Test
    @DisplayName("Un evento sin mapeo propio viaja igual, con el id del agregado en data")
    void unEventoSinMapeoPropioLlevaElAgregado() throws Exception {
        DomainEvent otro = new EventoDePrueba("agg-9", OCURRIDO);

        var mensaje = mapeador.mapear(otro, CORRELACION);

        JsonNode sobre = json.readTree(mensaje.json());
        assertThat(sobre.path("eventName").asText()).isEqualTo("bc4.EventoDePrueba");
        assertThat(sobre.path("data").path("agregadoId").asText()).isEqualTo("agg-9");
    }

    @Test
    @DisplayName("Cada mapeo genera un eventId distinto")
    void cadaMapeoGeneraUnEventIdDistinto() {
        var evento = ordenGenerada();

        assertThat(mapeador.mapear(evento, CORRELACION).eventId())
                .isNotEqualTo(mapeador.mapear(evento, CORRELACION).eventId());
    }

    @Test
    @DisplayName("Si el JSON no se puede generar, falla con IllegalStateException")
    void fallaSiNoSePuedeSerializar() throws Exception {
        ObjectMapper roto = mock(ObjectMapper.class);
        when(roto.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonGenerationException("roto", (com.fasterxml.jackson.core.JsonGenerator) null));
        var mapeadorRoto = new MapeadorEventoIntegracion(roto, ZoneOffset.UTC);

        assertThatThrownBy(() -> mapeadorRoto.mapear(ordenGenerada(), CORRELACION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bc4.OrdenGenerada");
    }

    private static OrdenGenerada ordenGenerada() {
        return new OrdenGenerada("orden-1", LocalDate.of(2026, 10, 22), 3, OCURRIDO);
    }

    private record EventoDePrueba(String agregadoId, LocalDateTime occurredAt) implements DomainEvent {
    }
}
