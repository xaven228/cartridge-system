package com.inventory.backend.service;

import com.inventory.backend.dto.AdjustQuantityRequest;
import com.inventory.backend.dto.CartridgeResponse;
import com.inventory.backend.dto.CreateCartridgeRequest;
import com.inventory.backend.dto.InstallCartridgeRequest;
import com.inventory.backend.dto.ReplaceCartridgeRequest;
import com.inventory.backend.dto.ReturnFromRefillRequest;
import com.inventory.backend.dto.RemoveCartridgeInstallationRequest;
import com.inventory.backend.dto.SendToRefillRequest;
import com.inventory.backend.dto.UpdateCartridgeRefillableRequest;
import com.inventory.backend.entity.Cartridge;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.entity.CartridgeStatus;
import com.inventory.backend.entity.Department;
import com.inventory.backend.entity.Printer;
import com.inventory.backend.entity.PrinterInstallation;
import com.inventory.backend.entity.RefillHistory;
import com.inventory.backend.entity.RefillStatus;
import com.inventory.backend.exception.BadRequestException;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.exception.NotFoundException;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.DepartmentRepository;
import com.inventory.backend.repository.PrinterInstallationRepository;
import com.inventory.backend.repository.PrinterRepository;
import com.inventory.backend.repository.RefillHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartridgeService {
    private static final String STOCK_DEPARTMENT_NAME = "Склад";

    private final CartridgeRepository cartridgeRepository;
    private final CartridgeModelRepository cartridgeModelRepository;
    private final DepartmentRepository departmentRepository;
    private final PrinterRepository printerRepository;
    private final PrinterInstallationRepository printerInstallationRepository;
    private final RefillHistoryRepository refillHistoryRepository;
    private final ActionLogService actionLogService;

    public List<CartridgeResponse> getAll(Long departmentId, CartridgeStatus status) {
        List<Cartridge> cartridges;

        if (departmentId != null && status != null) {
            cartridges = cartridgeRepository.findByDepartmentIdAndStatus(departmentId, status);
        } else if (departmentId != null) {
            cartridges = cartridgeRepository.findByDepartmentId(departmentId);
        } else if (status != null) {
            cartridges = cartridgeRepository.findByStatus(status);
        } else {
            cartridges = cartridgeRepository.findAll();
        }

        return cartridges.stream()
                .map(this::toResponse)
                .toList();
    }

    public CartridgeResponse getById(Long id) {
        return toResponse(getCartridgeEntity(id));
    }

    @Transactional
    public CartridgeResponse create(CreateCartridgeRequest request) {
        CartridgeModel cartridgeModel = cartridgeModelRepository.findById(request.getCartridgeModelId())
                .orElseThrow(() -> new NotFoundException("Модель картриджа не найдена: " + request.getCartridgeModelId()));

        Department department = request.getDepartmentId() != null
                ? departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Отдел не найден: " + request.getDepartmentId()))
                : getOrCreateStockDepartment();

        String inventoryCode = resolveInventoryCode(request.getInventoryCode());

        Cartridge cartridge = Cartridge.builder()
                .inventoryCode(inventoryCode)
                .cartridgeModel(cartridgeModel)
                .department(department)
                .quantity(request.getQuantity())
                .refillable(request.getRefillable())
                .empty(false)
                .status(request.getStatus() != null ? request.getStatus() : CartridgeStatus.IN_STOCK)
                .refillCount(0)
                .comment(request.getComment())
                .build();

        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_CREATED,
                saved.getCartridgeModel().getName(),
                "Приход в остаток: " + saved.getQuantity() + " шт., тип: "
                        + (Boolean.TRUE.equals(saved.getRefillable()) ? "перезаправляемый" : "одноразовый"),
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse adjustQuantity(Long id, AdjustQuantityRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);

        if (cartridge.getStatus() == CartridgeStatus.WRITTEN_OFF) {
            throw new ConflictException("Нельзя изменять количество у списанного картриджа");
        }

        if (request.getQuantity() < 0) {
            throw new BadRequestException("Количество не может быть отрицательным");
        }

        cartridge.setQuantity(request.getQuantity());

        if (request.getComment() != null && !request.getComment().isBlank()) {
            cartridge.setComment(request.getComment());
        }

        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_QUANTITY_CHANGED,
                saved.getCartridgeModel().getName(),
                "Новый остаток: " + saved.getQuantity() + " шт.",
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse sendToRefill(Long id, SendToRefillRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);

        if (Boolean.FALSE.equals(cartridge.getRefillable())) {
            throw new ConflictException("Этот тип картриджа не заправляется и должен списываться");
        }

        if (cartridge.getStatus() == CartridgeStatus.ON_REFILL) {
            throw new ConflictException("Картридж уже находится на заправке");
        }

        if (cartridge.getStatus() == CartridgeStatus.WRITTEN_OFF) {
            throw new ConflictException("Нельзя отправить на заправку списанный картридж");
        }

        if (Boolean.FALSE.equals(cartridge.getEmpty())) {
            throw new ConflictException("На заправку можно отправлять только пустой картридж");
        }

        cartridge.setStatus(CartridgeStatus.ON_REFILL);

        RefillHistory refillHistory = RefillHistory.builder()
                .cartridge(cartridge)
                .sentAt(request.getSentAt())
                .status(RefillStatus.SENT)
                .comment(request.getComment())
                .createdBy(request.getCreatedBy())
                .build();

        refillHistoryRepository.save(refillHistory);
        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_SENT_TO_REFILL,
                saved.getCartridgeModel().getName(),
                "Отправлен на заправку. Комментарий: " + safeText(request.getComment()),
                request.getCreatedBy()
        );

        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse returnFromRefill(Long id, ReturnFromRefillRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);

        if (cartridge.getStatus() != CartridgeStatus.ON_REFILL) {
            throw new ConflictException("Картридж не находится на заправке");
        }

        RefillHistory lastSentRecord = refillHistoryRepository
                .findFirstByCartridgeIdAndStatusOrderByIdDesc(id, RefillStatus.SENT)
                .orElseThrow(() -> new NotFoundException("Не найдена запись об отправке на заправку"));

        lastSentRecord.setReturnedAt(request.getReturnedAt());
        lastSentRecord.setStatus(RefillStatus.RETURNED);

        String oldComment = lastSentRecord.getComment();
        String newComment = request.getComment();

        if (newComment != null && !newComment.isBlank()) {
            if (oldComment == null || oldComment.isBlank()) {
                lastSentRecord.setComment(newComment);
            } else {
                lastSentRecord.setComment(oldComment + " | " + newComment);
            }
        }

        lastSentRecord.setCreatedBy(request.getCreatedBy());
        refillHistoryRepository.save(lastSentRecord);

        cartridge.setStatus(CartridgeStatus.IN_STOCK);
        cartridge.setEmpty(false);
        cartridge.setRefillCount(cartridge.getRefillCount() + 1);
        cartridge.setLastRefillDate(request.getReturnedAt());

        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_RETURNED_FROM_REFILL,
                saved.getCartridgeModel().getName(),
                "Возвращен с заправки. Остаток: " + saved.getQuantity() + " шт.",
                request.getCreatedBy()
        );
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse updateRefillable(Long id, UpdateCartridgeRefillableRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);
        cartridge.setRefillable(request.getRefillable());
        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_REFILLABLE_CHANGED,
                saved.getCartridgeModel().getName(),
                "Тип изменен на: " + (Boolean.TRUE.equals(saved.getRefillable()) ? "перезаправляемый" : "одноразовый"),
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse installToPrinter(Long id, InstallCartridgeRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);
        Printer printer = getPrinterEntity(request.getPrinterId());

        if (printer.getCartridgeModel() != null
                && !printer.getCartridgeModel().getId().equals(cartridge.getCartridgeModel().getId())) {
            throw new ConflictException("Для этого принтера назначен другой тип картриджа");
        }

        if (cartridge.getStatus() == CartridgeStatus.ON_REFILL) {
            throw new ConflictException("Нельзя устанавливать картридж, пока он на заправке");
        }

        if (cartridge.getStatus() == CartridgeStatus.WRITTEN_OFF) {
            throw new ConflictException("Нельзя устанавливать списанный картридж");
        }

        if (Boolean.TRUE.equals(cartridge.getEmpty())) {
            throw new ConflictException("Нельзя устанавливать пустой картридж");
        }

        if (cartridge.getQuantity() < request.getQuantity()) {
            throw new ConflictException("На складе недостаточно картриджей для установки");
        }

        if (printerInstallationRepository.findFirstByPrinterIdAndQuantityGreaterThan(printer.getId(), 0).isPresent()) {
            throw new ConflictException("В этой точке замены уже установлен картридж");
        }

        Cartridge installationCartridge = cartridge;
        if (cartridge.getQuantity() > request.getQuantity()) {
            cartridge.setQuantity(cartridge.getQuantity() - request.getQuantity());
            cartridge.setStatus(CartridgeStatus.IN_STOCK);
            cartridgeRepository.save(cartridge);
            installationCartridge = createInstalledCartridge(cartridge, request.getComment());
        } else {
            cartridge.setQuantity(cartridge.getQuantity() - request.getQuantity());
            cartridge.setStatus(CartridgeStatus.INSTALLED);
            if (request.getComment() != null && !request.getComment().isBlank()) {
                cartridge.setComment(request.getComment());
            }
            installationCartridge = cartridgeRepository.save(cartridge);
        }

        PrinterInstallation installation = PrinterInstallation.builder()
                .printer(printer)
                .cartridge(installationCartridge)
                .quantity(request.getQuantity())
                .build();
        printerInstallationRepository.save(installation);

        printer.setPreviousReplacementDate(printer.getLastReplacementDate());
        printer.setLastReplacementDate(LocalDate.now());
        printerRepository.save(printer);

        Cartridge saved = installationCartridge;
        actionLogService.log(
                ActionLogType.CARTRIDGE_INSTALLED,
                saved.getCartridgeModel().getName(),
                "Установлен в точку \"" + printer.getName() + "\". Остаток: " + saved.getQuantity() + " шт.",
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse replaceInPrinter(Long newCartridgeId, ReplaceCartridgeRequest request) {
        Cartridge newCartridge = getCartridgeEntity(newCartridgeId);
        Printer printer = getPrinterEntity(request.getPrinterId());

        PrinterInstallation currentInstallation = printerInstallationRepository
                .findFirstByPrinterIdAndQuantityGreaterThan(printer.getId(), 0)
                .orElse(null);

        if (currentInstallation != null) {
            Cartridge installedCartridge = currentInstallation.getCartridge();

            String removedOutcome = request.getRemovedOutcome().trim().toUpperCase(Locale.ROOT);
            switch (removedOutcome) {
                case "STOCK" -> removeFromPrinter(installedCartridge.getId(),
                        buildRemoveRequest(printer.getId(), true, request.getComment()));
                case "REFILL" -> {
                    if (Boolean.FALSE.equals(installedCartridge.getRefillable())) {
                        throw new ConflictException("Старый картридж помечен как одноразовый и не может быть отправлен на заправку");
                    }
                    removeFromPrinter(installedCartridge.getId(),
                            buildRemoveRequest(printer.getId(), true, request.getComment()));
                }
                case "WRITE_OFF" -> {
                    removeFromPrinter(installedCartridge.getId(),
                            buildRemoveRequest(printer.getId(), false, request.getComment()));
                    writeOff(installedCartridge.getId(), request.getComment());
                }
                default -> throw new BadRequestException("Неизвестный сценарий замены: " + request.getRemovedOutcome());
            }
        }

        return installToPrinter(newCartridgeId, buildInstallRequest(printer.getId(), request.getComment()));
    }

    @Transactional
    public CartridgeResponse removeFromPrinter(Long id, RemoveCartridgeInstallationRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);
        Printer printer = getPrinterEntity(request.getPrinterId());
        PrinterInstallation installation = printerInstallationRepository.findByCartridgeIdAndPrinterId(id, printer.getId())
                .orElseThrow(() -> new NotFoundException("Для этого картриджа нет установки в выбранный принтер"));
        int remainingInstalledQuantity = getInstalledQuantity(id) - request.getQuantity();

        if (installation.getQuantity() < request.getQuantity()) {
            throw new ConflictException("Нельзя снять больше картриджей, чем установлено в выбранный принтер");
        }

        installation.setQuantity(installation.getQuantity() - request.getQuantity());
        if (installation.getQuantity() == 0) {
            printerInstallationRepository.delete(installation);
        } else {
            printerInstallationRepository.save(installation);
        }

        boolean returnToStock = !Boolean.FALSE.equals(request.getReturnToStock());
        if (returnToStock) {
            cartridge.setQuantity(cartridge.getQuantity() + request.getQuantity());
            cartridge.setStatus(CartridgeStatus.IN_STOCK);
            if (Boolean.TRUE.equals(cartridge.getRefillable())) {
                cartridge.setEmpty(true);
            }
        } else if (remainingInstalledQuantity <= 0 && cartridge.getQuantity() == 0) {
            cartridge.setStatus(CartridgeStatus.WRITTEN_OFF);
            cartridge.setEmpty(false);
        }

        if (request.getComment() != null && !request.getComment().isBlank()) {
            cartridge.setComment(request.getComment());
        }

        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_REMOVED,
                saved.getCartridgeModel().getName(),
                "Снят с точки \"" + printer.getName() + "\". "
                        + (returnToStock
                        ? (Boolean.TRUE.equals(saved.getRefillable()) ? "Возвращен в остаток как пустой." : "Возвращен в остаток.")
                        : "Снят без возврата в остаток."),
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse writeOff(Long id, String comment) {
        Cartridge cartridge = getCartridgeEntity(id);

        if (cartridge.getStatus() == CartridgeStatus.ON_REFILL) {
            throw new ConflictException("Нельзя списать картридж, пока он находится на заправке");
        }

        cartridge.setStatus(CartridgeStatus.WRITTEN_OFF);
        cartridge.setQuantity(0);

        if (comment != null && !comment.isBlank()) {
            cartridge.setComment(comment);
        }

        Cartridge saved = cartridgeRepository.save(cartridge);
        actionLogService.log(
                ActionLogType.CARTRIDGE_WRITTEN_OFF,
                saved.getCartridgeModel().getName(),
                "Списан. Комментарий: " + safeText(comment),
                "Система"
        );
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Cartridge cartridge = getCartridgeEntity(id);

        if (cartridge.getStatus() == CartridgeStatus.ON_REFILL) {
            throw new ConflictException("Нельзя удалить картриджный остаток, пока он на заправке");
        }

        if (getInstalledQuantity(id) > 0) {
            throw new ConflictException("Нельзя удалить картриджный остаток, пока часть количества установлена");
        }

        actionLogService.log(
                ActionLogType.CARTRIDGE_DELETED,
                cartridge.getCartridgeModel().getName(),
                "Остаток удален из системы",
                "Система"
        );
        cartridgeRepository.delete(cartridge);
    }

    private Cartridge getCartridgeEntity(Long id) {
        return cartridgeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Картридж не найден: " + id));
    }

    private InstallCartridgeRequest buildInstallRequest(Long printerId, String comment) {
        InstallCartridgeRequest request = new InstallCartridgeRequest();
        request.setPrinterId(printerId);
        request.setQuantity(1);
        request.setComment(comment);
        return request;
    }

    private RemoveCartridgeInstallationRequest buildRemoveRequest(Long printerId, boolean returnToStock, String comment) {
        RemoveCartridgeInstallationRequest request = new RemoveCartridgeInstallationRequest();
        request.setPrinterId(printerId);
        request.setQuantity(1);
        request.setReturnToStock(returnToStock);
        request.setComment(comment);
        return request;
    }

    private Cartridge createInstalledCartridge(Cartridge source, String comment) {
        Cartridge installationCartridge = Cartridge.builder()
                .inventoryCode(resolveInventoryCode(null))
                .cartridgeModel(source.getCartridgeModel())
                .department(source.getDepartment())
                .quantity(0)
                .refillable(source.getRefillable())
                .empty(source.getEmpty())
                .status(CartridgeStatus.INSTALLED)
                .refillCount(source.getRefillCount())
                .lastRefillDate(source.getLastRefillDate())
                .comment(comment != null && !comment.isBlank() ? comment : source.getComment())
                .build();
        return cartridgeRepository.save(installationCartridge);
    }

    private SendToRefillRequest buildSendToRefillRequest(ReplaceCartridgeRequest source) {
        SendToRefillRequest request = new SendToRefillRequest();
        request.setSentAt(source.getActionDate() != null ? source.getActionDate() : LocalDate.now());
        request.setCreatedBy(source.getCreatedBy());
        request.setComment(source.getComment());
        return request;
    }

    private String resolveInventoryCode(String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            if (cartridgeRepository.existsByInventoryCodeIgnoreCase(requestedCode)) {
                throw new ConflictException("Картридж с таким inventoryCode уже существует: " + requestedCode);
            }
            return requestedCode.trim();
        }

        String generatedCode;
        do {
            generatedCode = "CRT-" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
        } while (cartridgeRepository.existsByInventoryCodeIgnoreCase(generatedCode));

        return generatedCode;
    }

    private String safeText(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private CartridgeResponse toResponse(Cartridge cartridge) {
        int installedQuantity = getInstalledQuantity(cartridge.getId());
        return CartridgeResponse.builder()
                .id(cartridge.getId())
                .inventoryCode(cartridge.getInventoryCode())
                .cartridgeModelId(
                        cartridge.getCartridgeModel() != null ? cartridge.getCartridgeModel().getId() : null
                )
                .cartridgeModelName(
                        cartridge.getCartridgeModel() != null ? cartridge.getCartridgeModel().getName() : null
                )
                .departmentId(
                        cartridge.getDepartment() != null ? cartridge.getDepartment().getId() : null
                )
                .departmentName(
                        cartridge.getDepartment() != null ? cartridge.getDepartment().getName() : null
                )
                .quantity(cartridge.getQuantity())
                .installedQuantity(installedQuantity)
                .refillable(cartridge.getRefillable())
                .empty(cartridge.getEmpty())
                .status(resolveDisplayStatus(cartridge, installedQuantity))
                .refillCount(cartridge.getRefillCount())
                .lastRefillDate(cartridge.getLastRefillDate())
                .comment(cartridge.getComment())
                .createdAt(cartridge.getCreatedAt())
                .updatedAt(cartridge.getUpdatedAt())
                .build();
    }

    private Department getOrCreateStockDepartment() {
        return departmentRepository.findByNameIgnoreCase(STOCK_DEPARTMENT_NAME)
                .orElseGet(() -> departmentRepository.save(Department.builder()
                        .name(STOCK_DEPARTMENT_NAME)
                        .description("Системный отдел для общего остатка")
                        .build()));
    }

    private Printer getPrinterEntity(Long printerId) {
        return printerRepository.findById(printerId)
                .orElseThrow(() -> new NotFoundException("Принтер не найден: " + printerId));
    }

    private int getInstalledQuantity(Long cartridgeId) {
        if (printerInstallationRepository == null) {
            return 0;
        }
        return printerInstallationRepository.findByCartridgeId(cartridgeId).stream()
                .mapToInt(PrinterInstallation::getQuantity)
                .sum();
    }

    private CartridgeStatus resolveDisplayStatus(Cartridge cartridge, int installedQuantity) {
        if (cartridge.getStatus() == CartridgeStatus.WRITTEN_OFF || cartridge.getStatus() == CartridgeStatus.ON_REFILL) {
            return cartridge.getStatus();
        }
        if (installedQuantity > 0 && cartridge.getQuantity() == 0) {
            return CartridgeStatus.INSTALLED;
        }
        return CartridgeStatus.IN_STOCK;
    }
}
