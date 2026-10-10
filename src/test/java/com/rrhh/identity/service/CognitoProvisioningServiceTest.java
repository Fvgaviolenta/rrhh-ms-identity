package com.rrhh.identity.service;

import com.rrhh.identity.exception.DomainException;
import com.rrhh.identity.model.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CognitoProvisioningServiceTest {
    private final CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    private final CognitoProvisioningService service = new CognitoProvisioningService("pool", "us-east-1");

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setEmail("persona@example.com");
        u.setNombre("Persona");
        u.setTenantId("empresa");
        u.setTrabajadorId("trabajador");
        return u;
    }

    private AttributeType attr(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }

    @Test void creaCuentaSinEnviarCorreo() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(
                AdminCreateUserResponse.builder().user(UserType.builder().attributes(attr("sub", "sub-1")).build()).build());
        assertEquals("sub-1", service.crear(client, usuario()));
        var request = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        verify(client).adminCreateUser(request.capture());
        assertEquals(MessageActionType.SUPPRESS, request.getValue().messageAction());
        assertTrue(request.getValue().userAttributes().contains(attr("custom:tenant_id", "empresa")));
    }

    @Test void reintentoRecuperaSoloLaMismaCuenta() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenThrow(UsernameExistsException.builder().build());
        when(client.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(AdminGetUserResponse.builder()
                .userAttributes(attr("sub", "sub-1"), attr("custom:tenant_id", "empresa"), attr("custom:trabajador_id", "trabajador")).build());
        assertEquals("sub-1", service.crear(client, usuario()));
    }

    @Test void noAdoptaCuentaDeOtraEmpresa() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenThrow(UsernameExistsException.builder().build());
        when(client.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(AdminGetUserResponse.builder()
                .userAttributes(attr("sub", "sub-1"), attr("custom:tenant_id", "otra"), attr("custom:trabajador_id", "trabajador")).build());
        assertThrows(DomainException.class, () -> service.crear(client, usuario()));
    }

    @Test void configuracionAusenteNoSimulaExito() {
        assertThrows(DomainException.class, () -> new CognitoProvisioningService("", "us-east-1").crear(usuario()));
    }
}
