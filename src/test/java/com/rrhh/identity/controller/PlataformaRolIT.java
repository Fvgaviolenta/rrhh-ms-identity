package com.rrhh.identity.controller;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.PlataformaFixtures;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.repository.AuditLogRepository;
import com.rrhh.identity.repository.GrupoEmpresasRepository;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cambio de rol de un integrante desde la plataforma (S5). */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class PlataformaRolIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoEmpresasRepository grupoEmpresasRepository;
    @Autowired private GrupoTenantRepository grupoTenantRepository;
    @Autowired private GrupoOperadorRepository grupoOperadorRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private String a;
    private String b;

    @BeforeEach
    void datos() {
        a = PlataformaFixtures.tenant(tenantRepository, "Rol IT A").getId();
        b = PlataformaFixtures.tenant(tenantRepository, "Rol IT B").getId();
        PlataformaFixtures.grupoConOperador(grupoEmpresasRepository, grupoTenantRepository,
                grupoOperadorRepository, usuarioRepository, a);
    }

    @Test
    void operadorCambiaATrabajadorAJefaturaEnSuGrupo() throws Exception {
        String trab = integrante(a, "Trabajador", "ACTIVO");
        cambiar("operador-grupo", a, trab, "Jefatura")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("Jefatura"));
        assertEquals("Jefatura", usuarioRepository.findById(trab).orElseThrow().getRol());
    }

    @Test
    void adminPlataformaCambiaElRolEnCualquierEmpresa() throws Exception {
        String trab = integrante(b, "Trabajador", "ACTIVO");
        cambiar("admin-plataforma", b, trab, "Admin de RRHH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("Admin de RRHH"));
    }

    @Test
    void rolDeSuperAdminNoSeCambia() throws Exception {
        String superAdmin = integrante(a, "SuperAdmin", "ACTIVO");
        cambiar("operador-grupo", a, superAdmin, "Trabajador").andExpect(status().isForbidden());
    }

    @Test
    void rolNoPermitidoResponde400() throws Exception {
        String trab = integrante(a, "Trabajador", "ACTIVO");
        cambiar("operador-grupo", a, trab, "SuperAdmin").andExpect(status().isBadRequest());
        cambiar("operador-grupo", a, trab, "AdminPlataforma").andExpect(status().isBadRequest());
    }

    @Test
    void cuentaNoActivaResponde400() throws Exception {
        String invitado = integrante(a, "Trabajador", "INVITADO");
        cambiar("operador-grupo", a, invitado, "Jefatura").andExpect(status().isBadRequest());
    }

    @Test
    void usuarioDeOtraEmpresaEnLaRutaResponde404() throws Exception {
        String deB = integrante(b, "Trabajador", "ACTIVO");
        cambiar("operador-grupo", a, deB, "Jefatura").andExpect(status().isNotFound());
        cambiar("admin-plataforma", a, deB, "Jefatura").andExpect(status().isNotFound());
    }

    @Test
    void fueraDelGrupoResponde404() throws Exception {
        String deB = integrante(b, "Trabajador", "ACTIVO");
        cambiar("operador-grupo", b, deB, "Jefatura").andExpect(status().isNotFound());
        assertEquals("Trabajador", usuarioRepository.findById(deB).orElseThrow().getRol());
    }

    @Test
    void rolDeEmpresaNoUsaLaRutaDePlataforma() throws Exception {
        String trab = integrante("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Trabajador", "ACTIVO");
        cambiar("admin", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", trab, "Jefatura").andExpect(status().isForbidden());
    }

    @Test
    void cambioDeRolQuedaAuditadoEnLaEmpresa() throws Exception {
        String trab = integrante(a, "Trabajador", "ACTIVO");
        cambiar("operador-grupo", a, trab, "Jefatura").andExpect(status().isOk());
        AuditLog log = auditLogRepository.findAll().stream()
                .filter(l -> trab.equals(l.getEntidadId()) && "Modificacion".equals(l.getAccion()))
                .findFirst().orElseThrow();
        assertEquals(a, log.getTenantId());
        assertEquals(TestJwtConfig.OPERADOR_GRUPO_ID, log.getUsuarioId());
        assertEquals("rol=Trabajador", log.getValoresAnteriores());
        assertTrue(log.getValoresNuevos().contains("rol=Jefatura"));
    }

    private String integrante(String tenantId, String rol, String estado) {
        return PlataformaFixtures.usuario(usuarioRepository, tenantId, rol, estado).getId();
    }

    private ResultActions cambiar(String token, String tenantId, String usuarioId, String rol) throws Exception {
        return mockMvc.perform(patch("/api/v1/plataforma/tenants/" + tenantId + "/usuarios/" + usuarioId + "/rol")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"" + rol + "\"}"));
    }
}
