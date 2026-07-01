package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.event.PaqueteListo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaqueteEtiquetadoTest {

    private DatosPaciente datosCompletos() {
        DireccionImpresa direccion = new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz");
        return new DatosPaciente(PacienteId.de("paciente-1"), "Ana Perez", direccion);
    }

    @Test
    void noSePuedeEtiquetarSinEnvasar() {
        PaqueteEtiquetado paquete = PaqueteEtiquetado.crear(PacienteId.de("paciente-1"), "orden-1");

        assertThatThrownBy(() -> paquete.generarEtiqueta(datosCompletos()))
                .isInstanceOf(EtiquetaSinEnvasadoException.class);
    }

    @Test
    void noSePuedeGenerarEtiquetaConDatosIncompletos() {
        PaqueteEtiquetado paquete = PaqueteEtiquetado.crear(PacienteId.de("paciente-1"), "orden-1");
        paquete.registrarEnvasado();

        assertThatThrownBy(() -> paquete.generarEtiqueta(null))
                .isInstanceOf(DatosPacienteIncompletosException.class);
    }

    @Test
    void noSePuedeMarcarListoSinEtiqueta() {
        PaqueteEtiquetado paquete = PaqueteEtiquetado.crear(PacienteId.de("paciente-1"), "orden-1");
        paquete.registrarEnvasado();

        assertThatThrownBy(paquete::marcarListo).isInstanceOf(TransicionEstadoInvalidaException.class);
    }

    @Test
    void cicloDeVidaCompletoHastaListoEmiteEvento() {
        PaqueteEtiquetado paquete = PaqueteEtiquetado.crear(PacienteId.de("paciente-1"), "orden-1");

        paquete.registrarEnvasado();
        paquete.generarEtiqueta(datosCompletos());
        paquete.marcarListo();

        assertThat(paquete.estado()).isEqualTo(EstadoPaquete.LISTO);
        assertThat(paquete.etiqueta().esValida()).isTrue();
        assertThat(paquete.pullEventos().get(0)).isInstanceOf(PaqueteListo.class);
    }
}
