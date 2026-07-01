package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.DomainException;

/**
 * Se lanza cuando los DatosPaciente recibidos del CalendarioConsolidado
 * son insuficientes para generar una Etiqueta valida.
 */
public class DatosPacienteIncompletosException extends DomainException {
    public DatosPacienteIncompletosException(String message) { super(message); }
}
