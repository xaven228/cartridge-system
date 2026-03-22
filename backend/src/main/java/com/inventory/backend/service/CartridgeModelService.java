package com.inventory.backend.service;

import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.repository.CartridgeModelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartridgeModelService {

    private final CartridgeModelRepository cartridgeModelRepository;

    public List<CartridgeModel> getAll() {
        return cartridgeModelRepository.findAll();
    }

    public CartridgeModel create(CartridgeModel cartridgeModel) {
        boolean exists = cartridgeModelRepository.findAll()
                .stream()
                .anyMatch(model -> model.getName().equalsIgnoreCase(cartridgeModel.getName()));

        if (exists) {
            throw new ConflictException("Модель картриджа уже существует: " + cartridgeModel.getName());
        }

        return cartridgeModelRepository.save(cartridgeModel);
    }
}