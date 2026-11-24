package com.rrhh.identity.controller;

import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.PlataformaFixtures;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.UsuarioRepository;
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

/** Lista de cuentas OperadorSaaS para asignarlas a un grupo desde la consola (S2, S8). */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class OperadoresPlataformaIT {

    private static final String URL = "/api/v1/plataforma/operadores";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;

    @Test
    void listaOperadoresAunSinVincular() throws Exception {
        Usuario operador = PlataformaFixtures.usuario(usuarioRepository, null, "OperadorSaaS", "ACTIVO");
        mockMvc.perform(get(URL).header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[*].id", hasItem(operador.getId())))
                .andExpect(jsonPath("$.datos[?(@.id=='" + operador.getId() + "')].email", hasItem(operador.getEmail())))
                .andExpect(jsonPath("$.datos[?(@.id=='" + operador.getId() + "')].estado", hasItem("ACTIVO")));
    }

    @Test
    void listaSoloOperadores() throws Exception {
        Usuario admin = PlataformaFixtures.usuario(usuarioRepository, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "Admin de RRHH", "ACTIVO");
        Usuario pendiente = PlataformaFixtures.usuario(usuarioRepository, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", null, "PENDIENTE");
        mockMvc.perform(get(URL).header("Authorization", "Bearer admin-plataforma"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datos[*].id", not(hasItem(admin.getId()))))
                .andExpect(jsonPath("$.datos[*].id", not(hasItem(pendiente.getId()))));
    }

    @Test
    void soloElAdminPlataformaVeLaLista() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer operador")).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).header("Authorization", "Bearer admin")).andExpect(status().isForbidden());
    }
}
