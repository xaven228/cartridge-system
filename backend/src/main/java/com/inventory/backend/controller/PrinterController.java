package com.inventory.backend.controller;

import com.inventory.backend.dto.PrinterResponse;
import com.inventory.backend.dto.UpsertPrinterRequest;
import com.inventory.backend.service.PrinterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/printers")
@RequiredArgsConstructor
@CrossOrigin
public class PrinterController {

    private final PrinterService printerService;

    @GetMapping
    public List<PrinterResponse> getAll() {
        return printerService.getAll();
    }

    @PostMapping
    public PrinterResponse create(@Valid @RequestBody UpsertPrinterRequest request) {
        return printerService.create(request);
    }

    @PutMapping("/{id}")
    public PrinterResponse update(@PathVariable Long id, @Valid @RequestBody UpsertPrinterRequest request) {
        return printerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        printerService.delete(id);
    }
}
