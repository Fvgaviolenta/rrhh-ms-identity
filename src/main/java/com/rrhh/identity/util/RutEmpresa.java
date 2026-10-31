package com.rrhh.identity.util;

import java.util.Locale;
import java.util.Optional;

/**
 * RUT chileno de una empresa: acepta {@code 76.086.428-5}, {@code 76086428-5} y {@code 760864285},
 * valida el dígito verificador por módulo 11 y devuelve la forma normalizada {@code 76086428-5}
 * (sin puntos, con guion y {@code K} en mayúscula). ADR-017.
 */
public final class RutEmpresa {

    private RutEmpresa() {
    }

    public static Optional<String> normalizar(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String limpio = raw.replace(".", "").replace("-", "").replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (limpio.length() < 2) {
            return Optional.empty();
        }
        String cuerpo = limpio.substring(0, limpio.length() - 1);
        char dv = limpio.charAt(limpio.length() - 1);
        if (cuerpo.length() > 8 || !cuerpo.matches("\\d+")) {
            return Optional.empty();
        }
        if (dv != digitoVerificador(cuerpo)) {
            return Optional.empty();
        }
        return Optional.of(cuerpo + "-" + dv);
    }

    private static char digitoVerificador(String cuerpo) {
        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += (cuerpo.charAt(i) - '0') * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }
        int resto = 11 - (suma % 11);
        if (resto == 11) {
            return '0';
        }
        if (resto == 10) {
            return 'K';
        }
        return (char) ('0' + resto);
    }
}
