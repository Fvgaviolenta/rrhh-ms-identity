package com.rrhh.identity.service;

import com.rrhh.identity.dto.request.MensajePlataformaRequest;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.Roles;
import com.rrhh.identity.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mensaje escrito por la plataforma al Admin de RRHH de una empresa (S7). Identity resuelve los destinatarios
 * y deja el evento {@code plataforma.mensaje.enviado} en su outbox; notifications lo entrega en la app y por SNS.
 */
@Service
public class MensajePlataformaService {

    static final String EVENTO = "plataforma.mensaje.enviado";
    private static final String ESTADO_ACTIVO = "ACTIVO";

    private final UsuarioRepository usuarioRepository;
    private final AlcancePlataformaService alcancePlataformaService;
    private final OutboxPublisher outboxPublisher;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;

    public MensajePlataformaService(UsuarioRepository usuarioRepository,
                                    AlcancePlataformaService alcancePlataformaService,
                                    OutboxPublisher outboxPublisher,
                                    AuditLogService auditLogService,
                                    TenantContext tenantContext) {
        this.usuarioRepository = usuarioRepository;
        this.alcancePlataformaService = alcancePlataformaService;
        this.outboxPublisher = outboxPublisher;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
    }

    /**
     * Destinatarios: los Admin de RRHH ACTIVO de la empresa; si no hay, sus SuperAdmin ACTIVO; sin ninguno, 409.
     * La auditoría guarda el asunto y los destinatarios, nunca el cuerpo. Devuelve cuántos destinatarios hubo.
     */
    @Transactional
    public int enviar(String tenantId, MensajePlataformaRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        alcancePlataformaService.exigir(actor, tenantId);
        List<Usuario> activos = usuarioRepository.findByTenantId(tenantId).stream()
                .filter(u -> ESTADO_ACTIVO.equals(u.getEstado()))
                .toList();
        List<Usuario> destinatarios = conRol(activos, Roles.ADMIN_RRHH);
        if (destinatarios.isEmpty()) {
            destinatarios = conRol(activos, Roles.SUPERADMIN);
        }
        if (destinatarios.isEmpty()) {
            throw new DomainException(409, "La empresa no tiene un Admin de RRHH ni un SuperAdmin activo");
        }
        String asunto = request.asunto().trim();
        String mensajeId = UUID.randomUUID().toString();
        List<Map<String, Object>> lista = destinatarios.stream()
                .map(u -> Map.<String, Object>of("usuarioId", u.getId(), "email", u.getEmail()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("asunto", asunto);
        payload.put("cuerpo", request.cuerpo());
        payload.put("autorEmail", actor.email());
        payload.put("destinatarios", lista);
        outboxPublisher.registrar(EVENTO, tenantId, mensajeId, payload);
        List<String> emails = destinatarios.stream().map(Usuario::getEmail).toList();
        auditLogService.registrar(tenantId, actor.userId(), "MensajePlataforma", mensajeId, "EnvioMensaje",
                null, "asunto=" + asunto + "|destinatarios=" + emails);
        return destinatarios.size();
    }

    private static List<Usuario> conRol(List<Usuario> usuarios, String autoridad) {
        return usuarios.stream()
                .filter(u -> autoridad.equals(Roles.authorityFromClaim(u.getRol())))
                .toList();
    }
}
