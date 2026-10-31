package com.rrhh.identity.security;

public final class Roles {

    public static final String ADMIN_PLATAFORMA = "ROLE_ADMIN_PLATAFORMA";
    public static final String OPERADOR_SAAS = "ROLE_OPERADOR_SAAS";
    public static final String SUPERADMIN = "ROLE_SUPERADMIN";
    public static final String ADMIN_RRHH = "ROLE_ADMIN_RRHH";
    public static final String JEFATURA = "ROLE_JEFATURA";
    public static final String TRABAJADOR = "ROLE_TRABAJADOR";
    /** Token sin rol, o con uno desconocido: no recibe ningún permiso de rol (ADR-017). */
    public static final String SIN_ROL = "ROLE_SIN_ROL";

    private Roles() {
    }

    public static String authorityFromClaim(String roleClaim) {
        if (roleClaim == null || roleClaim.isBlank()) {
            return SIN_ROL;
        }
        return switch (roleClaim.trim()) {
            case "AdminPlataforma", "ROLE_ADMIN_PLATAFORMA" -> ADMIN_PLATAFORMA;
            case "OperadorSaaS", "ROLE_OPERADOR_SAAS" -> OPERADOR_SAAS;
            case "SuperAdmin", "SUPERADMIN", "ROLE_SUPERADMIN" -> SUPERADMIN;
            case "Admin de RRHH", "ADMIN_RRHH", "ROLE_ADMIN_RRHH" -> ADMIN_RRHH;
            case "Jefatura", "JEFATURA", "ROLE_JEFATURA" -> JEFATURA;
            case "Trabajador", "TRABAJADOR", "ROLE_TRABAJADOR" -> TRABAJADOR;
            default -> SIN_ROL;
        };
    }

    public static String claimFromAuthority(String authority) {
        if (authority == null) {
            return null;
        }
        return switch (authority) {
            case ADMIN_PLATAFORMA -> "AdminPlataforma";
            case OPERADOR_SAAS -> "OperadorSaaS";
            case SUPERADMIN -> "SuperAdmin";
            case ADMIN_RRHH -> "Admin de RRHH";
            case JEFATURA -> "Jefatura";
            case TRABAJADOR -> "Trabajador";
            default -> null;
        };
    }
}
