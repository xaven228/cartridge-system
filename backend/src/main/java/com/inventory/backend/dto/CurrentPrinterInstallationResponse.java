package com.inventory.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentPrinterInstallationResponse {

    private Long cartridgeId;
    private String inventoryCode;
    private String cartridgeModelName;
    private Integer quantity;
}
