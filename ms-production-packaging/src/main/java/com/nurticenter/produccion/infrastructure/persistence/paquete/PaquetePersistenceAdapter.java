package com.nurticenter.produccion.infrastructure.persistence.paquete;

import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import com.nurticenter.produccion.domain.repository.PaqueteEtiquetadoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTADOR DE PERSISTENCIA - implementa el puerto PaqueteEtiquetadoRepository.
 */
@Repository
public class PaquetePersistenceAdapter implements PaqueteEtiquetadoRepository {

    private final SpringDataPaqueteRepository jpa;

    public PaquetePersistenceAdapter(SpringDataPaqueteRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void guardar(PaqueteEtiquetado paquete) {
        jpa.save(PaqueteEntityMapper.aEntidad(paquete));
    }

    @Override
    public Optional<PaqueteEtiquetado> buscarPorId(PaqueteId id) {
        return jpa.findById(id.valor()).map(PaqueteEntityMapper::aDominio);
    }

    @Override
    public List<PaqueteEtiquetado> listarPorOrden(String ordenId) {
        return jpa.findByOrdenId(ordenId).stream()
                .map(PaqueteEntityMapper::aDominio)
                .toList();
    }

    @Override
    public List<PaqueteEtiquetado> listarPorPacienteYFecha(PacienteId pacienteId, LocalDate fecha) {
        return jpa.buscarPorPacienteYFecha(pacienteId.valor(), fecha).stream()
                .map(PaqueteEntityMapper::aDominio)
                .toList();
    }

    @Override
    public List<PaqueteEtiquetado> listarListosPendientesDeTransferencia() {
        // Sin flag de "transferido" en el modelo actual: se consideran pendientes
        // todos los paquetes en estado LISTO.
        return jpa.findByEstado(EstadoPaquete.LISTO).stream()
                .map(PaqueteEntityMapper::aDominio)
                .toList();
    }

    @Override
    public List<PaqueteEtiquetado> listarPorEstado(EstadoPaquete estado) {
        return jpa.findByEstado(estado).stream()
                .map(PaqueteEntityMapper::aDominio)
                .toList();
    }

    @Override
    public boolean existeCodigoIdentificacion(String codigo) {
        return jpa.existsByCodigoValor(codigo);
    }
}
