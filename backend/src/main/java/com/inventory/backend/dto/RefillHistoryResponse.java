package com.inventory.backend.dto;

import com.inventory.backend.entity.RefillStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefillHistoryResponse {

    private Long id;
    private Long cartridgeId;
    private String inventoryCode;
    private LocalDate sentAt;
    private LocalDate returnedAt;
    private RefillStatus status;
    private String comment;
    private String createdBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
