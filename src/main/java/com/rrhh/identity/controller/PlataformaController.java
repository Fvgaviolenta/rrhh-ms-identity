package com.rrhh.identity.controller;

import com.rrhh.identity.dto.ApiResponse;
import com.rrhh.identity.dto.response.TenantResumenResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.service.TenantService;
import com.rrhh.identity.service.UsuarioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plataforma")
public class PlataformaController {

    private final TenantService tenantService;
    private final UsuarioService usuarioService;

    public PlataformaController(TenantService tenantService, UsuarioService usuarioService) {
        this.tenantService = tenantService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasRole('OPERADOR_SAAS')")
    public ApiResponse<List<TenantResumenResponse>> tenants() {
        return ApiResponse.ok(tenantService.listarClientes(), "Empresas cliente");
    }

    @GetMapping("/tenants/{tenant_id}/usuarios")
    @PreAuthorize("hasRole('OPERADOR_SAAS')")
    public ApiResponse<List<UsuarioResponse>> usuariosPorTenant(@PathVariable("tenant_id") String tenantId) {
        return ApiResponse.ok(usuarioService.listarPorTenantPlataforma(tenantId), "Usuarios del tenant solicitado");
    }
}
