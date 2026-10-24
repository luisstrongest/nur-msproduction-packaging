package com.nurticenter.produccion.infrastructure.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * correlationId del flujo de negocio al que pertenece una orden.
 *
 * Se guarda al publicar el primer evento de una orden que nacio de un evento
 * entrante (el calendario de BC3), para que los eventos posteriores de esa orden
 * y de sus paquetes (PaqueteListo, OrdenCompletada, que llegan por la API REST)
 * conserven la misma correlacion.
 */
@Entity
@Table(name = "orden_correlacion")
public class CorrelacionOrdenJpaEntity {

    @Id
    @Column(name = "orden_id", nullable = false, updatable = false)
    private String ordenId;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 100)
    private String correlationId;

    protected CorrelacionOrdenJpaEntity() {
        // requerido por JPA
    }

    public CorrelacionOrdenJpaEntity(String ordenId, String correlationId) {
        this.ordenId = ordenId;
        this.correlationId = correlationId;
    }

    public String getOrdenId()       { return ordenId; }
    public String getCorrelationId() { return correlationId; }
}
