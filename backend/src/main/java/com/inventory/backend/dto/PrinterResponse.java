package com.inventory.backend.dto;

import com.inventory.backend.entity.PrinterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrinterResponse {
    private Long id;
    private String name;
    private Long departmentId;
    private String departmentName;
    private PrinterType printerType;
    private List<PrinterSlotResponse> slots;
}
