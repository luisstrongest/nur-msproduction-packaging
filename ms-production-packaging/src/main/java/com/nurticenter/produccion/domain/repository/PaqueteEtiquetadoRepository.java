package com.nurticenter.produccion.domain.repository;

import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * PUERTO - PaqueteEtiquetadoRepository
 *
 * Define el contrato de persistencia del agregado PaqueteEtiquetado.
 *
 * Principio DIP: interfaz en dominio, implementacion en infraestructura.
 * Principio ISP: metodos especificos del dominio, sin ruido de frameworks.
 */
public interface PaqueteEtiquetadoRepository {

    /**
     * Persiste o actualiza un PaqueteEtiquetado.
     *
     * @param paquete Agregado a persistir (nunca null)
     */
    void guardar(PaqueteEtiquetado paquete);

    /**
     * Busca un paquete por su identidad.
     *
     * @param id Identificador del paquete
     * @return Optional con el paquete si existe
     */
    Optional<PaqueteEtiquetado> buscarPorId(PaqueteId id);

    /**
     * Lista todos los paquetes generados por una OrdenDeProduccion.
     * Usado para verificar que todos los paquetes estan LISTOS antes de completar la orden.
     *
     * @param ordenId Id de la orden (String opaco)
     * @return Lista de paquetes de esa orden
     */
    List<PaqueteEtiquetado> listarPorOrden(String ordenId);

    /**
     * Lista los paquetes de un paciente especifico en una fecha.
     * Usado para el historial de entregas del paciente (consulta cross-BC limitada).
     *
     * @param pacienteId Referencia opaca al paciente
     * @param fecha      Fecha de produccion
     * @return Lista de paquetes del paciente en esa fecha
     */
    List<PaqueteEtiquetado> listarPorPacienteYFecha(PacienteId pacienteId, LocalDate fecha);

    /**
     * Lista los paquetes en estado LISTO pendientes de transferencia a BC5.
     * La capa de aplicacion usa este metodo para orquestar la transferencia.
     *
     * @return Lista de paquetes LISTOS no transferidos aun
     */
    List<PaqueteEtiquetado> listarListosPendientesDeTransferencia();

    /**
     * Lista paquetes por estado.
     *
     * @param estado Estado a filtrar
     * @return Lista de paquetes en ese estado
     */
    List<PaqueteEtiquetado> listarPorEstado(EstadoPaquete estado);

    /**
     * Verifica unicidad del codigo de identificacion en todo el sistema.
     * El dominio genera el codigo; la infraestructura garantiza que no existe otro igual.
     *
     * @param codigo Valor del CodigoIdentificacion a verificar
     * @return true si el codigo ya esta en uso
     */
    boolean existeCodigoIdentificacion(String codigo);
}
