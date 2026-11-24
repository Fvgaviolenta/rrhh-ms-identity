package com.rrhh.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Grupos de empresas (S2). Cada prueba usa nombres y empresas propias: la base H2 se comparte entre clases. */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class GrupoControllerIT {

    private static final String URL = "/api/v1/plataforma/grupos";
    private static final String TENANT_PLATAFORMA = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN = "Bearer admin-plataforma";

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void soloElAdminPlataformaGestionaGrupos() throws Exception {
        crear("Bearer operador", "Sur").andExpect(status().isForbidden());
        crear("Bearer admin", "Sur").andExpect(status().isForbidden());
        crear(ADMIN, "Sur")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.nombre").value("Sur"))
                .andExpect(jsonPath("$.datos.tenant_ids").isEmpty());
        mockMvc.perform(get(URL).header("Authorization", "Bearer operador")).andExpect(status().isForbidden());
    }

    @Test
    void nombreRepetidoResponde409() throws Exception {
        crear(ADMIN, "Norte").andExpect(status().isCreated());
        crear(ADMIN, "Norte").andExpect(status().isConflict());
        String otro = idDe(crear(ADMIN, "Norte Chico"));
        mockMvc.perform(patch(URL + "/" + otro).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Norte\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nombreVacioOMayorA80Responde400() throws Exception {
        crear(ADMIN, "").andExpect(status().isBadRequest());
        crear(ADMIN, "x".repeat(81)).andExpect(status().isBadRequest());
        crear(ADMIN, "y".repeat(80)).andExpect(status().isCreated());
    }

    @Test
    void unaEmpresaEnOtroGrupoResponde409() throws Exception {
        String a = tenant();
        String g1 = idDe(crear(ADMIN, "Centro 1"));
        String g2 = idDe(crear(ADMIN, "Centro 2"));
        fijar(g1, "empresas", a).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.tenant_ids[0]").value(a));
        fijar(g2, "empresas", a).andExpect(status().isConflict());
        fijar(g1, "empresas", a).andExpect(status().isOk());
        fijar(g2, "empresas", TENANT_PLATAFORMA).andExpect(status().isNotFound());
        fijar(g2, "empresas", "no-existe").andExpect(status().isNotFound());
    }

    @Test
    void soloSeAsignanCuentasOperadorSaaS() throws Exception {
        String g = idDe(crear(ADMIN, "Oriente"));
        String adminRrhh = usuario("Admin de RRHH", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").getId();
        String operador = usuario("OperadorSaaS", null).getId();
        fijar(g, "operadores", adminRrhh).andExpect(status().isBadRequest());
        fijar(g, "operadores", "no-existe").andExpect(status().isBadRequest());
        fijar(g, "operadores", operador).andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.operador_ids[0]").value(operador));
    }

    @Test
    void borrarGrupoLiberaSusEmpresas() throws Exception {
        String a = tenant();
        String g1 = idDe(crear(ADMIN, "Poniente 1"));
        String g2 = idDe(crear(ADMIN, "Poniente 2"));
        fijar(g1, "empresas", a).andExpect(status().isOk());
        fijar(g1, "operadores", usuario("OperadorSaaS", null).getId()).andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + g1).header("Authorization", ADMIN)).andExpect(status().isNoContent());
        fijar(g2, "empresas", a).andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + g1).header("Authorization", ADMIN)).andExpect(status().isNotFound());
    }

    @Test
    void cadaCambioQuedaAuditadoEnElTenantPlataforma() throws Exception {
        long antes = auditoriasDeGrupo();
        String g = idDe(crear(ADMIN, "Auditado"));
        mockMvc.perform(patch(URL + "/" + g).header("Authorization", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Auditado 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.nombre").value("Auditado 2"));
        fijar(g, "empresas", tenant()).andExpect(status().isOk());
        fijar(g, "operadores", usuario("OperadorSaaS", null).getId()).andExpect(status().isOk());
        mockMvc.perform(delete(URL + "/" + g).header("Authorization", ADMIN)).andExpect(status().isNoContent());
        assertEquals(antes + 5, auditoriasDeGrupo());
    }

    private long auditoriasDeGrupo() {
        return auditLogRepository.findAll().stream()
                .filter(l -> "GrupoEmpresas".equals(l.getEntidad()))
                .map(AuditLog::getTenantId)
                .filter(TENANT_PLATAFORMA::equals)
                .count();
    }

    private ResultActions crear(String auth, String nombre) throws Exception {
        return mockMvc.perform(post(URL).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("nombre", nombre))));
    }

    private ResultActions fijar(String grupo, String que, String id) throws Exception {
        return mockMvc.perform(put(URL + "/" + grupo + "/" + que).header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"" + id + "\"]}"));
    }

    private String idDe(ResultActions r) throws Exception {
        String body = r.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/datos/id").asText();
    }

    private String tenant() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Tenant t = new Tenant();
        t.setId(UUID.randomUUID().toString());
        t.setNombreEmpresa("Grupo IT " + sufijo);
        t.setSlug("grupo-it-" + sufijo);
        t.setRutEmpresa("GR-" + sufijo);
        t.setCreadoEn(Instant.now());
        t.cambiarEstado(TenantEstado.ACTIVO);
        return tenantRepository.save(t).getId();
    }

    private Usuario usuario(String rol, String tenantId) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID().toString());
        u.setEmail("grupo-" + u.getId().substring(0, 8) + "@rrhh.local");
        u.setRol(rol);
        u.setTenantId(tenantId);
        u.setEstado("ACTIVO");
        u.setCreadoEn(Instant.now());
        return usuarioRepository.saveAndFlush(u);
    }
}
