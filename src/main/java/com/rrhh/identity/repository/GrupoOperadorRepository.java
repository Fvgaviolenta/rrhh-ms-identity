package com.rrhh.identity.repository;

import com.rrhh.identity.model.GrupoOperador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrupoOperadorRepository extends JpaRepository<GrupoOperador, GrupoOperador.Pk> {

    List<GrupoOperador> findByUsuarioId(String usuarioId);

    List<GrupoOperador> findByGrupoId(String grupoId);

    void deleteByGrupoId(String grupoId);
}
