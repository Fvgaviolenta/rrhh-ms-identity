package com.rrhh.identity.model;

/**
 * Estados de un tenant (ADR-017). {@code estado} es la fuente de verdad; {@link Tenant#isActivo()}
 * se mantiene igual a {@code estado == ACTIVO} vía {@link Tenant#cambiarEstado(String)}.
 */
public final class TenantEstado {

    public static final String PENDIENTE_ACTIVACION = "PENDIENTE_ACTIVACION";
    public static final String ACTIVO = "ACTIVO";
    public static final String RECHAZADO = "RECHAZADO";
    public static final String SUSPENDIDO = "SUSPENDIDO";

    private TenantEstado() {
    }
}
