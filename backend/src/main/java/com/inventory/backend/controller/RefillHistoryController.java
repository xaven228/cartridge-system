package com.inventory.backend.controller;

import com.inventory.backend.dto.RefillHistoryResponse;
import com.inventory.backend.entity.RefillHistory;
import com.inventory.backend.service.RefillHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/refill-history")
@RequiredArgsConstructor
@CrossOrigin
public class RefillHistoryController {

    private final RefillHistoryService refillHistoryService;

    @GetMapping("/cartridge/{cartridgeId}")
    public List<RefillHistoryResponse> getByCartridgeId(@PathVariable Long cartridgeId) {
        return refillHistoryService.getByCartridgeId(cartridgeId);
    }

    @PostMapping
    public RefillHistoryResponse create(@RequestBody RefillHistory refillHistory) {
        return refillHistoryService.create(refillHistory);
    }
}