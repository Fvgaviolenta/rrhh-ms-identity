package com.rrhh.identity.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TenantSlug {

    public static final String PLATAFORMA = "plataforma";

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

    public static boolean isPlataforma(String slugOrName) {
        String normalized = normalize(slugOrName);
        return PLATAFORMA.equals(normalized) || "plataforma-rrhh-saas".equals(normalized)
                || "rrhh-saas".equals(normalized);
    }
}
