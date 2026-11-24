package com.rrhh.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.PlataformaFixtures;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.model.OutboxEvent;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.AuditLogRepository;
import com.rrhh.identity.repository.GrupoEmpresasRepository;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.OutboxEventRepository;
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mensaje de la plataforma al Admin de RRHH de una empresa (S7). */
@SpringBootTest(classes = IdentityApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class MensajePlataformaIT {

    private final ObjectMapper json = new ObjectMapper();

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private GrupoEmpresasRepository grupoEmpresasRepository;
    @Autowired private GrupoTenantRepository grupoTenantRepository;
    @Autowired private GrupoOperadorRepository grupoOperadorRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private String a;

    @BeforeEach
    void datos() {
        a = PlataformaFixtures.tenant(tenantRepository, "Mensaje IT").getId();
        PlataformaFixtures.grupoConOperador(grupoEmpresasRepository, grupoTenantRepository,
                grupoOperadorRepository, usuarioRepository, a);
    }

    @Test
    void mensajeVaALosAdminRrhhActivos() throws Exception {
        Usuario uno = integrante("Admin de RRHH", "ACTIVO");
        Usuario dos = integrante("Admin de RRHH", "ACTIVO");
        integrante("Admin de RRHH", "INVITADO");
        integrante("SuperAdmin", "ACTIVO");
        integrante("Trabajador", "ACTIVO");
        enviar("operador-grupo", a, "Aviso", "Hola")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.codigo").value(202))
                .andExpect(jsonPath("$.datos.destinatarios").value(2));
        JsonNode payload = payloadDe(a);
        assertEquals("Aviso", payload.get("asunto").asText());
        assertEquals("Hola", payload.get("cuerpo").asText());
        assertEquals("operador.grupo@rrhh.local", payload.get("autorEmail").asText());
        Set<String> emails = new HashSet<>();
        payload.get("destinatarios").forEach(d -> emails.add(d.get("email").asText()));
        assertEquals(Set.of(uno.getEmail(), dos.getEmail()), emails);
    }

    @Test
    void sinAdminRrhhElMensajeVaAlSuperAdmin() throws Exception {
        Usuario superAdmin = integrante("SuperAdmin", "ACTIVO");
        integrante("Admin de RRHH", "SUSPENDIDO");
        enviar("admin-plataforma", a, "Aviso", "Hola")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.datos.destinatarios").value(1));
        assertEquals(superAdmin.getId(), payloadDe(a).get("destinatarios").get(0).get("usuarioId").asText());
    }

    @Test
    void sinDestinatariosResponde409() throws Exception {
        integrante("Trabajador", "ACTIVO");
        enviar("operador-grupo", a, "Aviso", "Hola").andExpect(status().isConflict());
        assertTrue(eventosDe(a).isEmpty());
    }

    @Test
    void fueraDeAlcanceResponde404() throws Exception {
        String otra = PlataformaFixtures.tenant(tenantRepository, "Mensaje IT otra").getId();
        PlataformaFixtures.usuario(usuarioRepository, otra, "Admin de RRHH", "ACTIVO");
        enviar("operador-grupo", otra, "Aviso", "Hola").andExpect(status().isNotFound());
        enviar("admin", otra, "Aviso", "Hola").andExpect(status().isForbidden());
    }

    @Test
    void asuntoConSaltoDeLineaOMayorA100Responde400() throws Exception {
        integrante("Admin de RRHH", "ACTIVO");
        enviar("operador-grupo", a, "Linea uno\nLinea dos", "Hola").andExpect(status().isBadRequest());
        enviar("operador-grupo", a, "x".repeat(101), "Hola").andExpect(status().isBadRequest());
        enviar("operador-grupo", a, "", "Hola").andExpect(status().isBadRequest());
        enviar("operador-grupo", a, "x".repeat(100), "Hola").andExpect(status().isAccepted());
    }

    @Test
    void cuerpoMayorA4000Responde400() throws Exception {
        integrante("Admin de RRHH", "ACTIVO");
        enviar("operador-grupo", a, "Aviso", "c".repeat(4001)).andExpect(status().isBadRequest());
        enviar("operador-grupo", a, "Aviso", " ").andExpect(status().isBadRequest());
        enviar("operador-grupo", a, "Aviso", "c".repeat(4000)).andExpect(status().isAccepted());
    }

    @Test
    void auditoriaGuardaAsuntoSinCuerpo() throws Exception {
        Usuario admin = integrante("Admin de RRHH", "ACTIVO");
        enviar("operador-grupo", a, "Asunto auditado", "Cuerpo secreto").andExpect(status().isAccepted());
        AuditLog log = auditLogRepository.findAll().stream()
                .filter(l -> a.equals(l.getTenantId()) && "MensajePlataforma".equals(l.getEntidad()))
                .findFirst().orElseThrow();
        assertTrue(log.getValoresNuevos().contains("Asunto auditado"));
        assertTrue(log.getValoresNuevos().contains(admin.getEmail()));
        assertFalse(log.getValoresNuevos().contains("Cuerpo secreto"));
    }

    private Usuario integrante(String rol, String estado) {
        return PlataformaFixtures.usuario(usuarioRepository, a, rol, estado);
    }

    private List<OutboxEvent> eventosDe(String tenantId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> "plataforma.mensaje.enviado".equals(e.getEventType()) && tenantId.equals(e.getTenantId()))
                .toList();
    }

    private JsonNode payloadDe(String tenantId) throws Exception {
        List<OutboxEvent> eventos = eventosDe(tenantId);
        assertEquals(1, eventos.size());
        return json.readTree(eventos.get(0).getPayload());
    }

    private ResultActions enviar(String token, String tenantId, String asunto, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/plataforma/tenants/" + tenantId + "/mensajes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("asunto", asunto, "cuerpo", cuerpo))));
    }
}
