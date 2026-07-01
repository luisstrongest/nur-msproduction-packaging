package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.shared.Porcion;

import java.util.Objects;

/**
 * VALUE OBJECT - ItemOrden
 *
 * Representa una receta traducida al lenguaje de produccion mediante la ACL.
 * No conoce el modelo de Receta de BC2; solo trabaja con RecetaId (String opaco)
 * y los datos necesarios para produccion.
 *
 * La cantidad total se calcula en la capa de aplicacion (ACL):
 *   cantidadTotal = porcionIndividual x numero de pacientes con esa receta ese dia.
 */
public final class ItemOrden {

    private final String recetaId;          // ref. opaca a BC2 - nunca un objeto Receta
    private final String nombreReceta;      // desnormalizado para no consultar BC2
    private final int cantidadTotal;        // total a preparar (ya calculado por ACL)
    private final Porcion porcionIndividual;
    private EstadoItem estado;              // mutable: avanza de PENDIENTE -> PREPARADO

    public ItemOrden(String recetaId, String nombreReceta, int cantidadTotal, Porcion porcionIndividual) {
        Objects.requireNonNull(recetaId,           "recetaId es obligatorio.");
        Objects.requireNonNull(nombreReceta,        "nombreReceta es obligatorio.");
        Objects.requireNonNull(porcionIndividual,   "porcionIndividual es obligatoria.");
        if (cantidadTotal <= 0)
            throw new IllegalArgumentException("cantidadTotal debe ser mayor a cero.");

        this.recetaId           = recetaId;
        this.nombreReceta       = nombreReceta;
        this.cantidadTotal      = cantidadTotal;
        this.porcionIndividual  = porcionIndividual;
        this.estado             = EstadoItem.PENDIENTE;
    }

    /** Avanza el item a estado PREPARADO. Solo puede llamarse una vez. */
    public void marcarPreparado() {
        if (this.estado == EstadoItem.PREPARADO) {
            throw new IllegalStateException("El item ya esta marcado como PREPARADO: " + recetaId);
        }
        this.estado = EstadoItem.PREPARADO;
    }

    // -- Consultas --------------------------------------------------------------
    public String recetaId()                { return recetaId; }
    public String nombreReceta()            { return nombreReceta; }
    public int cantidadTotal()              { return cantidadTotal; }
    public Porcion porcionIndividual()      { return porcionIndividual; }
    public EstadoItem estado()              { return estado; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemOrden other)) return false;
        return recetaId.equals(other.recetaId);  // identidad por recetaId dentro de la orden
    }

    @Override
    public int hashCode() { return Objects.hash(recetaId); }
}
