package com.nurticenter.produccion.domain.model.paquete;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * VALUE OBJECT - Etiqueta
 *
 * Representa los datos impresos en el paquete fisico.
 * Es completamente inmutable: una vez generada, no cambia.
 *
 * Si hay un error (nombre incorrecto, direccion equivocada), el flujo correcto es:
 *   1. Anular el PaqueteEtiquetado actual.
 *   2. Crear uno nuevo con los datos correctos.
 * Nunca se modifica la Etiqueta existente.
 */
public final class Etiqueta {

    private final String nombrePaciente;
    private final DireccionImpresa direccion;
    private final CodigoIdentificacion codigo;
    private final LocalDateTime fechaGeneracion;

    private Etiqueta(String nombrePaciente,
                     DireccionImpresa direccion,
                     CodigoIdentificacion codigo,
                     LocalDateTime fechaGeneracion) {
        this.nombrePaciente  = nombrePaciente;
        this.direccion       = direccion;
        this.codigo          = codigo;
        this.fechaGeneracion = fechaGeneracion;
    }

    /**
     * Factory: crea una Etiqueta con timestamp actual.
     */
    public static Etiqueta crear(String nombrePaciente,
                                 DireccionImpresa direccion,
                                 CodigoIdentificacion codigo) {
        Objects.requireNonNull(nombrePaciente, "El nombre del paciente es obligatorio.");
        Objects.requireNonNull(direccion,      "La direccion impresa es obligatoria.");
        Objects.requireNonNull(codigo,         "El codigo de identificacion es obligatorio.");
        if (nombrePaciente.isBlank())
            throw new IllegalArgumentException("El nombre del paciente no puede estar vacio.");

        return new Etiqueta(nombrePaciente, direccion, codigo, LocalDateTime.now());
    }

    /**
     * Reconstruye una Etiqueta desde persistencia, preservando su fecha de generacion original.
     * Uso exclusivo de los adaptadores de infraestructura.
     */
    public static Etiqueta reconstituir(String nombrePaciente,
                                        DireccionImpresa direccion,
                                        CodigoIdentificacion codigo,
                                        LocalDateTime fechaGeneracion) {
        return new Etiqueta(nombrePaciente, direccion, codigo, fechaGeneracion);
    }

    /**
     * Valida que la etiqueta tiene todos sus componentes correctos.
     * Usado como condicion de salida antes de marcar el paquete como LISTO.
     */
    public boolean esValida() {
        return nombrePaciente != null && !nombrePaciente.isBlank()
                && direccion != null && direccion.estaCompleta()
                && codigo != null;
    }

    // -- Consultas -------------------------------------------------------------
    public String nombrePaciente()          { return nombrePaciente; }
    public DireccionImpresa direccion()     { return direccion; }
    public CodigoIdentificacion codigo()    { return codigo; }
    public LocalDateTime fechaGeneracion()  { return fechaGeneracion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Etiqueta e)) return false;
        return Objects.equals(codigo, e.codigo);  // el codigo es unico por paquete
    }

    @Override
    public int hashCode() { return Objects.hash(codigo); }
}
