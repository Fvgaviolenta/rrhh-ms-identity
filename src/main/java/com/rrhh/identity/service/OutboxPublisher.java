package com.rrhh.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rrhh.identity.model.OutboxEvent;
import com.rrhh.identity.repository.OutboxEventRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<SqsClient> sqsClient;
    private final boolean enabled;
    private final String queueUrl;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper, ObjectProvider<SqsClient> sqsClient,
                           @Value("${rrhh.sqs.enabled:false}") boolean enabled,
                           @Value("${rrhh.sqs.events-queue-url:}") String queueUrl) {
        this.repository = repository; this.objectMapper = objectMapper; this.sqsClient = sqsClient;
        this.enabled = enabled; this.queueUrl = queueUrl;
    }

    @Transactional
    public void registrar(String eventType, String tenantId, String aggregateId, Map<String, Object> payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setId(UUID.randomUUID().toString()); event.setTenantId(tenantId); event.setEventType(eventType);
            event.setAggregateId(aggregateId); event.setPayload(objectMapper.writeValueAsString(payload));
            event.setEstado("PENDIENTE"); event.setIntentos(0); event.setCreadoEn(Instant.now());
            repository.save(event);
        } catch (JsonProcessingException ex) { throw new IllegalStateException("No fue posible serializar el evento", ex); }
    }

    @Scheduled(fixedDelayString = "${rrhh.sqs.publish-delay-ms:5000}")
    @Transactional
    public void publicarPendientes() {
        if (!enabled || queueUrl.isBlank() || sqsClient.getIfAvailable() == null) return;
        for (OutboxEvent event : repository.findTop20ByEstadoOrderByCreadoEnAsc("PENDIENTE")) {
            try {
                String body = objectMapper.writeValueAsString(Map.of("eventId", event.getId(), "eventType", event.getEventType(),
                        "tenantId", event.getTenantId(), "occurredAt", event.getCreadoEn().toString(), "aggregateId", event.getAggregateId(),
                        "payload", objectMapper.readTree(event.getPayload())));
                sqsClient.getObject().sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(body).build());
                event.setEstado("PUBLICADO"); event.setPublicadoEn(Instant.now()); event.setUltimoError(null);
            } catch (Exception ex) {
                event.setIntentos(event.getIntentos() + 1); event.setUltimoError(ex.getMessage());
            }
        }
    }
}
