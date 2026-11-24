package com.rrhh.identity.controller;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.PlataformaFixtures;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.AuditLogRepository;
import com.rrhh.identity.repository.GrupoEmpresasRepository;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.OutboxEventRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.TenantEstadoCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Suspender y reactivar una empresa (S6). */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class SuspensionIT {

    private static final String SUSPENDIDA = "La empresa está suspendida";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoEmpresasRepository grupoEmpresasRepository;
    @Autowired private GrupoTenantRepository grupoTenantRepository;
    @Autowired private GrupoOperadorRepository grupoOperadorRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private TenantEstadoCache tenantEstadoCache;

    private String a;
    private String b;

    @BeforeEach
    void datos() {
        a = PlataformaFixtures.tenant(tenantRepository, "Suspension IT A").getId();
        b = PlataformaFixtures.tenant(tenantRepository, "Suspension IT B").getId();
        PlataformaFixtures.grupoConOperador(grupoEmpresasRepository, grupoTenantRepository,
                grupoOperadorRepository, usuarioRepository, a);
        empresaDelTokenActiva();
    }

    @Test
    void operadorSuspendeEmpresaDeSuGrupo() throws Exception {
        suspender("operador-grupo", a, "Impago")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("SUSPENDIDO"))
                .andExpect(jsonPath("$.datos.activo").value(false));
        assertEquals(TenantEstado.SUSPENDIDO, tenantRepository.findById(a).orElseThrow().getEstado());
    }

    @Test
    void operadorFueraDeSuGrupoRecibe404() throws Exception {
        suspender("operador-grupo", b, "Impago").andExpect(status().isNotFound());
        assertEquals(TenantEstado.ACTIVO, tenantRepository.findById(b).orElseThrow().getEstado());
    }

    @Test
    void rolDeEmpresaNoSuspende() throws Exception {
        suspender("admin", a, "Impago").andExpect(status().isForbidden());
    }

    @Test
    void suspenderSinMotivoResponde400() throws Exception {
        suspender("admin-plataforma", a, "").andExpect(status().isBadRequest());
        suspender("admin-plataforma", a, "x".repeat(501)).andExpect(status().isBadRequest());
    }

    @Test
    void suspenderNoActivaResponde409() throws Exception {
        String pendiente = PlataformaFixtures.tenant(tenantRepository, "Pendiente", TenantEstado.PENDIENTE_ACTIVACION).getId();
        suspender("admin-plataforma", pendiente, "Impago").andExpect(status().isConflict());
        suspender("admin-plataforma", a, "Impago").andExpect(status().isOk());
        suspender("admin-plataforma", a, "Impago").andExpect(status().isConflict());
    }

    @Test
    void reactivarNoSuspendidaResponde409() throws Exception {
        reactivar("admin-plataforma", a).andExpect(status().isConflict());
    }

    @Test
    void tokenPrevioDeEmpresaSuspendidaRecibe403() throws Exception {
        me("admin-suspendida").andExpect(status().isOk());
        suspender("admin-plataforma", TestJwtConfig.TENANT_SUSPENDIBLE, "Impago").andExpect(status().isOk());
        me("admin-suspendida")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403))
                .andExpect(jsonPath("$.mensaje").value(SUSPENDIDA));
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer admin-suspendida"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value(SUSPENDIDA));
    }

    @Test
    void elFiltroBloqueaSegunElEstadoGuardadoAlVencerElCache() throws Exception {
        me("admin-suspendida").andExpect(status().isOk());
        Tenant t = tenantRepository.findById(TestJwtConfig.TENANT_SUSPENDIBLE).orElseThrow();
        t.cambiarEstado(TenantEstado.SUSPENDIDO);
        tenantRepository.saveAndFlush(t);
        tenantEstadoCache.invalidar(TestJwtConfig.TENANT_SUSPENDIBLE);
        me("admin-suspendida").andExpect(status().isForbidden());
    }

    @Test
    void rolDePlataformaNoEsBloqueadoPorElFiltro() throws Exception {
        suspender("admin-plataforma", TestJwtConfig.TENANT_SUSPENDIBLE, "Impago").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer operador-grupo"))
                .andExpect(status().isOk());
    }

    @Test
    void reactivarDevuelveElAcceso() throws Exception {
        suspender("admin-plataforma", TestJwtConfig.TENANT_SUSPENDIBLE, "Impago").andExpect(status().isOk());
        me("admin-suspendida").andExpect(status().isForbidden());
        reactivar("admin-plataforma", TestJwtConfig.TENANT_SUSPENDIBLE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"));
        me("admin-suspendida").andExpect(status().isOk());
    }

    @Test
    void suspensionQuedaAuditadaYEnElOutbox() throws Exception {
        suspender("operador-grupo", a, "Impago de marzo").andExpect(status().isOk());
        AuditLog log = auditLogRepository.findAll().stream()
                .filter(l -> a.equals(l.getEntidadId()) && "SuspensionEmpresa".equals(l.getAccion()))
                .findFirst().orElseThrow();
        assertEquals(a, log.getTenantId());
        assertTrue(log.getValoresNuevos().contains("Impago de marzo"));
        assertTrue(outboxEventRepository.findAll().stream()
                .anyMatch(e -> "tenant.suspendido".equals(e.getEventType()) && a.equals(e.getAggregateId())));
        reactivar("operador-grupo", a).andExpect(status().isOk());
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(l -> a.equals(l.getEntidadId()) && "ReactivacionEmpresa".equals(l.getAccion())));
        assertTrue(outboxEventRepository.findAll().stream()
                .anyMatch(e -> "tenant.reactivado".equals(e.getEventType()) && a.equals(e.getAggregateId())));
    }

    private void empresaDelTokenActiva() {
        Tenant t = tenantRepository.findById(TestJwtConfig.TENANT_SUSPENDIBLE).orElseGet(() -> {
            Tenant nuevo = new Tenant();
            nuevo.setId(TestJwtConfig.TENANT_SUSPENDIBLE);
            nuevo.setNombreEmpresa("Empresa Suspendible");
            nuevo.setSlug("empresa-suspendible");
            nuevo.setRutEmpresa("SUSP-1");
            nuevo.setCreadoEn(Instant.now());
            return nuevo;
        });
        t.cambiarEstado(TenantEstado.ACTIVO);
        tenantRepository.saveAndFlush(t);
        if (usuarioRepository.findByCognitoSub("cognito-admin-suspendida").isEmpty()) {
            Usuario u = new Usuario();
            u.setId(java.util.UUID.randomUUID().toString());
            u.setTenantId(TestJwtConfig.TENANT_SUSPENDIBLE);
            u.setEmail("admin.suspendida@rrhh.local");
            u.setCognitoSub("cognito-admin-suspendida");
            u.setRol("Admin de RRHH");
            u.setEstado("ACTIVO");
            u.setCreadoEn(Instant.now());
            usuarioRepository.saveAndFlush(u);
        }
        tenantEstadoCache.invalidar(TestJwtConfig.TENANT_SUSPENDIBLE);
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
    }

    private ResultActions suspender(String token, String tenantId, String motivo) throws Exception {
        return mockMvc.perform(post("/api/v1/plataforma/tenants/" + tenantId + "/suspender")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private ResultActions reactivar(String token, String tenantId) throws Exception {
        return mockMvc.perform(post("/api/v1/plataforma/tenants/" + tenantId + "/reactivar")
                .header("Authorization", "Bearer " + token));
    }
}
