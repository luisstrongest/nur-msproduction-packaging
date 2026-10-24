package com.nurticenter.produccion.domain.model.paquete;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DestinoEntrega (value object)")
class DestinoEntregaTest {

    private static final LocalDate ENTREGA = LocalDate.of(2026, 10, 23);

    @Test
    @DisplayName("Guarda los datos de la entrega del calendario")
    void guardaLosDatosDeLaEntrega() {
        var destino = new DestinoEntrega("CD-70011", "SUS-501", ENTREGA, "DIR-58", -17.7612, -63.1825, 4);

        assertThat(destino.idCalendarioDia()).isEqualTo("CD-70011");
        assertThat(destino.idSuscripcion()).isEqualTo("SUS-501");
        assertThat(destino.fechaEntrega()).isEqualTo(ENTREGA);
        assertThat(destino.idDireccion()).isEqualTo("DIR-58");
        assertThat(destino.latitud()).isEqualTo(-17.7612);
        assertThat(destino.longitud()).isEqualTo(-63.1825);
        assertThat(destino.nroPorciones()).isEqualTo(4);
    }

    @Test
    @DisplayName("La direccion y las coordenadas son opcionales")
    void direccionYCoordenadasOpcionales() {
        var destino = new DestinoEntrega("CD-70011", "SUS-501", ENTREGA, null, null, null, 1);

        assertThat(destino.idDireccion()).isNull();
        assertThat(destino.latitud()).isNull();
        assertThat(destino.longitud()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180"})
    @DisplayName("Acepta coordenadas en el borde del rango")
    void aceptaCoordenadasEnElBorde(double latitud, double longitud) {
        var destino = new DestinoEntrega("CD-1", "SUS-1", ENTREGA, null, latitud, longitud, 1);

        assertThat(destino.latitud()).isEqualTo(latitud);
        assertThat(destino.longitud()).isEqualTo(longitud);
    }

    @ParameterizedTest
    @ValueSource(doubles = {90.0001, -90.0001})
    void rechazaUnaLatitudFueraDeRango(double latitud) {
        assertThatThrownBy(() -> new DestinoEntrega("CD-1", "SUS-1", ENTREGA, null, latitud, 0.0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("latitud");
    }

    @ParameterizedTest
    @ValueSource(doubles = {180.0001, -180.0001})
    void rechazaUnaLongitudFueraDeRango(double longitud) {
        assertThatThrownBy(() -> new DestinoEntrega("CD-1", "SUS-1", ENTREGA, null, 0.0, longitud, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("longitud");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void exigeAlMenosUnaPorcion(int porciones) {
        assertThatThrownBy(() -> new DestinoEntrega("CD-1", "SUS-1", ENTREGA, null, null, null, porciones))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nroPorciones");
    }

    @Test
    void exigeElDiaDelCalendario() {
        assertThatThrownBy(() -> new DestinoEntrega(null, "SUS-1", ENTREGA, null, null, null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idCalendarioDia");
    }

    @Test
    void exigeLaSuscripcion() {
        assertThatThrownBy(() -> new DestinoEntrega("CD-1", null, ENTREGA, null, null, null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idSuscripcion");
    }

    @Test
    void exigeLaFechaDeEntrega() {
        assertThatThrownBy(() -> new DestinoEntrega("CD-1", "SUS-1", null, null, null, null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("fechaEntrega");
    }
}
