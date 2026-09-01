package com.rrhh.identity.repository;

import com.rrhh.identity.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, String> {

    Optional<Rol> findByCodigo(String codigo);
}
