package com.rrhh.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "identity_tenant")
public class Tenant {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "nombre_empresa", nullable = false)
    private String nombreEmpresa;

    @Column(nullable = false, length = 80, unique = true)
    private String slug;

    @Column(name = "rut_empresa", nullable = false, length = 20)
    private String rutEmpresa;

    @Column(nullable = false, length = 50)
    private String pais = "Chile";

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "razon_social")
    private String razonSocial;

    @Column(name = "giro")
    private String giro;

    @Column(name = "codigo_actividad", length = 10)
    private String codigoActividad;

    @Column(name = "direccion_calle")
    private String direccionCalle;

    @Column(name = "direccion_numero", length = 20)
    private String direccionNumero;

    @Column(name = "comuna", length = 100)
    private String comuna;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "email_contacto")
    private String emailContacto;

    @Column(name = "telefono_contacto", length = 30)
    private String telefonoContacto;

    @Column(name = "representante_nombre")
    private String representanteNombre;

    @Column(name = "representante_rut", length = 12)
    private String representanteRut;

    @Column(name = "terminos_aceptados_en")
    private Instant terminosAceptadosEn;

    @Column(name = "registrado_por", length = 36, columnDefinition = "CHAR(36)")
    private String registradoPor;

    @Setter(AccessLevel.NONE)
    @Column(name = "estado", nullable = false, length = 30)
    private String estado = TenantEstado.ACTIVO;

    /** Cambia el estado y mantiene {@code activo == (estado == ACTIVO)} (ADR-017). */
    public void cambiarEstado(String estado) {
        this.estado = estado;
        this.activo = TenantEstado.ACTIVO.equals(estado);
    }
}
