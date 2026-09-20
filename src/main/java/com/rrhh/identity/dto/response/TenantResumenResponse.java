package com.rrhh.identity.dto.response;

public record TenantResumenResponse(
        String id,
        String nombreEmpresa,
        String slug,
        boolean activo
) {
}
