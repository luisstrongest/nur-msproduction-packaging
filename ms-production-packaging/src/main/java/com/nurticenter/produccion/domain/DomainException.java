package com.nurticenter.produccion.domain;

/**
 * Excepcion base para todas las violaciones de reglas de negocio en BC4.
 *
 * Es unchecked (RuntimeException) porque las violaciones de reglas de negocio
 * son errores de programacion o de flujo, no condiciones recuperables en el dominio.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
