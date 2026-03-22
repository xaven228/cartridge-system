package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReplaceCartridgeRequest {

    @NotNull
    private Long printerId;

    @NotBlank
    private String removedOutcome;

    private String comment;

    private LocalDate actionDate;

    private String createdBy;
}
