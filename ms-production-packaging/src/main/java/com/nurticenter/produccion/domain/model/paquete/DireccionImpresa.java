package com.nurticenter.produccion.domain.model.paquete;

import java.util.Objects;

/**
 * VALUE OBJECT - DireccionImpresa
 *
 * Copia inmutable de la DireccionEntrega de BC3 en el momento de etiquetar.
 * No cambia aunque el paciente modifique su direccion en BC3 despues.
 *
 * Distincion semantica con BC3:
 *   BC3: DireccionEntrega -> lo que el paciente configuro (editable)
 *   BC4: DireccionImpresa -> lo que esta impreso en el paquete (inmutable)
 */
public record DireccionImpresa(
        String calle,
        String numero,
        String referencia,
        String ciudad
) {
    public DireccionImpresa {
        Objects.requireNonNull(calle,   "La calle es obligatoria.");
        Objects.requireNonNull(numero,  "El numero es obligatorio.");
        Objects.requireNonNull(ciudad,  "La ciudad es obligatoria.");
        // referencia es opcional (puede ser null o vacio)
    }

    /**
     * Verifica que los campos minimos para imprimir la etiqueta estan presentes.
     */
    public boolean estaCompleta() {
        return !calle.isBlank() && !numero.isBlank() && !ciudad.isBlank();
    }

    /**
     * Formato legible para imprimir en la etiqueta fisica.
     * Ejemplo: "Av. Mutualista 450, Ref: Edificio azul - Santa Cruz"
     */
    public String formatoParaEtiqueta() {
        String base = calle + " " + numero;
        if (referencia != null && !referencia.isBlank()) {
            base += ", Ref: " + referencia;
        }
        return base + " - " + ciudad;
    }
}
