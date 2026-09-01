package com.rrhh.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "identity_usuario")
public class Usuario {

    @Id
    @Column(length = 36, columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "tenant_id", length = 36, columnDefinition = "CHAR(36)")
    private String tenantId;

    @Column(name = "trabajador_id", length = 36, columnDefinition = "CHAR(36)")
    private String trabajadorId;

    @Column(nullable = false)
    private String email;

    @Column
    private String nombre;

    @Column(name = "cognito_sub")
    private String cognitoSub;

    @Column(length = 50)
    private String rol;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO";

    @Generated(event = EventType.INSERT)
    @Column(name = "secuencia", insertable = false, updatable = false)
    private Long secuencia;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;
}
