package com.rrhh.identity.service;

import com.rrhh.identity.dto.response.GrupoResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.GrupoEmpresas;
import com.rrhh.identity.model.GrupoOperador;
import com.rrhh.identity.model.GrupoTenant;
import com.rrhh.identity.repository.GrupoEmpresasRepository;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;
import com.rrhh.identity.security.TenantContext;
import com.rrhh.identity.util.TenantSlug;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grupos de empresas (ADR-020, S2): solo el AdminPlataforma los administra. Una empresa está en a lo
 * más un grupo; un OperadorSaaS puede estar en varios. Cada cambio se audita en el tenant de plataforma.
 */
@Service
public class GrupoService {

    private static final String ENTIDAD = "GrupoEmpresas";
    private static final String OPERADOR_SAAS = "OperadorSaaS";

    private final GrupoEmpresasRepository grupoRepository;
    private final GrupoTenantRepository grupoTenantRepository;
    private final GrupoOperadorRepository grupoOperadorRepository;
    private final TenantRepository tenantRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditLogService auditLogService;
    private final TenantContext tenantContext;
    private final AlcancePlataformaService alcancePlataformaService;

    public GrupoService(GrupoEmpresasRepository grupoRepository,
                        GrupoTenantRepository grupoTenantRepository,
                        GrupoOperadorRepository grupoOperadorRepository,
                        TenantRepository tenantRepository,
                        UsuarioRepository usuarioRepository,
                        AuditLogService auditLogService,
                        TenantContext tenantContext,
                        AlcancePlataformaService alcancePlataformaService) {
        this.grupoRepository = grupoRepository;
        this.grupoTenantRepository = grupoTenantRepository;
        this.grupoOperadorRepository = grupoOperadorRepository;
        this.tenantRepository = tenantRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditLogService = auditLogService;
        this.tenantContext = tenantContext;
        this.alcancePlataformaService = alcancePlataformaService;
    }

    @Transactional(readOnly = true)
    public List<GrupoResponse> listar() {
        return grupoRepository.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public GrupoResponse crear(String nombre) {
        String limpio = nombre.trim();
        exigirNombreLibre(limpio);
        GrupoEmpresas grupo = new GrupoEmpresas();
        grupo.setId(UUID.randomUUID().toString());
        grupo.setNombre(limpio);
        grupo.setCreadoEn(Instant.now());
        guardar(grupo);
        auditar(grupo.getId(), "Creacion", null, "nombre=" + limpio);
        return toResponse(grupo);
    }

    @Transactional
    public GrupoResponse renombrar(String grupoId, String nombre) {
        GrupoEmpresas grupo = buscar(grupoId);
        String limpio = nombre.trim();
        String anterior = grupo.getNombre();
        if (!anterior.equals(limpio)) {
            exigirNombreLibre(limpio);
            grupo.setNombre(limpio);
            guardar(grupo);
        }
        auditar(grupoId, "Modificacion", "nombre=" + anterior, "nombre=" + limpio);
        return toResponse(grupo);
    }

    @Transactional
    public void borrar(String grupoId) {
        GrupoEmpresas grupo = buscar(grupoId);
        GrupoResponse anterior = toResponse(grupo);
        grupoTenantRepository.deleteByGrupoId(grupoId);
        grupoOperadorRepository.deleteByGrupoId(grupoId);
        grupoTenantRepository.flush();
        grupoOperadorRepository.flush();
        grupoRepository.delete(grupo);
        auditar(grupoId, "Eliminacion", "nombre=" + anterior.nombre() + "|empresas=" + anterior.tenantIds()
                + "|operadores=" + anterior.operadorIds(), null);
    }

    /** Reemplaza las empresas del grupo: 404 si una no existe o es la de plataforma, 409 si está en otro grupo. */
    @Transactional
    public GrupoResponse fijarEmpresas(String grupoId, List<String> tenantIds) {
        GrupoEmpresas grupo = buscar(grupoId);
        Set<String> nuevos = limpiar(tenantIds);
        for (String tenantId : nuevos) {
            boolean cliente = tenantRepository.findById(tenantId)
                    .filter(t -> !TenantSlug.PLATAFORMA.equals(t.getSlug()))
                    .isPresent();
            if (!cliente) {
                throw new DomainException(404, "Empresa no encontrada", "ids");
            }
            grupoTenantRepository.findByTenantId(tenantId)
                    .filter(gt -> !gt.getGrupoId().equals(grupoId))
                    .ifPresent(gt -> {
                        throw new DomainException(409, "La empresa ya pertenece a otro grupo", "ids");
                    });
        }
        List<GrupoTenant> actuales = grupoTenantRepository.findByGrupoId(grupoId);
        List<String> anteriores = actuales.stream().map(GrupoTenant::getTenantId).sorted().toList();
        actuales.stream().filter(gt -> !nuevos.contains(gt.getTenantId())).forEach(grupoTenantRepository::delete);
        grupoTenantRepository.flush();
        try {
            nuevos.stream().filter(id -> !anteriores.contains(id))
                    .forEach(id -> grupoTenantRepository.save(new GrupoTenant(grupoId, id)));
            grupoTenantRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(409, "La empresa ya pertenece a otro grupo", "ids");
        }
        auditar(grupoId, "AsignacionEmpresas", "empresas=" + anteriores, "empresas=" + nuevos.stream().sorted().toList());
        return toResponse(grupo);
    }

    /** Reemplaza los operadores del grupo: 400 si alguna cuenta no existe o no es OperadorSaaS. */
    @Transactional
    public GrupoResponse fijarOperadores(String grupoId, List<String> usuarioIds) {
        GrupoEmpresas grupo = buscar(grupoId);
        Set<String> nuevos = limpiar(usuarioIds);
        for (String usuarioId : nuevos) {
            boolean operador = usuarioRepository.findById(usuarioId)
                    .filter(u -> OPERADOR_SAAS.equals(u.getRol()))
                    .isPresent();
            if (!operador) {
                throw new DomainException(400, "Solo se asignan cuentas OperadorSaaS", "ids");
            }
        }
        List<GrupoOperador> actuales = grupoOperadorRepository.findByGrupoId(grupoId);
        List<String> anteriores = actuales.stream().map(GrupoOperador::getUsuarioId).sorted().toList();
        actuales.stream().filter(go -> !nuevos.contains(go.getUsuarioId())).forEach(grupoOperadorRepository::delete);
        nuevos.stream().filter(id -> !anteriores.contains(id))
                .forEach(id -> grupoOperadorRepository.save(new GrupoOperador(grupoId, id)));
        grupoOperadorRepository.flush();
        auditar(grupoId, "AsignacionOperadores", "operadores=" + anteriores,
                "operadores=" + nuevos.stream().sorted().toList());
        return toResponse(grupo);
    }

    private GrupoEmpresas buscar(String grupoId) {
        return grupoRepository.findById(grupoId)
                .orElseThrow(() -> new DomainException(404, "Grupo no encontrado"));
    }

    private void exigirNombreLibre(String nombre) {
        if (grupoRepository.existsByNombre(nombre)) {
            throw new DomainException(409, "Ya existe un grupo con ese nombre", "nombre");
        }
    }

    private void guardar(GrupoEmpresas grupo) {
        try {
            grupoRepository.saveAndFlush(grupo);
        } catch (DataIntegrityViolationException e) {
            throw new DomainException(409, "Ya existe un grupo con ese nombre", "nombre");
        }
    }

    private static Set<String> limpiar(List<String> ids) {
        Set<String> limpios = new LinkedHashSet<>();
        ids.forEach(id -> limpios.add(id.trim()));
        return limpios;
    }

    private void auditar(String grupoId, String accion, String anterior, String nuevo) {
        auditLogService.registrar(null, alcancePlataformaService.actorId(tenantContext.current()), ENTIDAD, grupoId, accion, anterior, nuevo);
    }

    private GrupoResponse toResponse(GrupoEmpresas grupo) {
        List<String> tenants = grupoTenantRepository.findByGrupoId(grupo.getId()).stream()
                .map(GrupoTenant::getTenantId).sorted().toList();
        List<String> operadores = grupoOperadorRepository.findByGrupoId(grupo.getId()).stream()
                .map(GrupoOperador::getUsuarioId).sorted().toList();
        return new GrupoResponse(grupo.getId(), grupo.getNombre(), tenants, operadores);
    }
}
