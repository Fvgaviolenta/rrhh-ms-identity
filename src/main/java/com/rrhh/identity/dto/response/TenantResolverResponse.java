package com.rrhh.identity.dto.response;

public record TenantResolverResponse(
        boolean existe,
        String slug,
        String nombreVisible
) {
}
