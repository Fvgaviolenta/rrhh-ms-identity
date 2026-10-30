package com.rrhh.identity.service;

import com.rrhh.identity.dto.request.AsignarUsuarioRequest;
import com.rrhh.identity.dto.request.CambiarEstadoUsuarioRequest;
import com.rrhh.identity.dto.request.CrearUsuarioRequest;
import com.rrhh.identity.dto.request.InvitarUsuarioRequest;
import com.rrhh.identity.dto.request.SolicitarAccesoRequest;
import com.rrhh.identity.dto.response.MeResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.mapper.UsuarioMapper;
import com.rrhh.identity.model.AuditLog;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
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
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UsuarioService {

    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_INVITADO = "INVITADO";
    private static final String ESTADO_RECHAZADO = "RECHAZADO";
    public static final String HEADER_EMPRESA_SLUG = "X-Empresa-Slug";

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final RolRepository rolRepository;
    private final AuditLogRepository auditLogRepository;
    private final TenantContext tenantContext;
    private final UsuarioMapper usuarioMapper;
    private final CognitoProvisionService cognitoProvisionService;
    private final TenantService tenantService;
    private final OutboxPublisher outboxPublisher;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            TenantRepository tenantRepository,
            RolRepository rolRepository,
            AuditLogRepository auditLogRepository,
            TenantContext tenantContext,
            UsuarioMapper usuarioMapper,
            CognitoProvisionService cognitoProvisionService,
            TenantService tenantService,
            OutboxPublisher outboxPublisher
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.rolRepository = rolRepository;
        this.auditLogRepository = auditLogRepository;
        this.tenantContext = tenantContext;
        this.usuarioMapper = usuarioMapper;
        this.cognitoProvisionService = cognitoProvisionService;
        this.tenantService = tenantService;
        this.outboxPublisher = outboxPublisher;
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
            local = usuarioRepository.findByEmailIgnoreCase(actor.email()).orElse(null);
            if (local != null && local.getCognitoSub() != null
                    && actor.cognitoSub() != null
                    && !actor.cognitoSub().equals(local.getCognitoSub())) {
                throw new DomainException(403, "Este correo ya está vinculado a otra identidad", "email");
            }
            if (local != null && local.getCognitoSub() == null) {
                local.setCognitoSub(actor.cognitoSub());
                local = usuarioRepository.saveAndFlush(local);
            }
        }
        if (local == null && actor.email() != null) {
            usuarioRepository.findByEmailIgnoreCase(actor.email()).ifPresent(otro -> {
                if (esperado != null && !esperado.getId().equals(otro.getTenantId())
                        && !TenantSlug.PLATAFORMA.equals(esperado.getSlug())) {
                    throw new DomainException(403, "Este usuario no pertenece a la empresa indicada", "email");
                }
            });
        }
        if (local == null && actor.cognitoSub() != null
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
        boolean identificadorProvisional = invitacion.getCognitoSub() != null
                && invitacion.getEmail() != null
                && invitacion.getCognitoSub().equalsIgnoreCase(invitacion.getEmail());
        if (invitacion.getCognitoSub() != null && actor.cognitoSub() != null
                && !invitacion.getCognitoSub().equals(actor.cognitoSub())
                && !identificadorProvisional) {
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
            boolean identidadCompleta = actor.tenantId() != null
                    && actor.role() != null
                    && tenantRepository.findById(actor.tenantId()).isPresent();
            usuario.setTenantId(identidadCompleta ? actor.tenantId() : null);
            usuario.setRol(identidadCompleta ? actor.role() : null);
            usuario.setEstado(identidadCompleta ? ESTADO_ACTIVO : ESTADO_PENDIENTE);
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
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        return usuarioRepository.findByTenantId(tenantId).stream()
                .filter(usuario -> puedeGestionarUsuario(actor, usuario))
                .map(usuarioMapper::toResponse)
                .toList();
    }

    public List<UsuarioResponse> listarPorTenantPlataforma(String tenantId) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        if (!Roles.OPERADOR_SAAS.equals(Roles.authorityFromClaim(actor.role()))) {
            throw new DomainException(403, "Acceso exclusivo para el operador SaaS");
        }
        return usuarioRepository.findByTenantId(tenantId).stream()
                .map(usuarioMapper::toResponse)
                .toList();
    }

    public UsuarioResponse obtener(String usuarioId) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        Usuario usuario = buscarDelTenant(usuarioId);
        validarQuePuedeGestionarUsuario(actor, usuario);
        return usuarioMapper.toResponse(usuario);
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

    /**
     * Solicitud de acceso a una empresa por su RUT (S3/S5). Una solicitud nueva, o que cambia de
     * empresa, queda PENDIENTE en el tenant destino y registra {@code acceso.solicitado} en el outbox
     * dentro de la misma transacción.
     */
    @Transactional
    public ResultadoSolicitud solicitarAcceso(SolicitarAccesoRequest request) {
        Tenant tenant = tenantService.tenantActivoPorRut(request.rutEmpresa());
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        if (actor.email() == null || actor.email().isBlank()) {
            throw new DomainException(400, "Google no entregó un correo válido para solicitar acceso", "email");
        }

        Usuario usuario = actor.cognitoSub() == null
                ? null
                : usuarioRepository.findByCognitoSub(actor.cognitoSub()).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.findByEmailIgnoreCase(actor.email()).orElse(null);
            if (usuario != null && usuario.getCognitoSub() != null && actor.cognitoSub() != null
                    && !actor.cognitoSub().equals(usuario.getCognitoSub())) {
                throw new DomainException(403, "Este correo ya está vinculado a otra identidad", "email");
            }
        }
        if (usuario == null) {
            usuario = new Usuario();
            usuario.setId(UUID.randomUUID().toString());
            usuario.setCreadoEn(Instant.now());
            usuario.setEmail(actor.email().toLowerCase());
        }

        String autoridad = Roles.authorityFromClaim(usuario.getRol());
        boolean rolDePlataforma = Roles.OPERADOR_SAAS.equals(autoridad) || Roles.ADMIN_PLATAFORMA.equals(autoridad);
        boolean perteneceAEmpresa = usuario.getTenantId() != null
                && (ESTADO_ACTIVO.equals(usuario.getEstado()) || ESTADO_INVITADO.equals(usuario.getEstado()));
        if (perteneceAEmpresa || rolDePlataforma) {
            throw new DomainException(409, "Tu cuenta ya pertenece a una empresa", "rut_empresa");
        }
        boolean mismoTenant = tenant.getId().equals(usuario.getTenantId());
        if (ESTADO_PENDIENTE.equals(usuario.getEstado()) && usuario.getTenantId() != null) {
            boolean registranteEnRevision = tenantRepository.findById(usuario.getTenantId())
                    .map(t -> TenantEstado.PENDIENTE_ACTIVACION.equals(t.getEstado()))
                    .orElse(false);
            if (registranteEnRevision) {
                throw new DomainException(409, "Tu cuenta ya está asociada a una empresa", "rut_empresa");
            }
            if (mismoTenant) {
                return new ResultadoSolicitud(usuarioMapper.toResponse(usuario), false);
            }
        }
        if (ESTADO_RECHAZADO.equals(usuario.getEstado()) && mismoTenant) {
            throw new DomainException(409, "Tu solicitud fue rechazada por esta empresa", "rut_empresa");
        }

        if (usuario.getCognitoSub() == null) {
            usuario.setCognitoSub(actor.cognitoSub());
        }
        if (actor.cognitoUsername() != null) {
            usuario.setCognitoUsername(actor.cognitoUsername());
        }
        if (actor.name() != null && !actor.name().isBlank()) {
            usuario.setNombre(actor.name());
        }
        usuario.setTenantId(tenant.getId());
        usuario.setRol(null);
        usuario.setEstado(ESTADO_PENDIENTE);
        usuario.setActivo(true);
        usuario = usuarioRepository.saveAndFlush(usuario);
        String nombre = usuario.getNombre() != null && !usuario.getNombre().isBlank()
                ? usuario.getNombre()
                : usuario.getEmail();
        outboxPublisher.registrar("acceso.solicitado", tenant.getId(), usuario.getId(),
                Map.of("usuarioId", usuario.getId(), "email", usuario.getEmail(), "nombre", nombre));
        return new ResultadoSolicitud(usuarioMapper.toResponse(usuario), true);
    }

    /** {@code nueva} es true cuando la solicitud se creó o cambió de empresa (201 y evento). */
    public record ResultadoSolicitud(UsuarioResponse usuario, boolean nueva) {
    }

    @Transactional
    public UsuarioResponse invitar(InvitarUsuarioRequest request) {
        if (request.crearEnCognito() && (request.trabajadorId() == null || request.trabajadorId().isBlank())) {
            throw new DomainException(400, "El trabajador es obligatorio para crear la cuenta", "trabajador_id");
        }
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        String actorAuthority = Roles.authorityFromClaim(actor.role());
        if (request.rol() == null || request.rol().isBlank()) {
            throw new DomainException(400, "El rol es obligatorio", "rol");
        }
        String requestedRole = request.rol().trim();
        if (!Set.of("ADMIN_RRHH", "JEFATURA", "TRABAJADOR", "Admin de RRHH", "Jefatura", "Trabajador",
                "ROLE_ADMIN_RRHH", "ROLE_JEFATURA", "ROLE_TRABAJADOR", "SUPERADMIN", "SuperAdmin", "ROLE_SUPERADMIN")
                .contains(requestedRole)) {
            throw new DomainException(400, "Rol no oficial", "rol");
        }
        String requestedAuthority = Roles.authorityFromClaim(requestedRole);
        if (Roles.ADMIN_RRHH.equals(actorAuthority) && Roles.SUPERADMIN.equals(requestedAuthority)) {
            throw new DomainException(403, "Admin de RRHH no puede asignar el rol SUPERADMIN", "rol");
        }
        if (!Roles.ADMIN_RRHH.equals(requestedAuthority)
                && !Roles.JEFATURA.equals(requestedAuthority)
                && !Roles.TRABAJADOR.equals(requestedAuthority)
                && !(Roles.OPERADOR_SAAS.equals(actorAuthority) && Roles.SUPERADMIN.equals(requestedAuthority))) {
            throw new DomainException(400, "Rol no permitido para esta invitación", "rol");
        }
        String rol = Roles.claimFromAuthority(requestedAuthority);
        String tenantId = actor.tenantId();
        if (!tenantRepository.existsById(tenantId)) {
            throw new DomainException(400, "El tenant del token no existe", "tenant_id");
        }
        String email = request.email().toLowerCase();
        if (usuarioRepository.existsByTenantIdAndEmail(tenantId, email)) {
            Usuario existente = usuarioRepository.findByEmailIgnoreCase(email).orElseThrow();
            if (request.crearEnCognito() && tenantId.equals(existente.getTenantId())
                    && request.trabajadorId().equals(existente.getTrabajadorId())) {
                existente.setRol(rol);
                existente.setEstado(ESTADO_ACTIVO);
                existente.setActivo(true);
                usuarioRepository.saveAndFlush(existente);
                CognitoProvisionService.Result cognito = new CognitoProvisionService.Result(null, null, null);
                if (existente.getCognitoSub() == null) {
                    cognito = cognitoProvisionService.provision(existente);
                    if (cognito.username() != null) {
                        existente.setCognitoSub(cognito.username());
                        usuarioRepository.saveAndFlush(existente);
                    }
                }
                return usuarioMapper.toResponse(existente, cognito.password(), cognito.error());
            }
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
        usuario.setRol(rol);
        usuario.setTrabajadorId(request.trabajadorId());
        // La cuenta fue creada por RRHH, por lo que no requiere aprobación posterior.
        usuario.setEstado(ESTADO_ACTIVO);
        usuario.setActivo(true);
        usuario.setCreadoEn(Instant.now());
        usuarioRepository.saveAndFlush(usuario);
        CognitoProvisionService.Result cognito = new CognitoProvisionService.Result(null, null, null);
        if (request.crearEnCognito()) {
            cognito = cognitoProvisionService.provision(usuario);
            if (cognito.username() != null) {
                usuario.setCognitoSub(cognito.username());
                usuarioRepository.saveAndFlush(usuario);
            }
        }
        auditar(actor, "Usuario", usuario.getId(), "Invitacion", null, email + "|" + rol);
        return usuarioMapper.toResponse(usuario, cognito.password(), cognito.error());
    }

    @Transactional
    public UsuarioResponse sincronizarPorTrabajador(String trabajadorId, String email, String nombre) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        Usuario usuario = usuarioRepository.findByTrabajadorIdAndTenantId(trabajadorId, actor.tenantId())
                .orElseThrow(() -> new DomainException(404, "No hay usuario de acceso para este trabajador", "trabajador_id"));
        validarQuePuedeGestionarUsuario(actor, usuario);
        if (email != null && !email.isBlank()) {
            usuario.setEmail(email.toLowerCase());
        }
        if (nombre != null && !nombre.isBlank()) {
            usuario.setNombre(nombre);
        }
        usuarioRepository.save(usuario);
        String cognitoError = cognitoProvisionService.actualizar(usuario);
        auditar(actor, "Usuario", usuario.getId(), "SincronizacionCognito", null, usuario.getEmail());
        return usuarioMapper.toResponse(usuario, null, cognitoError);
    }

    @Transactional
    public UsuarioResponse cambiarRolPorTrabajador(String trabajadorId, String rolSolicitado) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        Usuario usuario = usuarioRepository.findByTrabajadorIdAndTenantId(trabajadorId, actor.tenantId())
                .orElseThrow(() -> new DomainException(404, "El trabajador no tiene una cuenta de acceso", "trabajador_id"));
        validarQuePuedeGestionarUsuario(actor, usuario);
        boolean esMismaCuenta = usuario.getId().equals(actor.userId())
                || (usuario.getCognitoSub() != null && usuario.getCognitoSub().equals(actor.cognitoSub()));
        if (esMismaCuenta) {
            throw new DomainException(403, "No puedes cambiar tu propio rol", "rol");
        }
        String autoridadActual = Roles.authorityFromClaim(usuario.getRol());
        if (Roles.SUPERADMIN.equals(autoridadActual) || Roles.OPERADOR_SAAS.equals(autoridadActual)) {
            throw new DomainException(403, "El rol de esta cuenta no se puede modificar", "rol");
        }
        if (!ESTADO_ACTIVO.equals(usuario.getEstado())) {
            throw new DomainException(400, "Solo se puede cambiar el rol de una cuenta activa", "estado");
        }
        String autoridadNueva = Roles.authorityFromClaim(rolSolicitado);
        if (!Set.of(Roles.ADMIN_RRHH, Roles.JEFATURA, Roles.TRABAJADOR).contains(autoridadNueva)) {
            throw new DomainException(400, "Rol no permitido", "rol");
        }
        String rolNuevo = Roles.claimFromAuthority(autoridadNueva);
        String anterior = usuario.getRol();
        usuario.setRol(rolNuevo);
        usuarioRepository.save(usuario);
        // custom:role vive en Cognito: sin esta sincronización el siguiente JWT conservaría el rol anterior.
        String cognitoError = cognitoProvisionService.actualizar(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Modificacion", "rol=" + anterior, "rol=" + rolNuevo);
        return usuarioMapper.toResponse(usuario, null, cognitoError);
    }

    @Transactional
    public UsuarioResponse asignar(String usuarioId, AsignarUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        validarRolAsignablePor(actor, request.rol());
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
        if (usuario.getTenantId() != null
                && (!tenantId.equals(usuario.getTenantId()) || !ESTADO_PENDIENTE.equals(usuario.getEstado()))) {
            throw new DomainException(400, "El usuario ya esta asignado a un tenant");
        }
        if (usuario.getEmail() != null) {
            usuarioRepository.findByTenantIdAndEmail(tenantId, usuario.getEmail())
                    .filter(existente -> !existente.getId().equals(usuario.getId()))
                    .ifPresent(existente -> {
                        throw new DomainException(400, "El email ya existe en el tenant", "email");
                    });
        }
        String anterior = usuario.getEstado() + "|" + usuario.getTenantId() + "|" + usuario.getRol();
        usuario.setTenantId(tenantId);
        usuario.setRol(request.rol());
        usuario.setTrabajadorId(request.trabajadorId());
        usuario.setEstado(ESTADO_ACTIVO);
        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        // La asignación cambia los claims de autorización. Sin esta sincronización,
        // el siguiente token de Cognito seguiría sin custom:trabajador_id.
        String cognitoError = cognitoProvisionService.actualizar(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Asignacion", anterior,
                usuario.getTenantId() + "|" + usuario.getRol());
        return usuarioMapper.toResponse(usuario, null, cognitoError);
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        validarRolAsignablePor(actor, request.rol());
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
        validarQuePuedeGestionarUsuario(actor, usuario);
        String anterior = String.valueOf(usuario.isActivo());
        usuario.setActivo(request.activo());
        usuarioRepository.save(usuario);
        auditar(actor, "Usuario", usuario.getId(), "Modificacion", anterior, String.valueOf(usuario.isActivo()));
        return usuarioMapper.toResponse(usuario);
    }

    @Transactional
    public UsuarioResponse rechazarSolicitud(String usuarioId) {
        TenantContext.AuthenticatedUser actor = tenantContext.require();
        Usuario usuario = buscarDelTenant(usuarioId);
        validarQuePuedeGestionarUsuario(actor, usuario);
        if (!ESTADO_PENDIENTE.equals(usuario.getEstado())) {
            throw new DomainException(400, "La solicitud ya fue resuelta");
        }
        usuario.setEstado("RECHAZADO");
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        auditar(actor, "Usuario", usuario.getId(), "SolicitudRechazada", null, usuario.getEmail());
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

    private boolean puedeGestionarUsuario(TenantContext.AuthenticatedUser actor, Usuario usuario) {
        return !Roles.ADMIN_RRHH.equals(Roles.authorityFromClaim(actor.role()))
                || !Roles.SUPERADMIN.equals(Roles.authorityFromClaim(usuario.getRol()));
    }

    private void validarQuePuedeGestionarUsuario(TenantContext.AuthenticatedUser actor, Usuario usuario) {
        if (!puedeGestionarUsuario(actor, usuario)) {
            // Se responde como no encontrado para no revelar datos de la cuenta protegida.
            throw new DomainException(404, "Usuario no encontrado");
        }
    }

    private void validarRolAsignablePor(TenantContext.AuthenticatedUser actor, String rol) {
        if (Roles.ADMIN_RRHH.equals(Roles.authorityFromClaim(actor.role()))
                && Roles.SUPERADMIN.equals(Roles.authorityFromClaim(rol))) {
            throw new DomainException(403, "Admin de RRHH no puede crear ni asignar cuentas SUPERADMIN", "rol");
        }
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
