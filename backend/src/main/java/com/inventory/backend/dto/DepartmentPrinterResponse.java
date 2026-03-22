package com.inventory.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentPrinterResponse {

    private Long id;
    private String name;
    private Long cartridgeModelId;
    private String cartridgeModelName;
    private LocalDate previousReplacementDate;
    private LocalDate lastReplacementDate;
    private CurrentPrinterInstallationResponse currentInstallation;
}
