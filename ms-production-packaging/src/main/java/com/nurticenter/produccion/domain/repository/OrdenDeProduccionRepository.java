package com.nurticenter.produccion.domain.repository;

import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * PUERTO - OrdenDeProduccionRepository
 *
 * Define el contrato de persistencia del agregado OrdenDeProduccion.
 * La implementacion concreta vive en la capa de infraestructura.
 *
 * Principio DIP: el dominio depende de esta abstraccion,
 * no de Spring Data, JPA ni ninguna tecnologia concreta.
 *
 * Principio ISP: solo los metodos que el dominio realmente necesita.
 * No hay metodos heredados de frameworks (save, findAll, deleteById, etc.)
 * que el dominio no use.
 */
public interface OrdenDeProduccionRepository {

    /**
     * Persiste o actualiza una OrdenDeProduccion.
     * Si ya existe (mismo OrdenId), actualiza. Si no, inserta.
     *
     * @param orden Agregado a persistir (nunca null)
     */
    void guardar(OrdenDeProduccion orden);

    /**
     * Busca una orden por su identidad.
     *
     * @param id Identificador de la orden
     * @return Optional con la orden si existe, vacio si no
     */
    Optional<OrdenDeProduccion> buscarPorId(OrdenId id);

    /**
     * Busca la orden de produccion para una fecha especifica.
     * En condiciones normales, existe a lo sumo una orden por dia.
     *
     * @param fecha Fecha de produccion
     * @return Optional con la orden del dia si existe
     */
    Optional<OrdenDeProduccion> buscarPorFecha(LocalDate fecha);

    /**
     * Lista las ordenes filtradas por estado.
     * Util para el panel de administracion y para detectar ordenes incompletas.
     *
     * @param estado Estado a filtrar
     * @return Lista de ordenes en ese estado (puede ser vacia, nunca null)
     */
    List<OrdenDeProduccion> listarPorEstado(EstadoOrden estado);

    /**
     * Verifica si ya existe una orden para la fecha dada.
     * Usado como guardia antes de crear una nueva orden desde CalendarioConsolidado.
     *
     * @param fecha Fecha de produccion
     * @return true si ya existe una orden para esa fecha
     */
    boolean existePorFecha(LocalDate fecha);
}
