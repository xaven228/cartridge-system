package com.inventory.backend.service;

import com.inventory.backend.dto.AdjustQuantityRequest;
import com.inventory.backend.dto.CartridgeResponse;
import com.inventory.backend.dto.CreateCartridgeRequest;
import com.inventory.backend.dto.ReturnFromRefillRequest;
import com.inventory.backend.dto.SendToRefillRequest;
import com.inventory.backend.entity.Cartridge;
import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.entity.CartridgeStatus;
import com.inventory.backend.entity.Department;
import com.inventory.backend.entity.RefillHistory;
import com.inventory.backend.entity.RefillStatus;
import com.inventory.backend.exception.BadRequestException;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.exception.NotFoundException;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.DepartmentRepository;
import com.inventory.backend.repository.RefillHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartridgeService {

    private final CartridgeRepository cartridgeRepository;
    private final CartridgeModelRepository cartridgeModelRepository;
    private final DepartmentRepository departmentRepository;
    private final RefillHistoryRepository refillHistoryRepository;

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

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Отдел не найден: " + request.getDepartmentId()));

        boolean inventoryCodeExists = cartridgeRepository.findAll()
                .stream()
                .anyMatch(c -> c.getInventoryCode().equalsIgnoreCase(request.getInventoryCode()));

        if (inventoryCodeExists) {
            throw new ConflictException("Картридж с таким inventoryCode уже существует: " + request.getInventoryCode());
        }

        Cartridge cartridge = Cartridge.builder()
                .inventoryCode(request.getInventoryCode())
                .cartridgeModel(cartridgeModel)
                .department(department)
                .quantity(request.getQuantity())
                .status(request.getStatus() != null ? request.getStatus() : CartridgeStatus.IN_STOCK)
                .refillCount(0)
                .comment(request.getComment())
                .build();

        Cartridge saved = cartridgeRepository.save(cartridge);
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
        return toResponse(saved);
    }

    @Transactional
    public CartridgeResponse sendToRefill(Long id, SendToRefillRequest request) {
        Cartridge cartridge = getCartridgeEntity(id);

        if (cartridge.getStatus() == CartridgeStatus.ON_REFILL) {
            throw new ConflictException("Картридж уже находится на заправке");
        }

        if (cartridge.getStatus() == CartridgeStatus.WRITTEN_OFF) {
            throw new ConflictException("Нельзя отправить на заправку списанный картридж");
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
        cartridge.setRefillCount(cartridge.getRefillCount() + 1);
        cartridge.setLastRefillDate(request.getReturnedAt());

        Cartridge saved = cartridgeRepository.save(cartridge);
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
        return toResponse(saved);
    }

    private Cartridge getCartridgeEntity(Long id) {
        return cartridgeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Картридж не найден: " + id));
    }

    private CartridgeResponse toResponse(Cartridge cartridge) {
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
                .status(cartridge.getStatus())
                .refillCount(cartridge.getRefillCount())
                .lastRefillDate(cartridge.getLastRefillDate())
                .comment(cartridge.getComment())
                .createdAt(cartridge.getCreatedAt())
                .updatedAt(cartridge.getUpdatedAt())
                .build();
    }
}