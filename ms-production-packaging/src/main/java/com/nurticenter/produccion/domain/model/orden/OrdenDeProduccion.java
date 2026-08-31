package com.nurticenter.produccion.domain.model.orden;

import com.nurticenter.produccion.domain.event.OrdenCompletada;
import com.nurticenter.produccion.domain.event.OrdenGenerada;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AGREGADO RAIZ - OrdenDeProduccion
 *
 * Controla el ciclo de vida completo de la orden de cocina:
 * PENDIENTE -> EN_PREPARACION -> COMPLETADA
 *
 * Principio SRP: solo gestiona la orden. No sabe nada de paquetes, rutas ni recetas originales.
 * Principio OCP: los eventos emitidos son extensibles sin modificar este agregado.
 */
public class OrdenDeProduccion {

    // -- Identity -----------------------------------------------------------------
    private final OrdenId id;

    // -- Atributos ------------------------------------------------------------------
    private final LocalDate fechaProduccion;
    private EstadoOrden estado;
    private final List<ItemOrden> items;
    private final List<String> paqueteIdsGenerados; // referencias a PaqueteEtiquetado (solo Id)

    // -- Eventos pendientes de publicacion (patron outbox) -------------------------
    private final List<Object> eventos;

    // -- Constructor privado - solo Factory Method ----------------------------------
    private OrdenDeProduccion(OrdenId id, LocalDate fechaProduccion, List<ItemOrden> items) {
        if (items == null || items.isEmpty()) {
            throw new OrdenSinItemsException("Una OrdenDeProduccion debe tener al menos un ItemOrden.");
        }
        this.id               = id;
        this.fechaProduccion  = fechaProduccion;
        this.estado           = EstadoOrden.PENDIENTE;
        this.items            = new ArrayList<>(items);
        this.paqueteIdsGenerados = new ArrayList<>();
        this.eventos          = new ArrayList<>();

        // Registra el evento de creacion
        this.eventos.add(new OrdenGenerada(
                this.id.valor(),
                this.fechaProduccion,
                this.items.size(),
                LocalDateTime.now()
        ));
    }

    // -- Factory Method (DIP: el dominio no usa new con parametros externos) -------
    /**
     * Crea una nueva OrdenDeProduccion a partir del CalendarioConsolidado.
     * Es el unico punto de creacion valido.
     *
     * @param fechaProduccion Fecha para la cual se produce
     * @param items           Lista de items calculados por la ACL (Receta -> ItemOrden)
     * @return OrdenDeProduccion en estado PENDIENTE con OrdenGenerada registrado
     */
    public static OrdenDeProduccion crear(LocalDate fechaProduccion, List<ItemOrden> items) {
        OrdenId id = OrdenId.nuevo();
        return new OrdenDeProduccion(id, fechaProduccion, items);
    }

    // -- Constructor de reconstitucion (para la capa de infraestructura) -----------
    private OrdenDeProduccion(OrdenId id, LocalDate fechaProduccion, EstadoOrden estado,
                              List<ItemOrden> items, List<String> paqueteIds) {
        this.id                  = id;
        this.fechaProduccion     = fechaProduccion;
        this.estado              = estado;
        this.items               = new ArrayList<>(items);
        this.paqueteIdsGenerados = new ArrayList<>(paqueteIds);
        this.eventos             = new ArrayList<>();
        // No se registra OrdenGenerada: la orden ya existia; solo se rehidrata desde la BD.
    }

    /**
     * Reconstruye una OrdenDeProduccion desde el almacenamiento persistente.
     * NO emite eventos: representa un estado ya existente, no un hecho nuevo.
     * Uso exclusivo de los adaptadores de persistencia (infraestructura).
     */
    public static OrdenDeProduccion reconstituir(OrdenId id, LocalDate fechaProduccion,
                                                 EstadoOrden estado, List<ItemOrden> items,
                                                 List<String> paqueteIds) {
        return new OrdenDeProduccion(id, fechaProduccion, estado, items, paqueteIds);
    }

    // -- Comportamiento de dominio ---------------------------------------------------

    /**
     * Inicia la preparacion de la orden.
     * Transicion: PENDIENTE -> EN_PREPARACION
     */
    public void iniciarPreparacion() {
        if (this.estado != EstadoOrden.PENDIENTE) {
            throw new TransicionEstadoInvalidaException(
                    "Solo se puede iniciar la preparacion desde estado PENDIENTE. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoOrden.EN_PREPARACION;
    }

    /**
     * Marca un item especifico como preparado.
     * El item se identifica por su RecetaId (String opaco).
     *
     * @param recetaId Identificador de la receta en BC2 (referencia externa)
     */
    public void marcarItemPreparado(String recetaId) {
        if (this.estado == EstadoOrden.COMPLETADA) {
            throw new OrdenYaCompletadaException("No se puede modificar una orden ya completada.");
        }
        ItemOrden item = buscarItem(recetaId);
        item.marcarPreparado();
    }

    /**
     * Registra la referencia a un PaqueteEtiquetado generado.
     * BC4 no instancia PaqueteEtiquetado; solo guarda su Id como referencia.
     *
     * @param paqueteId Id del paquete generado (String del PaqueteId)
     */
    public void registrarPaqueteGenerado(String paqueteId) {
        if (this.estado == EstadoOrden.COMPLETADA) {
            throw new OrdenYaCompletadaException("No se pueden registrar paquetes en una orden completada.");
        }
        this.paqueteIdsGenerados.add(paqueteId);
    }

    /**
     * Completa la orden.
     * Transicion: EN_PREPARACION -> COMPLETADA
     *
     * Invariante: todos los items deben estar en estado PREPARADO.
     */
    public void completar() {
        if (this.estado != EstadoOrden.EN_PREPARACION) {
            throw new TransicionEstadoInvalidaException(
                    "Solo se puede completar una orden EN_PREPARACION. Estado actual: " + this.estado
            );
        }
        boolean hayItemsPendientes = this.items.stream()
                .anyMatch(item -> item.estado() == EstadoItem.PENDIENTE);
        if (hayItemsPendientes) {
            throw new ItemsPendientesException("No se puede completar la orden: hay items sin preparar.");
        }
        this.estado = EstadoOrden.COMPLETADA;
        this.eventos.add(new OrdenCompletada(
                this.id.valor(),
                List.copyOf(this.paqueteIdsGenerados),
                LocalDateTime.now()
        ));
    }

    /**
     * Calcula el total de porciones de todos los items de la orden.
     * Util para estimar el volumen de produccion del dia.
     */
    public int calcularTotalPorciones() {
        return this.items.stream()
                .mapToInt(ItemOrden::cantidadTotal)
                .sum();
    }

    // -- Consultas (getters inmutables) ------------------------------------------------

    public OrdenId id()                      { return id; }
    public LocalDate fechaProduccion()       { return fechaProduccion; }
    public EstadoOrden estado()              { return estado; }
    public List<ItemOrden> items()           { return Collections.unmodifiableList(items); }
    public List<String> paqueteIds()         { return Collections.unmodifiableList(paqueteIdsGenerados); }

    /**
     * Retorna y limpia los eventos pendientes de publicacion.
     * La capa de aplicacion los consume y publica al bus de mensajes.
     */
    public List<Object> pullEventos() {
        List<Object> copia = List.copyOf(this.eventos);
        this.eventos.clear();
        return copia;
    }

    // -- Helpers privados ---------------------------------------------------------------

    private ItemOrden buscarItem(String recetaId) {
        return this.items.stream()
                .filter(i -> i.recetaId().equals(recetaId))
                .findFirst()
                .orElseThrow(() -> new ItemNoEncontradoException(
                        "No existe un ItemOrden con RecetaId: " + recetaId
                ));
    }
}
