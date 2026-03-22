package com.inventory.backend.dto;

import com.inventory.backend.entity.CartridgeStatus;
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
public class CartridgeResponse {

    private Long id;
    private String inventoryCode;

    private Long cartridgeModelId;
    private String cartridgeModelName;

    private Long departmentId;
    private String departmentName;

    private Integer quantity;
    private CartridgeStatus status;
    private Integer refillCount;
    private LocalDate lastRefillDate;
    private String comment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
