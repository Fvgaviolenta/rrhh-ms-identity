package com.rrhh.identity.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Empresa dentro de un grupo; {@code tenant_id} es único: una empresa está en a lo más un grupo. */
@Getter
@NoArgsConstructor
@Entity
@IdClass(GrupoTenant.Pk.class)
@Table(name = "identity_grupo_tenant")
public class GrupoTenant {
    @Id @Column(name = "grupo_id", length = 36, columnDefinition = "CHAR(36)") private String grupoId;
    @Id @Column(name = "tenant_id", length = 36, columnDefinition = "CHAR(36)", unique = true) private String tenantId;

    public GrupoTenant(String grupoId, String tenantId) {
        this.grupoId = grupoId;
        this.tenantId = tenantId;
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String grupoId;
        private String tenantId;
    }
}
