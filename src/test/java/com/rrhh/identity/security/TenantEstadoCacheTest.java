package com.rrhh.identity.security;

import com.rrhh.identity.model.Tenant;
import com.rrhh.identity.model.TenantEstado;
import com.rrhh.identity.repository.TenantRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantEstadoCacheTest {

    private final TenantRepository repo = mock(TenantRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));
    private final TenantEstadoCache cache = new TenantEstadoCache(repo, clock);

    private static Optional<Tenant> con(String estado) {
        Tenant t = new Tenant();
        t.cambiarEstado(estado);
        return Optional.of(t);
    }

    @Test
    void dentroDe60SegundosUsaElEstadoGuardadoYDespuesReleeLaBase() {
        when(repo.findById("t")).thenReturn(con(TenantEstado.ACTIVO), con(TenantEstado.SUSPENDIDO));
        assertEquals(TenantEstado.ACTIVO, cache.estado("t"));
        clock.avanzar(59);
        assertEquals(TenantEstado.ACTIVO, cache.estado("t"));
        clock.avanzar(1);
        assertEquals(TenantEstado.SUSPENDIDO, cache.estado("t"));
        verify(repo, times(2)).findById("t");
    }

    @Test
    void invalidarObligaALeerLaBase() {
        when(repo.findById("t")).thenReturn(con(TenantEstado.ACTIVO), con(TenantEstado.SUSPENDIDO));
        cache.estado("t");
        cache.invalidar("t");
        assertEquals(TenantEstado.SUSPENDIDO, cache.estado("t"));
    }

    private static final class MutableClock extends Clock {
        private Instant ahora;

        MutableClock(Instant inicio) {
            this.ahora = inicio;
        }

        void avanzar(long segundos) {
            ahora = ahora.plusSeconds(segundos);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }
}
