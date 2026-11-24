package com.rrhh.identity.service;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.GrupoEmpresas;
import com.rrhh.identity.model.GrupoOperador;
import com.rrhh.identity.model.GrupoTenant;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.GrupoEmpresasRepository;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.TenantContext.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = IdentityApplication.class)
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class AlcancePlataformaServiceIT {

    private static final String TENANT_PLATAFORMA = "00000000-0000-0000-0000-000000000001";

    @Autowired private AlcancePlataformaService alcance;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoEmpresasRepository grupoEmpresasRepository;
    @Autowired private GrupoTenantRepository grupoTenantRepository;
    @Autowired private GrupoOperadorRepository grupoOperadorRepository;

    private String a;
    private String b;
    private String operadorId;
    private String operadorSinGrupoId;

    @BeforeEach
    void datos() {
        a = tenant("Alcance A").getId();
        b = tenant("Alcance B").getId();
        operadorId = operadorRow().getId();
        operadorSinGrupoId = operadorRow().getId();

        GrupoEmpresas grupo = new GrupoEmpresas();
        grupo.setId(UUID.randomUUID().toString());
        grupo.setNombre("Grupo " + UUID.randomUUID().toString().substring(0, 8));
        grupo.setCreadoEn(Instant.now());
        grupoEmpresasRepository.save(grupo);
        grupoTenantRepository.save(new GrupoTenant(grupo.getId(), a));
        grupoOperadorRepository.save(new GrupoOperador(grupo.getId(), operadorId));
    }

    @Test
    void adminPlataformaAlcanzaCualquierEmpresaCliente() {
        assertTrue(alcance.alcanza(admin(), a));
        assertTrue(alcance.alcanza(admin(), b));
    }

    @Test
    void adminPlataformaNoAlcanzaLaEmpresaPlataforma() {
        assertFalse(alcance.alcanza(admin(), TENANT_PLATAFORMA));
    }

    @Test
    void operadorSoloAlcanzaLasEmpresasDeSuGrupo() {
        assertTrue(alcance.alcanza(operador(operadorId), a));
        assertFalse(alcance.alcanza(operador(operadorId), b));
    }

    @Test
    void operadorSinGrupoNoAlcanzaNinguna() {
        assertEquals(Optional.of(Set.of()), alcance.tenantsEnAlcance(operador(operadorSinGrupoId)));
    }

    @Test
    void rolDeEmpresaNoAlcanzaNada() {
        assertFalse(alcance.alcanza(adminRrhh(a), a));
    }

    @Test
    void exigirFueraDeAlcanceResponde404() {
        DomainException e = assertThrows(DomainException.class, () -> alcance.exigir(operador(operadorId), b));
        assertEquals(404, e.getCodigo());
    }

    @Test
    void operadorSinClaimUserIdSeResuelvePorSuCognitoSub() {
        Usuario row = usuarioRepository.findById(operadorId).orElseThrow();
        row.setCognitoSub("cognito-sub-" + operadorId);
        usuarioRepository.saveAndFlush(row);
        AuthenticatedUser sinClaim = new AuthenticatedUser(null, null, row.getEmail(), "OperadorSaaS",
                null, "cognito-sub-" + operadorId, null, null, true);
        assertTrue(alcance.alcanza(sinClaim, a));
        assertFalse(alcance.alcanza(sinClaim, b));
    }

    private Tenant tenant(String nombre) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Tenant t = new Tenant();
        t.setId(UUID.randomUUID().toString());
        t.setNombreEmpresa(nombre + " " + sufijo);
        t.setSlug("alcance-" + sufijo);
        t.setRutEmpresa("AL-" + sufijo);
        t.setCreadoEn(Instant.now());
        t.cambiarEstado(TenantEstado.ACTIVO);
        return tenantRepository.save(t);
    }

    private Usuario operadorRow() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID().toString());
        u.setEmail("op-" + u.getId().substring(0, 8) + "@rrhh.local");
        u.setRol("OperadorSaaS");
        u.setEstado("ACTIVO");
        u.setCreadoEn(Instant.now());
        return usuarioRepository.saveAndFlush(u);
    }

    private static AuthenticatedUser admin() {
        return new AuthenticatedUser(null, "admin-plataforma-id", "owner@gmail.com", "AdminPlataforma",
                null, "cognito-owner", null, null, true);
    }

    private static AuthenticatedUser operador(String userId) {
        return new AuthenticatedUser(null, userId, "op@rrhh.local", "OperadorSaaS",
                null, "cognito-" + userId, null, null, false);
    }

    private static AuthenticatedUser adminRrhh(String tenantId) {
        return new AuthenticatedUser(tenantId, "rrhh-id", "rrhh@rrhh.local", "Admin de RRHH",
                null, "cognito-rrhh", null, null, false);
    }
}
