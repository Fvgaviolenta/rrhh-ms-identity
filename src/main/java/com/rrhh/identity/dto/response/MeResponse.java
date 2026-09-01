package com.rrhh.identity.dto.response;

public record MeResponse(
        String userId,
        String tenantId,
        String email,
        String nombre,
        String role,
        String trabajadorId,
        String cognitoSub,
        String estado,
        String codigo,
        boolean pendiente
) {
}
