package com.rrhh.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
