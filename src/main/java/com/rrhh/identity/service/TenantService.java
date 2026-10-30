package com.rrhh.identity.service;

import com.rrhh.identity.dto.response.TenantResolverResponse;
import com.rrhh.identity.dto.response.TenantResumenResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.repository.TenantRepository;
import com.rrhh.identity.util.RutEmpresa;
import com.rrhh.identity.util.TenantSlug;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TenantService {

    static final String RUT_INVALIDO = "El RUT de la empresa no es válido";
    static final String EMPRESA_NO_REGISTRADA = "La empresa no está registrada en el sistema";

    private final TenantRepository tenantRepository;
    private final long delayMs;

    public TenantService(
            TenantRepository tenantRepository,
            @Value("${rrhh.tenant-resolver.delay-ms:150}") long delayMs
    ) {
        this.tenantRepository = tenantRepository;
        this.delayMs = delayMs;
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

    public List<TenantResumenResponse> listarClientes() {
        return tenantRepository.findByActivoTrueAndSlugNotOrderByNombreEmpresaAsc(TenantSlug.PLATAFORMA)
                .stream()
                .map(t -> new TenantResumenResponse(t.getId(), t.getNombreEmpresa(), t.getSlug(), t.isActivo()))
                .toList();
    }
}
