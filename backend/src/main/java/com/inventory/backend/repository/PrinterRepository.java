package com.inventory.backend.repository;

import com.inventory.backend.entity.Printer;
import com.inventory.backend.entity.PrinterType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    List<Printer> findByDepartmentIdOrderByIdAsc(Long departmentId);
    long countByPrinterType(PrinterType printerType);
}
