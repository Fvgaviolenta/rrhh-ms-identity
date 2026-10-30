package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos tributarios con los que una persona registra su empresa (S1, ADR-017). */
public record RegistrarEmpresaRequest(
        @NotBlank(message = "La razón social es obligatoria") @Size(max = 255) String razonSocial,
        @NotBlank(message = "El nombre de fantasía es obligatorio") @Size(max = 255) String nombreFantasia,
        @NotBlank(message = "El RUT de la empresa es obligatorio") @Size(max = 20) String rutEmpresa,
        @NotBlank(message = "El giro es obligatorio") @Size(max = 255) String giro,
        @Size(max = 10) String codigoActividad,
        @NotBlank(message = "La calle es obligatoria") @Size(max = 255) String direccionCalle,
        @NotBlank(message = "El número es obligatorio") @Size(max = 20) String direccionNumero,
        @NotBlank(message = "La comuna es obligatoria") @Size(max = 100) String comuna,
        @NotBlank(message = "La región es obligatoria") @Size(max = 100) String region,
        @NotBlank(message = "El correo de contacto es obligatorio") @Email(message = "El correo de contacto no es válido")
        @Size(max = 255) String emailContacto,
        @Size(max = 30) String telefonoContacto,
        @NotBlank(message = "El nombre del representante es obligatorio") @Size(max = 255) String representanteNombre,
        @NotBlank(message = "El RUT del representante es obligatorio") @Size(max = 20) String representanteRut,
        @NotNull(message = "Debes aceptar los términos")
        @AssertTrue(message = "Debes aceptar los términos") Boolean aceptaTerminos
) {
}
