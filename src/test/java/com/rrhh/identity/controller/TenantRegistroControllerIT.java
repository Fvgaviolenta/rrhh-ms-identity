package com.rrhh.identity.controller;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.AuditLogRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class TenantRegistroControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    static String body(String rut) {
        return """
                {"razon_social":"Acme Servicios SpA","nombre_fantasia":"Acme","rut_empresa":"%s","giro":"Servicios",
                 "direccion_calle":"Av. Siempre Viva","direccion_numero":"742","comuna":"Santiago","region":"Metropolitana",
                 "email_contacto":"contacto@acme.cl","representante_nombre":"Rita Registrante","representante_rut":"11.111.111-1",
                 "acepta_terminos":true}""".formatted(rut);
    }

    @Test
    void registraEmpresaPendienteYUsuarioSuperAdminPendiente() throws Exception {
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer registrante")
                        .contentType(MediaType.APPLICATION_JSON).content(body("76.086.428-5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE_ACTIVACION"))
                .andExpect(jsonPath("$.datos.rut_empresa").value("76086428-5"))
                .andExpect(jsonPath("$.datos.slug").value("acme"));
        Usuario u = usuarioRepository.findByCognitoSub("cognito-reg-1").orElseThrow();
        assertEquals("SuperAdmin", u.getRol());
        assertEquals("PENDIENTE", u.getEstado());
        assertEquals("google_222", u.getCognitoUsername());
        Tenant t = tenantRepository.findByRutEmpresa("76086428-5").orElseThrow();
        assertEquals(u.getId(), t.getRegistradoPor());
        assertEquals("11111111-1", t.getRepresentanteRut());
        assertNotNull(t.getTerminosAceptadosEn());
        assertFalse(t.isActivo());
        assertEquals(t.getId(), u.getTenantId());
        assertTrue(auditLogRepository.findAll().stream()
                .anyMatch(a -> "RegistroEmpresa".equals(a.getAccion()) && t.getId().equals(a.getEntidadId())));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer registrante"))
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.datos.tenant_estado").value("PENDIENTE_ACTIVACION"))
                .andExpect(jsonPath("$.datos.tenant_nombre").value("Acme"));
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "76086428-5")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer registrante")
                        .contentType(MediaType.APPLICATION_JSON).content(body("12.345.678-5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Tu cuenta ya está asociada a una empresa"));
    }

    @Test
    void rechazaRutDuplicadoInvalidoTerminosYUsuarioConEmpresa() throws Exception {
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer postulante")
                        .contentType(MediaType.APPLICATION_JSON).content(body("76.123.456-0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya existe una empresa registrada con ese RUT"));
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer postulante")
                        .contentType(MediaType.APPLICATION_JSON).content(body("76.123.456-7")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El RUT de la empresa no es válido"));
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer postulante")
                        .contentType(MediaType.APPLICATION_JSON).content(body("12.345.678-5").replace("true", "false")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content(body("12.345.678-5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Tu cuenta ya está asociada a una empresa"));
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer pending")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("12.345.678-5").replace("11.111.111-1", "11.111.111-2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El RUT del representante no es válido"));
        mockMvc.perform(post("/api/v1/tenants").contentType(MediaType.APPLICATION_JSON).content(body("12.345.678-5")))
                .andExpect(status().isUnauthorized());
        assertTrue(tenantRepository.findByRutEmpresa("12345678-5").isEmpty());
    }
}
