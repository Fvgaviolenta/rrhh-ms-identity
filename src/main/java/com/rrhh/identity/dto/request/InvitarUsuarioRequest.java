package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InvitarUsuarioRequest(
        @NotBlank @Email String email,
        String trabajadorId,
        String nombre
) {
}
