package com.inventory.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UpdateDepartmentRequest {

    @NotBlank
    private String name;

    private String description;

    @Valid
    private List<DepartmentPrinterRequest> printers = new ArrayList<>();

    @Getter
    @Setter
    public static class DepartmentPrinterRequest {
        @NotBlank
        private String name;
        private IdRef cartridgeModel;
    }

    @Getter
    @Setter
    public static class IdRef {
        private Long id;
    }
}
