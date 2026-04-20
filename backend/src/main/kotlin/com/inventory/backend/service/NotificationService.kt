package com.inventory.backend.service

import com.inventory.backend.dto.NotificationAlertResponse
import com.inventory.backend.dto.NotificationThresholdResponse
import com.inventory.backend.dto.UpsertNotificationThresholdRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.entity.NotificationThreshold
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.CartridgeModelRepository
import com.inventory.backend.repository.CartridgeRepository
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.NotificationThresholdRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationService(
    private val notificationThresholdRepository: NotificationThresholdRepository,
    private val cartridgeModelRepository: CartridgeModelRepository,
    private val departmentRepository: DepartmentRepository,
    private val cartridgeRepository: CartridgeRepository,
    private val actionLogService: ActionLogService,
) {

    @Transactional(readOnly = true)
    fun getThresholds(): List<NotificationThresholdResponse> = notificationThresholdRepository.findAll()
        .sortedWith(compareBy<NotificationThreshold> { it.cartridgeModel.name.lowercase() }
            .thenBy { it.department?.name?.lowercase() ?: "" })
        .map(::toThresholdResponse)

    @Transactional
    fun createThreshold(request: UpsertNotificationThresholdRequest): NotificationThresholdResponse {
        validateUnique(request, null)

        val model = cartridgeModelRepository.findById(request.cartridgeModelId)
            .orElseThrow { NotFoundException("Модель картриджа не найдена: ${request.cartridgeModelId}") }
        val department = request.departmentId?.let {
            departmentRepository.findById(it).orElseThrow { NotFoundException("Отдел не найден: $it") }
        }

        val saved = notificationThresholdRepository.save(
            NotificationThreshold().apply {
                cartridgeModel = model
                this.department = department
                minimumQuantity = request.minimumQuantity
                active = request.active
                comment = request.comment?.trim()?.ifBlank { null }
            }
        )

        actionLogService.log(
            ActionLogType.THRESHOLD_CREATED,
            saved.cartridgeModel.name,
            "Порог: ${saved.minimumQuantity}, отдел: ${saved.department?.name ?: "все отделы"}",
            "Система"
        )

        return toThresholdResponse(saved)
    }

    @Transactional
    fun updateThreshold(id: Long, request: UpsertNotificationThresholdRequest): NotificationThresholdResponse {
        val threshold = notificationThresholdRepository.findById(id)
            .orElseThrow { NotFoundException("Настройка порога не найдена: $id") }

        validateUnique(request, id)

        val model = cartridgeModelRepository.findById(request.cartridgeModelId)
            .orElseThrow { NotFoundException("Модель картриджа не найдена: ${request.cartridgeModelId}") }
        val department = request.departmentId?.let {
            departmentRepository.findById(it).orElseThrow { NotFoundException("Отдел не найден: $it") }
        }

        threshold.cartridgeModel = model
        threshold.department = department
        threshold.minimumQuantity = request.minimumQuantity
        threshold.active = request.active
        threshold.comment = request.comment?.trim()?.ifBlank { null }

        val saved = notificationThresholdRepository.save(threshold)

        actionLogService.log(
            ActionLogType.THRESHOLD_UPDATED,
            saved.cartridgeModel.name,
            "Порог: ${saved.minimumQuantity}, отдел: ${saved.department?.name ?: "все отделы"}",
            "Система"
        )

        return toThresholdResponse(saved)
    }

    @Transactional
    fun deleteThreshold(id: Long) {
        val threshold = notificationThresholdRepository.findById(id)
            .orElseThrow { NotFoundException("Настройка порога не найдена: $id") }

        actionLogService.log(
            ActionLogType.THRESHOLD_DELETED,
            threshold.cartridgeModel.name,
            "Удален порог для отдела: ${threshold.department?.name ?: "все отделы"}",
            "Система"
        )
        notificationThresholdRepository.delete(threshold)
    }

    @Transactional(readOnly = true)
    fun getAlerts(): List<NotificationAlertResponse> {
        val activeThresholds = notificationThresholdRepository.findByActiveTrueOrderByIdAsc()
        val defaultByModel = mutableMapOf<Long, Int>()
        val departmentSpecific = mutableMapOf<Pair<Long, Long>, Int>()

        for (item in activeThresholds) {
            val modelId = item.cartridgeModel.id!!
            val department = item.department
            if (department == null) {
                defaultByModel[modelId] = item.minimumQuantity
            } else {
                departmentSpecific[modelId to department.id!!] = item.minimumQuantity
            }
        }

        val modelMinimums = cartridgeModelRepository.findAll().associate { it.id!! to it.minimumQuantity }

        val quantities = mutableMapOf<Pair<Long, Long>, Int>()
        cartridgeRepository.findAll()
            .asSequence()
            .filter { it.empty == false }
            .filter { it.status == CartridgeStatus.IN_STOCK || it.status == CartridgeStatus.RESERVE }
            .forEach { cartridge ->
            val key = cartridge.cartridgeModel.id!! to cartridge.department.id!!
            quantities[key] = (quantities[key] ?: 0) + cartridge.quantity
        }

        val candidateKeys = mutableSetOf<Pair<Long, Long>>()
        candidateKeys.addAll(quantities.keys)
        candidateKeys.addAll(departmentSpecific.keys)

        val modelNames = cartridgeModelRepository.findAll().associate { it.id!! to it.name }
        val departmentNames = departmentRepository.findAll().associate { it.id!! to it.name }

        return candidateKeys.mapNotNull { key ->
            val (modelId, departmentId) = key
            val threshold = departmentSpecific[key] ?: defaultByModel[modelId] ?: modelMinimums[modelId] ?: 0
            if (threshold <= 0) {
                return@mapNotNull null
            }
            val currentQuantity = quantities[key] ?: 0
            if (currentQuantity > threshold) {
                return@mapNotNull null
            }

            NotificationAlertResponse(
                cartridgeModelId = modelId,
                cartridgeModelName = modelNames[modelId] ?: "Модель #$modelId",
                departmentId = departmentId,
                departmentName = departmentNames[departmentId] ?: "Отдел #$departmentId",
                currentQuantity = currentQuantity,
                thresholdQuantity = threshold,
                source = if (departmentSpecific.containsKey(key)) "DEPARTMENT" else if (defaultByModel.containsKey(modelId)) "MODEL_DEFAULT" else "MODEL_MINIMUM",
            )
        }
            .sortedWith(compareBy<NotificationAlertResponse> { it.currentQuantity - it.thresholdQuantity }
                .thenBy { it.departmentName }
                .thenBy { it.cartridgeModelName })
    }

    private fun validateUnique(request: UpsertNotificationThresholdRequest, currentId: Long?) {
        val exists = if (request.departmentId == null) {
            if (currentId == null) {
                notificationThresholdRepository.existsByCartridgeModelIdAndDepartmentIdIsNull(request.cartridgeModelId)
            } else {
                notificationThresholdRepository.existsByCartridgeModelIdAndDepartmentIdIsNullAndIdNot(request.cartridgeModelId, currentId)
            }
        } else {
            if (currentId == null) {
                notificationThresholdRepository.existsByCartridgeModelIdAndDepartmentId(request.cartridgeModelId, request.departmentId)
            } else {
                notificationThresholdRepository.existsByCartridgeModelIdAndDepartmentIdAndIdNot(request.cartridgeModelId, request.departmentId, currentId)
            }
        }

        if (exists) {
            throw ConflictException("Для этой модели и отдела порог уже настроен")
        }
    }

    private fun toThresholdResponse(entity: NotificationThreshold): NotificationThresholdResponse = NotificationThresholdResponse(
        id = entity.id!!,
        cartridgeModelId = entity.cartridgeModel.id!!,
        cartridgeModelName = entity.cartridgeModel.name,
        departmentId = entity.department?.id,
        departmentName = entity.department?.name,
        minimumQuantity = entity.minimumQuantity,
        active = entity.active == true,
        comment = entity.comment,
    )
}
