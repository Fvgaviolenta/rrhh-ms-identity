package com.rrhh.identity.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JwtTenantConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TenantContext tenantContext;
    private final String roleClaim;
    private final String tenantClaim;
    private final String userIdClaim;
    private final String trabajadorIdClaim;

    public JwtTenantConverter(
            TenantContext tenantContext,
            @Value("${rrhh.security.jwt.role-claim}") String roleClaim,
            @Value("${rrhh.security.jwt.tenant-claim}") String tenantClaim,
            @Value("${rrhh.security.jwt.user-id-claim}") String userIdClaim,
            @Value("${rrhh.security.jwt.trabajador-id-claim}") String trabajadorIdClaim
    ) {
        this.tenantContext = tenantContext;
        this.roleClaim = roleClaim;
        this.tenantClaim = tenantClaim;
        this.userIdClaim = userIdClaim;
        this.trabajadorIdClaim = trabajadorIdClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String tenantId = firstClaim(jwt, tenantClaim, "tenant_id");
        String role = firstClaim(jwt, roleClaim, "role");
        String userId = firstClaim(jwt, userIdClaim, "user_id");
        String trabajadorId = firstClaim(jwt, trabajadorIdClaim, "trabajador_id");
        String email = firstClaim(jwt, "email", "username");
        String name = firstClaim(jwt, "name");
        if (name == null) {
            String given = firstClaim(jwt, "given_name");
            String family = firstClaim(jwt, "family_name");
            name = joinNames(given, family);
        }
        String authority = Roles.authorityFromClaim(role);

        tenantContext.set(new TenantContext.AuthenticatedUser(
                tenantId,
                userId,
                email,
                role == null ? null : Roles.claimFromAuthority(authority),
                trabajadorId,
                jwt.getSubject(),
                name
        ));

        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }

    private static String firstClaim(Jwt jwt, String... names) {
        for (String name : names) {
            String value = jwt.getClaimAsString(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String joinNames(String given, String family) {
        String combined = ((given != null ? given : "") + " " + (family != null ? family : "")).trim();
        return combined.isBlank() ? null : combined;
    }
}
