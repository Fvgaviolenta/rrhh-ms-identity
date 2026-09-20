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
}
