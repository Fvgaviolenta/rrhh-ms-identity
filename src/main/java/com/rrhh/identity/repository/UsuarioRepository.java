package com.rrhh.identity.repository;

import com.rrhh.identity.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    List<Usuario> findByTenantId(String tenantId);

    List<Usuario> findByEstado(String estado);

    Optional<Usuario> findByIdAndTenantId(String id, String tenantId);

    Optional<Usuario> findByTenantIdAndEmail(String tenantId, String email);

    Optional<Usuario> findByCognitoSub(String cognitoSub);

    boolean existsByTenantIdAndEmail(String tenantId, String email);
}
