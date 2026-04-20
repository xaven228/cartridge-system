package com.inventory.backend.service

import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.CartridgeModel
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.CartridgeModelRepository
import com.inventory.backend.repository.CartridgeRepository
import com.inventory.backend.repository.PrinterSlotRepository
import org.springframework.stereotype.Service

@Service
class CartridgeModelService(
    private val cartridgeModelRepository: CartridgeModelRepository,
    private val cartridgeRepository: CartridgeRepository,
    private val printerSlotRepository: PrinterSlotRepository,
    private val actionLogService: ActionLogService,
) {
    fun getAll(): List<CartridgeModel> = cartridgeModelRepository.findAll()

    fun create(cartridgeModel: CartridgeModel): CartridgeModel {
        cartridgeModel.name = cartridgeModel.name.trim()
        if (cartridgeModelRepository.existsByNameIgnoreCase(cartridgeModel.name)) {
            throw ConflictException("Модель картриджа уже существует: ${cartridgeModel.name}")
        }

        if (cartridgeModel.refillable == null) {
            cartridgeModel.refillable = true
        }
        if (cartridgeModel.minimumQuantity == null || cartridgeModel.minimumQuantity!! < 0) {
            cartridgeModel.minimumQuantity = 0
        }
        cartridgeModel.compatiblePrinterModels = normalizeCompatiblePrinterModels(cartridgeModel.compatiblePrinterModels)

        val saved = cartridgeModelRepository.save(cartridgeModel)
        actionLogService.log(
            ActionLogType.CARTRIDGE_MODEL_CREATED,
            saved.name,
            "Создана модель картриджа",
            "Система",
        )
        return saved
    }

    fun update(id: Long, cartridgeModel: CartridgeModel): CartridgeModel {
        val existing = cartridgeModelRepository.findById(id)
            .orElseThrow { NotFoundException("Модель картриджа не найдена: $id") }
        val normalizedName = cartridgeModel.name.trim()

        if (
            !existing.name.equals(normalizedName, ignoreCase = true) &&
            cartridgeModelRepository.existsByNameIgnoreCase(normalizedName)
        ) {
            throw ConflictException("Модель картриджа уже существует: $normalizedName")
        }

        existing.name = normalizedName
        existing.refillable = cartridgeModel.refillable ?: existing.refillable
        existing.minimumQuantity = if (cartridgeModel.minimumQuantity == null || cartridgeModel.minimumQuantity!! < 0) {
            0
        } else {
            cartridgeModel.minimumQuantity
        }
        existing.compatiblePrinterModels = normalizeCompatiblePrinterModels(cartridgeModel.compatiblePrinterModels)

        val saved = cartridgeModelRepository.save(existing)
        actionLogService.log(
            ActionLogType.CARTRIDGE_MODEL_CREATED,
            saved.name,
            "Параметры модели обновлены",
            "Система",
        )
        return saved
    }

    fun delete(id: Long) {
        val model = cartridgeModelRepository.findById(id)
            .orElseThrow { NotFoundException("Модель картриджа не найдена: $id") }

        if (cartridgeRepository.countByCartridgeModelId(id) > 0) {
            throw ConflictException("Нельзя удалить модель, пока по ней есть остатки картриджей")
        }

        if (printerSlotRepository.countByCartridgeModelId(id) > 0) {
            throw ConflictException("Нельзя удалить модель, пока она назначена в точках замены отделов")
        }

        actionLogService.log(
            ActionLogType.CARTRIDGE_MODEL_DELETED,
            model.name,
            "Модель картриджа удалена",
            "Система",
        )
        cartridgeModelRepository.delete(model)
    }

    private fun normalizeCompatiblePrinterModels(values: List<String>?): MutableList<String> =
        values
            ?.asSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toMutableList()
            ?: mutableListOf()
}
