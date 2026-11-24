package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MensajePlataformaRequest(
        @NotBlank(message = "El asunto es obligatorio")
        @Size(max = 100)
        @Pattern(regexp = "[^\\r\\n]+", message = "El asunto no puede tener saltos de línea")
        String asunto,
        @NotBlank(message = "El cuerpo es obligatorio")
        @Size(max = 4000)
        String cuerpo
) {
}
