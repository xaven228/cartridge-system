package com.inventory.backend.service

import com.inventory.backend.dto.RefillHistoryResponse
import com.inventory.backend.entity.RefillHistory
import com.inventory.backend.repository.RefillHistoryRepository
import org.springframework.stereotype.Service

@Service
class RefillHistoryService(
    private val refillHistoryRepository: RefillHistoryRepository,
) {
    fun getByCartridgeId(cartridgeId: Long): List<RefillHistoryResponse> =
        refillHistoryRepository.findByCartridgeIdOrderByIdDesc(cartridgeId).map(::toResponse)

    fun create(refillHistory: RefillHistory): RefillHistoryResponse =
        toResponse(refillHistoryRepository.save(refillHistory))

    private fun toResponse(refillHistory: RefillHistory): RefillHistoryResponse =
        RefillHistoryResponse(
            id = refillHistory.id,
            cartridgeId = refillHistory.cartridge?.id,
            inventoryCode = refillHistory.cartridge?.inventoryCode,
            sentAt = refillHistory.sentAt,
            returnedAt = refillHistory.returnedAt,
            status = refillHistory.status,
            quantity = refillHistory.quantity,
            comment = refillHistory.comment,
            createdBy = refillHistory.createdBy,
            createdAt = refillHistory.createdAt,
            updatedAt = refillHistory.updatedAt,
        )
}
