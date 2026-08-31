package com.nurticenter.produccion.infrastructure.persistence.paquete;

import com.nurticenter.produccion.domain.model.paquete.CodigoIdentificacion;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import com.nurticenter.produccion.domain.model.paquete.Etiqueta;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;

/**
 * Mapper entre el agregado de dominio PaqueteEtiquetado y su entidad JPA.
 */
final class PaqueteEntityMapper {

    private PaqueteEntityMapper() {}

    static PaqueteJpaEntity aEntidad(PaqueteEtiquetado paquete) {
        PaqueteJpaEntity entidad = new PaqueteJpaEntity(
                paquete.id().valor(),
                paquete.pacienteId().valor(),
                paquete.ordenId(),
                paquete.estado());

        Etiqueta etiqueta = paquete.etiqueta();
        if (etiqueta != null) {
            DireccionImpresa dir = etiqueta.direccion();
            CodigoIdentificacion codigo = etiqueta.codigo();
            entidad.asignarEtiqueta(
                    etiqueta.nombrePaciente(),
                    dir.calle(),
                    dir.numero(),
                    dir.referencia(),
                    dir.ciudad(),
                    codigo.valor(),
                    codigo.generadoEn(),
                    etiqueta.fechaGeneracion());
        }
        return entidad;
    }

    static PaqueteEtiquetado aDominio(PaqueteJpaEntity entidad) {
        Etiqueta etiqueta = null;
        if (entidad.tieneEtiqueta()) {
            DireccionImpresa direccion = new DireccionImpresa(
                    entidad.getEtiquetaCalle(),
                    entidad.getEtiquetaNumero(),
                    entidad.getEtiquetaReferencia(),
                    entidad.getEtiquetaCiudad());
            CodigoIdentificacion codigo = CodigoIdentificacion.de(
                    entidad.getCodigoValor(), entidad.getCodigoGeneradoEn());
            etiqueta = Etiqueta.reconstituir(
                    entidad.getEtiquetaNombrePaciente(),
                    direccion,
                    codigo,
                    entidad.getEtiquetaFechaGeneracion());
        }
        return PaqueteEtiquetado.reconstituir(
                PaqueteId.de(entidad.getId()),
                PacienteId.de(entidad.getPacienteId()),
                entidad.getOrdenId(),
                entidad.getEstado(),
                etiqueta);
    }
}
