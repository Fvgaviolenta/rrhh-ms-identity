package com.rrhh.identity.controller;

import com.rrhh.identity.dto.ApiResponse;
import com.rrhh.identity.dto.request.AsignarUsuarioRequest;
import com.rrhh.identity.dto.request.CambiarEstadoUsuarioRequest;
import com.rrhh.identity.dto.request.CrearUsuarioRequest;
import com.rrhh.identity.dto.request.InvitarUsuarioRequest;
import com.rrhh.identity.dto.response.MeResponse;
import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.security.Roles;
import com.rrhh.identity.security.TenantContext;
import com.rrhh.identity.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final TenantContext tenantContext;

    public UsuarioController(UsuarioService usuarioService, TenantContext tenantContext) {
        this.usuarioService = usuarioService;
        this.tenantContext = tenantContext;
    }

    @GetMapping("/auth/me")
    public ApiResponse<MeResponse> me(
            @RequestHeader(value = "X-Empresa-Slug", required = false) String empresaSlug
    ) {
        return ApiResponse.ok(usuarioService.me(empresaSlug), "Identidad actual");
    }

    @PostMapping("/usuarios/invitar")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> invitar(@Valid @RequestBody InvitarUsuarioRequest request) {
        validarTenantPresente();
        UsuarioResponse creado = usuarioService.invitar(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(creado, "Trabajador invitado"));
    }

    @GetMapping("/usuarios")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<List<UsuarioResponse>> listar() {
        return ApiResponse.ok(usuarioService.listar(), "Usuarios del tenant");
    }

    @GetMapping("/usuarios/pendientes")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<List<UsuarioResponse>> pendientes() {
        return ApiResponse.ok(usuarioService.listarPendientes(), "Usuarios pendientes de asignacion");
    }

    @PostMapping("/usuarios/{usuario_id}/asignar")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH','OPERADOR_SAAS')")
    public ApiResponse<UsuarioResponse> asignar(
            @PathVariable("usuario_id") String usuarioId,
            @Valid @RequestBody AsignarUsuarioRequest request
    ) {
        return ApiResponse.ok(usuarioService.asignar(usuarioId, request), "Usuario asignado al tenant");
    }

    @GetMapping("/usuarios/{usuario_id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<UsuarioResponse> obtener(@PathVariable("usuario_id") String usuarioId) {
        return ApiResponse.ok(usuarioService.obtener(usuarioId), "Usuario encontrado");
    }

    @PostMapping("/usuarios")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ResponseEntity<ApiResponse<UsuarioResponse>> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        validarTenantPresente();
        UsuarioResponse creado = usuarioService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(creado, "Usuario registrado en el tenant"));
    }

    @PatchMapping("/usuarios/{usuario_id}/estado")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN_RRHH')")
    public ApiResponse<UsuarioResponse> cambiarEstado(
            @PathVariable("usuario_id") String usuarioId,
            @Valid @RequestBody CambiarEstadoUsuarioRequest request
    ) {
        return ApiResponse.ok(usuarioService.cambiarEstado(usuarioId, request), "Estado actualizado");
    }

    private void validarTenantPresente() {
        if (tenantContext.require().tenantId() == null) {
            throw new DomainException(401, "El token no incluye tenant_id");
        }
        Roles.authorityFromClaim(tenantContext.require().role());
    }
}
