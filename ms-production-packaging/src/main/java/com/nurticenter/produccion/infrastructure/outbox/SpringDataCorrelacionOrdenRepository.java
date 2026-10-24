package com.nurticenter.produccion.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCorrelacionOrdenRepository extends JpaRepository<CorrelacionOrdenJpaEntity, String> {
}
