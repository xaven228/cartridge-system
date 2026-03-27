package com.inventory.backend.service;

import com.inventory.backend.dto.CurrentPrinterInstallationResponse;
import com.inventory.backend.dto.DepartmentPrinterResponse;
import com.inventory.backend.dto.DepartmentResponse;
import com.inventory.backend.dto.PrinterSlotResponse;
import com.inventory.backend.dto.UpdateDepartmentRequest;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.entity.Department;
import com.inventory.backend.entity.Printer;
import com.inventory.backend.exception.NotFoundException;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.DepartmentRepository;
import com.inventory.backend.repository.PrinterInstallationRepository;
import com.inventory.backend.repository.PrinterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CartridgeRepository cartridgeRepository;
    private final CartridgeModelRepository cartridgeModelRepository;
    private final PrinterInstallationRepository printerInstallationRepository;
    private final PrinterRepository printerRepository;
    private final ActionLogService actionLogService;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAll() {
        return departmentRepository.findAll().stream()
                .map(department -> DepartmentResponse.builder()
                        .id(department.getId())
                        .name(department.getName())
                        .description(department.getDescription())
                        .printers(mapPrinters(department))
                        .build())
                .toList();
    }

    @Transactional
    public DepartmentResponse create(UpdateDepartmentRequest request) {
        if (departmentRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new ConflictException("Отдел с таким названием уже существует: " + request.getName());
        }

        Department department = Department.builder().build();
        applyRequest(department, request);
        Department saved = departmentRepository.save(department);
        actionLogService.log(
                ActionLogType.DEPARTMENT_CREATED,
                saved.getName(),
                "Создан отдел",
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Отдел не найден: " + id));

        boolean nameTaken = departmentRepository.existsByNameIgnoreCase(request.getName().trim())
                && departmentRepository.findAll().stream()
                .anyMatch(item -> !item.getId().equals(id) && item.getName().equalsIgnoreCase(request.getName().trim()));

        if (nameTaken) {
            throw new ConflictException("Отдел с таким названием уже существует: " + request.getName());
        }

        applyRequest(department, request);
        Department saved = departmentRepository.save(department);
        actionLogService.log(
                ActionLogType.DEPARTMENT_UPDATED,
                saved.getName(),
                "Обновлен отдел",
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Отдел не найден: " + id));

        if (cartridgeRepository.countByDepartmentId(id) > 0) {
            throw new ConflictException("Нельзя удалить отдел, пока в нем есть картриджные остатки");
        }

        actionLogService.log(
                ActionLogType.DEPARTMENT_DELETED,
                department.getName(),
                "Отдел удален",
                "Система"
        );
        departmentRepository.delete(department);
    }

    private void applyRequest(Department department, UpdateDepartmentRequest request) {
        department.setName(request.getName().trim());
        department.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        if (department.getPrinters() == null) {
            department.setPrinters(new ArrayList<>());
        }
    }

    private DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .printers(mapPrinters(department))
                .build();
    }

    private List<DepartmentPrinterResponse> mapPrinters(Department department) {
        return printerRepository.findByDepartmentIdOrderByIdAsc(department.getId()).stream()
                .map(printer -> DepartmentPrinterResponse.builder()
                        .id(printer.getId())
                        .name(printer.getName())
                        .printerType(printer.getPrinterType())
                        .slots(printer.getSlots().stream()
                                .map(slot -> {
                                    var installation = slot.getId() == null
                                            ? null
                                            : printerInstallationRepository
                                            .findFirstByPrinterSlotIdAndQuantityGreaterThan(slot.getId(), 0)
                                            .orElse(null);

                                    return PrinterSlotResponse.builder()
                                            .id(slot.getId())
                                            .name(slot.getName())
                                            .cartridgeModelId(slot.getCartridgeModel() != null ? slot.getCartridgeModel().getId() : null)
                                            .cartridgeModelName(slot.getCartridgeModel() != null ? slot.getCartridgeModel().getName() : null)
                                            .previousReplacementDate(slot.getPreviousReplacementDate())
                                            .lastReplacementDate(slot.getLastReplacementDate())
                                            .currentInstallation(installation == null ? null : CurrentPrinterInstallationResponse.builder()
                                                    .cartridgeId(installation.getCartridge().getId())
                                                    .inventoryCode(installation.getCartridge().getInventoryCode())
                                                    .cartridgeModelName(installation.getCartridge().getCartridgeModel().getName())
                                                    .quantity(installation.getQuantity())
                                                    .build())
                                            .build();
                                })
                                .toList())
                        .build())
                .toList();
    }
}
