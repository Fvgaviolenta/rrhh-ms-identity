package com.rrhh.identity.repository;

import com.rrhh.identity.model.GrupoTenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GrupoTenantRepository extends JpaRepository<GrupoTenant, GrupoTenant.Pk> {

    Optional<GrupoTenant> findByTenantId(String tenantId);

    List<GrupoTenant> findByGrupoId(String grupoId);

    List<GrupoTenant> findByGrupoIdIn(Collection<String> grupoIds);

    void deleteByGrupoId(String grupoId);
}
