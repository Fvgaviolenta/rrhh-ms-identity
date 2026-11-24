package com.rrhh.identity.controller;

import com.rrhh.identity.dto.ApiResponse;
import com.rrhh.identity.dto.request.CambiarRolUsuarioRequest;
import com.rrhh.identity.dto.request.CrearUsuarioPlataformaRequest;
import com.rrhh.identity.dto.request.MensajePlataformaRequest;
import com.rrhh.identity.dto.request.RechazarEmpresaRequest;
import com.rrhh.identity.dto.response.TenantResumenResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.service.MensajePlataformaService;
import com.rrhh.identity.service.TenantService;
import com.rrhh.identity.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/plataforma")
public class PlataformaController {

    private final TenantService tenantService;
    private final UsuarioService usuarioService;
    private final MensajePlataformaService mensajePlataformaService;

    public PlataformaController(TenantService tenantService, UsuarioService usuarioService,
                                MensajePlataformaService mensajePlataformaService) {
        this.tenantService = tenantService;
        this.usuarioService = usuarioService;
        this.mensajePlataformaService = mensajePlataformaService;
    }

    @GetMapping("/tenants")
    @PreAuthorize("hasAnyRole('OPERADOR_SAAS','ADMIN_PLATAFORMA')")
    public ApiResponse<List<TenantResumenResponse>> tenants(@RequestParam(value = "estado", required = false) String estado) {
        return ApiResponse.ok(tenantService.listar(estado), "Empresas cliente");
    }

    @PostMapping("/tenants/{tenant_id}/activar")
    @PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
    public ApiResponse<TenantResumenResponse> activar(@PathVariable("tenant_id") String tenantId) {
        return ApiResponse.ok(tenantService.activar(tenantId), "Empresa activada");
    }

    @PostMapping("/tenants/{tenant_id}/rechazar")
    @PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
    public ApiResponse<TenantResumenResponse> rechazar(
            @PathVariable("tenant_id") String tenantId,
            @Valid @RequestBody RechazarEmpresaRequest request
    ) {
        return ApiResponse.ok(tenantService.rechazar(tenantId, request.motivo()), "Empresa rechazada");
    }

    @PostMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> crearUsuario(@Valid @RequestBody CrearUsuarioPlataformaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(usuarioService.crearUsuarioPlataforma(request), "Cuenta de plataforma creada"));
    }

    @GetMapping("/tenants/{tenant_id}/usuarios")
    @PreAuthorize("hasAnyRole('OPERADOR_SAAS','ADMIN_PLATAFORMA')")
    public ApiResponse<List<UsuarioResponse>> usuariosPorTenant(@PathVariable("tenant_id") String tenantId) {
        return ApiResponse.ok(usuarioService.listarPorTenantPlataforma(tenantId), "Usuarios del tenant solicitado");
    }

    @PatchMapping("/tenants/{tenant_id}/usuarios/{usuario_id}/rol")
    @PreAuthorize("hasAnyRole('OPERADOR_SAAS','ADMIN_PLATAFORMA')")
    public ApiResponse<UsuarioResponse> cambiarRol(
            @PathVariable("tenant_id") String tenantId,
            @PathVariable("usuario_id") String usuarioId,
            @Valid @RequestBody CambiarRolUsuarioRequest request
    ) {
        return ApiResponse.ok(usuarioService.cambiarRolDesdePlataforma(tenantId, usuarioId, request.rol()), "Rol actualizado");
    }

    @PostMapping("/tenants/{tenant_id}/suspender")
    @PreAuthorize("hasAnyRole('OPERADOR_SAAS','ADMIN_PLATAFORMA')")
    public ApiResponse<TenantResumenResponse> suspender(
            @PathVariable("tenant_id") String tenantId,
            @Valid @RequestBody RechazarEmpresaRequest request
    ) {
        return ApiResponse.ok(tenantService.suspender(tenantId, request.motivo()), "Empresa suspendida");
    }

    @PostMapping("/tenants/{tenant_id}/reactivar")
    @PreAuthorize("hasAnyRole('OPERADOR_SAAS','ADMIN_PLATAFORMA')")
    public ApiResponse<TenantResumenResponse> reactivar(@PathVariable("tenant_id") String tenantId) {
        return ApiResponse.ok(tenantService.reactivar(tenantId), "Empresa reactivada");
    }

    @PostMapping("/tenants/{tenant_id}/mensajes")
    @PreAuthorize("hasAnyRole('OPERADOR_SAAS','ADMIN_PLATAFORMA')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> enviarMensaje(
            @PathVariable("tenant_id") String tenantId,
            @Valid @RequestBody MensajePlataformaRequest request
    ) {
        int destinatarios = mensajePlataformaService.enviar(tenantId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(202, "Mensaje enviado", Map.of("destinatarios", destinatarios), List.of()));
    }
}
