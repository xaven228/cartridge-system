package com.inventory.backend.repository;

import com.inventory.backend.entity.RefillHistory;
import com.inventory.backend.entity.RefillStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefillHistoryRepository extends JpaRepository<RefillHistory, Long> {

    List<RefillHistory> findByCartridgeIdOrderByIdDesc(Long cartridgeId);

    Optional<RefillHistory> findFirstByCartridgeIdAndStatusOrderByIdDesc(Long cartridgeId, RefillStatus status);
}