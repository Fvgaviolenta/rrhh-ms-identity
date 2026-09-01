package com.rrhh.identity.service;

import com.rrhh.identity.dto.request.AsignarUsuarioRequest;
import com.rrhh.identity.dto.request.CambiarEstadoUsuarioRequest;
import com.rrhh.identity.dto.request.CrearUsuarioRequest;
import com.rrhh.identity.dto.response.MeResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.mapper.UsuarioMapper;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.AuditLogRepository;
import com.rrhh.identity.repository.RolRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.Roles;
import com.rrhh.identity.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UsuarioService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final RolRepository rolRepository;
    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;
    private final UsuarioMapper usuarioMapper;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            TenantRepository tenantRepository,
            RolRepository rolRepository,
            AuditLogRepository auditLogRepository,
            TenantContext tenantContext,
            UsuarioMapper usuarioMapper
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.rolRepository = rolRepository;
        this.auditLogRepository = auditLogRepository;
        this.tenantContext = tenantContext;
        this.usuarioMapper = usuarioMapper;
    }

    @Transactional
    public MeResponse me() {
        TenantContext.AuthenticatedUser actor = tenantContext.current();

        Usuario local = null;
        if (actor.cognitoSub() != null) {
            local = usuarioRepository.findByCognitoSub(actor.cognitoSub()).orElse(null);
        }
        if (local == null && actor.tenantId() != null && actor.email() != null) {
            local = usuarioRepository.findByTenantIdAndEmail(actor.tenantId(), actor.email()).orElse(null);
        }
        // Usuario federado (Google) sin tenant/rol: se registra como PENDIENTE en el primer ingreso.
        if (local == null && actor.tenantId() == null && actor.cognitoSub() != null) {
            local = provisionarPendiente(actor);
        }

        if (local != null) {
            // Backfill de nombre si el usuario quedó sin nombre en el primer ingreso.
            if ((local.getNombre() == null || local.getNombre().isBlank())
                    && actor.name() != null && !actor.name().isBlank()) {
                local.setNombre(actor.name());
                usuarioRepository.save(local);
            }
            boolean pendiente = ESTADO_PENDIENTE.equals(local.getEstado())
                    || local.getTenantId() == null || local.getRol() == null;
            String nombreVisible = (local.getNombre() != null && !local.getNombre().isBlank())
                    ? local.getNombre()
                    : actor.name();
            return new MeResponse(
                    local.getId(),
                    local.getTenantId() != null ? local.getTenantId() : actor.tenantId(),
                    local.getEmail() != null ? local.getEmail() : actor.email(),
                    nombreVisible,
                    local.getRol() != null ? local.getRol() : actor.role(),
                    local.getTrabajadorId() != null ? local.getTrabajadorId() : actor.trabajadorId(),
                    actor.cognitoSub(),
                    local.getEstado(),
                    UsuarioMapper.formatCodigo(local.getSecuencia()),
                    pendiente
            );
        }

        boolean pendiente = actor.tenantId() == null || actor.role() == null;
        return new MeResponse(
                actor.userId(),
                actor.tenantId(),
                actor.email(),
                actor.name(),
                actor.role(),
                actor.trabajadorId(),
                actor.cognitoSub(),
                pendiente ? ESTADO_PENDIENTE : ESTADO_ACTIVO,
                null,
                pendiente
        );
    }

    private Usuario provisionarPendiente(TenantContext.AuthenticatedUser actor) {
        return usuarioRepository.findByCognitoSub(actor.cognitoSub()).orElseGet(() -> {
            Usuario usuario = new Usuario();
            usuario.setId(UUID.randomUUID().toString());
            usuario.setTenantId(null);
            usuario.setRol(null);
            usuario.setEstado(ESTADO_PENDIENTE);
            usuario.setEmail(actor.email() != null
                    ? actor.email().toLowerCase()
                    : actor.cognitoSub() + "@pendiente.local");
            usuario.setNombre(actor.name());
            usuario.setCognitoSub(actor.cognitoSub());
            usuario.setActivo(true);
            usuario.setCreadoEn(Instant.now());
            // saveAndFlush fuerza el INSERT para que @Generated recargue la secuencia (codigo) de inmediato.
            return usuarioRepository.saveAndFlush(usuario);
        });
    }

    public List<UsuarioResponse> listar() {
        String tenantId = requireTenant();
        return usuarioRepository.findByTenantId(tenantId).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    public UsuarioResponse obtener(String usuarioId) {
        return usuarioMapper.toResponse(buscarDelTenant(usuarioId));
    }

    public List<UsuarioResponse> listarPendientes() {
        requireTenant();
        return usuarioRepository.findByEstado(ESTADO_PENDIENTE).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    @Transactional
    public UsuarioResponse asignar(String usuarioId, AsignarUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String tenantId = actor.tenantId();
        if (!tenantRepository.existsById(tenantId)) {
            throw new DomainException(400, "El tenant del token no existe", "tenant_id");
        }
        rolRepository.findByCodigo(request.rol())
                .orElseThrow(() -> new DomainException(400, "Rol no oficial", "rol"));
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new DomainException(404, "Usuario no encontrado"));
        if (usuario.getTenantId() != null) {
            throw new DomainException(400, "El usuario ya esta asignado a un tenant");
        }
        if (usuario.getEmail() != null && usuarioRepository.existsByTenantIdAndEmail(tenantId, usuario.getEmail())) {
            throw new DomainException(400, "El email ya existe en el tenant", "email");
        }
        String anterior = usuario.getEstado() + "|" + usuario.getTenantId() + "|" + usuario.getRol();
        usuario.setTenantId(tenantId);
        usuario.setRol(request.rol());
        usuario.setTrabajadorId(request.trabajadorId());
        usuario.setEstado(ESTADO_ACTIVO);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Asignacion", anterior,
                usuario.getTenantId() + "|" + usuario.getRol());
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String tenantId = actor.tenantId();
        if (!tenantRepository.existsById(tenantId)) {
            throw new DomainException(400, "El tenant del token no existe", "tenant_id");
        }
        rolRepository.findByCodigo(request.rol())
                .orElseThrow(() -> new DomainException(400, "Rol no oficial", "rol"));
        if (usuarioRepository.existsByTenantIdAndEmail(tenantId, request.email())) {
            throw new DomainException(400, "El email ya existe en el tenant", "email");
        }
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setTenantId(tenantId);
        usuario.setEmail(request.email().toLowerCase());
        usuario.setRol(request.rol());
        usuario.setTrabajadorId(request.trabajadorId());
        usuario.setCognitoSub(request.cognitoSub());
        usuario.setActivo(true);
        usuario.setCreadoEn(Instant.now());
        usuarioRepository.save(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Creacion", null, usuario.getEmail() + "|" + usuario.getRol());
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse cambiarEstado(String usuarioId, CambiarEstadoUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        Usuario usuario = buscarDelTenant(usuarioId);
        String anterior = String.valueOf(usuario.isActivo());
        usuario.setActivo(request.activo());
        usuarioRepository.save(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Modificacion", anterior, String.valueOf(usuario.isActivo()));
        return usuarioMapper.toResponse(usuario);
    }

    public static boolean esAdministrador(String role) {
        String authority = Roles.authorityFromClaim(role);
        return Roles.SUPERADMIN.equals(authority) || Roles.ADMIN_RRHH.equals(authority);
    }

    private Usuario buscarDelTenant(String usuarioId) {
        return usuarioRepository.findByIdAndTenantId(usuarioId, requireTenant())
                .orElseThrow(() -> new DomainException(404, "Usuario no encontrado"));
    }

    private String requireTenant() {
        String tenantId = tenantContext.require().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new DomainException(401, "El token no incluye tenant_id");
        }
        return tenantId;
    }

    private void auditar(
            TenantContext.AuthenticatedUser actor,
            String entidad,
            String entidadId,
            String accion,
            String anterior,
            String nuevo
    ) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID().toString());
        log.setTenantId(actor.tenantId());
        log.setUsuarioId(actor.userId());
        log.setEntidad(entidad);
        log.setEntidadId(entidadId);
        log.setAccion(accion);
        log.setValoresAnteriores(anterior);
        log.setValoresNuevos(nuevo);
        log.setFechaEvento(Instant.now());
        auditLogRepository.save(log);
    }
}
