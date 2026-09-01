package com.rrhh.identity.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RolesTest {

    @Test
    void mapeaRolesOficialesAAuthorities() {
        assertEquals(Roles.SUPERADMIN, Roles.authorityFromClaim("SuperAdmin"));
        assertEquals(Roles.ADMIN_RRHH, Roles.authorityFromClaim("Admin de RRHH"));
        assertEquals(Roles.JEFATURA, Roles.authorityFromClaim("Jefatura"));
        assertEquals(Roles.TRABAJADOR, Roles.authorityFromClaim("Trabajador"));
    }
}
