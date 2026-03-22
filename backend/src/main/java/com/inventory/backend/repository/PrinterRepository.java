package com.inventory.backend.repository;

import com.inventory.backend.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    long countByCartridgeModelId(Long cartridgeModelId);
}
