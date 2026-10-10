package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SolicitarAccesoRequest(
        @NotBlank(message = "El tenant es obligatorio")
        String tenantSlug
) {
}
