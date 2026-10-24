package com.nurticenter.produccion.infrastructure.persistence.paquete;

import com.nurticenter.produccion.domain.model.paquete.CodigoIdentificacion;
import com.nurticenter.produccion.domain.model.paquete.DestinoEntrega;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.Etiqueta;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * TEST UNITARIO CON MOCKS - PaquetePersistenceAdapter.
 *
 * Verifica el mapeo del agregado (con y sin Etiqueta) contra un mock de
 * Spring Data JPA. La Etiqueta es opcional: solo existe a partir del estado
 * ETIQUETADO, y el mapper debe respetarlo en ambos sentidos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaquetePersistenceAdapter (unit test con mocks)")
class PaquetePersistenceAdapterTest {

    private static final String PAQUETE_ID = "paquete-1";
    private static final LocalDateTime GENERADO_EN = LocalDateTime.of(2026, 7, 15, 8, 30);

    @Mock
    private SpringDataPaqueteRepository jpa;

    @InjectMocks
    private PaquetePersistenceAdapter adaptador;

    @Captor
    private ArgumentCaptor<PaqueteJpaEntity> entidadCaptor;

    private Etiqueta etiqueta() {
        return Etiqueta.reconstituir(
                "Ana Perez",
                new DireccionImpresa("Av. Mutualista", "450", "Edificio azul", "Santa Cruz"),
                CodigoIdentificacion.de("PKG-ABC12345", GENERADO_EN),
                GENERADO_EN);
    }

    private PaqueteEtiquetado paquete(EstadoPaquete estado, Etiqueta etiqueta) {
        return PaqueteEtiquetado.reconstituir(
                PaqueteId.de(PAQUETE_ID), PacienteId.de("PAC-1001"), "orden-1", estado, etiqueta);
    }

    private PaqueteJpaEntity entidadConEtiqueta() {
        PaqueteJpaEntity entidad = new PaqueteJpaEntity(
                PAQUETE_ID, "PAC-1001", "orden-1", EstadoPaquete.LISTO);
        entidad.asignarEtiqueta("Ana Perez", "Av. Mutualista", "450", "Edificio azul",
                "Santa Cruz", "PKG-ABC12345", GENERADO_EN, GENERADO_EN);
        return entidad;
    }

    @Test
    @DisplayName("guardar() aplana la Etiqueta en columnas de la entidad")
    void guardarAplanaLaEtiqueta() {
        adaptador.guardar(paquete(EstadoPaquete.LISTO, etiqueta()));

        verify(jpa).save(entidadCaptor.capture());
        PaqueteJpaEntity entidad = entidadCaptor.getValue();

        assertThat(entidad.getId()).isEqualTo(PAQUETE_ID);
        assertThat(entidad.getPacienteId()).isEqualTo("PAC-1001");
        assertThat(entidad.getOrdenId()).isEqualTo("orden-1");
        assertThat(entidad.getEstado()).isEqualTo(EstadoPaquete.LISTO);
        assertThat(entidad.tieneEtiqueta()).isTrue();
        assertThat(entidad.getEtiquetaNombrePaciente()).isEqualTo("Ana Perez");
        assertThat(entidad.getEtiquetaCalle()).isEqualTo("Av. Mutualista");
        assertThat(entidad.getEtiquetaNumero()).isEqualTo("450");
        assertThat(entidad.getEtiquetaReferencia()).isEqualTo("Edificio azul");
        assertThat(entidad.getEtiquetaCiudad()).isEqualTo("Santa Cruz");
        assertThat(entidad.getCodigoValor()).isEqualTo("PKG-ABC12345");
        assertThat(entidad.getCodigoGeneradoEn()).isEqualTo(GENERADO_EN);
        assertThat(entidad.getEtiquetaFechaGeneracion()).isEqualTo(GENERADO_EN);
    }

    @Test
    @DisplayName("guardar() de un paquete sin etiquetar deja las columnas de etiqueta vacias")
    void guardarSinEtiquetaNoAsignaColumnas() {
        adaptador.guardar(paquete(EstadoPaquete.ENVASADO, null));

        verify(jpa).save(entidadCaptor.capture());
        PaqueteJpaEntity entidad = entidadCaptor.getValue();

        assertThat(entidad.tieneEtiqueta()).isFalse();
        assertThat(entidad.getCodigoValor()).isNull();
        assertThat(entidad.getEtiquetaNombrePaciente()).isNull();
    }

    @Test
    @DisplayName("buscarPorId() reconstituye el agregado con su Etiqueta original")
    void buscarPorIdReconstituyeLaEtiqueta() {
        given(jpa.findById(PAQUETE_ID)).willReturn(Optional.of(entidadConEtiqueta()));

        PaqueteEtiquetado paquete = adaptador.buscarPorId(PaqueteId.de(PAQUETE_ID)).orElseThrow();

        assertThat(paquete.estado()).isEqualTo(EstadoPaquete.LISTO);
        assertThat(paquete.pacienteId().valor()).isEqualTo("PAC-1001");
        assertThat(paquete.etiqueta().nombrePaciente()).isEqualTo("Ana Perez");
        assertThat(paquete.etiqueta().codigo().valor()).isEqualTo("PKG-ABC12345");
        assertThat(paquete.etiqueta().fechaGeneracion()).isEqualTo(GENERADO_EN);
        assertThat(paquete.etiqueta().esValida()).isTrue();
        assertThat(paquete.pullEventos()).isEmpty();
    }

    @Test
    @DisplayName("buscarPorId() de un paquete sin etiquetar devuelve etiqueta null")
    void buscarPorIdSinEtiqueta() {
        PaqueteJpaEntity sinEtiqueta = new PaqueteJpaEntity(
                PAQUETE_ID, "PAC-1001", "orden-1", EstadoPaquete.ENVASADO);
        given(jpa.findById(PAQUETE_ID)).willReturn(Optional.of(sinEtiqueta));

        PaqueteEtiquetado paquete = adaptador.buscarPorId(PaqueteId.de(PAQUETE_ID)).orElseThrow();

        assertThat(paquete.etiqueta()).isNull();
        assertThat(paquete.estado()).isEqualTo(EstadoPaquete.ENVASADO);
    }

    @Test
    void buscarPorIdDevuelveVacioSiNoExiste() {
        given(jpa.findById("no-existe")).willReturn(Optional.empty());

        assertThat(adaptador.buscarPorId(PaqueteId.de("no-existe"))).isEmpty();
    }

    @Test
    void listarPorOrdenMapeaCadaEntidad() {
        given(jpa.findByOrdenId("orden-1")).willReturn(List.of(entidadConEtiqueta()));

        assertThat(adaptador.listarPorOrden("orden-1"))
                .singleElement()
                .extracting(p -> p.id().valor())
                .isEqualTo(PAQUETE_ID);
    }

    @Test
    void listarPorEstadoMapeaCadaEntidad() {
        given(jpa.findByEstado(EstadoPaquete.LISTO)).willReturn(List.of(entidadConEtiqueta()));

        assertThat(adaptador.listarPorEstado(EstadoPaquete.LISTO)).hasSize(1);
    }

    @Test
    @DisplayName("listarListosPendientesDeTransferencia() consulta los paquetes en estado LISTO")
    void listarListosConsultaElEstadoListo() {
        given(jpa.findByEstado(EstadoPaquete.LISTO)).willReturn(List.of(entidadConEtiqueta()));

        assertThat(adaptador.listarListosPendientesDeTransferencia()).hasSize(1);
        verify(jpa).findByEstado(EstadoPaquete.LISTO);
    }

    @Test
    void listarPorPacienteYFechaDelegaEnLaConsultaJpql() {
        LocalDate fecha = LocalDate.of(2026, 7, 15);
        given(jpa.buscarPorPacienteYFecha("PAC-1001", fecha)).willReturn(List.of(entidadConEtiqueta()));

        assertThat(adaptador.listarPorPacienteYFecha(PacienteId.de("PAC-1001"), fecha)).hasSize(1);
        verify(jpa).buscarPorPacienteYFecha("PAC-1001", fecha);
    }

    @Test
    void existeCodigoIdentificacionDelegaEnSpringData() {
        given(jpa.existsByCodigoValor("PKG-ABC12345")).willReturn(true);

        assertThat(adaptador.existeCodigoIdentificacion("PKG-ABC12345")).isTrue();
    }

    @Test
    @DisplayName("Un paquete de una entrega guarda y recupera su destino")
    void idaYVueltaConDestino() {
        var destino = new DestinoEntrega("CD-70011", "SUS-501", LocalDate.of(2026, 10, 23),
                "DIR-58", -17.7612, -63.1825, 4);
        PaqueteEtiquetado original = PaqueteEtiquetado.reconstituir(PaqueteId.de(PAQUETE_ID),
                PacienteId.de("PAC-1001"), "orden-1", EstadoPaquete.ENVASADO, null, destino);
        adaptador.guardar(original);
        verify(jpa).save(entidadCaptor.capture());
        PaqueteJpaEntity entidad = entidadCaptor.getValue();
        given(jpa.findById(PAQUETE_ID)).willReturn(Optional.of(entidad));

        PaqueteEtiquetado recuperado = adaptador.buscarPorId(PaqueteId.de(PAQUETE_ID)).orElseThrow();

        assertThat(entidad.tieneDestino()).isTrue();
        assertThat(entidad.getDestinoIdCalendarioDia()).isEqualTo("CD-70011");
        assertThat(entidad.getDestinoIdSuscripcion()).isEqualTo("SUS-501");
        assertThat(entidad.getDestinoFechaEntrega()).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(entidad.getDestinoIdDireccion()).isEqualTo("DIR-58");
        assertThat(entidad.getDestinoLatitud()).isEqualTo(-17.7612);
        assertThat(entidad.getDestinoLongitud()).isEqualTo(-63.1825);
        assertThat(entidad.getDestinoNroPorciones()).isEqualTo(4);
        assertThat(recuperado.destino()).contains(destino);
    }

    @Test
    @DisplayName("Un paquete creado a mano no llena las columnas del destino")
    void paqueteSinDestinoNoLlenaColumnas() {
        adaptador.guardar(paquete(EstadoPaquete.LISTO, etiqueta()));

        verify(jpa).save(entidadCaptor.capture());
        assertThat(entidadCaptor.getValue().tieneDestino()).isFalse();
        assertThat(entidadCaptor.getValue().getDestinoNroPorciones()).isNull();
        assertThat(PaqueteEntityMapper.aDominio(entidadCaptor.getValue()).destino()).isEmpty();
    }
}
