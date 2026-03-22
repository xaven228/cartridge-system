package com.inventory.backend.service;

import com.inventory.backend.dto.CurrentPrinterInstallationResponse;
import com.inventory.backend.dto.DepartmentPrinterResponse;
import com.inventory.backend.dto.DepartmentResponse;
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
    private final ActionLogService actionLogService;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAll() {
        return departmentRepository.findAll().stream()
                .map(department -> DepartmentResponse.builder()
                        .id(department.getId())
                        .name(department.getName())
                        .description(department.getDescription())
                        .printers((department.getPrinters() == null ? List.<DepartmentPrinterResponse>of() : department.getPrinters().stream()
                                .map(printer -> {
                                    var installation = printer.getId() == null
                                            ? null
                                            : printerInstallationRepository
                                                    .findFirstByPrinterIdAndQuantityGreaterThan(printer.getId(), 0)
                                                    .orElse(null);

                                    return DepartmentPrinterResponse.builder()
                                            .id(printer.getId())
                                            .name(printer.getName())
                                            .cartridgeModelId(printer.getCartridgeModel() != null ? printer.getCartridgeModel().getId() : null)
                                            .cartridgeModelName(printer.getCartridgeModel() != null ? printer.getCartridgeModel().getName() : null)
                                            .previousReplacementDate(printer.getPreviousReplacementDate())
                                            .lastReplacementDate(printer.getLastReplacementDate())
                                            .currentInstallation(installation == null ? null : CurrentPrinterInstallationResponse.builder()
                                                    .cartridgeId(installation.getCartridge().getId())
                                                    .inventoryCode(installation.getCartridge().getInventoryCode())
                                                    .cartridgeModelName(installation.getCartridge().getCartridgeModel().getName())
                                                    .quantity(installation.getQuantity())
                                                    .build())
                                            .build();
                                })
                                .toList()))
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
                "Создан отдел. Точек замены: " + saved.getPrinters().size(),
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
                "Обновлен отдел. Точек замены: " + saved.getPrinters().size(),
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

        List<Printer> normalizedPrinters = new ArrayList<>(request.getPrinters().stream()
                .filter(printer -> printer.getName() != null && !printer.getName().isBlank())
                .map(printer -> Printer.builder()
                        .name(printer.getName().trim())
                        .department(department)
                        .cartridgeModel(
                                printer.getCartridgeModel() != null && printer.getCartridgeModel().getId() != null
                                        ? cartridgeModelRepository.findById(printer.getCartridgeModel().getId())
                                        .orElseThrow(() -> new NotFoundException(
                                                "Модель картриджа не найдена: " + printer.getCartridgeModel().getId()
                                        ))
                                        : null
                        )
                        .build())
                .toList());
        department.getPrinters().clear();
        department.getPrinters().addAll(normalizedPrinters);
    }

    private DepartmentResponse toResponse(Department department) {
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .printers((department.getPrinters() == null ? List.<DepartmentPrinterResponse>of() : department.getPrinters().stream()
                        .map(printer -> {
                            var installation = printer.getId() == null
                                    ? null
                                    : printerInstallationRepository
                                    .findFirstByPrinterIdAndQuantityGreaterThan(printer.getId(), 0)
                                    .orElse(null);

                            return DepartmentPrinterResponse.builder()
                                    .id(printer.getId())
                                    .name(printer.getName())
                                    .cartridgeModelId(printer.getCartridgeModel() != null ? printer.getCartridgeModel().getId() : null)
                                    .cartridgeModelName(printer.getCartridgeModel() != null ? printer.getCartridgeModel().getName() : null)
                                    .previousReplacementDate(printer.getPreviousReplacementDate())
                                    .lastReplacementDate(printer.getLastReplacementDate())
                                    .currentInstallation(installation == null ? null : CurrentPrinterInstallationResponse.builder()
                                            .cartridgeId(installation.getCartridge().getId())
                                            .inventoryCode(installation.getCartridge().getInventoryCode())
                                            .cartridgeModelName(installation.getCartridge().getCartridgeModel().getName())
                                            .quantity(installation.getQuantity())
                                            .build())
                                    .build();
                        })
                        .toList()))
                .build();
    }
}
