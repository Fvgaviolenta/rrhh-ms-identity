package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GrupoRequest(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 80) String nombre
) {
}
