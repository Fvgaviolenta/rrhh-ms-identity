package com.rrhh.identity.service;

import com.rrhh.identity.dto.request.AsignarUsuarioRequest;
import com.rrhh.identity.dto.request.CambiarEstadoUsuarioRequest;
import com.rrhh.identity.dto.request.CrearUsuarioPlataformaRequest;
import com.rrhh.identity.dto.request.CrearUsuarioRequest;
import com.rrhh.identity.dto.request.InvitarUsuarioRequest;
import com.rrhh.identity.dto.request.SolicitarAccesoRequest;
import com.rrhh.identity.dto.response.MeResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.mapper.UsuarioMapper;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.model.Usuario;
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
    private static final String ESTADO_NO_REGISTRADO = "NO_REGISTRADO";
    private static final String ROL_ADMIN_PLATAFORMA = "AdminPlataforma";
    public static final String HEADER_EMPRESA_SLUG = "X-Empresa-Slug";

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final RolRepository rolRepository;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final UsuarioMapper usuarioMapper;
    private final CognitoProvisionService cognitoProvisionService;
    private final TenantService tenantService;
    private final OutboxPublisher outboxPublisher;
    private final AdminPlataformaBootstrap adminPlataformaBootstrap;

    public UsuarioService(
            UsuarioRepository usuarioRepository,
            TenantRepository tenantRepository,
            RolRepository rolRepository,
            AuditLogService auditLogService,
            TenantContext tenantContext,
            UsuarioMapper usuarioMapper,
            CognitoProvisionService cognitoProvisionService,
            TenantService tenantService,
            OutboxPublisher outboxPublisher,
            AdminPlataformaBootstrap adminPlataformaBootstrap
    ) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.rolRepository = rolRepository;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.usuarioMapper = usuarioMapper;
        this.cognitoProvisionService = cognitoProvisionService;
        this.tenantService = tenantService;
        this.outboxPublisher = outboxPublisher;
        this.adminPlataformaBootstrap = adminPlataformaBootstrap;
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
        if (local == null && actor.email() != null) {
            local = vincularPorEmail(actor);
        }
        if (adminPlataformaBootstrap.corresponde(actor)) {
            local = promoverAdminPlataforma(actor, local);
        }

        if (esperado != null && actor.tenantId() != null
                && !esperado.getId().equals(actor.tenantId())
                && !esRolDePlataforma(actor.role())) {
            throw new DomainException(403, "Este usuario no pertenece a la empresa indicada");
        }

        // S4: sin fila propia, o con una fila sin empresa y sin rol de plataforma (incluye las
        // PENDIENTE antiguas sin tenant), la cuenta no está asociada a ninguna empresa.
        if (local == null || (local.getTenantId() == null && !esRolDePlataforma(local.getRol()))) {
            return noRegistrado(actor);
        }
        if ((local.getNombre() == null || local.getNombre().isBlank())
                && actor.name() != null && !actor.name().isBlank()) {
            local.setNombre(actor.name());
            usuarioRepository.save(local);
        }
        validarSlugContraUsuario(esperado, local);
        return toMeResponse(local, actor);
    }

    /**
     * Vincula la identidad con una fila creada por un administrador que tiene su correo: sin
     * cognito_sub, o con el identificador provisional que deja provision() (el username de Cognito,
     * igual al correo). Si esa fila ya pertenece a otra identidad, esta identidad no tiene fila propia.
     */
    private Usuario vincularPorEmail(TenantContext.AuthenticatedUser actor) {
        Usuario porEmail = usuarioRepository.findByEmailIgnoreCase(actor.email()).orElse(null);
        if (porEmail == null) {
            return null;
        }
        boolean sinIdentidad = porEmail.getCognitoSub() == null
                || porEmail.getCognitoSub().equalsIgnoreCase(porEmail.getEmail());
        if (!sinIdentidad) {
            return null;
        }
        porEmail.setCognitoSub(actor.cognitoSub());
        if (actor.cognitoUsername() != null) {
            porEmail.setCognitoUsername(actor.cognitoUsername());
        }
        Usuario vinculado = usuarioRepository.saveAndFlush(porEmail);
        // Los claims custom:* del token aún no existen para esta identidad (S7).
        cognitoProvisionService.actualizar(vinculado);
        return vinculado;
    }

    /**
     * Única fila que /auth/me crea o promueve (S6): la del AdminPlataforma. Solo escribe y
     * sincroniza Cognito cuando algo cambió.
     */
    private Usuario promoverAdminPlataforma(TenantContext.AuthenticatedUser actor, Usuario local) {
        Usuario admin = local;
        if (admin == null) {
            Usuario porEmail = usuarioRepository.findByEmailIgnoreCase(actor.email()).orElse(null);
            if (porEmail != null && porEmail.getCognitoSub() != null
                    && !porEmail.getCognitoSub().equals(actor.cognitoSub())) {
                // El correo ya es de otra identidad: no se crea una segunda fila con el mismo correo.
                return null;
            }
            admin = porEmail;
        }
        if (admin == null) {
            admin = new Usuario();
            admin.setId(UUID.randomUUID().toString());
            admin.setEmail(actor.email().toLowerCase());
            admin.setNombre(actor.name());
            admin.setCreadoEn(Instant.now());
        }
        boolean yaEsAdmin = ROL_ADMIN_PLATAFORMA.equals(admin.getRol())
                && admin.getTenantId() == null
                && ESTADO_ACTIVO.equals(admin.getEstado())
                && admin.isActivo()
                && actor.cognitoSub() != null && actor.cognitoSub().equals(admin.getCognitoSub())
                && (actor.cognitoUsername() == null || actor.cognitoUsername().equals(admin.getCognitoUsername()));
        if (yaEsAdmin) {
            return admin;
        }
        String anterior = admin.getEstado() + "|" + admin.getTenantId() + "|" + admin.getRol();
        admin.setRol(ROL_ADMIN_PLATAFORMA);
        admin.setTenantId(null);
        admin.setTrabajadorId(null);
        admin.setEstado(ESTADO_ACTIVO);
        admin.setActivo(true);
        admin.setCognitoSub(actor.cognitoSub());
        if (actor.cognitoUsername() != null) {
            admin.setCognitoUsername(actor.cognitoUsername());
        }
        admin = usuarioRepository.saveAndFlush(admin);
        cognitoProvisionService.actualizar(admin);
        auditar(actor, "Usuario", admin.getId(), "PromocionAdminPlataforma", anterior, "null|" + ROL_ADMIN_PLATAFORMA);
        return admin;
    }

    private MeResponse noRegistrado(TenantContext.AuthenticatedUser actor) {
        return new MeResponse(
                null,
                null,
                actor.email(),
                actor.name(),
                null,
                null,
                actor.cognitoSub(),
                ESTADO_NO_REGISTRADO,
                null,
                true,
                null,
                null,
                null
        );
    }

    private static boolean esRolDePlataforma(String rol) {
        String autoridad = Roles.authorityFromClaim(rol);
        return Roles.OPERADOR_SAAS.equals(autoridad) || Roles.ADMIN_PLATAFORMA.equals(autoridad);
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
        if (actor.cognitoUsername() != null) {
            invitacion.setCognitoUsername(actor.cognitoUsername());
        }
        Usuario vinculada = usuarioRepository.saveAndFlush(invitacion);
        cognitoProvisionService.actualizar(vinculada);
        return vinculada;
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
        boolean operador = esRolDePlataforma(local.getRol());
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
        boolean operador = esRolDePlataforma(local.getRol());
        boolean pendiente = ESTADO_PENDIENTE.equals(local.getEstado())
                || local.getRol() == null
                || (local.getTenantId() == null && !operador);
        String nombreVisible = (local.getNombre() != null && !local.getNombre().isBlank())
                ? local.getNombre()
                : actor.name();
        String slug = null;
        Tenant tenant = null;
        if (operador) {
            slug = TenantSlug.PLATAFORMA;
        } else if (local.getTenantId() != null) {
            tenant = tenantRepository.findById(local.getTenantId()).orElse(null);
            slug = tenant != null ? tenant.getSlug() : null;
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
                slug,
                tenant != null ? tenant.getEstado() : null,
                tenant != null ? tenant.getNombreEmpresa() : null
        );
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
        if (!esRolDePlataforma(actor.role())) {
            throw new DomainException(403, "Acceso exclusivo para la plataforma");
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

    /** Solo las solicitudes PENDIENTE de la empresa del actor (S4); nunca filas sin tenant. */
    public List<UsuarioResponse> listarPendientes() {
        String tenantId = requireTenant();
        return usuarioRepository.findByEstadoAndTenantId(ESTADO_PENDIENTE, tenantId).stream()
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

    /**
     * Cuenta creada por el AdminPlataforma (S6): OperadorSaaS sin tenant, o SuperAdmin de un tenant
     * ACTIVO. Nace ACTIVO sin cognito_sub; la persona entra con Google y /auth/me la vincula por correo.
     */
    @Transactional
    public UsuarioResponse crearUsuarioPlataforma(CrearUsuarioPlataformaRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        String rol = request.rol().trim();
        String tenantId = request.tenantId() == null || request.tenantId().isBlank() ? null : request.tenantId().trim();
        if ("OperadorSaaS".equals(rol)) {
            if (tenantId != null) {
                throw new DomainException(400, "Un OperadorSaaS no pertenece a una empresa", "tenant_id");
            }
        } else if ("SuperAdmin".equals(rol)) {
            if (tenantId == null) {
                throw new DomainException(400, "La empresa es obligatoria para un SuperAdmin", "tenant_id");
            }
            boolean activa = tenantRepository.findById(tenantId)
                    .filter(t -> TenantEstado.ACTIVO.equals(t.getEstado()))
                    .filter(t -> !TenantSlug.PLATAFORMA.equals(t.getSlug()))
                    .isPresent();
            if (!activa) {
                throw new DomainException(400, "La empresa no existe o no está activa", "tenant_id");
            }
        } else {
            throw new DomainException(400, "Rol no permitido: solo OperadorSaaS o SuperAdmin", "rol");
        }
        String email = request.email().trim().toLowerCase();
        if (usuarioRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DomainException(409, "Ya existe una cuenta con ese correo", "email");
        }
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setEmail(email);
        usuario.setNombre(request.nombre().trim());
        usuario.setRol(rol);
        usuario.setTenantId(tenantId);
        usuario.setEstado(ESTADO_ACTIVO);
        usuario.setActivo(true);
        usuario.setCreadoEn(Instant.now());
        usuarioRepository.saveAndFlush(usuario);
        auditLogService.registrar(tenantId, actor.userId(), "Usuario", usuario.getId(), "CreacionUsuarioPlataforma",
                null, email + "|" + rol);
        return usuarioMapper.toResponse(usuario);
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
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new DomainException(404, "Usuario no encontrado"));
        // S4: un administrador del tenant solo asigna solicitudes PENDIENTE de su propia empresa.
        if (!operador && (!ESTADO_PENDIENTE.equals(usuario.getEstado()) || !tenantId.equals(usuario.getTenantId()))) {
            throw new DomainException(404, "Usuario no encontrado");
        }
        if (!tenantRepository.existsById(tenantId)) {
            throw new DomainException(400, "El tenant del token no existe", "tenant_id");
        }
        rolRepository.findByCodigo(request.rol())
                .orElseThrow(() -> new DomainException(400, "Rol no oficial", "rol"));
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
        auditLogService.registrar(actor.tenantId(), actor.userId(), entidad, entidadId, accion, anterior, nuevo);
    }
}
