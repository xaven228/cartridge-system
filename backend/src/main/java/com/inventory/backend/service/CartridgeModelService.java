package com.inventory.backend.service;

import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.exception.NotFoundException;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.PrinterSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartridgeModelService {

    private final CartridgeModelRepository cartridgeModelRepository;
    private final CartridgeRepository cartridgeRepository;
    private final PrinterSlotRepository printerSlotRepository;
    private final ActionLogService actionLogService;

    public List<CartridgeModel> getAll() {
        return cartridgeModelRepository.findAll();
    }

    public CartridgeModel create(CartridgeModel cartridgeModel) {
        if (cartridgeModelRepository.existsByNameIgnoreCase(cartridgeModel.getName())) {
            throw new ConflictException("Модель картриджа уже существует: " + cartridgeModel.getName());
        }

        if (cartridgeModel.getRefillable() == null) {
            cartridgeModel.setRefillable(true);
        }
        if (cartridgeModel.getMinimumQuantity() == null || cartridgeModel.getMinimumQuantity() < 0) {
            cartridgeModel.setMinimumQuantity(0);
        }

        CartridgeModel saved = cartridgeModelRepository.save(cartridgeModel);
        actionLogService.log(
                ActionLogType.CARTRIDGE_MODEL_CREATED,
                saved.getName(),
                "Создана модель картриджа",
                "Система"
        );
        return saved;
    }

    public CartridgeModel update(Long id, CartridgeModel cartridgeModel) {
        CartridgeModel existing = cartridgeModelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Модель картриджа не найдена: " + id));

        if (!existing.getName().equalsIgnoreCase(cartridgeModel.getName())
                && cartridgeModelRepository.existsByNameIgnoreCase(cartridgeModel.getName())) {
            throw new ConflictException("Модель картриджа уже существует: " + cartridgeModel.getName());
        }

        existing.setName(cartridgeModel.getName());
        existing.setRefillable(cartridgeModel.getRefillable() != null ? cartridgeModel.getRefillable() : existing.getRefillable());
        existing.setMinimumQuantity(
                cartridgeModel.getMinimumQuantity() == null || cartridgeModel.getMinimumQuantity() < 0
                        ? 0
                        : cartridgeModel.getMinimumQuantity()
        );

        CartridgeModel saved = cartridgeModelRepository.save(existing);
        actionLogService.log(
                ActionLogType.CARTRIDGE_MODEL_CREATED,
                saved.getName(),
                "Параметры модели обновлены",
                "Система"
        );
        return saved;
    }

    public void delete(Long id) {
        CartridgeModel model = cartridgeModelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Модель картриджа не найдена: " + id));

        if (cartridgeRepository.countByCartridgeModelId(id) > 0) {
            throw new ConflictException("Нельзя удалить модель, пока по ней есть остатки картриджей");
        }

        if (printerSlotRepository.countByCartridgeModelId(id) > 0) {
            throw new ConflictException("Нельзя удалить модель, пока она назначена в точках замены отделов");
        }

        actionLogService.log(
                ActionLogType.CARTRIDGE_MODEL_DELETED,
                model.getName(),
                "Модель картриджа удалена",
                "Система"
        );
        cartridgeModelRepository.delete(model);
    }
}
