package com.inventory.backend.service;

import com.inventory.backend.dto.RefillHistoryResponse;
import com.inventory.backend.entity.RefillHistory;
import com.inventory.backend.repository.RefillHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RefillHistoryService {

    private final RefillHistoryRepository refillHistoryRepository;

    public List<RefillHistoryResponse> getByCartridgeId(Long cartridgeId) {
        return refillHistoryRepository.findByCartridgeIdOrderByIdDesc(cartridgeId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public RefillHistoryResponse create(RefillHistory refillHistory) {
        RefillHistory saved = refillHistoryRepository.save(refillHistory);
        return toResponse(saved);
    }

    private RefillHistoryResponse toResponse(RefillHistory refillHistory) {
        return RefillHistoryResponse.builder()
                .id(refillHistory.getId())
                .cartridgeId(
                        refillHistory.getCartridge() != null ? refillHistory.getCartridge().getId() : null
                )
                .inventoryCode(
                        refillHistory.getCartridge() != null ? refillHistory.getCartridge().getInventoryCode() : null
                )
                .sentAt(refillHistory.getSentAt())
                .returnedAt(refillHistory.getReturnedAt())
                .status(refillHistory.getStatus())
                .comment(refillHistory.getComment())
                .createdBy(refillHistory.getCreatedBy())
                .createdAt(refillHistory.getCreatedAt())
                .updatedAt(refillHistory.getUpdatedAt())
                .build();
    }
}