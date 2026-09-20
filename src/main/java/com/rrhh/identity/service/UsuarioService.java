package com.rrhh.identity.service;

import com.rrhh.identity.dto.request.AsignarUsuarioRequest;
import com.rrhh.identity.dto.request.CambiarEstadoUsuarioRequest;
import com.rrhh.identity.dto.request.CrearUsuarioRequest;
import com.rrhh.identity.dto.request.InvitarUsuarioRequest;
import com.rrhh.identity.dto.response.MeResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.mapper.UsuarioMapper;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.AuditLogRepository;
import com.rrhh.identity.repository.RolRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.Roles;
import com.rrhh.identity.security.TenantContext;
import com.rrhh.identity.util.TenantSlug;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UsuarioService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_INVITADO = "INVITADO";
    public static final String HEADER_EMPRESA_SLUG = "X-Empresa-Slug";

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
        return me(null);
    }

    @Transactional
    public MeResponse me(String empresaSlug) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        Tenant esperado = resolverTenantDelSlug(empresaSlug);

        Usuario local = null;
        if (actor.cognitoSub() != null) {
            local = usuarioRepository.findByCognitoSub(actor.cognitoSub()).orElse(null);
        }
        if (local == null && actor.email() != null && esperado != null && !TenantSlug.PLATAFORMA.equals(esperado.getSlug())) {
            local = vincularInvitacion(actor, esperado);
        }
        if (local == null && actor.tenantId() != null && actor.email() != null) {
            local = usuarioRepository.findByTenantIdAndEmail(actor.tenantId(), actor.email()).orElse(null);
        }
        if (local == null && actor.email() != null) {
            usuarioRepository.findByEmailIgnoreCase(actor.email()).ifPresent(otro -> {
                if (esperado != null && !esperado.getId().equals(otro.getTenantId())
                        && !TenantSlug.PLATAFORMA.equals(esperado.getSlug())) {
                    throw new DomainException(403, "Este usuario no pertenece a la empresa indicada", "email");
                }
            });
        }
        if (local == null && actor.tenantId() == null && actor.cognitoSub() != null
                && !Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(actor.role()))) {
            local = provisionarPendiente(actor);
        }

        if (esperado != null && actor.tenantId() != null
                && !esperado.getId().equals(actor.tenantId())
                && !Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(actor.role()))) {
            throw new DomainException(403, "Este usuario no pertenece a la empresa indicada");
        }

        if (local != null) {
            if ((local.getNombre() == null || local.getNombre().isBlank())
                    && actor.name() != null && !actor.name().isBlank()) {
                local.setNombre(actor.name());
                usuarioRepository.save(local);
            }
            validarSlugContraUsuario(esperado, local);
            return toMeResponse(local, actor);
        }

        if (Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(actor.role()))) {
            if (esperado != null && !TenantSlug.PLATAFORMA.equals(esperado.getSlug())) {
                throw new DomainException(403, "El operador de plataforma solo ingresa por el acceso plataforma");
            }
            return new MeResponse(
                    actor.userId(),
                    null,
                    actor.email(),
                    actor.name(),
                    "OperadorSaaS",
                    null,
                    actor.cognitoSub(),
                    ESTADO_ACTIVO,
                    null,
                    false,
                    TenantSlug.PLATAFORMA
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
                pendiente,
                esperado != null ? esperado.getSlug() : null
        );
    }

    private Usuario vincularInvitacion(TenantContext.AuthenticatedUser actor, Tenant esperado) {
        Usuario invitacion = usuarioRepository
                .findByEmailIgnoreCaseAndTenantId(actor.email().toLowerCase(), esperado.getId())
                .orElse(null);
        if (invitacion == null) {
            return null;
        }
        if (invitacion.getCognitoSub() != null && actor.cognitoSub() != null
                && !invitacion.getCognitoSub().equals(actor.cognitoSub())) {
            throw new DomainException(403, "Este correo ya está vinculado a otra identidad", "email");
        }
        invitacion.setCognitoSub(actor.cognitoSub());
        if (ESTADO_INVITADO.equals(invitacion.getEstado())) {
            invitacion.setEstado(ESTADO_ACTIVO);
        }
        if ((invitacion.getNombre() == null || invitacion.getNombre().isBlank()) && actor.name() != null) {
            invitacion.setNombre(actor.name());
        }
        return usuarioRepository.saveAndFlush(invitacion);
    }

    private Tenant resolverTenantDelSlug(String empresaSlug) {
        if (empresaSlug == null || empresaSlug.isBlank()) {
            return null;
        }
        String slug = TenantSlug.normalize(empresaSlug);
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainException(404, "La empresa no se encuentra en nuestra base de datos", "slug"));
    }

    private void validarSlugContraUsuario(Tenant esperado, Usuario local) {
        if (esperado == null) {
            return;
        }
        boolean operador = Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(local.getRol()));
        if (operador) {
            if (!TenantSlug.PLATAFORMA.equals(esperado.getSlug())) {
                throw new DomainException(403, "El operador de plataforma solo ingresa por el acceso plataforma");
            }
            return;
        }
        if (TenantSlug.PLATAFORMA.equals(esperado.getSlug())) {
            throw new DomainException(403, "Esta cuenta no es de operador de plataforma");
        }
        if (local.getTenantId() == null) {
            return;
        }
        if (!esperado.getId().equals(local.getTenantId())) {
            throw new DomainException(403, "Este usuario no pertenece a la empresa indicada");
        }
    }

    private MeResponse toMeResponse(Usuario local, TenantContext.AuthenticatedUser actor) {
        boolean operador = Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(local.getRol()));
        boolean pendiente = ESTADO_PENDIENTE.equals(local.getEstado())
                || local.getRol() == null
                || (local.getTenantId() == null && !operador);
        String nombreVisible = (local.getNombre() != null && !local.getNombre().isBlank())
                ? local.getNombre()
                : actor.name();
        String slug = null;
        if (operador) {
            slug = TenantSlug.PLATAFORMA;
        } else if (local.getTenantId() != null) {
            slug = tenantRepository.findById(local.getTenantId()).map(Tenant::getSlug).orElse(null);
        }
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
                pendiente,
                slug
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
        String tenantId = requireTenant();
        return usuarioRepository.findByEstado(ESTADO_PENDIENTE).stream()
                .filter(u -> u.getTenantId() == null || tenantId.equals(u.getTenantId()))
                .map(usuarioMapper::toResponse)
                .toList();
    }

    public List<UsuarioResponse> listarPendientesGlobales() {
        return usuarioRepository.findByEstadoAndTenantIdIsNull(ESTADO_PENDIENTE).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    @Transactional
    public UsuarioResponse invitar(InvitarUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String tenantId = actor.tenantId();
        if (!tenantRepository.existsById(tenantId)) {
            throw new DomainException(400, "El tenant del token no existe", "tenant_id");
        }
        String email = request.email().toLowerCase();
        if (usuarioRepository.existsByTenantIdAndEmail(tenantId, email)) {
            throw new DomainException(400, "El email ya existe en el tenant", "email");
        }
        usuarioRepository.findByEmailIgnoreCase(email).ifPresent(existente -> {
            if (existente.getTenantId() != null && !tenantId.equals(existente.getTenantId())) {
                throw new DomainException(409, "El email ya pertenece a otra empresa", "email");
            }
        });
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setTenantId(tenantId);
        usuario.setEmail(email);
        usuario.setNombre(request.nombre());
        usuario.setRol("Trabajador");
        usuario.setTrabajadorId(request.trabajadorId());
        usuario.setEstado(ESTADO_INVITADO);
        usuario.setActivo(true);
        usuario.setCreadoEn(Instant.now());
        usuarioRepository.saveAndFlush(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Invitacion", null, email + "|Trabajador");
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse asignar(String usuarioId, AsignarUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        boolean operador = Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(actor.role()));
        String tenantId = operador ? request.tenantId() : actor.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new DomainException(400, "El tenant es obligatorio para asignar", "tenant_id");
        }
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
        usuario.setEstado(ESTADO_ACTIVO);
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
        log.setTenantId(actor.tenantId() != null ? actor.tenantId() : "00000000-0000-0000-0000-000000000001");
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
