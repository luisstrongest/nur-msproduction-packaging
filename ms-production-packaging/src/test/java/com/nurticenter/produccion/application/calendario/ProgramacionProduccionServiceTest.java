package com.nurticenter.produccion.application.calendario;

import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.EntregaCommand;
import com.nurticenter.produccion.application.calendario.dto.ProgramarProduccionCommand.RecetaCommand;
import com.nurticenter.produccion.application.event.EventPublisher;
import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrigenCalendario;
import com.nurticenter.produccion.domain.model.paquete.DestinoEntrega;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import com.nurticenter.produccion.domain.repository.PaqueteEtiquetadoRepository;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TEST UNITARIO CON MOCKS - ProgramacionProduccionService.
 *
 * Los puertos (repositorios y EventPublisher) son mocks; los agregados son reales.
 * Se verifica la ACL calendario -> ItemOrden, la orden y los paquetes creados y
 * la orquestacion guardar -> publicar.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProgramacionProduccionService (unit test con mocks)")
class ProgramacionProduccionServiceTest {

    private static final LocalDate ENTREGA = LocalDate.of(2026, 10, 23);
    private static final LocalDate PRODUCCION = LocalDate.of(2026, 10, 22);

    @Mock
    private OrdenDeProduccionRepository ordenRepository;

    @Mock
    private PaqueteEtiquetadoRepository paqueteRepository;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private ProgramacionProduccionService servicio;

    @Captor
    private ArgumentCaptor<PaqueteEtiquetado> paquetes;

    @Captor
    private ArgumentCaptor<List<Object>> eventos;

    @Test
    @DisplayName("Crea la orden del dia anterior a la entrega, con el origen del calendario")
    void creaLaOrdenDelDiaAnterior() {
        OrdenDeProduccion orden = servicio.programar(calendario());

        assertThat(orden.fechaProduccion()).isEqualTo(PRODUCCION);
        assertThat(orden.origen()).contains(new OrigenCalendario("CAL-20261023", ENTREGA));
        verify(ordenRepository).existePorFecha(PRODUCCION);
    }

    @Test
    @DisplayName("ACL: una receta de N entregas es un solo item con N pacientes y su porcion en gramos")
    void agrupaLasRecetasDeTodasLasEntregas() {
        OrdenDeProduccion orden = servicio.programar(calendario());

        assertThat(orden.items())
                .extracting(ItemOrden::recetaId, ItemOrden::nombreReceta, ItemOrden::cantidadTotal)
                .containsExactly(
                        tuple("REC-14", "Avena cocida", 2),
                        tuple("REC-31", "Sopa de verduras", 2),
                        tuple("REC-33", "Ensalada fresca", 1));
        ItemOrden avena = orden.items().get(0);
        assertThat(avena.porcionIndividual().cantidad()).isEqualTo(250);
        assertThat(avena.porcionIndividual().unidad()).isEqualTo(UnidadMedida.GRAMOS);
        assertThat(avena.porcionIndividual().descripcion()).isEqualTo("Servir a las 08:00");
    }

    @Test
    @DisplayName("Crea un paquete por entrega con su destino y lo registra en la orden")
    void creaUnPaquetePorEntrega() {
        OrdenDeProduccion orden = servicio.programar(calendario());

        verify(paqueteRepository, times(2)).guardar(paquetes.capture());
        assertThat(paquetes.getAllValues())
                .extracting(p -> p.pacienteId().valor(), PaqueteEtiquetado::ordenId, PaqueteEtiquetado::estado)
                .containsExactly(
                        tuple("PAC-1024", orden.id().valor(), EstadoPaquete.ENVASADO_PENDIENTE),
                        tuple("PAC-1031", orden.id().valor(), EstadoPaquete.ENVASADO_PENDIENTE));
        assertThat(paquetes.getAllValues().get(0).destino()).contains(new DestinoEntrega(
                "CD-70011", "SUS-501", ENTREGA, "DIR-58", -17.7612, -63.1825, 2));
        assertThat(paquetes.getAllValues().get(1).destino().orElseThrow().nroPorciones()).isEqualTo(3);
        assertThat(orden.paqueteIds())
                .containsExactly(paquetes.getAllValues().get(0).id().valor(), paquetes.getAllValues().get(1).id().valor());
    }

    @Test
    @DisplayName("Guarda paquetes y orden y despues publica OrdenGenerada")
    void guardaYDespuesPublica() {
        OrdenDeProduccion orden = servicio.programar(calendario());

        InOrder secuencia = inOrder(paqueteRepository, ordenRepository, eventPublisher);
        secuencia.verify(paqueteRepository, times(2)).guardar(any());
        secuencia.verify(ordenRepository).guardar(orden);
        secuencia.verify(eventPublisher).publicar(eventos.capture());
        assertThat(eventos.getValue()).singleElement().isInstanceOfSatisfying(OrdenGenerada.class, e -> {
            assertThat(e.ordenId()).isEqualTo(orden.id().valor());
            assertThat(e.totalItems()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("Si ya hay una orden para el dia de produccion, rechaza el calendario sin guardar nada")
    void rechazaSiYaHayOrdenParaEseDia() {
        when(ordenRepository.existePorFecha(PRODUCCION)).thenReturn(true);

        assertThatThrownBy(() -> servicio.programar(calendario()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PRODUCCION.toString());
        verify(ordenRepository, never()).guardar(any());
        verifyNoInteractions(paqueteRepository, eventPublisher);
    }

    @Test
    @DisplayName("Si una receta llega con porciones distintas, rechaza el calendario sin guardar nada")
    void rechazaPorcionesDistintasDeLaMismaReceta() {
        var comando = new ProgramarProduccionCommand("CAL-1", ENTREGA, List.of(
                entrega("CD-1", "PAC-1", new RecetaCommand("REC-14", "Avena cocida", "08:00", 250)),
                entrega("CD-2", "PAC-2", new RecetaCommand("REC-14", "Avena cocida", "08:00", 300))));

        assertThatThrownBy(() -> servicio.programar(comando))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REC-14");
        verify(ordenRepository, never()).guardar(any());
        verifyNoInteractions(paqueteRepository, eventPublisher);
    }

    private static ProgramarProduccionCommand calendario() {
        return new ProgramarProduccionCommand("CAL-20261023", ENTREGA, List.of(
                new EntregaCommand("CD-70011", "SUS-501", "PAC-1024", "DIR-58", -17.7612, -63.1825, List.of(
                        new RecetaCommand("REC-14", "Avena cocida", "08:00", 250),
                        new RecetaCommand("REC-31", "Sopa de verduras", "13:00", 300))),
                new EntregaCommand("CD-70012", "SUS-502", "PAC-1031", null, null, null, List.of(
                        new RecetaCommand("REC-14", "Avena cocida", "08:00", 250),
                        new RecetaCommand("REC-31", "Sopa de verduras", "13:00", 300),
                        new RecetaCommand("REC-33", "Ensalada fresca", "13:00", 200)))));
    }

    private static EntregaCommand entrega(String idCalendarioDia, String paciente, RecetaCommand receta) {
        return new EntregaCommand(idCalendarioDia, "SUS-1", paciente, null, null, null, List.of(receta));
    }
}
