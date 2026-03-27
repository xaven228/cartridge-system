package com.inventory.backend.controller;

import com.inventory.backend.dto.AdjustQuantityRequest;
import com.inventory.backend.dto.CartridgeResponse;
import com.inventory.backend.dto.CreateCartridgeRequest;
import com.inventory.backend.dto.InstallCartridgeRequest;
import com.inventory.backend.dto.ReplaceCartridgeRequest;
import com.inventory.backend.dto.ReturnFromRefillRequest;
import com.inventory.backend.dto.RemoveCartridgeInstallationRequest;
import com.inventory.backend.dto.SendToRefillRequest;
import com.inventory.backend.dto.UpdateCartridgeRefillableRequest;
import com.inventory.backend.entity.CartridgeStatus;
import com.inventory.backend.service.CartridgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cartridges")
@RequiredArgsConstructor
@CrossOrigin
public class CartridgeController {

    private final CartridgeService cartridgeService;

    @GetMapping
    public List<CartridgeResponse> getAll(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) CartridgeStatus status
    ) {
        return cartridgeService.getAll(departmentId, status);
    }

    @GetMapping("/{id}")
    public CartridgeResponse getById(@PathVariable Long id) {
        return cartridgeService.getById(id);
    }

    @PostMapping
    public CartridgeResponse create(@Valid @RequestBody CreateCartridgeRequest request) {
        return cartridgeService.create(request);
    }

    @PatchMapping("/{id}/quantity")
    public CartridgeResponse adjustQuantity(
            @PathVariable Long id,
            @Valid @RequestBody AdjustQuantityRequest request
    ) {
        return cartridgeService.adjustQuantity(id, request);
    }

    @PostMapping("/{id}/send-to-refill")
    public CartridgeResponse sendToRefill(
            @PathVariable Long id,
            @Valid @RequestBody SendToRefillRequest request
    ) {
        return cartridgeService.sendToRefill(id, request);
    }

    @PostMapping("/{id}/return-from-refill")
    public CartridgeResponse returnFromRefill(
            @PathVariable Long id,
            @Valid @RequestBody ReturnFromRefillRequest request
    ) {
        return cartridgeService.returnFromRefill(id, request);
    }

    @PostMapping("/{id}/install")
    public CartridgeResponse installToPrinter(
            @PathVariable Long id,
            @Valid @RequestBody InstallCartridgeRequest request
    ) {
        return cartridgeService.installToPrinter(id, request);
    }

    @PostMapping("/{id}/replace")
    public CartridgeResponse replaceInPrinter(
            @PathVariable Long id,
            @Valid @RequestBody ReplaceCartridgeRequest request
    ) {
        return cartridgeService.replaceInPrinter(id, request);
    }

    @PostMapping("/{id}/remove-installation")
    public CartridgeResponse removeFromPrinter(
            @PathVariable Long id,
            @Valid @RequestBody RemoveCartridgeInstallationRequest request
    ) {
        return cartridgeService.removeFromPrinter(id, request);
    }

    @PatchMapping("/{id}/refillable")
    public CartridgeResponse updateRefillable(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCartridgeRefillableRequest request
    ) {
        return cartridgeService.updateRefillable(id, request);
    }

    @PostMapping("/{id}/write-off")
    public CartridgeResponse writeOff(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String comment = body != null ? body.get("comment") : null;
        return cartridgeService.writeOff(id, comment);
    }

    @PostMapping("/{id}/mark-empty")
    public CartridgeResponse markInstalledAsEmpty(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String comment = body != null ? body.get("comment") : null;
        return cartridgeService.markInstalledAsEmpty(id, comment);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        cartridgeService.delete(id);
    }
}
