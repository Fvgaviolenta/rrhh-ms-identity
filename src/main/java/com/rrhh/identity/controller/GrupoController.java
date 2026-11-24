package com.rrhh.identity.controller;

import com.rrhh.identity.dto.ApiResponse;
import com.rrhh.identity.dto.request.GrupoRequest;
import com.rrhh.identity.dto.request.IdsRequest;
import com.rrhh.identity.dto.response.GrupoResponse;
import com.rrhh.identity.service.GrupoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Grupos de empresas de la consola de plataforma (ADR-020, S2): solo el AdminPlataforma. */
@RestController
@RequestMapping("/api/v1/plataforma/grupos")
@PreAuthorize("hasRole('ADMIN_PLATAFORMA')")
public class GrupoController {

    private final GrupoService grupoService;

    public GrupoController(GrupoService grupoService) {
        this.grupoService = grupoService;
    }

    @GetMapping
    public ApiResponse<List<GrupoResponse>> listar() {
        return ApiResponse.ok(grupoService.listar(), "Grupos de empresas");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GrupoResponse>> crear(@Valid @RequestBody GrupoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(grupoService.crear(request.nombre()), "Grupo creado"));
    }

    @PatchMapping("/{grupo_id}")
    public ApiResponse<GrupoResponse> renombrar(@PathVariable("grupo_id") String grupoId,
                                                @Valid @RequestBody GrupoRequest request) {
        return ApiResponse.ok(grupoService.renombrar(grupoId, request.nombre()), "Grupo actualizado");
    }

    @DeleteMapping("/{grupo_id}")
    public ResponseEntity<Void> borrar(@PathVariable("grupo_id") String grupoId) {
        grupoService.borrar(grupoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{grupo_id}/empresas")
    public ApiResponse<GrupoResponse> empresas(@PathVariable("grupo_id") String grupoId,
                                               @Valid @RequestBody IdsRequest request) {
        return ApiResponse.ok(grupoService.fijarEmpresas(grupoId, request.ids()), "Empresas del grupo actualizadas");
    }

    @PutMapping("/{grupo_id}/operadores")
    public ApiResponse<GrupoResponse> operadores(@PathVariable("grupo_id") String grupoId,
                                                 @Valid @RequestBody IdsRequest request) {
        return ApiResponse.ok(grupoService.fijarOperadores(grupoId, request.ids()), "Operadores del grupo actualizados");
    }
}
