-- Usuarios federados (Google via Cognito) que ingresan sin tenant ni rol asignado.
-- Quedan registrados como PENDIENTE hasta que un administrador les asigne tenant y rol.

ALTER TABLE identity_usuario MODIFY tenant_id CHAR(36) NULL;
ALTER TABLE identity_usuario MODIFY rol VARCHAR(50) NULL;

ALTER TABLE identity_usuario ADD COLUMN estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO';
ALTER TABLE identity_usuario ADD COLUMN nombre VARCHAR(255) NULL;
ALTER TABLE identity_usuario ADD COLUMN secuencia BIGINT NOT NULL AUTO_INCREMENT UNIQUE;

ALTER TABLE identity_usuario ADD CONSTRAINT uk_usuario_cognito UNIQUE (cognito_sub);
