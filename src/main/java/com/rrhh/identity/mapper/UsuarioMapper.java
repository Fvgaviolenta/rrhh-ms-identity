package com.rrhh.identity.mapper;

import com.rrhh.identity.dto.response.UsuarioResponse;
import com.rrhh.identity.model.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    public UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                formatCodigo(usuario.getSecuencia()),
                usuario.getTenantId(),
                usuario.getEmail(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getTrabajadorId(),
                usuario.getCognitoSub(),
                usuario.getEstado(),
                usuario.isActivo(),
                null,
                null
        );
    }

    public UsuarioResponse toResponse(Usuario usuario, String contrasenaTemporal, String cognitoError) {
        UsuarioResponse base = toResponse(usuario);
        return new UsuarioResponse(
                base.id(),
                base.codigo(),
                base.tenantId(),
                base.email(),
                base.nombre(),
                base.rol(),
                base.trabajadorId(),
                base.cognitoSub(),
                base.estado(),
                base.activo(),
                contrasenaTemporal,
                cognitoError
        );
    }

    public static String formatCodigo(Long secuencia) {
        if (secuencia == null) {
            return null;
        }
        return String.format("USR-%06d", secuencia);
    }
}
// comentario para subir a github