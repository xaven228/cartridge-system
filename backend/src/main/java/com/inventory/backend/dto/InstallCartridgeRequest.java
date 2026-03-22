package com.inventory.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InstallCartridgeRequest {

    @NotNull
    private Long printerId;

    @NotNull
    @Min(1)
    private Integer quantity;

    private String comment;
}
