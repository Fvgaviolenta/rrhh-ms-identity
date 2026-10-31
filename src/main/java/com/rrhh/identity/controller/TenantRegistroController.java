package com.rrhh.identity.controller;

import com.rrhh.identity.dto.ApiResponse;
import com.rrhh.identity.dto.request.RegistrarEmpresaRequest;
import com.rrhh.identity.dto.response.TenantResumenResponse;
import com.rrhh.identity.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registro de una empresa por una persona autenticada sin empresa (S1). Exige JWT. */
@RestController
@RequestMapping("/api/v1/tenants")
public class TenantRegistroController {

    private final TenantService tenantService;

    public TenantRegistroController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantResumenResponse>> registrar(@Valid @RequestBody RegistrarEmpresaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(tenantService.registrar(request), "Tu empresa está en revisión"));
    }
}
