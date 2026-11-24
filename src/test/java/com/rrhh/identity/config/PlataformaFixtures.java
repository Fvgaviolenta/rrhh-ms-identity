package com.rrhh.identity.config;

import com.rrhh.identity.model.GrupoEmpresas;
import com.rrhh.identity.model.GrupoOperador;
import com.rrhh.identity.model.GrupoTenant;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.model.Usuario;
import com.rrhh.identity.repository.GrupoEmpresasRepository;
import com.rrhh.identity.repository.GrupoOperadorRepository;
import com.rrhh.identity.repository.GrupoTenantRepository;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.repository.UsuarioRepository;

import java.time.Instant;
import java.util.UUID;

/**
 * Datos de prueba de la consola de plataforma: empresas, usuarios y grupos con identificadores
 * propios de cada prueba, porque todas las clases comparten la misma base H2.
 */
public final class PlataformaFixtures {

    private PlataformaFixtures() {
    }

    public static Tenant tenant(TenantRepository repo, String nombre, String estado) {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Tenant t = new Tenant();
        t.setId(UUID.randomUUID().toString());
        t.setNombreEmpresa(nombre + " " + sufijo);
        t.setSlug("fx-" + sufijo);
        t.setRutEmpresa("FX-" + sufijo);
        t.setCreadoEn(Instant.now());
        t.cambiarEstado(estado);
        return repo.save(t);
    }

    public static Tenant tenant(TenantRepository repo, String nombre) {
        return tenant(repo, nombre, TenantEstado.ACTIVO);
    }

    public static Usuario usuario(UsuarioRepository repo, String tenantId, String rol, String estado) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID().toString());
        u.setTenantId(tenantId);
        u.setEmail("fx-" + u.getId().substring(0, 8) + "@rrhh.local");
        u.setNombre("Persona " + u.getId().substring(0, 8));
        u.setRol(rol);
        u.setEstado(estado);
        u.setActivo("ACTIVO".equals(estado));
        u.setCreadoEn(Instant.now());
        return repo.saveAndFlush(u);
    }

    /** La fila del token "operador-grupo", creada una sola vez para toda la corrida. */
    public static void operadorGrupo(UsuarioRepository repo) {
        if (repo.existsById(TestJwtConfig.OPERADOR_GRUPO_ID)) {
            return;
        }
        Usuario u = new Usuario();
        u.setId(TestJwtConfig.OPERADOR_GRUPO_ID);
        u.setEmail("operador.grupo@rrhh.local");
        u.setCognitoSub("cognito-operador-grupo");
        u.setNombre("Operador Grupo");
        u.setRol("OperadorSaaS");
        u.setEstado("ACTIVO");
        u.setCreadoEn(Instant.now());
        repo.saveAndFlush(u);
    }

    /** Grupo nuevo con esas empresas y el operador de "operador-grupo" asignado; devuelve su nombre. */
    public static String grupoConOperador(GrupoEmpresasRepository grupos, GrupoTenantRepository grupoTenants,
                                          GrupoOperadorRepository grupoOperadores, UsuarioRepository usuarios,
                                          String... tenantIds) {
        operadorGrupo(usuarios);
        GrupoEmpresas grupo = new GrupoEmpresas();
        grupo.setId(UUID.randomUUID().toString());
        grupo.setNombre("Grupo " + UUID.randomUUID().toString().substring(0, 8));
        grupo.setCreadoEn(Instant.now());
        grupos.save(grupo);
        for (String tenantId : tenantIds) {
            grupoTenants.save(new GrupoTenant(grupo.getId(), tenantId));
        }
        grupoOperadores.save(new GrupoOperador(grupo.getId(), TestJwtConfig.OPERADOR_GRUPO_ID));
        return grupo.getNombre();
    }
}
