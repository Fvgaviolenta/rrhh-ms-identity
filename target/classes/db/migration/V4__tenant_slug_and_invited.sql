-- Slug de empresa para el login en dos pasos + rol OperadorSaaS (ADR-013).
-- No modificar V1–V3.

ALTER TABLE identity_tenant ADD COLUMN slug VARCHAR(80);

UPDATE identity_tenant
SET slug = 'empresa-demo-spa'
WHERE id = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';

UPDATE identity_tenant
SET slug = LOWER(REPLACE(REPLACE(REPLACE(nombre_empresa, ' ', '-'), '.', ''), ',', ''))
WHERE slug IS NULL;

INSERT INTO identity_tenant (id, nombre_empresa, rut_empresa, pais, activo, creado_en, slug) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Plataforma RRHH SaaS', '00.000.000-0', 'Chile', TRUE, CURRENT_TIMESTAMP, 'plataforma');

ALTER TABLE identity_tenant MODIFY slug VARCHAR(80) NOT NULL;
ALTER TABLE identity_tenant ADD CONSTRAINT uk_tenant_slug UNIQUE (slug);

INSERT INTO identity_rol (id, codigo, nombre) VALUES
    ('11111111-1111-1111-1111-111111111005', 'OperadorSaaS', 'Operador de plataforma');

INSERT INTO identity_usuario (id, tenant_id, trabajador_id, email, cognito_sub, rol, activo, creado_en, estado, nombre) VALUES
    ('dddddddd-dddd-dddd-dddd-dddddddddddd',
     NULL,
     NULL,
     'operador@rrhh.local',
     NULL,
     'OperadorSaaS',
     TRUE,
     CURRENT_TIMESTAMP,
     'ACTIVO',
     'Operador Plataforma');
