package com.rrhh.identity.dto.response;

import java.util.List;

public record GrupoResponse(
        String id,
        String nombre,
        List<String> tenantIds,
        List<String> operadorIds
) {
}
