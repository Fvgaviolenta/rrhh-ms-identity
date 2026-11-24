package com.rrhh.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Grupo de empresas con nombre que el AdminPlataforma asigna a OperadoresSaaS (ADR-020). */
@Getter
@Setter
@Entity
@Table(name = "identity_grupo_empresas")
public class GrupoEmpresas {
    @Id @Column(length = 36, columnDefinition = "CHAR(36)") private String id;
    @Column(nullable = false, length = 80, unique = true) private String nombre;
    @Column(name = "creado_en", nullable = false) private Instant creadoEn;
}
