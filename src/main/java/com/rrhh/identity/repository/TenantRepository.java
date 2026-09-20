package com.rrhh.identity.repository;

import com.rrhh.identity.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByNombreEmpresaIgnoreCase(String nombreEmpresa);

    List<Tenant> findByActivoTrueAndSlugNotOrderByNombreEmpresaAsc(String slug);
}
