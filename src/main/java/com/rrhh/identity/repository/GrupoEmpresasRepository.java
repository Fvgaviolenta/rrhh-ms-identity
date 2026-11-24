package com.rrhh.identity.repository;

import com.rrhh.identity.model.GrupoEmpresas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrupoEmpresasRepository extends JpaRepository<GrupoEmpresas, String> {

    boolean existsByNombre(String nombre);

    List<GrupoEmpresas> findAllByOrderByNombreAsc();
}
