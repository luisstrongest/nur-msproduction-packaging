package com.nurticenter.logistica.bc4;

/**
 * BC4 respondio 404: el paquete que BC5 iba a transferir no existe.
 * Lleva el cuerpo ApiError para que el llamador pueda registrar el motivo.
 */
public class PaqueteNoEncontradoException extends RuntimeException {

    private final transient ApiErrorDto error;

    public PaqueteNoEncontradoException(ApiErrorDto error) {
        super(error.mensaje());
        this.error = error;
    }

    public ApiErrorDto error() {
        return error;
    }
}
