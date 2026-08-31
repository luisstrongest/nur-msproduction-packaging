package com.nurticenter.produccion.domain.model.paquete;

import com.nurticenter.produccion.domain.event.PaqueteListo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AGREGADO RAIZ - PaqueteEtiquetado
 *
 * Gestiona el ciclo de vida del paquete de alimentacion de un paciente:
 * ENVASADO_PENDIENTE -> ENVASADO -> ETIQUETADO -> LISTO
 *
 * Principio SRP: solo gestiona el paquete fisico. No conoce recetas, rutas ni contratos.
 * Principio DIP: PacienteId es una referencia opaca; BC4 no depende de BC1.
 */
public class PaqueteEtiquetado {

    // -- Identity ------------------------------------------------------------------
    private final PaqueteId id;

    // -- Referencias externas (solo IDs - no objetos de otros BC) -------------------
    private final PacienteId pacienteId;   // ref. opaca a BC1
    private final String ordenId;          // ref. opaca a OrdenDeProduccion

    // -- Atributos -------------------------------------------------------------------
    private Etiqueta etiqueta;
    private EstadoPaquete estado;

    // -- Eventos pendientes -----------------------------------------------------------
    private final List<Object> eventos;

    // -- Constructor privado ----------------------------------------------------------
    private PaqueteEtiquetado(PaqueteId id, PacienteId pacienteId, String ordenId) {
        this.id         = id;
        this.pacienteId = pacienteId;
        this.ordenId    = ordenId;
        this.estado     = EstadoPaquete.ENVASADO_PENDIENTE;
        this.etiqueta   = null;
        this.eventos    = new ArrayList<>();
    }

    // -- Factory Method -----------------------------------------------------------------
    /**
     * Crea un nuevo PaqueteEtiquetado para un paciente.
     * Los datos del paciente se reciben desnormalizados desde el CalendarioConsolidado
     * - BC4 no consulta BC1 en tiempo de ejecucion.
     *
     * @param pacienteId Referencia opaca al paciente (solo el Id)
     * @param ordenId    Id de la OrdenDeProduccion que genera este paquete
     * @return PaqueteEtiquetado en estado ENVASADO_PENDIENTE
     */
    public static PaqueteEtiquetado crear(PacienteId pacienteId, String ordenId) {
        return new PaqueteEtiquetado(PaqueteId.nuevo(), pacienteId, ordenId);
    }

    /**
     * Reconstruye un PaqueteEtiquetado desde persistencia, preservando su estado y etiqueta.
     * NO emite eventos. Uso exclusivo de los adaptadores de infraestructura.
     */
    public static PaqueteEtiquetado reconstituir(PaqueteId id, PacienteId pacienteId, String ordenId,
                                                 EstadoPaquete estado, Etiqueta etiqueta) {
        PaqueteEtiquetado paquete = new PaqueteEtiquetado(id, pacienteId, ordenId);
        paquete.estado   = estado;
        paquete.etiqueta = etiqueta;
        return paquete;
    }

    // -- Comportamiento -----------------------------------------------------------------

    /**
     * Registra el envasado de la porcion.
     * Transicion: ENVASADO_PENDIENTE -> ENVASADO
     */
    public void registrarEnvasado() {
        if (this.estado != EstadoPaquete.ENVASADO_PENDIENTE) {
            throw new TransicionEstadoInvalidaException(
                    "El paquete ya fue envasado. Estado actual: " + this.estado
            );
        }
        this.estado = EstadoPaquete.ENVASADO;
    }

    /**
     * Genera la Etiqueta del paquete a partir de los datos del paciente.
     * La etiqueta es inmutable: incluye una copia de DireccionImpresa
     * que no cambia aunque BC3 actualice la direccion mas tarde.
     *
     * Transicion: ENVASADO -> ETIQUETADO
     *
     * @param datos DatosPaciente recibidos del CalendarioConsolidado (desnormalizados)
     */
    public void generarEtiqueta(DatosPaciente datos) {
        if (this.estado != EstadoPaquete.ENVASADO) {
            throw new EtiquetaSinEnvasadoException(
                    "No se puede etiquetar un paquete que no ha sido envasado."
            );
        }
        if (datos == null || !datos.esCompleto()) {
            throw new DatosPacienteIncompletosException(
                    "Los datos del paciente son insuficientes para generar la etiqueta."
            );
        }
        CodigoIdentificacion codigo = CodigoIdentificacion.generar(this.id);
        this.etiqueta = Etiqueta.crear(datos.nombreCompleto(), datos.direccionDelDia(), codigo);
        this.estado   = EstadoPaquete.ETIQUETADO;
    }

    /**
     * Marca el paquete como listo para transferir a BC5.
     * Transicion: ETIQUETADO -> LISTO
     *
     * Invariante: solo puede marcarse LISTO si la Etiqueta es valida.
     */
    public void marcarListo() {
        if (this.estado != EstadoPaquete.ETIQUETADO) {
            throw new TransicionEstadoInvalidaException(
                    "El paquete debe estar ETIQUETADO antes de marcarse LISTO."
            );
        }
        if (this.etiqueta == null || !this.etiqueta.esValida()) {
            throw new EtiquetaInvalidaException(
                    "El paquete no puede marcarse LISTO: su etiqueta es invalida o esta ausente."
            );
        }
        this.estado = EstadoPaquete.LISTO;
        this.eventos.add(new PaqueteListo(
                this.id.valor(),
                this.pacienteId.valor(),
                this.etiqueta.codigo().valor(),
                LocalDateTime.now()
        ));
    }

    // -- Consultas -----------------------------------------------------------------------

    public PaqueteId id()           { return id; }
    public PacienteId pacienteId()  { return pacienteId; }
    public String ordenId()         { return ordenId; }
    public Etiqueta etiqueta()      { return etiqueta; }
    public EstadoPaquete estado()   { return estado; }

    public List<Object> pullEventos() {
        List<Object> copia = List.copyOf(this.eventos);
        this.eventos.clear();
        return copia;
    }
}
