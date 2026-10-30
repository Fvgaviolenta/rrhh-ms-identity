package com.rrhh.identity.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Decoder de prueba que mapea un token simple (por su valor) a un conjunto de claims,
 * de modo que la request fluya por el filtro real de resource server y por
 * {@code JwtTenantConverter} (poblando el TenantContext), tal como en produccion.
 *
 * Tokens disponibles:
 *  - "admin"      : Admin de RRHH del tenant demo.
 *  - "manual-admin" : Cuenta Cognito creada manualmente, sin usuario local.
 *  - "trabajador" : Trabajador del tenant demo.
 *  - "otro"       : Admin de otro tenant.
 *  - "pending"    : Usuario federado (Google) sin tenant/rol.
 *  - "pending2"   : Otro usuario federado sin tenant/rol (para el flujo de asignacion).
 *  - "invited"    : Google cuyo email fue pre-registrado (INVITADO).
 *  - "operador"   : OperadorSaaS sin tenant de cliente.
 *  - "owner-google"     : dueño de la plataforma (owner@gmail.com) entrando con Google.
 *  - "owner-native"     : mismo correo, cuenta nativa (sin claim identities): no se promueve.
 *  - "admin-plataforma" : owner-google ya con custom:role AdminPlataforma.
 *  - "registrante"      : Google sin empresa que registra una (Rita Registrante).
 *  - "postulante"       : Google sin empresa que pide acceso por RUT (Pablo Postulante).
 */
@TestConfiguration
public class TestJwtConfig {

    private static final String TENANT_DEMO = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_OTRO = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final List<Map<String, Object>> GOOGLE = List.of(Map.of("providerName", "Google"));

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
                case "manual-admin" -> builder.subject("cognito-admin-123")
                        .claim("email", "adminempresademospa@mail.com")
                        .claim("custom:tenant_id", TENANT_DEMO)
                        .claim("custom:role", "Admin de RRHH");
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
                case "invited" -> builder.subject("cognito-invited-789")
                        .claim("email", "invitado.google@gmail.com")
                        .claim("name", "Invitado Google");
                case "operador" -> builder.subject("cognito-operador")
                        .claim("email", "operador@rrhh.local")
                        .claim("custom:role", "OperadorSaaS")
                        .claim("custom:user_id", "dddddddd-dddd-dddd-dddd-dddddddddddd");
                case "owner-google" -> builder.subject("cognito-owner")
                        .claim("email", "owner@gmail.com")
                        .claim("cognito:username", "google_111")
                        .claim("identities", GOOGLE);
                case "owner-native" -> builder.subject("cognito-owner-native")
                        .claim("email", "owner@gmail.com");
                case "admin-plataforma" -> builder.subject("cognito-owner")
                        .claim("email", "owner@gmail.com")
                        .claim("cognito:username", "google_111")
                        .claim("identities", GOOGLE)
                        .claim("custom:role", "AdminPlataforma");
                case "registrante" -> builder.subject("cognito-reg-1")
                        .claim("email", "registrante@gmail.com")
                        .claim("name", "Rita Registrante")
                        .claim("cognito:username", "google_222")
                        .claim("identities", GOOGLE);
                case "postulante" -> builder.subject("cognito-post-1")
                        .claim("email", "postulante@gmail.com")
                        .claim("name", "Pablo Postulante")
                        .claim("cognito:username", "google_333")
                        .claim("identities", GOOGLE);
                default -> builder.subject("cognito-unknown")
                        .claim("email", "desconocido@rrhh.local");
            }

            return builder.build();
        };
    }
}
