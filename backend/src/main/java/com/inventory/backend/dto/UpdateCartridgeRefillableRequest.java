package com.inventory.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCartridgeRefillableRequest {

    @NotNull
    private Boolean refillable;
}
