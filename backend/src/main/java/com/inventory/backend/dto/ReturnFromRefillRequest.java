package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReturnFromRefillRequest {

    @NotNull
    private LocalDate returnedAt;

    @NotBlank
    private String createdBy;

    @NotNull
    @Positive
    private Integer quantity;

    private String comment;
}
