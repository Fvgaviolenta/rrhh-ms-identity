package com.rrhh.identity.dto.response;

public record UsuarioResponse(
        String id,
        String codigo,
        String tenantId,
        String email,
        String nombre,
        String rol,
        String trabajadorId,
        String cognitoSub,
        String estado,
        boolean activo
) {
}
