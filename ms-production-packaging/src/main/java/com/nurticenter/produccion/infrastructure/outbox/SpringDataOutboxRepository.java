package com.nurticenter.produccion.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, Long> {

    /**
     * Toma el siguiente lote de pendientes, en orden de insercion, BLOQUEANDO las
     * filas hasta el fin de la transaccion. SKIP LOCKED salta las que ya tomo otra
     * replica de BC4: dos instancias nunca publican la misma fila.
     */
    @Query(value = """
            SELECT * FROM outbox
            WHERE publicado_en IS NULL
            ORDER BY id
            LIMIT :lote
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxJpaEntity> bloquearPendientes(@Param("lote") int lote);

    List<OutboxJpaEntity> findByMessageKeyOrderByIdAsc(String messageKey);

    @Modifying
    @Query("DELETE FROM OutboxJpaEntity o WHERE o.publicadoEn < :limite")
    int borrarPublicadosAntesDe(@Param("limite") Instant limite);
}
