package com.rrhh.identity.controller;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.Usuario;
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

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consola de plataforma (S2, S6). Cada prueba registra su propia empresa con su propio usuario de
 * Google ("google-<x>") y un RUT distinto, porque todas las clases comparten la misma base H2.
 * RUT de prueba verificados a mano por módulo 11: 22222222-2, 33333333-3, 44444444-4.
 */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class PlataformaControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private String registrar(String token, String rut, String nombre) throws Exception {
        String body = TenantRegistroControllerIT.body(rut).replace("\"nombre_fantasia\":\"Acme\"", "\"nombre_fantasia\":\"" + nombre + "\"");
        mockMvc.perform(post("/api/v1/tenants").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return tenantRepository.findByRutEmpresa(rut).orElseThrow().getId();
    }

    @Test
    void adminPlataformaActivaYRegistranteQuedaSuperAdminActivo() throws Exception {
        String id = registrar("google-activa", "22222222-2", "Activa Uno");
        mockMvc.perform(get("/api/v1/plataforma/tenants").param("estado", "PENDIENTE_ACTIVACION")
                        .header("Authorization", "Bearer operador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id=='" + id + "')].registrado_por_email", hasItem("activa@gmail.com")))
                .andExpect(jsonPath("$.datos[?(@.estado!='PENDIENTE_ACTIVACION')]").isEmpty());
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/activar").header("Authorization", "Bearer operador"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/activar").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/activar").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.activo").value(true));
        Usuario u = usuarioRepository.findByCognitoSub("cognito-activa").orElseThrow();
        assertEquals("ACTIVO", u.getEstado());
        assertEquals("SuperAdmin", u.getRol());
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "22222222-2")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer google-activa"))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.role").value("SuperAdmin"))
                .andExpect(jsonPath("$.datos.tenant_estado").value("ACTIVO"));
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/activar").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/plataforma/tenants/no-existe/activar").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazarExigeMotivoYDejaRegistranteRechazado() throws Exception {
        String id = registrar("google-rechazada", "33333333-3", "Rechazada Dos");
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/rechazar").header("Authorization", "Bearer admin-plataforma")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/rechazar").header("Authorization", "Bearer operador")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Datos falsos\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/rechazar").header("Authorization", "Bearer admin-plataforma")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Datos falsos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("RECHAZADO"));
        Usuario u = usuarioRepository.findByCognitoSub("cognito-rechazada").orElseThrow();
        assertEquals("RECHAZADO", u.getEstado());
        assertFalse(u.isActivo());
        assertFalse(tenantRepository.findById(id).orElseThrow().isActivo());
        mockMvc.perform(post("/api/v1/plataforma/tenants/" + id + "/activar").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "33333333-3")).andExpect(status().isNotFound());
    }

    @Test
    void crearUsuarioPlataformaValidaRolYTenant() throws Exception {
        String url = "/api/v1/plataforma/usuarios";
        String pendiente = registrar("google-pendiente", "44444444-4", "Pendiente Tres");
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"soporte@rrhh.cl\",\"nombre\":\"Soporte\",\"rol\":\"OperadorSaaS\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.rol").value("OperadorSaaS"));
        Usuario soporte = usuarioRepository.findByEmailIgnoreCase("soporte@rrhh.cl").orElseThrow();
        assertNull(soporte.getCognitoSub());
        assertNull(soporte.getTenantId());
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jefe@acme.cl\",\"nombre\":\"Jefe\",\"rol\":\"SuperAdmin\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jefe@acme.cl\",\"nombre\":\"Jefe\",\"rol\":\"SuperAdmin\",\"tenant_id\":\"" + pendiente + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ops@rrhh.cl\",\"nombre\":\"Ops\",\"rol\":\"OperadorSaaS\",\"tenant_id\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jefe.demo@rrhh.cl\",\"nombre\":\"Jefe Demo\",\"rol\":\"SuperAdmin\",\"tenant_id\":\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tenant_id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.datos.rol").value("SuperAdmin"));
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@rrhh.cl\",\"nombre\":\"X\",\"rol\":\"AdminPlataforma\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(url).header("Authorization", "Bearer admin-plataforma").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"soporte@rrhh.cl\",\"nombre\":\"Soporte\",\"rol\":\"OperadorSaaS\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post(url).header("Authorization", "Bearer operador").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"y@rrhh.cl\",\"nombre\":\"Y\",\"rol\":\"OperadorSaaS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPlataformaListaTodasLasEmpresasYUsuariosDeUnTenant() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.slug=='empresa-demo-spa')]").isNotEmpty())
                .andExpect(jsonPath("$.datos[?(@.slug=='plataforma')]").isEmpty());
        mockMvc.perform(get("/api/v1/plataforma/tenants").param("estado", "OTRO").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/plataforma/tenants/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/usuarios")
                        .header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }
}
