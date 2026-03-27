package com.inventory.backend.repository;

import com.inventory.backend.entity.CartridgeModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartridgeModelRepository extends JpaRepository<CartridgeModel, Long> {
    boolean existsByNameIgnoreCase(String name);
}
