package com.rrhh.identity.service;

import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.GrupoOperador;
import com.rrhh.identity.model.GrupoTenant;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.Roles;
import com.rrhh.identity.security.TenantContext.AuthenticatedUser;
import com.rrhh.identity.util.TenantSlug;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Qué empresas puede tocar un actor de plataforma (ADR-020, S3): el AdminPlataforma toda empresa cliente
 * (nunca la de slug {@code plataforma}), un OperadorSaaS solo las de sus grupos, cualquier otro rol ninguna.
 * Fuera de alcance se responde 404, igual que el aislamiento entre empresas.
 */
@Service
public class AlcancePlataformaService {

    private static final String OPERADOR_SAAS = "OperadorSaaS";

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final GrupoTenantRepository grupoTenantRepository;
    private final GrupoOperadorRepository grupoOperadorRepository;

    public AlcancePlataformaService(TenantRepository tenantRepository,
                                    UsuarioRepository usuarioRepository,
                                    GrupoTenantRepository grupoTenantRepository,
                                    GrupoOperadorRepository grupoOperadorRepository) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.grupoTenantRepository = grupoTenantRepository;
        this.grupoOperadorRepository = grupoOperadorRepository;
    }

    /**
     * Empresas en alcance: vacío significa sin restricción (solo AdminPlataforma);
     * un conjunto vacío significa ninguna empresa.
     */
    @Transactional(readOnly = true)
    public Optional<Set<String>> tenantsEnAlcance(AuthenticatedUser actor) {
        String authority = Roles.authorityFromClaim(actor == null ? null : actor.role());
        if (Roles.ADMIN_PLATAFORMA.equals(authority)) {
            return Optional.empty();
        }
        if (!Roles.OPERADOR_SAAS.equals(authority)) {
            return Optional.of(Set.of());
        }
        String operadorId = operadorId(actor);
        if (operadorId == null) {
            return Optional.of(Set.of());
        }
        List<String> grupos = grupoOperadorRepository.findByUsuarioId(operadorId).stream()
                .map(GrupoOperador::getGrupoId)
                .toList();
        if (grupos.isEmpty()) {
            return Optional.of(Set.of());
        }
        return Optional.of(grupoTenantRepository.findByGrupoIdIn(grupos).stream()
                .map(GrupoTenant::getTenantId)
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Transactional(readOnly = true)
    public boolean alcanza(AuthenticatedUser actor, String tenantId) {
        return buscarEnAlcance(actor, tenantId).isPresent();
    }

    /** La empresa si está en alcance del actor; si no, 404 "Empresa no encontrada". */
    @Transactional(readOnly = true)
    public Tenant exigir(AuthenticatedUser actor, String tenantId) {
        return buscarEnAlcance(actor, tenantId)
                .orElseThrow(() -> new DomainException(404, "Empresa no encontrada"));
    }

    private Optional<Tenant> buscarEnAlcance(AuthenticatedUser actor, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Optional.empty();
        }
        Optional<Tenant> tenant = tenantRepository.findById(tenantId)
                .filter(t -> !TenantSlug.PLATAFORMA.equals(t.getSlug()));
        if (tenant.isEmpty()) {
            return Optional.empty();
        }
        Optional<Set<String>> alcance = tenantsEnAlcance(actor);
        if (alcance.isPresent() && !alcance.get().contains(tenantId)) {
            return Optional.empty();
        }
        return tenant;
    }

    /**
     * Fila local del operador: {@code custom:user_id} del token y, si falta (cuenta recién vinculada
     * por correo cuyo token aún no trae el claim), la fila OperadorSaaS de su {@code cognito_sub}.
     */
    private String operadorId(AuthenticatedUser actor) {
        if (actor.userId() != null && !actor.userId().isBlank()) {
            return actor.userId();
        }
        if (actor.cognitoSub() == null) {
            return null;
        }
        return usuarioRepository.findByCognitoSub(actor.cognitoSub())
                .filter(u -> OPERADOR_SAAS.equals(u.getRol()))
                .map(Usuario::getId)
                .orElse(null);
    }
}
