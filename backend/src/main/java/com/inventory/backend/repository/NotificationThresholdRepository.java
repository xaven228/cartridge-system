package com.inventory.backend.repository;

import com.inventory.backend.entity.NotificationThreshold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationThresholdRepository extends JpaRepository<NotificationThreshold, Long> {
    List<NotificationThreshold> findByActiveTrueOrderByIdAsc();

    boolean existsByCartridgeModelIdAndDepartmentIdIsNull(Long cartridgeModelId);
    boolean existsByCartridgeModelIdAndDepartmentId(Long cartridgeModelId, Long departmentId);

    boolean existsByCartridgeModelIdAndDepartmentIdIsNullAndIdNot(Long cartridgeModelId, Long id);
    boolean existsByCartridgeModelIdAndDepartmentIdAndIdNot(Long cartridgeModelId, Long departmentId, Long id);
}
