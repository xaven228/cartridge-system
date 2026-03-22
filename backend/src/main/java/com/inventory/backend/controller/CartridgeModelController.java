package com.inventory.backend.controller;

import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.service.CartridgeModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cartridge-models")
@RequiredArgsConstructor
@CrossOrigin
public class CartridgeModelController {

    private final CartridgeModelService cartridgeModelService;

    @GetMapping
    public List<CartridgeModel> getAll() {
        return cartridgeModelService.getAll();
    }

    @PostMapping
    public CartridgeModel create(@Valid @RequestBody CartridgeModel cartridgeModel) {
        return cartridgeModelService.create(cartridgeModel);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        cartridgeModelService.delete(id);
    }
}
