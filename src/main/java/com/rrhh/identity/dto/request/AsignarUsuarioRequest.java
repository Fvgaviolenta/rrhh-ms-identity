package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AsignarUsuarioRequest(
        @NotBlank
        @Pattern(
                regexp = "SuperAdmin|Admin de RRHH|Jefatura|Trabajador",
                message = "Rol no oficial"
        )
        String rol,
        String trabajadorId
) {
}
