package com.rrhh.identity.controller;

import com.rrhh.identity.dto.ApiResponse;
import com.rrhh.identity.dto.response.TenantResolverResponse;
import com.rrhh.identity.service.TenantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantResolverController {

    private final TenantService tenantService;

    public TenantResolverController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/resolver")
    public ApiResponse<TenantResolverResponse> resolver(@RequestParam("nombre") String nombre) {
        return ApiResponse.ok(tenantService.resolver(nombre), "Empresa encontrada");
    }
}
