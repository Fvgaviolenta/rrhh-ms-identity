package com.rrhh.identity.service;

import com.rrhh.identity.security.TenantContext.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Define quién es el único AdminPlataforma (ADR-017, S6): el correo de {@code RRHH_PLATFORM_ADMIN_EMAIL},
 * solo cuando entra con Google. Sin la variable (o en blanco) nadie es promovido.
 */
@Component
public class AdminPlataformaBootstrap {

    private final String email;

    public AdminPlataformaBootstrap(@Value("${rrhh.platform.admin-email:}") String email) {
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public boolean corresponde(AuthenticatedUser actor) {
        if (email.isEmpty() || actor == null || actor.email() == null || !actor.federadoGoogle()) {
            return false;
        }
        return email.equals(actor.email().trim().toLowerCase(Locale.ROOT));
    }
}
