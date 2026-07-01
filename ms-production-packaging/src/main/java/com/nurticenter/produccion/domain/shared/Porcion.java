package com.nurticenter.produccion.domain.shared;

import java.util.Objects;

/**
 * VALUE OBJECT - Porcion
 *
 * Representa la cantidad de un item para UN paciente, con su unidad de medida.
 * Encapsula la logica de multiplicacion para calcular el total de produccion.
 *
 * Ubicado en domain/shared porque puede ser referenciado
 * tanto por ItemOrden como por otros contextos internos del dominio.
 */
public record Porcion(double cantidad, UnidadMedida unidad, String descripcion) {

    public Porcion {
        if (cantidad <= 0)
            throw new IllegalArgumentException("La cantidad de la porcion debe ser positiva.");
        Objects.requireNonNull(unidad,      "La unidad de medida es obligatoria.");
        Objects.requireNonNull(descripcion, "La descripcion es obligatoria.");
    }

    /**
     * Calcula la cantidad total para N pacientes.
     * Usado por la ACL al construir un ItemOrden.
     *
     * @param numeroPacientes numero de pacientes que reciben esta receta ese dia
     * @return nueva Porcion con la cantidad total acumulada
     */
    public Porcion multiplicar(int numeroPacientes) {
        if (numeroPacientes <= 0)
            throw new IllegalArgumentException("El numero de pacientes debe ser mayor a cero.");
        return new Porcion(this.cantidad * numeroPacientes, this.unidad, this.descripcion);
    }

    /** Representacion legible para etiquetas e informes internos. */
    public String formatoCorto() {
        return String.format("%.0f %s", cantidad, unidad.simbolo());
    }
}
