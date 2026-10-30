package com.rrhh.identity.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantSlugTest {

    @Test
    void normalizaNombreConTildesYEspacios() {
        assertEquals("empresa-demo-spa", TenantSlug.normalize("Empresa Demo SpA"));
        assertEquals("andina", TenantSlug.normalize("  Andina  "));
    }

    @Test
    void reconoceSlugReservadoPlataforma() {
        assertTrue(TenantSlug.isPlataforma("plataforma"));
        assertTrue(TenantSlug.isPlataforma("Plataforma RRHH SaaS"));
    }

    @Test
    void unicoEvitaVacioYReservado() {
        assertEquals("empresa", TenantSlug.unico("!!!", s -> false));
        assertEquals("plataforma-2", TenantSlug.unico("Plataforma", s -> false));
        assertEquals("acme-3", TenantSlug.unico("ACME", s -> s.equals("acme") || s.equals("acme-2")));
    }
}
