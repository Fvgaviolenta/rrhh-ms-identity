package com.rrhh.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.identity.IdentityApplication;
import com.rrhh.identity.config.TestJwtConfig;
import com.rrhh.identity.model.OutboxEvent;
import com.rrhh.identity.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = IdentityApplication.class)
@ActiveProfiles("test")
@Import(TestJwtConfig.class)
class OutboxPublisherIT {

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void registrarGuardaEventoPendienteConPayload() throws Exception {
        long antes = outboxEventRepository.count();
        outboxPublisher.registrar("acceso.solicitado", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "u-1",
                Map.of("usuarioId", "u-1", "email", "x@gmail.com", "nombre", "X"));
        assertEquals(antes + 1, outboxEventRepository.count());
        OutboxEvent e = outboxEventRepository.findTop20ByEstadoOrderByCreadoEnAsc("PENDIENTE").stream()
                .filter(evento -> "u-1".equals(evento.getAggregateId()))
                .findFirst().orElseThrow();
        assertEquals("acceso.solicitado", e.getEventType());
        assertEquals("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", e.getTenantId());
        assertEquals("x@gmail.com", new ObjectMapper().readTree(e.getPayload()).get("email").asText());
    }
}
