package com.nurticenter.produccion.infrastructure.persistence.orden;

import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio Spring Data JPA de bajo nivel para OrdenJpaEntity.
 * NO es el puerto de dominio: es un detalle de infraestructura que el
 * adaptador (OrdenPersistenceAdapter) usa internamente.
 */
public interface SpringDataOrdenRepository extends JpaRepository<OrdenJpaEntity, String> {

    Optional<OrdenJpaEntity> findByFechaProduccion(LocalDate fechaProduccion);

    List<OrdenJpaEntity> findByEstado(EstadoOrden estado);

    boolean existsByFechaProduccion(LocalDate fechaProduccion);
}
