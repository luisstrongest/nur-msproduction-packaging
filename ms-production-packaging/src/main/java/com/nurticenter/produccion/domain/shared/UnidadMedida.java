package com.nurticenter.produccion.domain.shared;

/**
 * VALUE OBJECT (enum) - UnidadMedida
 */
public enum UnidadMedida {
    GRAMOS("g"),
    MILILITROS("ml"),
    UNIDADES("u");

    private final String simbolo;

    UnidadMedida(String simbolo) { this.simbolo = simbolo; }

    public String simbolo() { return simbolo; }
}
