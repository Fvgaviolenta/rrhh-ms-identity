package com.rrhh.identity.controller;

import com.jayway.jsonpath.JsonPath;
import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class UsuarioControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meDevuelveClaimsDelToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(200))
                .andExpect(jsonPath("$.datos.tenant_id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.datos.role").value("Admin de RRHH"));
    }

    @Test
    void listarUsuariosSinTokenEs401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trabajadorNoListaUsuarios() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer trabajador"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreaUsuarioEnSuTenant() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nuevo@rrhh.local","rol":"Trabajador"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tenant_id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.datos.email").value("nuevo@rrhh.local"));
    }

    @Test
    void otroTenantNoVeUsuariosDelSeed() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer otro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.length()").value(0));
    }

    @Test
    void meAutoProvisionaUsuarioPendiente() throws Exception {
        // Primer ingreso del usuario federado: se registra como PENDIENTE.
        String primero = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.email").value("nuevo.google@gmail.com"))
                .andExpect(jsonPath("$.datos.nombre").value("Nuevo Google"))
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.datos.pendiente").value(true))
                .andExpect(jsonPath("$.datos.codigo").value(org.hamcrest.Matchers.startsWith("USR-")))
                .andReturn().getResponse().getContentAsString();

        // Segundo ingreso: idempotente, mismo usuario (mismo id).
        String segundo = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();

        String idPrimero = JsonPath.read(primero, "$.datos.user_id");
        String idSegundo = JsonPath.read(segundo, "$.datos.user_id");
        org.junit.jupiter.api.Assertions.assertEquals(idPrimero, idSegundo);
    }

    @Test
    void adminAsignaTenantYRolAUsuarioPendiente() throws Exception {
        // Provisiona el pendiente.
        String me = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer pending2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        String usuarioId = JsonPath.read(me, "$.datos.user_id");

        // El admin lo asigna a su tenant con un rol.
        mockMvc.perform(post("/api/v1/usuarios/" + usuarioId + "/asignar")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"Trabajador"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.tenant_id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.datos.rol").value("Trabajador"))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"));

        // Reasignar el mismo usuario ya no es posible (ya tiene tenant).
        mockMvc.perform(post("/api/v1/usuarios/" + usuarioId + "/asignar")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"Jefatura"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trabajadorNoAccedeAPendientes() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/pendientes").header("Authorization", "Bearer trabajador"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolverEmpresaPublico() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/resolver").param("nombre", "Empresa Demo SpA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.existe").value(true))
                .andExpect(jsonPath("$.datos.slug").value("empresa-demo-spa"));
    }

    @Test
    void resolverEmpresaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/resolver").param("nombre", "No Existe SpA"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitacionGoogleVinculaEmailYActivaTrabajador() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/invitar")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invitado.google@gmail.com","nombre":"Invitado"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.estado").value("INVITADO"))
                .andExpect(jsonPath("$.datos.rol").value("Trabajador"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invited")
                        .header("X-Empresa-Slug", "empresa-demo-spa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.email").value("invitado.google@gmail.com"))
                .andExpect(jsonPath("$.datos.role").value("Trabajador"))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.pendiente").value(false))
                .andExpect(jsonPath("$.datos.tenant_slug").value("empresa-demo-spa"));
    }

    @Test
    void operadorNoEsPendiente() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer operador")
                        .header("X-Empresa-Slug", "plataforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.role").value("OperadorSaaS"))
                .andExpect(jsonPath("$.datos.pendiente").value(false))
                .andExpect(jsonPath("$.datos.tenant_id").doesNotExist());
    }

    @Test
    void adminNoEntraPorSlugPlataforma() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer admin")
                        .header("X-Empresa-Slug", "plataforma"))
                .andExpect(status().isForbidden());
    }

    @Test
    void meRechazaSiElSlugNoCoincideConElTenant() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer otro")
                        .header("X-Empresa-Slug", "empresa-demo-spa"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operadorListaEmpresasCliente() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer operador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[0].slug").value("empresa-demo-spa"));
    }

    @Test
    void adminNoListaTenantsDePlataforma() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }
}
