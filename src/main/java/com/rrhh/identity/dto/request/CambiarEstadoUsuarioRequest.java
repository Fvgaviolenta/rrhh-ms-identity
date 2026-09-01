package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotNull;

public record CambiarEstadoUsuarioRequest(
        @NotNull Boolean activo
) {
}
