package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuenta creada por el AdminPlataforma (S6): OperadorSaaS sin tenant o SuperAdmin de un tenant ACTIVO. */
public record CrearUsuarioPlataformaRequest(
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") @Size(max = 255) String email,
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 255) String nombre,
        @NotBlank(message = "El rol es obligatorio") String rol,
        String tenantId
) {
}
