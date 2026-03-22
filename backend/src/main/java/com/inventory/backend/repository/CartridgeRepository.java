package com.inventory.backend.repository;

import com.inventory.backend.entity.Cartridge;
import com.inventory.backend.entity.CartridgeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CartridgeRepository extends JpaRepository<Cartridge, Long> {

    List<Cartridge> findByDepartmentId(Long departmentId);

    List<Cartridge> findByStatus(CartridgeStatus status);

    List<Cartridge> findByDepartmentIdAndStatus(Long departmentId, CartridgeStatus status);
}