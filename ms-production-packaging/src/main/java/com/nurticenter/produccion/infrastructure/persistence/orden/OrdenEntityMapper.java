package com.nurticenter.produccion.infrastructure.persistence.orden;

import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;
import com.nurticenter.produccion.domain.model.orden.OrdenId;
import com.nurticenter.produccion.domain.shared.Porcion;

import java.util.List;

/**
 * Mapper entre el agregado de dominio OrdenDeProduccion y su entidad JPA.
 * Mantiene el dominio libre de anotaciones de persistencia.
 */
final class OrdenEntityMapper {

    private OrdenEntityMapper() {}

    static OrdenJpaEntity aEntidad(OrdenDeProduccion orden) {
        List<ItemOrdenEmbeddable> items = orden.items().stream()
                .map(OrdenEntityMapper::aItemEmbeddable)
                .toList();
        return new OrdenJpaEntity(
                orden.id().valor(),
                orden.fechaProduccion(),
                orden.estado(),
                items,
                orden.paqueteIds());
    }

    static OrdenDeProduccion aDominio(OrdenJpaEntity entidad) {
        List<ItemOrden> items = entidad.getItems().stream()
                .map(OrdenEntityMapper::aItemDominio)
                .toList();
        return OrdenDeProduccion.reconstituir(
                OrdenId.de(entidad.getId()),
                entidad.getFechaProduccion(),
                entidad.getEstado(),
                items,
                entidad.getPaqueteIds());
    }

    private static ItemOrdenEmbeddable aItemEmbeddable(ItemOrden item) {
        Porcion porcion = item.porcionIndividual();
        return new ItemOrdenEmbeddable(
                item.recetaId(),
                item.nombreReceta(),
                item.cantidadTotal(),
                item.estado(),
                porcion.cantidad(),
                porcion.unidad(),
                porcion.descripcion());
    }

    private static ItemOrden aItemDominio(ItemOrdenEmbeddable e) {
        Porcion porcion = new Porcion(e.getPorcionCantidad(), e.getPorcionUnidad(), e.getPorcionDescripcion());
        return ItemOrden.reconstituir(
                e.getRecetaId(),
                e.getNombreReceta(),
                e.getCantidadTotal(),
                porcion,
                e.getEstado());
    }
}
