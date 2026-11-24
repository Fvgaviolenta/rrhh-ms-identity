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

/** OperadorSaaS asignado a un grupo de empresas; un operador puede estar en varios grupos. */
@Getter
@NoArgsConstructor
@Entity
@IdClass(GrupoOperador.Pk.class)
@Table(name = "identity_grupo_operador")
public class GrupoOperador {
    @Id @Column(name = "grupo_id", length = 36, columnDefinition = "CHAR(36)") private String grupoId;
    @Id @Column(name = "usuario_id", length = 36, columnDefinition = "CHAR(36)") private String usuarioId;

    public GrupoOperador(String grupoId, String usuarioId) {
        this.grupoId = grupoId;
        this.usuarioId = usuarioId;
    }

    @Getter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        private String grupoId;
        private String usuarioId;
    }
}
