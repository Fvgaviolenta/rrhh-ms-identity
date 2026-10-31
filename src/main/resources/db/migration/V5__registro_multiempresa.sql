-- Registro multi-empresa por RUT, estado de tenant, username de Cognito,
-- rol AdminPlataforma y outbox de eventos (ADR-017). No modificar V1-V4.

ALTER TABLE identity_tenant ADD COLUMN razon_social VARCHAR(255) NULL;
ALTER TABLE identity_tenant ADD COLUMN giro VARCHAR(255) NULL;
ALTER TABLE identity_tenant ADD COLUMN codigo_actividad VARCHAR(10) NULL;
ALTER TABLE identity_tenant ADD COLUMN direccion_calle VARCHAR(255) NULL;
ALTER TABLE identity_tenant ADD COLUMN direccion_numero VARCHAR(20) NULL;
ALTER TABLE identity_tenant ADD COLUMN comuna VARCHAR(100) NULL;
ALTER TABLE identity_tenant ADD COLUMN region VARCHAR(100) NULL;
ALTER TABLE identity_tenant ADD COLUMN email_contacto VARCHAR(255) NULL;
ALTER TABLE identity_tenant ADD COLUMN telefono_contacto VARCHAR(30) NULL;
ALTER TABLE identity_tenant ADD COLUMN representante_nombre VARCHAR(255) NULL;
ALTER TABLE identity_tenant ADD COLUMN representante_rut VARCHAR(12) NULL;
ALTER TABLE identity_tenant ADD COLUMN terminos_aceptados_en DATETIME NULL;
ALTER TABLE identity_tenant ADD COLUMN registrado_por CHAR(36) NULL;
ALTER TABLE identity_tenant ADD COLUMN estado VARCHAR(30) NOT NULL DEFAULT 'ACTIVO';

UPDATE identity_tenant SET estado = 'SUSPENDIDO' WHERE activo = FALSE;

-- El RUT semilla 76.123.456-7 no pasa el modulo 11; el digito correcto es 0.
UPDATE identity_tenant SET rut_empresa = '76.123.456-0' WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

-- Normaliza: sin puntos ni espacios, K en mayuscula y con guion antes del digito verificador.
UPDATE identity_tenant SET rut_empresa = UPPER(REPLACE(REPLACE(rut_empresa, '.', ''), ' ', ''));
UPDATE identity_tenant
SET rut_empresa = CONCAT(LEFT(rut_empresa, CHAR_LENGTH(rut_empresa) - 1), '-', RIGHT(rut_empresa, 1))
WHERE LOCATE('-', rut_empresa) = 0 AND CHAR_LENGTH(rut_empresa) > 1;

ALTER TABLE identity_tenant ADD CONSTRAINT uk_tenant_rut UNIQUE (rut_empresa);

ALTER TABLE identity_usuario ADD COLUMN cognito_username VARCHAR(255) NULL;

INSERT INTO identity_rol (id, codigo, nombre) VALUES
    ('11111111-1111-1111-1111-111111111006', 'AdminPlataforma', 'Administrador de plataforma');

CREATE TABLE identity_outbox_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    payload TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    intentos INT NOT NULL DEFAULT 0,
    creado_en DATETIME NOT NULL,
    publicado_en DATETIME NULL,
    ultimo_error TEXT NULL
);
CREATE INDEX idx_identity_outbox_estado ON identity_outbox_event (estado, creado_en);
