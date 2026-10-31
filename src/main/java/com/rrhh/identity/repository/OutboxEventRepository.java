package com.rrhh.identity.repository;

import com.rrhh.identity.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop20ByEstadoOrderByCreadoEnAsc(String estado);
}
