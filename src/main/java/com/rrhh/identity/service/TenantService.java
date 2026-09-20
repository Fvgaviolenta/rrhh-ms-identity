package com.rrhh.identity.service;

import com.rrhh.identity.dto.response.TenantResolverResponse;
import com.rrhh.identity.dto.response.TenantResumenResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.util.TenantSlug;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final long delayMs;

    public TenantService(
            TenantRepository tenantRepository,
            @Value("${rrhh.tenant-resolver.delay-ms:150}") long delayMs
    ) {
        this.tenantRepository = tenantRepository;
        this.delayMs = delayMs;
    }

    public TenantResolverResponse resolver(String nombre) {
        retardarEnumeracion();
        if (nombre == null || nombre.isBlank()) {
            throw new DomainException(400, "El nombre de la empresa es obligatorio", "nombre");
        }
        Tenant tenant = buscarPorNombreOSlug(nombre)
                .orElseThrow(() -> new DomainException(
                        404,
                        "La empresa no se encuentra en nuestra base de datos",
                        "nombre"
                ));
        if (!tenant.isActivo()) {
            throw new DomainException(404, "La empresa no se encuentra en nuestra base de datos", "nombre");
        }
        return new TenantResolverResponse(true, tenant.getSlug(), tenant.getNombreEmpresa());
    }

    public Optional<Tenant> buscarPorNombreOSlug(String nombre) {
        String normalized = TenantSlug.normalize(nombre);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        Optional<Tenant> bySlug = tenantRepository.findBySlug(normalized);
        if (bySlug.isPresent()) {
            return bySlug;
        }
        Optional<Tenant> byNombre = tenantRepository.findByNombreEmpresaIgnoreCase(nombre.trim());
        if (byNombre.isPresent()) {
            return byNombre;
        }
        return tenantRepository.findAll().stream()
                .filter(t -> normalized.equals(TenantSlug.normalize(t.getNombreEmpresa())))
                .findFirst();
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

    public List<TenantResumenResponse> listarClientes() {
        return tenantRepository.findByActivoTrueAndSlugNotOrderByNombreEmpresaAsc(TenantSlug.PLATAFORMA)
                .stream()
                .map(t -> new TenantResumenResponse(t.getId(), t.getNombreEmpresa(), t.getSlug(), t.isActivo()))
                .toList();
    }
}
