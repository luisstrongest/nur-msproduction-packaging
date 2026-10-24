package com.nurticenter.produccion.infrastructure.persistence.orden;

import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ENTIDAD JPA - OrdenDeProduccion
 *
 * Representacion persistente del agregado. Vive SOLO en infraestructura;
 * el dominio no la conoce. Hibernate genera las tablas a partir de esta clase.
 */
@Entity
@Table(name = "orden_de_produccion")
public class OrdenJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "fecha_produccion", nullable = false)
    private LocalDate fechaProduccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoOrden estado;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "orden_item",
            joinColumns = @JoinColumn(name = "orden_id"))
    @OrderColumn(name = "posicion")
    private List<ItemOrdenEmbeddable> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "orden_paquete_generado",
            joinColumns = @JoinColumn(name = "orden_id"))
    @Column(name = "paquete_id")
    @OrderColumn(name = "posicion")
    private List<String> paqueteIds = new ArrayList<>();

    // -- Origen en el CalendarioConsolidado de BC3 (null si la orden se creo a mano) --
    @Column(name = "id_calendario")
    private String idCalendario;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    protected OrdenJpaEntity() {
        // requerido por JPA
    }

    public OrdenJpaEntity(String id, LocalDate fechaProduccion, EstadoOrden estado,
                          List<ItemOrdenEmbeddable> items, List<String> paqueteIds) {
        this.id = id;
        this.fechaProduccion = fechaProduccion;
        this.estado = estado;
        this.items = new ArrayList<>(items);
        this.paqueteIds = new ArrayList<>(paqueteIds);
    }

    public void asignarOrigen(String idCalendario, LocalDate fechaEntrega) {
        this.idCalendario = idCalendario;
        this.fechaEntrega = fechaEntrega;
    }

    public boolean tieneOrigen() {
        return idCalendario != null;
    }

    public String getId()                       { return id; }
    public LocalDate getFechaProduccion()       { return fechaProduccion; }
    public EstadoOrden getEstado()              { return estado; }
    public List<ItemOrdenEmbeddable> getItems() { return items; }
    public List<String> getPaqueteIds()         { return paqueteIds; }
    public String getIdCalendario()             { return idCalendario; }
    public LocalDate getFechaEntrega()          { return fechaEntrega; }
}
