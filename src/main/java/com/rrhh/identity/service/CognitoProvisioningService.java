package com.rrhh.identity.service;

import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import java.util.List;

@Service
public class CognitoProvisioningService {
    private final String poolId;
    private final String region;

    public CognitoProvisioningService(@Value("${COGNITO_USER_POOL_ID:}") String poolId,
                                      @Value("${AWS_REGION:us-east-1}") String region) {
        this.poolId = poolId;
        this.region = region;
    }

    public String crear(Usuario usuario) {
        if (poolId.isBlank()) {
            throw new DomainException(503, "Falta configurar COGNITO_USER_POOL_ID en ms-identity");
        }
        try (var client = CognitoIdentityProviderClient.builder().region(Region.of(region)).build()) {
            return crear(client, usuario);
        } catch (SdkException error) {
            throw new DomainException(502, "No se pudo crear la cuenta en Cognito. Reintenta el alta de la cuenta.");
        }
    }

    String crear(CognitoIdentityProviderClient client, Usuario usuario) {
        List<AttributeType> attributes;
        try {
            attributes = client.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(poolId).username(usuario.getEmail())
                    .messageAction(MessageActionType.SUPPRESS)
                    .userAttributes(
                            attr("email", usuario.getEmail()),
                            attr("name", usuario.getNombre() == null ? usuario.getEmail() : usuario.getNombre()),
                            attr("custom:tenant_id", usuario.getTenantId()),
                            attr("custom:trabajador_id", usuario.getTrabajadorId()),
                            attr("custom:role", "Trabajador"))
                    .build()).user().attributes();
        } catch (UsernameExistsException exists) {
            // Recover only accounts belonging to this exact worker; never adopt another account.
            attributes = client.adminGetUser(AdminGetUserRequest.builder()
                    .userPoolId(poolId).username(usuario.getEmail()).build()).userAttributes();
            if (!usuario.getTenantId().equals(value(attributes, "custom:tenant_id"))
                    || !usuario.getTrabajadorId().equals(value(attributes, "custom:trabajador_id"))) {
                throw new DomainException(409, "El correo ya tiene una cuenta Cognito con otra vinculación");
            }
        }
        String sub = value(attributes, "sub");
        if (sub == null || sub.isBlank()) throw new DomainException(502, "Cognito no devolvió el identificador de la cuenta");
        return sub;
    }

    private static AttributeType attr(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }

    private static String value(List<AttributeType> attributes, String name) {
        return attributes.stream().filter(a -> name.equals(a.name())).map(AttributeType::value).findFirst().orElse(null);
    }
}
