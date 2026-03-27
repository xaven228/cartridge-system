package com.inventory.backend.repository;

import com.inventory.backend.entity.PrinterInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrinterInstallationRepository extends JpaRepository<PrinterInstallation, Long> {

    List<PrinterInstallation> findByCartridgeId(Long cartridgeId);

    Optional<PrinterInstallation> findByCartridgeIdAndPrinterSlotId(Long cartridgeId, Long printerSlotId);

    Optional<PrinterInstallation> findFirstByPrinterSlotIdAndQuantityGreaterThan(Long printerSlotId, Integer quantity);

    long countByPrinterSlotId(Long printerSlotId);
}
