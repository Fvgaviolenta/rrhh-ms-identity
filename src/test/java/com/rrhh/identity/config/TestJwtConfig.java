package com.rrhh.identity.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Decoder de prueba que mapea un token simple (por su valor) a un conjunto de claims,
 * de modo que la request fluya por el filtro real de resource server y por
 * {@code JwtTenantConverter} (poblando el TenantContext), tal como en produccion.
 *
 * Tokens disponibles:
 *  - "admin"      : Admin de RRHH del tenant demo.
 *  - "trabajador" : Trabajador del tenant demo.
 *  - "otro"       : Admin de otro tenant.
 *  - "pending"    : Usuario federado (Google) sin tenant/rol.
 *  - "pending2"   : Otro usuario federado sin tenant/rol (para el flujo de asignacion).
 */
@TestConfiguration
public class TestJwtConfig {

    private static final String TENANT_DEMO = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_OTRO = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> {
            Instant now = Instant.now();
            Jwt.Builder builder = Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600));

            switch (token) {
                case "admin" -> builder.subject("cognito-admin")
                        .claim("email", "admin.demo@rrhh.local")
                        .claim("custom:tenant_id", TENANT_DEMO)
                        .claim("custom:role", "Admin de RRHH")
                        .claim("custom:user_id", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
                case "trabajador" -> builder.subject("cognito-trab")
                        .claim("email", "trab@rrhh.local")
                        .claim("custom:tenant_id", TENANT_DEMO)
                        .claim("custom:role", "Trabajador");
                case "otro" -> builder.subject("cognito-b")
                        .claim("email", "admin.b@rrhh.local")
                        .claim("custom:tenant_id", TENANT_OTRO)
                        .claim("custom:role", "Admin de RRHH");
                case "pending" -> builder.subject("cognito-google-123")
                        .claim("email", "nuevo.google@gmail.com")
                        .claim("name", "Nuevo Google");
                case "pending2" -> builder.subject("cognito-google-456")
                        .claim("email", "asignar.google@gmail.com")
                        .claim("name", "Asignar Google");
                default -> builder.subject("cognito-unknown")
                        .claim("email", "desconocido@rrhh.local");
            }

            return builder.build();
        };
    }
}
