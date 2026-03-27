package com.inventory.backend.dto;

import com.inventory.backend.entity.PrinterType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UpsertPrinterRequest {

    @NotBlank
    private String name;

    @NotNull
    private Long departmentId;

    @NotNull
    private PrinterType printerType;

    @Valid
    private List<PrinterSlotRequest> slots = new ArrayList<>();

    @Getter
    @Setter
    public static class PrinterSlotRequest {
        @NotBlank
        private String name;
        @NotNull
        private Long cartridgeModelId;
    }
}
