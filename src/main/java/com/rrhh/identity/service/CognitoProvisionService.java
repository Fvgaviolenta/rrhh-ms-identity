package com.rrhh.identity.service;

import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CognitoProvisionService {

    private static final Pattern REGION = Pattern.compile("cognito-idp\\.([a-z0-9-]+)\\.amazonaws\\.com");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String userPoolId;
    private final String region;

    public CognitoProvisionService(
            @Value("${COGNITO_USER_POOL_ID:}") String userPoolId,
            @Value("${COGNITO_ISSUER:}") String issuer,
            @Value("${COGNITO_REGION:us-east-1}") String region
    ) {
        this.userPoolId = resolverPool(userPoolId, issuer);
        this.region = resolverRegion(region, issuer);
    }

    public Result provision(Usuario usuario) {
        if (userPoolId.isBlank()) {
            return Result.error("Cognito no está configurado (COGNITO_ISSUER). El usuario quedó en la base.");
        }
        String password = generarContrasena();
        try (CognitoIdentityProviderClient client = client()) {
            String username = crearOActualizar(client, usuario, password);
            establecerContrasenaTemporal(client, username, password);
            return new Result(password, null, username);
        } catch (CognitoIdentityProviderException ex) {
            return Result.error(mensaje(ex));
        }
    }

    public String actualizar(Usuario usuario) {
        if (userPoolId.isBlank()) {
            return "Cognito no está configurado (COGNITO_ISSUER).";
        }
        if (usuario.getCognitoSub() == null || usuario.getCognitoSub().isBlank()) {
            return "El usuario aún no tiene identidad en Cognito.";
        }
        try (CognitoIdentityProviderClient client = client()) {
            client.adminUpdateUserAttributes(AdminUpdateUserAttributesRequest.builder()
                    .userPoolId(userPoolId)
                    .username(usernameParaAdmin(usuario))
                    .userAttributes(atributos(usuario))
                    .build());
            return null;
        } catch (CognitoIdentityProviderException ex) {
            return mensaje(ex);
        }
    }

    /**
     * Username que exige la API admin de Cognito: el del IdP para un usuario federado
     * ({@code google_123}, guardado desde el claim {@code cognito:username}); si no se conoce,
     * el email, porque el pool usa {@code username_attributes = ["email"]} (ADR-017, S7).
     */
    static String usernameParaAdmin(Usuario usuario) {
        String username = usuario.getCognitoUsername();
        if (username != null && !username.isBlank()) {
            return username;
        }
        return usuario.getEmail();
    }

    static String generarContrasena() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String symbols = "!@#$%&*";
        String all = upper + lower + digits + symbols;
        List<Character> chars = new ArrayList<>();
        chars.add(pick(upper));
        chars.add(pick(lower));
        chars.add(pick(digits));
        chars.add(pick(symbols));
        while (chars.size() < 12) {
            chars.add(pick(all));
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder out = new StringBuilder(chars.size());
        for (Character c : chars) {
            out.append(c);
        }
        return out.toString();
    }

    String crearOActualizar(CognitoIdentityProviderClient client, Usuario usuario, String password) {
        try {
            AdminCreateUserResponse created = client.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(usuario.getEmail())
                    .temporaryPassword(password)
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(atributos(usuario))
                    .build());
            return created.user().username();
        } catch (UsernameExistsException ex) {
            validarMismaVinculacion(client, usuario);
            client.adminUpdateUserAttributes(AdminUpdateUserAttributesRequest.builder()
                    .userPoolId(userPoolId)
                    .username(usuario.getEmail())
                    .userAttributes(atributos(usuario))
                    .build());
            return usuario.getEmail();
        }
    }

    /** Recupera solo la cuenta de esta misma persona; nunca adopta la cuenta de otra empresa o trabajador. */
    private void validarMismaVinculacion(CognitoIdentityProviderClient client, Usuario usuario) {
        List<AttributeType> existentes = client.adminGetUser(AdminGetUserRequest.builder()
                .userPoolId(userPoolId)
                .username(usuario.getEmail())
                .build()).userAttributes();
        boolean mismoTenant = Objects.equals(usuario.getTenantId(), valor(existentes, "custom:tenant_id"));
        boolean mismoTrabajador = usuario.getTrabajadorId() == null
                || usuario.getTrabajadorId().equals(valor(existentes, "custom:trabajador_id"));
        if (!mismoTenant || !mismoTrabajador) {
            throw new DomainException(409, "El correo ya tiene una cuenta Cognito con otra vinculación", "email");
        }
    }

    private static String valor(List<AttributeType> atributos, String nombre) {
        if (atributos == null) {
            return null;
        }
        return atributos.stream().filter(a -> nombre.equals(a.name())).map(AttributeType::value)
                .findFirst().orElse(null);
    }

    private void establecerContrasenaTemporal(CognitoIdentityProviderClient client, String username, String password) {
        client.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(username)
                .password(password)
                // Cognito responderá NEW_PASSWORD_REQUIRED al primer ingreso.
                // La contraseña se devuelve solo en esta respuesta y nunca se persiste en la base de datos.
                .permanent(false)
                .build());
    }

    private List<AttributeType> atributos(Usuario usuario) {
        List<AttributeType> attrs = new ArrayList<>();
        attrs.add(attr("email", usuario.getEmail()));
        attrs.add(attr("email_verified", "true"));
        if (usuario.getNombre() != null && !usuario.getNombre().isBlank()) {
            attrs.add(attr("name", usuario.getNombre()));
        }
        if (usuario.getTenantId() != null) {
            attrs.add(attr("custom:tenant_id", usuario.getTenantId()));
        }
        if (usuario.getRol() != null) {
            attrs.add(attr("custom:role", usuario.getRol()));
        }
        attrs.add(attr("custom:user_id", usuario.getId()));
        if (usuario.getTrabajadorId() != null) {
            attrs.add(attr("custom:trabajador_id", usuario.getTrabajadorId()));
        }
        return attrs;
    }

    private CognitoIdentityProviderClient client() {
        return CognitoIdentityProviderClient.builder()
                .region(software.amazon.awssdk.regions.Region.of(region))
                .build();
    }

    private static AttributeType attr(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private static String mensaje(CognitoIdentityProviderException ex) {
        String detail = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage();
        return "No se pudo actualizar Cognito: " + detail;
    }

    static String resolverPool(String userPoolId, String issuer) {
        if (userPoolId != null && !userPoolId.isBlank()) {
            return userPoolId.trim();
        }
        if (issuer == null || issuer.isBlank()) {
            return "";
        }
        int slash = issuer.lastIndexOf('/');
        return slash >= 0 ? issuer.substring(slash + 1).trim() : "";
    }

    static String resolverRegion(String region, String issuer) {
        if (issuer != null) {
            Matcher matcher = REGION.matcher(issuer);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return region == null || region.isBlank() ? "us-east-1" : region;
    }

    public record Result(String password, String error, String username) {
        static Result error(String error) {
            return new Result(null, error, null);
        }
    }
}
