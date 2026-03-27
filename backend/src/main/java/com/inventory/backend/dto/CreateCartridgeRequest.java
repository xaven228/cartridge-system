package com.inventory.backend.dto;

import com.inventory.backend.entity.CartridgeStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCartridgeRequest {

    private String inventoryCode;

    @NotNull
    private Long cartridgeModelId;

    private Long departmentId;

    @NotNull
    @Min(0)
    private Integer quantity;

    private Boolean refillable;

    private CartridgeStatus status;

    private String comment;
}
