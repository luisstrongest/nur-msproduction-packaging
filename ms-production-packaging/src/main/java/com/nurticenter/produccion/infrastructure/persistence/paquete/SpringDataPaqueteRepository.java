package com.nurticenter.produccion.infrastructure.persistence.paquete;

import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio Spring Data JPA de bajo nivel para PaqueteJpaEntity.
 */
public interface SpringDataPaqueteRepository extends JpaRepository<PaqueteJpaEntity, String> {

    List<PaqueteJpaEntity> findByOrdenId(String ordenId);

    List<PaqueteJpaEntity> findByEstado(EstadoPaquete estado);

    boolean existsByCodigoValor(String codigoValor);

    /**
     * Paquetes de un paciente cuya orden corresponde a la fecha dada.
     * Une (a nivel de infraestructura) paquete y orden por el ordenId.
     */
    @Query("""
            select p from PaqueteJpaEntity p
            where p.pacienteId = :pacienteId
              and p.ordenId in (
                  select o.id from OrdenJpaEntity o where o.fechaProduccion = :fecha)
            """)
    List<PaqueteJpaEntity> buscarPorPacienteYFecha(@Param("pacienteId") String pacienteId,
                                                   @Param("fecha") LocalDate fecha);
}
