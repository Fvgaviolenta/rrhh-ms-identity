package com.rrhh.identity.dto.response;

public record TenantResumenResponse(
        String id,
        String nombreEmpresa,
        String slug,
        boolean activo,
        String estado,
        String rutEmpresa,
        String razonSocial,
        String registradoPorEmail,
        String cognitoError,
        /** Nombre del grupo de empresas (ADR-020), o null si no está en ninguno. */
        String grupo
) {
}
