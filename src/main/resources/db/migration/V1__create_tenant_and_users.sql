CREATE TABLE identity_tenant (
    id CHAR(36) NOT NULL PRIMARY KEY,
    nombre_empresa VARCHAR(255) NOT NULL,
    rut_empresa VARCHAR(20) NOT NULL,
    pais VARCHAR(50) NOT NULL DEFAULT 'Chile',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en DATETIME NOT NULL
);

CREATE TABLE identity_rol (
    id CHAR(36) NOT NULL PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL
);

CREATE TABLE identity_usuario (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    trabajador_id CHAR(36) NULL,
    email VARCHAR(255) NOT NULL,
    cognito_sub VARCHAR(255) NULL,
    rol VARCHAR(50) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_usuario_tenant FOREIGN KEY (tenant_id) REFERENCES identity_tenant (id),
    CONSTRAINT uk_usuario_tenant_email UNIQUE (tenant_id, email)
);

CREATE TABLE identity_audit_log (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    usuario_id CHAR(36) NULL,
    entidad VARCHAR(80) NOT NULL,
    entidad_id CHAR(36) NOT NULL,
    accion VARCHAR(40) NOT NULL,
    valores_anteriores TEXT NULL,
    valores_nuevos TEXT NULL,
    fecha_evento DATETIME NOT NULL,
    CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES identity_tenant (id)
);

CREATE INDEX idx_usuario_cognito ON identity_usuario (cognito_sub);
CREATE INDEX idx_audit_tenant_fecha ON identity_audit_log (tenant_id, fecha_evento);
