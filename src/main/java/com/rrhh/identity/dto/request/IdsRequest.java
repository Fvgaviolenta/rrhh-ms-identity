package com.rrhh.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Conjunto completo que reemplaza al actual (empresas u operadores de un grupo); vacío lo deja sin ninguno. */
public record IdsRequest(
        @NotNull(message = "La lista es obligatoria") List<@NotBlank String> ids
) {
}
