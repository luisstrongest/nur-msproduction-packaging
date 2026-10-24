package com.nurticenter.produccion.infrastructure.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataEventoProcesadoRepository extends JpaRepository<EventoProcesadoJpaEntity, String> {
}
