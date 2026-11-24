package com.rrhh.identity.security;

import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.repository.TenantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de cada empresa para {@link TenantSuspendidoFilter}, con expiración de 60 segundos (S6):
 * una suspensión hecha en otra instancia se ve, como mucho, un minuto después.
 */
@Component
public class TenantEstadoCache {

    static final Duration TTL = Duration.ofSeconds(60);

    private final TenantRepository tenantRepository;
    private final Clock clock;
    private final Map<String, Entrada> entradas = new ConcurrentHashMap<>();

    @Autowired
    public TenantEstadoCache(TenantRepository tenantRepository) {
        this(tenantRepository, Clock.systemUTC());
    }

    TenantEstadoCache(TenantRepository tenantRepository, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.clock = clock;
    }

    /** Estado guardado de la empresa, o null si no existe. */
    public String estado(String tenantId) {
        Instant ahora = clock.instant();
        Entrada entrada = entradas.get(tenantId);
        if (entrada != null && ahora.isBefore(entrada.expira())) {
            return entrada.estado();
        }
        String estado = tenantRepository.findById(tenantId).map(Tenant::getEstado).orElse(null);
        entradas.put(tenantId, new Entrada(estado, ahora.plus(TTL)));
        return estado;
    }

    /** Olvida el estado de una empresa: la siguiente consulta lo lee de la base. */
    public void invalidar(String tenantId) {
        entradas.remove(tenantId);
    }

    private record Entrada(String estado, Instant expira) {
    }
}
