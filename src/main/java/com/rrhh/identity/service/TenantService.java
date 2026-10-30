package com.rrhh.identity.service;

import com.rrhh.identity.dto.request.RegistrarEmpresaRequest;
import com.rrhh.identity.dto.response.TenantResolverResponse;
import com.rrhh.identity.dto.response.TenantResumenResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.Roles;
import com.rrhh.identity.security.TenantContext;
import com.rrhh.identity.util.RutEmpresa;
import com.rrhh.identity.util.TenantSlug;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TenantService {

    static final String RUT_INVALIDO = "El RUT de la empresa no es válido";
    static final String EMPRESA_NO_REGISTRADA = "La empresa no está registrada en el sistema";

    static final String RUT_DUPLICADO = "Ya existe una empresa registrada con ese RUT";
    static final String CUENTA_CON_EMPRESA = "Tu cuenta ya está asociada a una empresa";

    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantContext tenantContext;
    private final AuditLogService auditLogService;
    private final CognitoProvisionService cognitoProvisionService;
    private final long delayMs;

    public TenantService(
            TenantRepository tenantRepository,
            UsuarioRepository usuarioRepository,
            TenantContext tenantContext,
            AuditLogService auditLogService,
            CognitoProvisionService cognitoProvisionService,
            @Value("${rrhh.tenant-resolver.delay-ms:150}") long delayMs
    ) {
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.tenantContext = tenantContext;
        this.auditLogService = auditLogService;
        this.cognitoProvisionService = cognitoProvisionService;
        this.delayMs = delayMs;
    }

    /**
     * Registro de una empresa (S1): nace PENDIENTE_ACTIVACION y quien la registra queda como su
     * SuperAdmin PENDIENTE hasta que el AdminPlataforma la active.
     */
    @Transactional
    public TenantResumenResponse registrar(RegistrarEmpresaRequest request) {
        TenantContext.AuthenticatedUser actor = tenantContext.current();
        String rut = RutEmpresa.normalizar(request.rutEmpresa())
                .orElseThrow(() -> new DomainException(400, RUT_INVALIDO, "rut_empresa"));
        String rutRepresentante = RutEmpresa.normalizar(request.representanteRut())
                .orElseThrow(() -> new DomainException(400, "El RUT del representante no es válido", "representante_rut"));
        if (tenantRepository.existsByRutEmpresa(rut)) {
            throw new DomainException(409, RUT_DUPLICADO, "rut_empresa");
        }
        if (actor.email() == null || actor.email().isBlank()) {
            throw new DomainException(400, "Tu cuenta no tiene un correo válido", "email");
        }
        Usuario usuario = filaDelActor(actor);
        if (actor.tenantId() != null || esRolDePlataforma(actor.role()) || tieneEmpresaOSolicitud(usuario)) {
            throw new DomainException(409, CUENTA_CON_EMPRESA, "rut_empresa");
        }
        if (usuario == null) {
            usuario = new Usuario();
            usuario.setId(UUID.randomUUID().toString());
            usuario.setEmail(actor.email().toLowerCase());
            usuario.setCreadoEn(Instant.now());
        }

        Instant ahora = Instant.now();
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID().toString());
        tenant.setNombreEmpresa(request.nombreFantasia().trim());
        tenant.setSlug(TenantSlug.unico(request.nombreFantasia(), tenantRepository::existsBySlug));
        tenant.setRutEmpresa(rut);
        tenant.setRazonSocial(request.razonSocial().trim());
        tenant.setGiro(request.giro().trim());
        tenant.setCodigoActividad(blankToNull(request.codigoActividad()));
        tenant.setDireccionCalle(request.direccionCalle().trim());
        tenant.setDireccionNumero(request.direccionNumero().trim());
        tenant.setComuna(request.comuna().trim());
        tenant.setRegion(request.region().trim());
        tenant.setEmailContacto(request.emailContacto().trim().toLowerCase());
        tenant.setTelefonoContacto(blankToNull(request.telefonoContacto()));
        tenant.setRepresentanteNombre(request.representanteNombre().trim());
        tenant.setRepresentanteRut(rutRepresentante);
        tenant.setTerminosAceptadosEn(ahora);
        tenant.setRegistradoPor(usuario.getId());
        tenant.setCreadoEn(ahora);
        tenant.cambiarEstado(TenantEstado.PENDIENTE_ACTIVACION);
        try {
            tenantRepository.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException ex) {
            // Otro registro con el mismo RUT ganó la carrera entre la verificación y el INSERT.
            throw new DomainException(409, RUT_DUPLICADO, "rut_empresa");
        }

        usuario.setTenantId(tenant.getId());
        usuario.setRol("SuperAdmin");
        usuario.setEstado("PENDIENTE");
        usuario.setActivo(true);
        usuario.setTrabajadorId(null);
        if (usuario.getCognitoSub() == null || usuario.getCognitoSub().equalsIgnoreCase(usuario.getEmail())) {
            usuario.setCognitoSub(actor.cognitoSub());
        }
        if (actor.cognitoUsername() != null) {
            usuario.setCognitoUsername(actor.cognitoUsername());
        }
        if (actor.name() != null && !actor.name().isBlank()) {
            usuario.setNombre(actor.name());
        }
        usuarioRepository.saveAndFlush(usuario);

        auditLogService.registrar(tenant.getId(), usuario.getId(), "Tenant", tenant.getId(), "RegistroEmpresa",
                null, rut + "|" + tenant.getNombreEmpresa() + "|" + tenant.getSlug());
        return resumen(tenant, usuario.getEmail(), null);
    }

    /** La fila local de quien llama: por cognito_sub o, si no tiene, la creada por un administrador con su correo. */
    private Usuario filaDelActor(TenantContext.AuthenticatedUser actor) {
        if (actor.cognitoSub() != null) {
            Usuario porSub = usuarioRepository.findByCognitoSub(actor.cognitoSub()).orElse(null);
            if (porSub != null) {
                return porSub;
            }
        }
        Usuario porEmail = usuarioRepository.findByEmailIgnoreCase(actor.email()).orElse(null);
        if (porEmail != null && porEmail.getCognitoSub() != null
                && !porEmail.getCognitoSub().equalsIgnoreCase(porEmail.getEmail())) {
            throw new DomainException(403, "Este correo ya está vinculado a otra identidad", "email");
        }
        return porEmail;
    }

    /**
     * S1: puede registrar quien no tiene fila, o tiene una sin empresa y sin rol de plataforma. Una
     * solicitud RECHAZADO no es una empresa ni una solicitud pendiente.
     */
    private static boolean tieneEmpresaOSolicitud(Usuario usuario) {
        if (usuario == null) {
            return false;
        }
        if (esRolDePlataforma(usuario.getRol())) {
            return true;
        }
        return usuario.getTenantId() != null && !"RECHAZADO".equals(usuario.getEstado());
    }

    private static boolean esRolDePlataforma(String rol) {
        String autoridad = Roles.authorityFromClaim(rol);
        return Roles.OPERADOR_SAAS.equals(autoridad) || Roles.ADMIN_PLATAFORMA.equals(autoridad);
    }

    private static String blankToNull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    TenantResumenResponse resumen(Tenant t, String registradoPorEmail, String cognitoError) {
        return new TenantResumenResponse(t.getId(), t.getNombreEmpresa(), t.getSlug(), t.isActivo(), t.getEstado(),
                t.getRutEmpresa(), t.getRazonSocial(), registradoPorEmail, cognitoError);
    }

    /** Resolver público del login "Unirme a mi empresa" (S3): solo empresas ACTIVO, nunca la de plataforma. */
    public TenantResolverResponse resolverPorRut(String rut) {
        retardarEnumeracion();
        Tenant tenant = tenantActivoPorRut(rut);
        return new TenantResolverResponse(true, tenant.getSlug(), tenant.getNombreEmpresa());
    }

    /**
     * Tenant ACTIVO con ese RUT. 400 si el RUT no es válido; 404 si no existe, no está ACTIVO
     * o es el tenant reservado de plataforma.
     */
    public Tenant tenantActivoPorRut(String rut) {
        String normalizado = RutEmpresa.normalizar(rut)
                .orElseThrow(() -> new DomainException(400, RUT_INVALIDO, "rut_empresa"));
        return tenantRepository.findByRutEmpresa(normalizado)
                .filter(t -> TenantEstado.ACTIVO.equals(t.getEstado()))
                .filter(t -> !TenantSlug.PLATAFORMA.equals(t.getSlug()))
                .orElseThrow(() -> new DomainException(404, EMPRESA_NO_REGISTRADA, "rut_empresa"));
    }

    private void retardarEnumeracion() {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final Set<String> ESTADOS = Set.of(
            TenantEstado.PENDIENTE_ACTIVACION, TenantEstado.ACTIVO, TenantEstado.RECHAZADO, TenantEstado.SUSPENDIDO);

    /** Consola de plataforma (S2): todas las empresas cliente, o las de un estado; nunca la de plataforma. */
    public List<TenantResumenResponse> listar(String estado) {
        List<Tenant> tenants;
        if (estado == null || estado.isBlank()) {
            tenants = tenantRepository.findBySlugNotOrderByNombreEmpresaAsc(TenantSlug.PLATAFORMA);
        } else {
            String filtro = estado.trim().toUpperCase(java.util.Locale.ROOT);
            if (!ESTADOS.contains(filtro)) {
                throw new DomainException(400, "Estado de empresa no válido", "estado");
            }
            tenants = tenantRepository.findByEstadoAndSlugNotOrderByNombreEmpresaAsc(filtro, TenantSlug.PLATAFORMA);
        }
        return tenants.stream()
                .map(t -> resumen(t, emailDe(t.getRegistradoPor()), null))
                .toList();
    }

    /** S2: la empresa pasa a ACTIVO y quien la registró a SuperAdmin ACTIVO, con sus claims en Cognito. */
    @Transactional
    public TenantResumenResponse activar(String tenantId) {
        Tenant tenant = pendienteDeActivacion(tenantId);
        tenant.cambiarEstado(TenantEstado.ACTIVO);
        tenantRepository.save(tenant);
        Usuario registrante = registranteDe(tenant);
        String cognitoError = null;
        if (registrante != null) {
            registrante.setEstado("ACTIVO");
            registrante.setRol("SuperAdmin");
            registrante.setActivo(true);
            usuarioRepository.saveAndFlush(registrante);
            cognitoError = cognitoProvisionService.actualizar(registrante);
        }
        auditLogService.registrar(tenant.getId(), tenantContext.current().userId(), "Tenant", tenant.getId(),
                "ActivacionEmpresa", TenantEstado.PENDIENTE_ACTIVACION, TenantEstado.ACTIVO);
        return resumen(tenant, registrante != null ? registrante.getEmail() : null, cognitoError);
    }

    /** S2: la empresa pasa a RECHAZADO y quien la registró a RECHAZADO, con el motivo en la auditoría. */
    @Transactional
    public TenantResumenResponse rechazar(String tenantId, String motivo) {
        Tenant tenant = pendienteDeActivacion(tenantId);
        tenant.cambiarEstado(TenantEstado.RECHAZADO);
        tenantRepository.save(tenant);
        Usuario registrante = registranteDe(tenant);
        if (registrante != null) {
            registrante.setEstado("RECHAZADO");
            registrante.setActivo(false);
            usuarioRepository.saveAndFlush(registrante);
        }
        auditLogService.registrar(tenant.getId(), tenantContext.current().userId(), "Tenant", tenant.getId(),
                "RechazoEmpresa", TenantEstado.PENDIENTE_ACTIVACION, TenantEstado.RECHAZADO + "|" + motivo.trim());
        return resumen(tenant, registrante != null ? registrante.getEmail() : null, null);
    }

    private Tenant pendienteDeActivacion(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .filter(t -> !TenantSlug.PLATAFORMA.equals(t.getSlug()))
                .orElseThrow(() -> new DomainException(404, "Empresa no encontrada"));
        if (!TenantEstado.PENDIENTE_ACTIVACION.equals(tenant.getEstado())) {
            throw new DomainException(409, "La empresa no está pendiente de activación", "estado");
        }
        return tenant;
    }

    /** El registrante solo se toca si su fila sigue apuntando a esta empresa. */
    private Usuario registranteDe(Tenant tenant) {
        if (tenant.getRegistradoPor() == null) {
            return null;
        }
        return usuarioRepository.findById(tenant.getRegistradoPor())
                .filter(u -> tenant.getId().equals(u.getTenantId()))
                .orElse(null);
    }

    private String emailDe(String usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return usuarioRepository.findById(usuarioId).map(Usuario::getEmail).orElse(null);
    }
}
