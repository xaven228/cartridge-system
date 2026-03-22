package com.inventory.backend.repository;

import com.inventory.backend.entity.PrinterInstallation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrinterInstallationRepository extends JpaRepository<PrinterInstallation, Long> {

    List<PrinterInstallation> findByCartridgeId(Long cartridgeId);

    Optional<PrinterInstallation> findByCartridgeIdAndPrinterId(Long cartridgeId, Long printerId);

    Optional<PrinterInstallation> findFirstByPrinterIdAndQuantityGreaterThan(Long printerId, Integer quantity);

    long countByPrinterId(Long printerId);
}
