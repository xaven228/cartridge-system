package com.inventory.backend.repository;

import com.inventory.backend.entity.Cartridge;
import com.inventory.backend.entity.CartridgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CartridgeRepository extends JpaRepository<Cartridge, Long> {

    boolean existsByInventoryCodeIgnoreCase(String inventoryCode);
    long countByDepartmentId(Long departmentId);
    long countByCartridgeModelId(Long cartridgeModelId);

    List<Cartridge> findByDepartmentId(Long departmentId);

    List<Cartridge> findByStatus(CartridgeStatus status);

    List<Cartridge> findByDepartmentIdAndStatus(Long departmentId, CartridgeStatus status);

    @Query("""
            select c
            from Cartridge c
            where c.department.id = :departmentId
              and c.cartridgeModel.id = :cartridgeModelId
              and c.status = :status
              and c.refillable = :refillable
              and c.empty = :empty
            order by c.id asc
            """)
    List<Cartridge> findCompatibleStockRows(
            @Param("departmentId") Long departmentId,
            @Param("cartridgeModelId") Long cartridgeModelId,
            @Param("status") CartridgeStatus status,
            @Param("refillable") Boolean refillable,
            @Param("empty") Boolean empty
    );
}
