package com.nurticenter.produccion.domain.repository;

import com.nurticenter.produccion.domain.model.orden.EstadoOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementacion en memoria del repositorio - SOLO PARA TESTS.
 * Demuestra que el dominio es testeable sin ningun framework.
 */
public class InMemoryOrdenDeProduccionRepository implements OrdenDeProduccionRepository {

    private final Map<String, OrdenDeProduccion> store = new HashMap<>();

    @Override
    public void guardar(OrdenDeProduccion orden) {
        store.put(orden.id().valor(), orden);
    }

    @Override
    public Optional<OrdenDeProduccion> buscarPorId(OrdenId id) {
        return Optional.ofNullable(store.get(id.valor()));
    }

    @Override
    public Optional<OrdenDeProduccion> buscarPorFecha(LocalDate fecha) {
        return store.values().stream()
                .filter(o -> o.fechaProduccion().equals(fecha))
                .findFirst();
    }

    @Override
    public List<OrdenDeProduccion> listarPorEstado(EstadoOrden estado) {
        return store.values().stream()
                .filter(o -> o.estado() == estado)
                .toList();
    }

    @Override
    public boolean existePorFecha(LocalDate fecha) {
        return store.values().stream()
                .anyMatch(o -> o.fechaProduccion().equals(fecha));
    }
}
