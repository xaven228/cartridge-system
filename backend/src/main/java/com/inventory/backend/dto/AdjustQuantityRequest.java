package com.inventory.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdjustQuantityRequest {

    @NotNull
    private Integer quantity;

    private String comment;
}