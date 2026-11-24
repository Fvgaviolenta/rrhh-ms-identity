-- Grupos de empresas para el alcance de los roles de plataforma (ADR-020).
-- Una empresa está en a lo más un grupo; un OperadorSaaS puede estar en varios. No modificar V1-V5.

CREATE TABLE identity_grupo_empresas (
    id CHAR(36) NOT NULL PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT uk_grupo_empresas_nombre UNIQUE (nombre)
);

CREATE TABLE identity_grupo_tenant (
    grupo_id CHAR(36) NOT NULL,
    tenant_id CHAR(36) NOT NULL,
    PRIMARY KEY (grupo_id, tenant_id),
    CONSTRAINT uk_grupo_tenant_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_grupo_tenant_grupo FOREIGN KEY (grupo_id) REFERENCES identity_grupo_empresas (id),
    CONSTRAINT fk_grupo_tenant_tenant FOREIGN KEY (tenant_id) REFERENCES identity_tenant (id)
);

CREATE TABLE identity_grupo_operador (
    grupo_id CHAR(36) NOT NULL,
    usuario_id CHAR(36) NOT NULL,
    PRIMARY KEY (grupo_id, usuario_id),
    CONSTRAINT fk_grupo_operador_grupo FOREIGN KEY (grupo_id) REFERENCES identity_grupo_empresas (id),
    CONSTRAINT fk_grupo_operador_usuario FOREIGN KEY (usuario_id) REFERENCES identity_usuario (id)
);
CREATE INDEX idx_grupo_operador_usuario ON identity_grupo_operador (usuario_id);
