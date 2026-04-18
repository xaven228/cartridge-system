package com.inventory.backend.repository;

import com.inventory.backend.entity.ActionLog;
import com.inventory.backend.entity.ActionLogType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long>, JpaSpecificationExecutor<ActionLog> {
    List<ActionLog> findByActionTypeInAndCreatedAtBetween(Collection<ActionLogType> actionTypes, LocalDateTime from, LocalDateTime to);
}
