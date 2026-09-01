INSERT INTO identity_rol (id, codigo, nombre) VALUES
    ('11111111-1111-1111-1111-111111111001', 'SuperAdmin', 'SuperAdmin del tenant'),
    ('11111111-1111-1111-1111-111111111002', 'Admin de RRHH', 'Administrador de RRHH'),
    ('11111111-1111-1111-1111-111111111003', 'Jefatura', 'Jefatura de equipo'),
    ('11111111-1111-1111-1111-111111111004', 'Trabajador', 'Trabajador');

INSERT INTO identity_tenant (id, nombre_empresa, rut_empresa, pais, activo, creado_en) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Empresa Demo SpA', '76.123.456-7', 'Chile', TRUE, CURRENT_TIMESTAMP);

INSERT INTO identity_usuario (id, tenant_id, trabajador_id, email, cognito_sub, rol, activo, creado_en) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     NULL,
     'admin.demo@rrhh.local',
     NULL,
     'Admin de RRHH',
     TRUE,
     CURRENT_TIMESTAMP);
