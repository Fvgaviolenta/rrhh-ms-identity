package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SolicitarAccesoRequest(
        @NotBlank(message = "El RUT de la empresa es obligatorio")
        String rutEmpresa
) {
}
