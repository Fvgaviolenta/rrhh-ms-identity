package com.rrhh.identity.service;

import com.rrhh.identity.security.TenantContext.AuthenticatedUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPlataformaBootstrapTest {

    private static AuthenticatedUser u(String email, boolean google) {
        return new AuthenticatedUser(null, null, email, null, null, "s", null, "google_1", google);
    }

    @Test
    void vacioNoPromueve() {
        assertFalse(new AdminPlataformaBootstrap("").corresponde(u("", true)));
        assertFalse(new AdminPlataformaBootstrap("  ").corresponde(u("owner@gmail.com", true)));
        assertFalse(new AdminPlataformaBootstrap(null).corresponde(u("owner@gmail.com", true)));
    }

    @Test
    void soloGoogleYMismoCorreo() {
        AdminPlataformaBootstrap b = new AdminPlataformaBootstrap("Owner@Gmail.com");
        assertTrue(b.corresponde(u("owner@gmail.com", true)));
        assertFalse(b.corresponde(u("owner@gmail.com", false)));
        assertFalse(b.corresponde(u("otro@gmail.com", true)));
        assertFalse(b.corresponde(u(null, true)));
    }
}
