package com.rrhh.identity.service;

import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Usuario;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CognitoProvisionServiceTest {

    private final CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    private final CognitoProvisionService service = new CognitoProvisionService("pool", "", "us-east-1");

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setId("u-1");
        u.setEmail("persona@example.com");
        u.setNombre("Persona");
        u.setTenantId("empresa");
        u.setTrabajadorId("trabajador");
        u.setRol("Trabajador");
        return u;
    }

    private static AttributeType attr(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }

    @Test
    void usernameParaAdminPrefiereElDelIdp() {
        Usuario u = new Usuario();
        u.setEmail("a@gmail.com");
        u.setCognitoUsername("google_222");
        assertEquals("google_222", CognitoProvisionService.usernameParaAdmin(u));
        u.setCognitoUsername(null);
        assertEquals("a@gmail.com", CognitoProvisionService.usernameParaAdmin(u));
        u.setCognitoUsername("  ");
        assertEquals("a@gmail.com", CognitoProvisionService.usernameParaAdmin(u));
    }

    @Test
    void cuentaExistenteDeLaMismaVinculacionSeReutiliza() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenThrow(UsernameExistsException.builder().build());
        when(client.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(AdminGetUserResponse.builder()
                .userAttributes(attr("custom:tenant_id", "empresa"), attr("custom:trabajador_id", "trabajador")).build());
        assertEquals("persona@example.com", service.crearOActualizar(client, usuario(), "Clave123!"));
    }

    @Test
    void noAdoptaCuentaCognitoDeOtraEmpresa() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenThrow(UsernameExistsException.builder().build());
        when(client.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(AdminGetUserResponse.builder()
                .userAttributes(attr("custom:tenant_id", "otra"), attr("custom:trabajador_id", "trabajador")).build());
        DomainException ex = assertThrows(DomainException.class,
                () -> service.crearOActualizar(client, usuario(), "Clave123!"));
        assertEquals(409, ex.getCodigo());
    }

    @Test
    void deshabilitarBloqueaLaCuentaYCierraSusSesiones() {
        Usuario u = usuario();
        u.setCognitoUsername("google_999");
        service.deshabilitar(client, u);
        verify(client).adminDisableUser(AdminDisableUserRequest.builder().userPoolId("pool").username("google_999").build());
        verify(client).adminUserGlobalSignOut(AdminUserGlobalSignOutRequest.builder().userPoolId("pool").username("google_999").build());
    }

    @Test
    void habilitarReactivaLaCuenta() {
        service.habilitar(client, usuario());
        verify(client).adminEnableUser(AdminEnableUserRequest.builder().userPoolId("pool").username("persona@example.com").build());
    }

    @Test
    void sinPoolConfiguradoDeshabilitarDevuelveElErrorSinLlamarACognito() {
        CognitoProvisionService sinPool = new CognitoProvisionService("", "", "us-east-1");
        assertEquals("Cognito no está configurado (COGNITO_ISSUER).", sinPool.deshabilitar(usuario()));
        assertEquals("Cognito no está configurado (COGNITO_ISSUER).", sinPool.habilitar(usuario()));
    }
}
