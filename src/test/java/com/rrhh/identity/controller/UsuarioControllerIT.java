package com.rrhh.identity.controller;

import com.jayway.jsonpath.JsonPath;
import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.OutboxEventRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
    void meSinFilaLocalEsNoRegistradoYNoInserta() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("NO_REGISTRADO"))
                .andExpect(jsonPath("$.datos.pendiente").value(true))
                .andExpect(jsonPath("$.datos.tenant_id").doesNotExist());
        assertTrue(usuarioRepository.findByCognitoSub("cognito-google-123").isEmpty());
    }

    @Test
    void meConClaimsDeTenantSinFilaLocalEsNoRegistradoYNoInserta() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer manual-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("NO_REGISTRADO"))
                .andExpect(jsonPath("$.datos.tenant_id").doesNotExist())
                .andExpect(jsonPath("$.datos.role").doesNotExist());
        assertTrue(usuarioRepository.findByCognitoSub("cognito-admin-123").isEmpty());
    }

    @Test
    void pendienteLegacySinTenantEsNoRegistradoEInvisible() throws Exception {
        Usuario legacy = new Usuario();
        legacy.setId("legacy-1");
        legacy.setEmail("asignar.google@gmail.com");
        legacy.setCognitoSub("cognito-google-456");
        legacy.setEstado("PENDIENTE");
        legacy.setActivo(true);
        legacy.setCreadoEn(Instant.now());
        usuarioRepository.saveAndFlush(legacy);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer pending2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("NO_REGISTRADO"));
        mockMvc.perform(get("/api/v1/usuarios/pendientes").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id=='legacy-1')]").isEmpty());
        mockMvc.perform(post("/api/v1/usuarios/legacy-1/asignar").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"Trabajador\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void meDeSolicitudPendienteTraeEmpresa() throws Exception {
        mockMvc.perform(post("/api/v1/auth/access-requests").header("Authorization", "Bearer postulante3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"76123456-0\"}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer postulante3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.datos.tenant_estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.tenant_nombre").value("Empresa Demo SpA"));
    }

    @Test
    void ownerGoogleSePromueveAdminPlataformaYNativoNo() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer owner-google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.role").value("AdminPlataforma"))
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.pendiente").value(false));
        Usuario owner = usuarioRepository.findByCognitoSub("cognito-owner").orElseThrow();
        assertEquals("AdminPlataforma", owner.getRol());
        assertEquals("google_111", owner.getCognitoUsername());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer owner-native"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("NO_REGISTRADO"));
    }

    @Test
    void cuentaCreadaPorRrhhConIdentificadorProvisionalSeVinculaEnMe() throws Exception {
        // provision() guarda el username de Cognito (el correo) como cognito_sub provisional.
        Usuario creada = new Usuario();
        creada.setId("provisional-1");
        creada.setTenantId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        creada.setEmail("trab@rrhh.local");
        creada.setCognitoSub("trab@rrhh.local");
        creada.setRol("Trabajador");
        creada.setEstado("ACTIVO");
        creada.setActivo(true);
        creada.setCreadoEn(Instant.now());
        usuarioRepository.saveAndFlush(creada);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer trabajador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.datos.role").value("Trabajador"))
                .andExpect(jsonPath("$.datos.tenant_id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        assertEquals("cognito-trab", usuarioRepository.findById("provisional-1").orElseThrow().getCognitoSub());
    }

    @Test
    void meCompletaCognitoUsernameDeUnaFilaGoogleAnteriorAV5() throws Exception {
        Usuario previa = new Usuario();
        previa.setId("previa-v5-1");
        previa.setTenantId("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        previa.setEmail("previav5@gmail.com");
        previa.setCognitoSub("cognito-previav5");
        previa.setRol("Trabajador");
        previa.setEstado("ACTIVO");
        previa.setActivo(true);
        previa.setCreadoEn(Instant.now());
        usuarioRepository.saveAndFlush(previa);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer google-previav5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"));
        assertEquals("google_previav5", usuarioRepository.findById("previa-v5-1").orElseThrow().getCognitoUsername());
    }

    @Test
    void ningunEndpointDeTenantAsignaRolesDePlataforma() throws Exception {
        String solicitud = mockMvc.perform(post("/api/v1/auth/access-requests")
                        .header("Authorization", "Bearer google-quiereplataforma")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"76123456-0\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String usuarioId = JsonPath.read(solicitud, "$.datos.id");
        for (String rol : new String[]{"AdminPlataforma", "OperadorSaaS"}) {
            mockMvc.perform(post("/api/v1/usuarios/" + usuarioId + "/asignar").header("Authorization", "Bearer admin")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"" + rol + "\"}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/v1/usuarios").header("Authorization", "Bearer admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"plataforma." + rol.toLowerCase() + "@rrhh.cl\",\"rol\":\"" + rol + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals("PENDIENTE", usuarioRepository.findById(usuarioId).orElseThrow().getEstado());
    }

    @Test
    void pendientesGlobalesYaNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/usuarios/pendientes").header("Authorization", "Bearer operador"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminAsignaRolASolicitudPendienteDeSuTenant() throws Exception {
        String solicitud = mockMvc.perform(post("/api/v1/auth/access-requests")
                        .header("Authorization", "Bearer postulante2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"76123456-0\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String usuarioId = JsonPath.read(solicitud, "$.datos.id");

        mockMvc.perform(get("/api/v1/usuarios/pendientes").header("Authorization", "Bearer otro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[?(@.id=='" + usuarioId + "')]").isEmpty());
        mockMvc.perform(post("/api/v1/usuarios/" + usuarioId + "/asignar").header("Authorization", "Bearer otro")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"Trabajador\"}"))
                .andExpect(status().isNotFound());

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

        // Ya no es una solicitud pendiente: reasignarla responde como no encontrada.
        mockMvc.perform(post("/api/v1/usuarios/" + usuarioId + "/asignar")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"Jefatura"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCambiaElRolDeUnTrabajadorConCuenta() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cambio.rol@rrhh.local","rol":"Trabajador","trabajador_id":"99999999-0000-0000-0000-000000000001"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/v1/usuarios/trabajador/99999999-0000-0000-0000-000000000001/rol")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"JEFATURA"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.rol").value("Jefatura"));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Jefatura",
                usuarioRepository.findByEmailIgnoreCase("cambio.rol@rrhh.local").orElseThrow().getRol()
        );
    }

    @Test
    void cambiarRolRechazaRolNoPermitidoYTrabajadorSinCuenta() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/trabajador/99999999-0000-0000-0000-000000000002/rol")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"SUPERADMIN"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/usuarios/trabajador/99999999-0000-0000-0000-000000000002/rol")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"Jefatura"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void trabajadorNoPuedeCambiarRoles() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/trabajador/99999999-0000-0000-0000-000000000001/rol")
                        .header("Authorization", "Bearer trabajador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rol":"Jefatura"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void trabajadorNoAccedeAPendientes() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/pendientes").header("Authorization", "Bearer trabajador"))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolverPorRut() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "76.123.456-0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos.existe").value(true))
                .andExpect(jsonPath("$.datos.slug").value("empresa-demo-spa"))
                .andExpect(jsonPath("$.datos.nombre_empresa").value("Empresa Demo SpA"));
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "11.111.111-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La empresa no está registrada en el sistema"));
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "76.123.456-7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El RUT de la empresa no es válido"));
        mockMvc.perform(get("/api/v1/tenants/resolver").param("rut", "00.000.000-0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void solicitudPorRutQuedaPendienteYEmiteEvento() throws Exception {
        long antes = outboxEventRepository.count();
        mockMvc.perform(post("/api/v1/auth/access-requests").header("Authorization", "Bearer postulante")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"76123456-0\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.tenant_id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.datos.estado").value("PENDIENTE"));
        assertEquals(antes + 1, outboxEventRepository.count());
        mockMvc.perform(post("/api/v1/auth/access-requests").header("Authorization", "Bearer postulante")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"76.123.456-0\"}"))
                .andExpect(status().isOk());
        assertEquals(antes + 1, outboxEventRepository.count());
        assertEquals("google_333", usuarioRepository.findByCognitoSub("cognito-post-1").orElseThrow().getCognitoUsername());
    }

    @Test
    void solicitudARutInexistenteNoCreaUsuario() throws Exception {
        mockMvc.perform(post("/api/v1/auth/access-requests").header("Authorization", "Bearer pending")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"11111111-1\"}"))
                .andExpect(status().isNotFound());
        assertTrue(usuarioRepository.findByCognitoSub("cognito-google-123").isEmpty());
    }

    @Test
    void activoNoPuedePedirAcceso() throws Exception {
        mockMvc.perform(post("/api/v1/auth/access-requests").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rut_empresa\":\"76123456-0\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Tu cuenta ya pertenece a una empresa"));
    }

    @Test
    void invitacionGoogleVinculaEmailYActivaTrabajador() throws Exception {
        mockMvc.perform(post("/api/v1/usuarios/invitar")
                        .header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invitado.google@gmail.com","nombre":"Invitado","rol":"Trabajador"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datos.estado").value("ACTIVO"))
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
                .andExpect(jsonPath("$.datos[?(@.slug=='empresa-demo-spa')]").isNotEmpty())
                .andExpect(jsonPath("$.datos[?(@.slug=='plataforma')]").isEmpty());
    }

    @Test
    void adminNoListaTenantsDePlataforma() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
    }
}
