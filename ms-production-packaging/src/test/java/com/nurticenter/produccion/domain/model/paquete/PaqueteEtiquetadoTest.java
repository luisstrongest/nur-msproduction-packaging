package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.event.PaqueteListo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - Agregado raiz PaqueteEtiquetado.
 *
 * Ciclo de vida: ENVASADO_PENDIENTE -> ENVASADO -> ETIQUETADO -> LISTO
 */
@DisplayName("PaqueteEtiquetado (Aggregate Root)")
class PaqueteEtiquetadoTest {

    private DatosPaciente datosCompletos() {
        DireccionImpresa direccion = new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz");
        return new DatosPaciente(PacienteId.de("paciente-1"), "Ana Perez", direccion);
    }

    private PaqueteEtiquetado paqueteNuevo() {
        return PaqueteEtiquetado.crear(PacienteId.de("paciente-1"), "orden-1");
    }

    @Test
    void seCreaEnEstadoEnvasadoPendienteYSinEtiqueta() {
        PaqueteEtiquetado paquete = paqueteNuevo();

        assertThat(paquete.estado()).isEqualTo(EstadoPaquete.ENVASADO_PENDIENTE);
        assertThat(paquete.etiqueta()).isNull();
        assertThat(paquete.pacienteId().valor()).isEqualTo("paciente-1");
        assertThat(paquete.ordenId()).isEqualTo("orden-1");
        assertThat(paquete.id().valor()).isNotBlank();
        assertThat(paquete.pullEventos()).isEmpty();   // crear no emite eventos
    }

    @Nested
    @DisplayName("Transiciones invalidas")
    class TransicionesInvalidas {

        @Test
        void noSePuedeEtiquetarSinEnvasar() {
            PaqueteEtiquetado paquete = paqueteNuevo();

            assertThatThrownBy(() -> paquete.generarEtiqueta(datosCompletos()))
                    .isInstanceOf(EtiquetaSinEnvasadoException.class);
        }

        @Test
        void noSePuedeEnvasarDosVeces() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();

            assertThatThrownBy(paquete::registrarEnvasado)
                    .isInstanceOf(TransicionEstadoInvalidaException.class)
                    .hasMessageContaining("ENVASADO");
        }

        @Test
        void noSePuedeGenerarEtiquetaConDatosNulos() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();

            assertThatThrownBy(() -> paquete.generarEtiqueta(null))
                    .isInstanceOf(DatosPacienteIncompletosException.class);
        }

        @Test
        void noSePuedeGenerarEtiquetaConDatosIncompletos() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();
            DatosPaciente incompletos = new DatosPaciente(
                    PacienteId.de("paciente-1"), "   ",
                    new DireccionImpresa("Av. Mutualista", "450", null, "Santa Cruz"));

            assertThatThrownBy(() -> paquete.generarEtiqueta(incompletos))
                    .isInstanceOf(DatosPacienteIncompletosException.class);
        }

        @Test
        void noSePuedeMarcarListoSinEtiqueta() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();

            assertThatThrownBy(paquete::marcarListo).isInstanceOf(TransicionEstadoInvalidaException.class);
        }

        @Test
        @DisplayName("Un paquete ETIQUETADO con etiqueta invalida no puede marcarse LISTO")
        void noSePuedeMarcarListoConEtiquetaInvalida() {
            // Se reconstituye un estado corrupto (ETIQUETADO pero sin etiqueta),
            // que es justo lo que protege la invariante de marcarListo().
            PaqueteEtiquetado paquete = PaqueteEtiquetado.reconstituir(
                    PaqueteId.de("paquete-1"), PacienteId.de("paciente-1"), "orden-1",
                    EstadoPaquete.ETIQUETADO, null);

            assertThatThrownBy(paquete::marcarListo).isInstanceOf(EtiquetaInvalidaException.class);
        }
    }

    @Nested
    @DisplayName("Camino feliz")
    class CaminoFeliz {

        @Test
        void cicloDeVidaCompletoHastaListoEmiteEvento() {
            PaqueteEtiquetado paquete = paqueteNuevo();

            paquete.registrarEnvasado();
            paquete.generarEtiqueta(datosCompletos());
            paquete.marcarListo();

            assertThat(paquete.estado()).isEqualTo(EstadoPaquete.LISTO);
            assertThat(paquete.etiqueta().esValida()).isTrue();
            assertThat(paquete.pullEventos().get(0)).isInstanceOf(PaqueteListo.class);
        }

        @Test
        @DisplayName("La etiqueta copia los datos del paciente y genera el codigo desde el PaqueteId")
        void laEtiquetaSeGeneraDesdeLosDatosDelPaciente() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();

            paquete.generarEtiqueta(datosCompletos());

            String sufijoEsperado = paquete.id().valor().replace("-", "").substring(0, 8).toUpperCase();
            assertThat(paquete.estado()).isEqualTo(EstadoPaquete.ETIQUETADO);
            assertThat(paquete.etiqueta().nombrePaciente()).isEqualTo("Ana Perez");
            assertThat(paquete.etiqueta().direccion().ciudad()).isEqualTo("Santa Cruz");
            assertThat(paquete.etiqueta().codigo().valor()).isEqualTo("PKG-" + sufijoEsperado);
        }

        @Test
        @DisplayName("El evento PaqueteListo lleva orden, paciente y codigo, para que BC5 lo rastree")
        void elEventoPaqueteListoLlevaLosDatosDeTrazabilidad() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();
            paquete.generarEtiqueta(datosCompletos());
            LocalDateTime antes = LocalDateTime.now();

            paquete.marcarListo();

            PaqueteListo evento = (PaqueteListo) paquete.pullEventos().get(0);
            assertThat(evento.paqueteId()).isEqualTo(paquete.id().valor());
            assertThat(evento.ordenId()).isEqualTo("orden-1");
            assertThat(evento.pacienteId()).isEqualTo("paciente-1");
            assertThat(evento.codigoIdentificacion()).isEqualTo(paquete.etiqueta().codigo().valor());
            assertThat(evento.agregadoId()).isEqualTo(paquete.id().valor());
            assertThat(evento.nombreEvento()).isEqualTo("bc4.PaqueteListo");
            assertThat(evento.occurredAt()).isAfterOrEqualTo(antes);
        }

        @Test
        @DisplayName("pullEventos() vacia la bandeja: el evento no se publica dos veces")
        void pullEventosLimpiaLaBandeja() {
            PaqueteEtiquetado paquete = paqueteNuevo();
            paquete.registrarEnvasado();
            paquete.generarEtiqueta(datosCompletos());
            paquete.marcarListo();

            assertThat(paquete.pullEventos()).hasSize(1);
            assertThat(paquete.pullEventos()).isEmpty();
        }

        @Test
        @DisplayName("reconstituir() rehidrata estado y etiqueta sin emitir eventos")
        void reconstituirNoEmiteEventos() {
            Etiqueta etiqueta = Etiqueta.reconstituir(
                    "Ana Perez",
                    new DireccionImpresa("Av. Mutualista", "450", null, "Santa Cruz"),
                    CodigoIdentificacion.de("PKG-ABC12345", LocalDateTime.now()),
                    LocalDateTime.now());

            PaqueteEtiquetado paquete = PaqueteEtiquetado.reconstituir(
                    PaqueteId.de("paquete-1"), PacienteId.de("paciente-1"), "orden-1",
                    EstadoPaquete.LISTO, etiqueta);

            assertThat(paquete.pullEventos()).isEmpty();
            assertThat(paquete.estado()).isEqualTo(EstadoPaquete.LISTO);
            assertThat(paquete.etiqueta()).isEqualTo(etiqueta);
        }
    }
}
