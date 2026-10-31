package com.rrhh.identity.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class TenantSlug {

    public static final String PLATAFORMA = "plataforma";
    /** La columna slug admite 80; se deja espacio para el sufijo. */
    private static final int MAX_BASE = 70;

    private TenantSlug() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        String withoutMarks = decomposed.replaceAll("\\p{M}+", "");
        String slugged = withoutMarks.replaceAll("[^a-z0-9]+", "-");
        return slugged.replaceAll("^-+", "").replaceAll("-+$", "");
    }

    /**
     * Slug único para una empresa nueva (S1): {@code normalize(nombre)}, {@code empresa} si queda vacío,
     * y un sufijo {@code -2}, {@code -3}... si es reservado de plataforma o ya existe.
     */
    public static String unico(String nombre, Predicate<String> existe) {
        String base = normalize(nombre);
        if (base.length() > MAX_BASE) {
            base = base.substring(0, MAX_BASE).replaceAll("-+$", "");
        }
        if (base.isEmpty()) {
            base = "empresa";
        }
        String candidato = base;
        int sufijo = 2;
        while (isPlataforma(candidato) || existe.test(candidato)) {
            candidato = base + "-" + sufijo++;
        }
        return candidato;
    }

    public static boolean isPlataforma(String slugOrName) {
        String normalized = normalize(slugOrName);
        return PLATAFORMA.equals(normalized) || "plataforma-rrhh-saas".equals(normalized)
                || "rrhh-saas".equals(normalized);
    }
}
