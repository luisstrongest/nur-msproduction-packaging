package com.nurticenter.produccion.infrastructure.persistence.paquete;

import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ENTIDAD JPA - PaqueteEtiquetado
 *
 * Los campos de la Etiqueta son nullable: solo se completan cuando el paquete
 * alcanza el estado ETIQUETADO. Hibernate genera la tabla desde esta clase.
 */
@Entity
@Table(name = "paquete_etiquetado", indexes = {
        @Index(name = "idx_paquete_orden", columnList = "orden_id"),
        @Index(name = "idx_paquete_paciente", columnList = "paciente_id"),
        @Index(name = "idx_paquete_codigo", columnList = "codigo_valor", unique = true)
})
public class PaqueteJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "paciente_id", nullable = false)
    private String pacienteId;

    @Column(name = "orden_id", nullable = false)
    private String ordenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoPaquete estado;

    // -- Etiqueta (nullable hasta el estado ETIQUETADO) --
    @Column(name = "etiqueta_nombre_paciente")
    private String etiquetaNombrePaciente;

    @Column(name = "etiqueta_calle")
    private String etiquetaCalle;

    @Column(name = "etiqueta_numero")
    private String etiquetaNumero;

    @Column(name = "etiqueta_referencia")
    private String etiquetaReferencia;

    @Column(name = "etiqueta_ciudad")
    private String etiquetaCiudad;

    @Column(name = "codigo_valor")
    private String codigoValor;

    @Column(name = "codigo_generado_en")
    private LocalDateTime codigoGeneradoEn;

    @Column(name = "etiqueta_fecha_generacion")
    private LocalDateTime etiquetaFechaGeneracion;

    // -- Destino de la entrega del CalendarioConsolidado (null si se creo a mano) --
    @Column(name = "destino_id_calendario_dia")
    private String destinoIdCalendarioDia;

    @Column(name = "destino_id_suscripcion")
    private String destinoIdSuscripcion;

    @Column(name = "destino_fecha_entrega")
    private LocalDate destinoFechaEntrega;

    @Column(name = "destino_id_direccion")
    private String destinoIdDireccion;

    @Column(name = "destino_latitud")
    private Double destinoLatitud;

    @Column(name = "destino_longitud")
    private Double destinoLongitud;

    @Column(name = "destino_nro_porciones")
    private Integer destinoNroPorciones;

    protected PaqueteJpaEntity() {
        // requerido por JPA
    }

    public PaqueteJpaEntity(String id, String pacienteId, String ordenId, EstadoPaquete estado) {
        this.id = id;
        this.pacienteId = pacienteId;
        this.ordenId = ordenId;
        this.estado = estado;
    }

    public void asignarEtiqueta(String nombrePaciente, String calle, String numero, String referencia,
                                String ciudad, String codigoValor, LocalDateTime codigoGeneradoEn,
                                LocalDateTime etiquetaFechaGeneracion) {
        this.etiquetaNombrePaciente = nombrePaciente;
        this.etiquetaCalle = calle;
        this.etiquetaNumero = numero;
        this.etiquetaReferencia = referencia;
        this.etiquetaCiudad = ciudad;
        this.codigoValor = codigoValor;
        this.codigoGeneradoEn = codigoGeneradoEn;
        this.etiquetaFechaGeneracion = etiquetaFechaGeneracion;
    }

    public boolean tieneEtiqueta() {
        return codigoValor != null;
    }

    public void asignarDestino(String idCalendarioDia, String idSuscripcion, LocalDate fechaEntrega,
                               String idDireccion, Double latitud, Double longitud, int nroPorciones) {
        this.destinoIdCalendarioDia = idCalendarioDia;
        this.destinoIdSuscripcion = idSuscripcion;
        this.destinoFechaEntrega = fechaEntrega;
        this.destinoIdDireccion = idDireccion;
        this.destinoLatitud = latitud;
        this.destinoLongitud = longitud;
        this.destinoNroPorciones = nroPorciones;
    }

    public boolean tieneDestino() {
        return destinoIdCalendarioDia != null;
    }

    public String getId()                          { return id; }
    public String getPacienteId()                  { return pacienteId; }
    public String getOrdenId()                     { return ordenId; }
    public EstadoPaquete getEstado()               { return estado; }
    public String getEtiquetaNombrePaciente()      { return etiquetaNombrePaciente; }
    public String getEtiquetaCalle()               { return etiquetaCalle; }
    public String getEtiquetaNumero()              { return etiquetaNumero; }
    public String getEtiquetaReferencia()          { return etiquetaReferencia; }
    public String getEtiquetaCiudad()              { return etiquetaCiudad; }
    public String getCodigoValor()                 { return codigoValor; }
    public LocalDateTime getCodigoGeneradoEn()     { return codigoGeneradoEn; }
    public LocalDateTime getEtiquetaFechaGeneracion() { return etiquetaFechaGeneracion; }
    public String getDestinoIdCalendarioDia()      { return destinoIdCalendarioDia; }
    public String getDestinoIdSuscripcion()        { return destinoIdSuscripcion; }
    public LocalDate getDestinoFechaEntrega()      { return destinoFechaEntrega; }
    public String getDestinoIdDireccion()          { return destinoIdDireccion; }
    public Double getDestinoLatitud()              { return destinoLatitud; }
    public Double getDestinoLongitud()             { return destinoLongitud; }
    public Integer getDestinoNroPorciones()        { return destinoNroPorciones; }
}
