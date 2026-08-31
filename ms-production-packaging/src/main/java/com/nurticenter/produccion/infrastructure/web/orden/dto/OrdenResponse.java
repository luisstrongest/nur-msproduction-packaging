package com.nurticenter.produccion.infrastructure.web.orden.dto;

import com.nurticenter.produccion.domain.model.orden.ItemOrden;
import com.nurticenter.produccion.domain.model.orden.OrdenDeProduccion;

import java.time.LocalDate;
import java.util.List;

/**
 * RESPONSE - vista de lectura de una OrdenDeProduccion.
 */
public record OrdenResponse(
        String id,
        LocalDate fechaProduccion,
        String estado,
        int totalPorciones,
        List<ItemResponse> items,
        List<String> paqueteIds
) {

    public record ItemResponse(
            String recetaId,
            String nombreReceta,
            int cantidadTotal,
            String estado,
            double porcionCantidad,
            String porcionUnidad,
            String porcionDescripcion
    ) {
        static ItemResponse desde(ItemOrden item) {
            return new ItemResponse(
                    item.recetaId(),
                    item.nombreReceta(),
                    item.cantidadTotal(),
                    item.estado().name(),
                    item.porcionIndividual().cantidad(),
                    item.porcionIndividual().unidad().name(),
                    item.porcionIndividual().descripcion());
        }
    }

    public static OrdenResponse desde(OrdenDeProduccion orden) {
        List<ItemResponse> items = orden.items().stream()
                .map(ItemResponse::desde)
                .toList();
        return new OrdenResponse(
                orden.id().valor(),
                orden.fechaProduccion(),
                orden.estado().name(),
                orden.calcularTotalPorciones(),
                items,
                orden.paqueteIds());
    }
}
