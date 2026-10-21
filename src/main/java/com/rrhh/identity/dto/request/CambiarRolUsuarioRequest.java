package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CambiarRolUsuarioRequest(
        @NotBlank
        @Pattern(
                regexp = "ADMIN_RRHH|JEFATURA|TRABAJADOR|Admin de RRHH|Jefatura|Trabajador",
                message = "Rol no permitido"
        )
        String rol
) {
}
