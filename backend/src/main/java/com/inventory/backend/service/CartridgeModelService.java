package com.inventory.backend.service;

import com.inventory.backend.entity.CartridgeModel;
import com.inventory.backend.entity.ActionLogType;
import com.inventory.backend.exception.ConflictException;
import com.inventory.backend.exception.NotFoundException;
import com.inventory.backend.repository.CartridgeModelRepository;
import com.inventory.backend.repository.CartridgeRepository;
import com.inventory.backend.repository.PrinterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartridgeModelService {

    private final CartridgeModelRepository cartridgeModelRepository;
    private final CartridgeRepository cartridgeRepository;
    private final PrinterRepository printerRepository;
    private final ActionLogService actionLogService;

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

        if (cartridgeModel.getRefillable() == null) {
            cartridgeModel.setRefillable(true);
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

    public void delete(Long id) {
        CartridgeModel model = cartridgeModelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Модель картриджа не найдена: " + id));

        if (cartridgeRepository.countByCartridgeModelId(id) > 0) {
            throw new ConflictException("Нельзя удалить модель, пока по ней есть остатки картриджей");
        }

        if (printerRepository.countByCartridgeModelId(id) > 0) {
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
