package com.rrhh.identity.controller;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.PlataformaFixtures;
import com.rrhh.identity.config.TestJwtConfig;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Alcance de las listas de plataforma (S1, S3, S4). */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class PlataformaAlcanceIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoEmpresasRepository grupoEmpresasRepository;
    @Autowired private GrupoTenantRepository grupoTenantRepository;
    @Autowired private GrupoOperadorRepository grupoOperadorRepository;

    private String a;
    private String b;
    private String grupo;

    @BeforeEach
    void datos() {
        a = PlataformaFixtures.tenant(tenantRepository, "Alcance IT A").getId();
        b = PlataformaFixtures.tenant(tenantRepository, "Alcance IT B").getId();
        grupo = PlataformaFixtures.grupoConOperador(grupoEmpresasRepository, grupoTenantRepository,
                grupoOperadorRepository, usuarioRepository, a);
    }

    @Test
    void operadorSoloListaLasEmpresasDeSuGrupo() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer operador-grupo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[*].id", hasItem(a)))
                .andExpect(jsonPath("$.datos[*].id", not(hasItem(b))));
    }

    @Test
    void operadorSinGrupoNoListaNingunaEmpresa() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer operador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos").isEmpty());
    }

    @Test
    void adminPlataformaListaTodasConSuGrupo() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants").header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[*].id", hasItem(a)))
                .andExpect(jsonPath("$.datos[*].id", hasItem(b)))
                .andExpect(jsonPath("$.datos[?(@.id=='" + a + "')].grupo", hasItem(grupo)))
                .andExpect(jsonPath("$.datos[?(@.id=='" + b + "')].grupo").isEmpty());
    }

    @Test
    void operadorRecibe404EnIntegrantesFueraDeAlcance() throws Exception {
        mockMvc.perform(get("/api/v1/plataforma/tenants/" + b + "/usuarios").header("Authorization", "Bearer operador-grupo"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/plataforma/tenants/" + a + "/usuarios").header("Authorization", "Bearer operador-grupo"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/plataforma/tenants/00000000-0000-0000-0000-000000000001/usuarios")
                        .header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isNotFound());
    }
}
