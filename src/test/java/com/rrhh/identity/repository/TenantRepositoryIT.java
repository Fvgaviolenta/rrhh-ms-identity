package com.rrhh.identity.repository;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IdentityApplication.class)
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class TenantRepositoryIT {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private RolRepository rolRepository;

    @Test
    void V5NormalizaRutDelSeedDemo() {
        Tenant demo = tenantRepository.findByRutEmpresa("76123456-0").orElseThrow();
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", demo.getId());
        assertEquals("ACTIVO", demo.getEstado());
        assertTrue(tenantRepository.findByRutEmpresa("76.123.456-7").isEmpty());
    }

    @Test
    void cambiarEstadoMantieneActivoSincronizado() {
        Tenant t = new Tenant();
        t.cambiarEstado(TenantEstado.PENDIENTE_ACTIVACION);
        assertFalse(t.isActivo());
        t.cambiarEstado(TenantEstado.ACTIVO);
        assertTrue(t.isActivo());
    }

    @Test
    void rolAdminPlataformaSembrado() {
        assertTrue(rolRepository.findByCodigo("AdminPlataforma").isPresent());
    }
}
