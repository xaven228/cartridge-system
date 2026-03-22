package com.inventory.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SendToRefillRequest {

    @NotNull
    private LocalDate sentAt;

    @NotBlank
    private String createdBy;

    private String comment;
}