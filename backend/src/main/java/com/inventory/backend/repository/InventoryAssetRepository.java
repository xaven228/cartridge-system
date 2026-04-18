package com.inventory.backend.repository;

import com.inventory.backend.entity.InventoryAsset;
import com.inventory.backend.entity.InventoryAssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryAssetRepository extends JpaRepository<InventoryAsset, Long> {
    boolean existsByInventoryCodeIgnoreCase(String inventoryCode);

    boolean existsByInventoryCodeIgnoreCaseAndIdNot(String inventoryCode, Long id);

    Optional<InventoryAsset> findByInventoryCodeIgnoreCase(String inventoryCode);

    List<InventoryAsset> findAllByOrderByNameAsc();

    List<InventoryAsset> findByDepartmentIdOrderByNameAsc(Long departmentId);

    List<InventoryAsset> findByRoomIdOrderByNameAsc(Long roomId);

    List<InventoryAsset> findByStatusOrderByNameAsc(InventoryAssetStatus status);
}
