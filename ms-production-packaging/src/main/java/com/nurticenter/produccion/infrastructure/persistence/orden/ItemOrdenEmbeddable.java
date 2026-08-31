package com.nurticenter.produccion.infrastructure.persistence.orden;

import com.nurticenter.produccion.domain.model.orden.EstadoItem;
import com.nurticenter.produccion.domain.shared.UnidadMedida;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Componente embebido - ItemOrden persistido dentro de la tabla de items de la orden.
 * Aplana el Value Object ItemOrden (incluida su Porcion) a columnas.
 */
@Embeddable
public class ItemOrdenEmbeddable {

    @Column(name = "receta_id", nullable = false)
    private String recetaId;

    @Column(name = "nombre_receta", nullable = false)
    private String nombreReceta;

    @Column(name = "cantidad_total", nullable = false)
    private int cantidadTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_item", nullable = false)
    private EstadoItem estado;

    // -- Porcion (aplanada) --
    @Column(name = "porcion_cantidad", nullable = false)
    private double porcionCantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "porcion_unidad", nullable = false)
    private UnidadMedida porcionUnidad;

    @Column(name = "porcion_descripcion", nullable = false)
    private String porcionDescripcion;

    protected ItemOrdenEmbeddable() {
        // requerido por JPA
    }

    public ItemOrdenEmbeddable(String recetaId, String nombreReceta, int cantidadTotal,
                               EstadoItem estado, double porcionCantidad,
                               UnidadMedida porcionUnidad, String porcionDescripcion) {
        this.recetaId = recetaId;
        this.nombreReceta = nombreReceta;
        this.cantidadTotal = cantidadTotal;
        this.estado = estado;
        this.porcionCantidad = porcionCantidad;
        this.porcionUnidad = porcionUnidad;
        this.porcionDescripcion = porcionDescripcion;
    }

    public String getRecetaId()            { return recetaId; }
    public String getNombreReceta()        { return nombreReceta; }
    public int getCantidadTotal()          { return cantidadTotal; }
    public EstadoItem getEstado()          { return estado; }
    public double getPorcionCantidad()     { return porcionCantidad; }
    public UnidadMedida getPorcionUnidad() { return porcionUnidad; }
    public String getPorcionDescripcion()  { return porcionDescripcion; }
}
