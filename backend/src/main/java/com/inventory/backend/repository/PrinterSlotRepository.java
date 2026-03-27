package com.inventory.backend.repository;

import com.inventory.backend.entity.PrinterSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrinterSlotRepository extends JpaRepository<PrinterSlot, Long> {
    List<PrinterSlot> findByPrinterIdOrderByIdAsc(Long printerId);
    long countByCartridgeModelId(Long cartridgeModelId);
}
