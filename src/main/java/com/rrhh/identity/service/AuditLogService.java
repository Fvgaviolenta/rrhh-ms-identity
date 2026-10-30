package com.rrhh.identity.service;

import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Escribe {@code identity_audit_log}. Un actor de plataforma (sin tenant) se registra en el tenant reservado. */
@Service
public class AuditLogService {

    static final String TENANT_PLATAFORMA = "00000000-0000-0000-0000-000000000001";

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void registrar(
            String tenantId,
            String usuarioId,
            String entidad,
            String entidadId,
            String accion,
            String anterior,
            String nuevo
    ) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID().toString());
        log.setTenantId(tenantId != null ? tenantId : TENANT_PLATAFORMA);
        log.setUsuarioId(usuarioId);
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setAccion(accion);
        log.setValoresAnteriores(anterior);
        log.setValoresNuevos(nuevo);
        log.setFechaEvento(Instant.now());
        auditLogRepository.save(log);
    }
}
