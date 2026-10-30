package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarEmpresaRequest(
        @NotBlank(message = "El motivo es obligatorio") @Size(max = 500) String motivo
) {
}
