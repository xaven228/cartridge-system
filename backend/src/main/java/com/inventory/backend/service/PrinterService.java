package com.inventory.backend.service;

import com.inventory.backend.dto.CurrentPrinterInstallationResponse;
import com.inventory.backend.dto.PrinterResponse;
import com.inventory.backend.dto.PrinterSlotResponse;
import com.inventory.backend.dto.UpsertPrinterRequest;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.entity.Department;
import com.inventory.backend.entity.Printer;
import com.inventory.backend.entity.PrinterInstallation;
import com.inventory.backend.entity.PrinterSlot;
import com.inventory.backend.entity.PrinterType;
import com.inventory.backend.entity.Room;
import com.inventory.backend.exception.NotFoundException;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.DepartmentRepository;
import com.inventory.backend.repository.PrinterInstallationRepository;
import com.inventory.backend.repository.PrinterRepository;
import com.inventory.backend.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrinterService {

    private final PrinterRepository printerRepository;
    private final DepartmentRepository departmentRepository;
    private final CartridgeModelRepository cartridgeModelRepository;
    private final PrinterInstallationRepository printerInstallationRepository;
    private final RoomRepository roomRepository;
    private final ActionLogService actionLogService;

    @Transactional(readOnly = true)
    public List<PrinterResponse> getAll() {
        return printerRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PrinterResponse create(UpsertPrinterRequest request) {
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Отдел не найден: " + request.getDepartmentId()));
        Room room = resolveRoom(request.getRoomId(), department);

        Printer printer = Printer.builder()
                .name(request.getName().trim())
                .department(department)
                .room(room)
                .printerType(request.getPrinterType())
                .slots(new ArrayList<>())
                .build();
        applySlots(printer, request);
        Printer saved = printerRepository.save(printer);
        actionLogService.log(
                ActionLogType.DEPARTMENT_UPDATED,
                saved.getName(),
                "Создан принтер. Слотов: " + saved.getSlots().size(),
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public PrinterResponse update(Long id, UpsertPrinterRequest request) {
        Printer printer = printerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Принтер не найден: " + id));

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Отдел не найден: " + request.getDepartmentId()));
        Room room = resolveRoom(request.getRoomId(), department);

        printer.setName(request.getName().trim());
        printer.setDepartment(department);
        printer.setRoom(room);
        printer.setPrinterType(request.getPrinterType());
        applySlots(printer, request);
        Printer saved = printerRepository.save(printer);
        actionLogService.log(
                ActionLogType.DEPARTMENT_UPDATED,
                saved.getName(),
                "Обновлен принтер. Слотов: " + saved.getSlots().size(),
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Printer printer = printerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Принтер не найден: " + id));
        actionLogService.log(
                ActionLogType.DEPARTMENT_UPDATED,
                printer.getName(),
                "Принтер удален",
                "Система"
        );
        printerRepository.delete(printer);
    }

    private void applySlots(Printer printer, UpsertPrinterRequest request) {
        List<UpsertPrinterRequest.PrinterSlotRequest> slotRequests = normalizeSlotRequests(request);
        printer.getSlots().clear();
        printer.getSlots().addAll(slotRequests.stream()
                .map(slot -> PrinterSlot.builder()
                        .name(slot.getName().trim())
                        .printer(printer)
                        .cartridgeModel(cartridgeModelRepository.findById(slot.getCartridgeModelId())
                                .orElseThrow(() -> new NotFoundException("Модель картриджа не найдена: " + slot.getCartridgeModelId())))
                        .build())
                .toList());
    }

    private List<UpsertPrinterRequest.PrinterSlotRequest> normalizeSlotRequests(UpsertPrinterRequest request) {
        if (request.getPrinterType() == PrinterType.MONOCHROME && request.getSlots().isEmpty()) {
            throw new NotFoundException("Для ч/б принтера нужно указать хотя бы один слот");
        }
        return request.getSlots().stream()
                .filter(slot -> slot.getName() != null && !slot.getName().isBlank())
                .toList();
    }

    public PrinterResponse toResponse(Printer printer) {
        return PrinterResponse.builder()
                .id(printer.getId())
                .name(printer.getName())
                .departmentId(printer.getDepartment() != null ? printer.getDepartment().getId() : null)
                .departmentName(printer.getDepartment() != null ? printer.getDepartment().getName() : null)
                .roomId(printer.getRoom() != null ? printer.getRoom().getId() : null)
                .roomName(printer.getRoom() != null ? printer.getRoom().getName() : null)
                .printerType(printer.getPrinterType())
                .slots(printer.getSlots().stream()
                        .map(this::toSlotResponse)
                        .toList())
                .build();
    }

    private Room resolveRoom(Long roomId, Department department) {
        if (roomId == null) {
            return null;
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Кабинет не найден: " + roomId));
        if (room.getDepartment() == null || !room.getDepartment().getId().equals(department.getId())) {
            throw new NotFoundException("Кабинет не относится к выбранному отделу");
        }
        return room;
    }

    public PrinterSlotResponse toSlotResponse(PrinterSlot slot) {
        PrinterInstallation installation = slot.getId() == null
                ? null
                : printerInstallationRepository.findFirstByPrinterSlotIdAndQuantityGreaterThan(slot.getId(), 0).orElse(null);

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
    }
}
