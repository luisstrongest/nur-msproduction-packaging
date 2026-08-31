package com.nurticenter.produccion.infrastructure.persistence.orden;

import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.repository.OrdenDeProduccionRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTADOR DE PERSISTENCIA - implementa el puerto OrdenDeProduccionRepository.
 *
 * Traduce entre el dominio y Spring Data JPA. Es la unica clase de infraestructura
 * que el resto del sistema ve a traves del puerto de dominio (DIP).
 */
@Repository
public class OrdenPersistenceAdapter implements OrdenDeProduccionRepository {

    private final SpringDataOrdenRepository jpa;

    public OrdenPersistenceAdapter(SpringDataOrdenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void guardar(OrdenDeProduccion orden) {
        jpa.save(OrdenEntityMapper.aEntidad(orden));
    }

    @Override
    public Optional<OrdenDeProduccion> buscarPorId(OrdenId id) {
        return jpa.findById(id.valor()).map(OrdenEntityMapper::aDominio);
    }

    @Override
    public Optional<OrdenDeProduccion> buscarPorFecha(LocalDate fecha) {
        return jpa.findByFechaProduccion(fecha).map(OrdenEntityMapper::aDominio);
    }

    @Override
    public List<OrdenDeProduccion> listarPorEstado(EstadoOrden estado) {
        return jpa.findByEstado(estado).stream()
                .map(OrdenEntityMapper::aDominio)
                .toList();
    }

    @Override
    public boolean existePorFecha(LocalDate fecha) {
        return jpa.existsByFechaProduccion(fecha);
    }
}
