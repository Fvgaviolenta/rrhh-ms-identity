package com.rrhh.identity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RolesTest {

    @Test
    void mapeaRolesOficialesAAuthorities() {
        assertEquals(Roles.SUPERADMIN, Roles.authorityFromClaim("SuperAdmin"));
        assertEquals(Roles.ADMIN_RRHH, Roles.authorityFromClaim("Admin de RRHH"));
        assertEquals(Roles.JEFATURA, Roles.authorityFromClaim("Jefatura"));
        assertEquals(Roles.TRABAJADOR, Roles.authorityFromClaim("Trabajador"));
    }

    @Test
    void sinRolOVacioEsSinRol() {
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim(null));
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim(""));
        assertEquals(Roles.SIN_ROL, Roles.authorityFromClaim("Desconocido"));
        assertEquals(Roles.ADMIN_PLATAFORMA, Roles.authorityFromClaim("AdminPlataforma"));
        assertNull(Roles.claimFromAuthority(Roles.SIN_ROL));
    }
}
