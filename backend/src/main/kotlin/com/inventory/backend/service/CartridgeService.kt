package com.inventory.backend.service

import com.inventory.backend.dto.AdjustQuantityRequest
import com.inventory.backend.dto.CartridgeResponse
import com.inventory.backend.dto.CreateCartridgeRequest
import com.inventory.backend.dto.InstallCartridgeRequest
import com.inventory.backend.dto.RemoveCartridgeInstallationRequest
import com.inventory.backend.dto.ReplaceCartridgeRequest
import com.inventory.backend.dto.ReturnFromRefillRequest
import com.inventory.backend.dto.SendToRefillRequest
import com.inventory.backend.dto.UpdateCartridgeRefillableRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.Cartridge
import com.inventory.backend.entity.CartridgeModel
import com.inventory.backend.entity.CartridgeStatus
import com.inventory.backend.entity.Department
import com.inventory.backend.entity.PrinterInstallation
import com.inventory.backend.entity.PrinterSlot
import com.inventory.backend.entity.RefillHistory
import com.inventory.backend.entity.RefillStatus
import com.inventory.backend.exception.BadRequestException
import com.inventory.backend.exception.ConflictException
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.CartridgeModelRepository
import com.inventory.backend.repository.CartridgeRepository
import com.inventory.backend.repository.DepartmentRepository
import com.inventory.backend.repository.PrinterInstallationRepository
import com.inventory.backend.repository.PrinterRepository
import com.inventory.backend.repository.PrinterSlotRepository
import com.inventory.backend.repository.RefillHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@Service
class CartridgeService(
    private val cartridgeRepository: CartridgeRepository,
    private val cartridgeModelRepository: CartridgeModelRepository,
    private val departmentRepository: DepartmentRepository,
    private val printerRepository: PrinterRepository,
    private val printerSlotRepository: PrinterSlotRepository,
    private val printerInstallationRepository: PrinterInstallationRepository,
    private val refillHistoryRepository: RefillHistoryRepository,
    private val actionLogService: ActionLogService,
) {
    fun getAll(departmentId: Long?, status: CartridgeStatus?): List<CartridgeResponse> {
        val cartridges = when {
            departmentId != null && status != null -> cartridgeRepository.findByDepartmentIdAndStatus(departmentId, status)
            departmentId != null -> cartridgeRepository.findByDepartmentId(departmentId)
            status != null -> cartridgeRepository.findByStatus(status)
            else -> cartridgeRepository.findAll()
        }

        return cartridges.map(::toResponse)
    }

    fun getById(id: Long): CartridgeResponse = toResponse(getCartridgeEntity(id))

    @Transactional
    fun create(request: CreateCartridgeRequest): CartridgeResponse {
        val cartridgeModel = cartridgeModelRepository.findById(request.cartridgeModelId!!)
            .orElseThrow { NotFoundException("Модель картриджа не найдена: ${request.cartridgeModelId}") }

        val department = request.departmentId?.let {
            departmentRepository.findById(it).orElseThrow { NotFoundException("Отдел не найден: $it") }
        } ?: getOrCreateStockDepartment()

        val refillable = request.refillable ?: cartridgeModel.refillable
        val targetStatus = request.status ?: CartridgeStatus.IN_STOCK
        val existingStock = findCompatibleStockForIncrement(request, cartridgeModel, department, targetStatus, refillable)

        if (existingStock != null) {
            existingStock.quantity = existingStock.quantity + request.quantity!!
            if (existingStock.comment.isNullOrBlank() && !request.comment.isNullOrBlank()) {
                existingStock.comment = request.comment
            }

            val saved = cartridgeRepository.save(existingStock)
            actionLogService.log(
                ActionLogType.CARTRIDGE_CREATED,
                saved.cartridgeModel.name,
                "Приход в остаток: ${request.quantity} шт., тип: ${if (saved.refillable == true) "перезаправляемый" else "одноразовый"}",
                "Система",
            )
            return toResponse(saved)
        }

        val cartridge = Cartridge().apply {
            inventoryCode = resolveInventoryCode(request.inventoryCode)
            this.cartridgeModel = cartridgeModel
            this.department = department
            quantity = request.quantity!!
            this.refillable = refillable ?: true
            empty = false
            status = targetStatus
            refillCount = 0
            comment = request.comment
        }

        val saved = cartridgeRepository.save(cartridge)
        actionLogService.log(
            ActionLogType.CARTRIDGE_CREATED,
            saved.cartridgeModel.name,
            "Приход в остаток: ${saved.quantity} шт., тип: ${if (saved.refillable == true) "перезаправляемый" else "одноразовый"}",
            "Система",
        )
        return toResponse(saved)
    }

    private fun findCompatibleStockForIncrement(
        request: CreateCartridgeRequest,
        cartridgeModel: CartridgeModel,
        department: Department,
        targetStatus: CartridgeStatus,
        refillable: Boolean?,
    ): Cartridge? {
        if (!request.inventoryCode.isNullOrBlank()) {
            return null
        }
        if (targetStatus != CartridgeStatus.IN_STOCK && targetStatus != CartridgeStatus.RESERVE) {
            return null
        }

        return findCompatibleAvailableRow(
            departmentId = department.id!!,
            cartridgeModelId = cartridgeModel.id!!,
            status = targetStatus,
            refillable = refillable,
            empty = false,
            excludeId = null,
        )
    }

    @Transactional
    fun adjustQuantity(id: Long, request: AdjustQuantityRequest): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)

        if (cartridge.status == CartridgeStatus.WRITTEN_OFF) {
            throw ConflictException("Нельзя изменять количество у списанного картриджа")
        }
        if ((request.quantity ?: 0) < 0) {
            throw BadRequestException("Количество не может быть отрицательным")
        }

        cartridge.quantity = request.quantity!!
        if (!request.comment.isNullOrBlank()) {
            cartridge.comment = request.comment
        }

        val saved = cartridgeRepository.save(cartridge)
        actionLogService.log(
            ActionLogType.CARTRIDGE_QUANTITY_CHANGED,
            saved.cartridgeModel.name,
            "Новый остаток: ${saved.quantity} шт.",
            "Система",
        )
        return toResponse(saved)
    }

    @Transactional
    fun sendToRefill(id: Long, request: SendToRefillRequest): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)

        if (cartridge.refillable == false) {
            throw ConflictException("Этот тип картриджа не заправляется и должен списываться")
        }
        if (cartridge.status == CartridgeStatus.ON_REFILL) {
            throw ConflictException("Картридж уже находится на заправке")
        }
        if (cartridge.status == CartridgeStatus.WRITTEN_OFF) {
            throw ConflictException("Нельзя отправить на заправку списанный картридж")
        }
        if (cartridge.empty == false) {
            throw ConflictException("На заправку можно отправлять только пустой картридж")
        }
        if (request.quantity!! > cartridge.quantity) {
            throw ConflictException("Нельзя отправить на заправку больше, чем есть в остатке")
        }

        val refillBatch = if (request.quantity == cartridge.quantity) {
            cartridge.status = CartridgeStatus.ON_REFILL
            cartridgeRepository.save(cartridge)
        } else {
            cartridge.quantity = cartridge.quantity - request.quantity!!
            cartridgeRepository.save(cartridge)
            cartridgeRepository.save(
                createBatchCartridge(
                    source = cartridge,
                    quantity = request.quantity!!,
                    status = CartridgeStatus.ON_REFILL,
                    empty = true,
                    comment = request.comment,
                ),
            )
        }

        refillHistoryRepository.save(
            RefillHistory().apply {
                this.cartridge = refillBatch
                sentAt = request.sentAt
                status = RefillStatus.SENT
                quantity = request.quantity!!
                comment = request.comment
                createdBy = request.createdBy
            },
        )

        actionLogService.log(
            ActionLogType.CARTRIDGE_SENT_TO_REFILL,
            refillBatch.cartridgeModel.name,
            "Отправлен на заправку: ${request.quantity} шт. Комментарий: ${safeText(request.comment)}",
            request.createdBy,
        )

        return toResponse(refillBatch)
    }

    @Transactional
    fun returnFromRefill(id: Long, request: ReturnFromRefillRequest): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)

        if (cartridge.status != CartridgeStatus.ON_REFILL) {
            throw ConflictException("Картридж не находится на заправке")
        }
        if (request.quantity!! > cartridge.quantity) {
            throw ConflictException("Нельзя вернуть с заправки больше, чем находится в партии")
        }

        val lastSentRecord = refillHistoryRepository
            .findFirstByCartridgeIdAndStatusOrderByIdDesc(id, RefillStatus.SENT)
            .orElseThrow { NotFoundException("Не найдена запись об отправке на заправку") }

        refillHistoryRepository.save(
            RefillHistory().apply {
                this.cartridge = cartridge
                sentAt = lastSentRecord.sentAt
                returnedAt = request.returnedAt
                status = RefillStatus.RETURNED
                quantity = request.quantity!!
                comment = mergeComments(lastSentRecord.comment, request.comment)
                createdBy = request.createdBy
            },
        )

        val saved = moveReturnedBatchToStock(cartridge, request)
        actionLogService.log(
            ActionLogType.CARTRIDGE_RETURNED_FROM_REFILL,
            saved.cartridgeModel.name,
            "Возвращен с заправки: ${request.quantity} шт. Остаток: ${saved.quantity} шт.",
            request.createdBy,
        )
        return toResponse(saved)
    }

    @Transactional
    fun updateRefillable(id: Long, request: UpdateCartridgeRefillableRequest): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)
        cartridge.refillable = request.refillable!!
        val saved = cartridgeRepository.save(cartridge)
        actionLogService.log(
            ActionLogType.CARTRIDGE_REFILLABLE_CHANGED,
            saved.cartridgeModel.name,
            "Тип изменен на: ${if (saved.refillable == true) "перезаправляемый" else "одноразовый"}",
            "Система",
        )
        return toResponse(saved)
    }

    @Transactional
    fun installToPrinter(id: Long, request: InstallCartridgeRequest): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)
        val slot = getPrinterSlotEntity(request.printerId!!)
        val printer = slot.printer

        val slotCartridgeModel = slot.cartridgeModel
        if (slotCartridgeModel != null && slotCartridgeModel.id != cartridge.cartridgeModel.id) {
            throw ConflictException("Для этого принтера назначен другой тип картриджа")
        }
        if (cartridge.status == CartridgeStatus.ON_REFILL) {
            throw ConflictException("Нельзя устанавливать картридж, пока он на заправке")
        }
        if (cartridge.status == CartridgeStatus.WRITTEN_OFF) {
            throw ConflictException("Нельзя устанавливать списанный картридж")
        }
        if (cartridge.status != CartridgeStatus.IN_STOCK && cartridge.status != CartridgeStatus.RESERVE) {
            throw ConflictException("Устанавливать можно только картриджи со склада или из резерва")
        }
        if (cartridge.empty == true) {
            throw ConflictException("Нельзя устанавливать пустой картридж")
        }
        if (cartridge.quantity < request.quantity!!) {
            throw ConflictException("На складе недостаточно картриджей для установки")
        }
        if (printerInstallationRepository.findFirstByPrinterSlotIdAndQuantityGreaterThan(slot.id!!, 0).isPresent) {
            throw ConflictException("В этой точке замены уже установлен картридж")
        }

        val sourceStatus = cartridge.status
        val installationCartridge = if (cartridge.quantity > request.quantity!!) {
            cartridge.quantity = cartridge.quantity - request.quantity!!
            cartridge.status = sourceStatus
            cartridgeRepository.save(cartridge)
            createInstalledCartridge(cartridge, request.comment)
        } else {
            cartridge.quantity = cartridge.quantity - request.quantity!!
            cartridge.status = CartridgeStatus.INSTALLED
            if (!request.comment.isNullOrBlank()) {
                cartridge.comment = request.comment
            }
            cartridgeRepository.save(cartridge)
        }

        printerInstallationRepository.save(
            PrinterInstallation().apply {
                printerSlot = slot
                this.cartridge = installationCartridge
                quantity = request.quantity!!
            },
        )

        slot.previousReplacementDate = slot.lastReplacementDate
        slot.lastReplacementDate = LocalDate.now()
        printerSlotRepository.save(slot)

        actionLogService.log(
            ActionLogType.CARTRIDGE_INSTALLED,
            installationCartridge.cartridgeModel.name,
            "Установлен в точку \"${printer.name}\". Остаток: ${installationCartridge.quantity} шт.",
            "Система",
        )
        return toResponse(installationCartridge)
    }

    @Transactional
    fun replaceInPrinter(newCartridgeId: Long, request: ReplaceCartridgeRequest): CartridgeResponse {
        getCartridgeEntity(newCartridgeId)
        val slot = getPrinterSlotEntity(request.printerId!!)

        val currentInstallation = printerInstallationRepository
            .findFirstByPrinterSlotIdAndQuantityGreaterThan(slot.id!!, 0)
            .orElse(null)

        if (currentInstallation != null) {
            val installedCartridge = currentInstallation.cartridge
            if (installedCartridge.refillable == false) {
                removeFromPrinter(
                    installedCartridge.id!!,
                    buildRemoveRequest(slot.id!!, false, defaultDisposableWriteOffComment(request.comment)),
                )
            } else {
                when (request.removedOutcome!!.trim().uppercase(Locale.ROOT)) {
                    "STOCK" -> removeFromPrinter(installedCartridge.id!!, buildRemoveRequest(slot.id!!, true, request.comment))
                    "REFILL" -> {
                        removeFromPrinter(installedCartridge.id!!, buildRemoveRequest(slot.id!!, true, request.comment))
                        sendToRefill(installedCartridge.id!!, buildSendToRefillRequest(request))
                    }
                    "WRITE_OFF" -> {
                        removeFromPrinter(installedCartridge.id!!, buildRemoveRequest(slot.id!!, false, request.comment))
                        writeOff(installedCartridge.id!!, request.comment)
                    }
                    else -> throw BadRequestException("Неизвестный сценарий замены: ${request.removedOutcome}")
                }
            }
        }

        return installToPrinter(newCartridgeId, buildInstallRequest(slot.id!!, request.comment))
    }

    @Transactional
    fun removeFromPrinter(id: Long, request: RemoveCartridgeInstallationRequest): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)
        val slot = getPrinterSlotEntity(request.printerId!!)
        val printer = slot.printer
        val installation = printerInstallationRepository.findByCartridgeIdAndPrinterSlotId(id, slot.id!!)
            .orElseThrow { NotFoundException("Для этого картриджа нет установки в выбранный принтер") }
        val remainingInstalledQuantity = getInstalledQuantity(id) - request.quantity!!

        if (installation.quantity < request.quantity!!) {
            throw ConflictException("Нельзя снять больше картриджей, чем установлено в выбранный принтер")
        }

        installation.quantity = installation.quantity - request.quantity!!
        if (installation.quantity == 0) {
            printerInstallationRepository.delete(installation)
        } else {
            printerInstallationRepository.save(installation)
        }

        val disposable = cartridge.refillable == false
        val returnToStock = request.returnToStock != false && !disposable
        val effectiveComment = if (disposable) defaultDisposableWriteOffComment(request.comment) else request.comment

        if (returnToStock) {
            val emptyStock = cartridge.refillable == true
            if (remainingInstalledQuantity <= 0) {
                val saved = moveQuantityToStock(
                    source = cartridge,
                    quantity = request.quantity!!,
                    empty = emptyStock,
                    comment = effectiveComment,
                    lastRefillDate = null,
                    incrementRefillCount = false,
                )
                actionLogService.log(
                    ActionLogType.CARTRIDGE_REMOVED,
                    saved.cartridgeModel.name,
                    "Снят с точки \"${printer.name}\". ${if (emptyStock) "Возвращен в остаток как пустой." else "Возвращен в остаток."}",
                    "Система",
                )
                if (saved.id != cartridge.id) {
                    cartridgeRepository.delete(cartridge)
                }
                return toResponse(saved)
            }

            cartridge.quantity = cartridge.quantity + request.quantity!!
            cartridge.status = CartridgeStatus.IN_STOCK
            cartridge.empty = emptyStock
        } else if (remainingInstalledQuantity <= 0 && cartridge.quantity == 0) {
            cartridge.status = CartridgeStatus.WRITTEN_OFF
            cartridge.quantity = 0
            cartridge.empty = false
        }

        if (!effectiveComment.isNullOrBlank()) {
            cartridge.comment = effectiveComment
        }

        val saved = cartridgeRepository.save(cartridge)
        actionLogService.log(
            ActionLogType.CARTRIDGE_REMOVED,
            saved.cartridgeModel.name,
            "Снят с точки \"${printer.name}\". ${
                if (returnToStock) {
                    if (saved.refillable == true) "Возвращен в остаток как пустой." else "Возвращен в остаток."
                } else {
                    if (disposable) "Списан как израсходованный." else "Снят без возврата в остаток."
                }
            }",
            "Система",
        )
        return toResponse(saved)
    }

    @Transactional
    fun writeOff(id: Long, comment: String?): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)

        if (cartridge.status == CartridgeStatus.ON_REFILL) {
            throw ConflictException("Нельзя списать картридж, пока он находится на заправке")
        }

        cartridge.status = CartridgeStatus.WRITTEN_OFF
        cartridge.quantity = 0

        val effectiveComment = if (cartridge.refillable == false) defaultDisposableWriteOffComment(comment) else comment
        if (!effectiveComment.isNullOrBlank()) {
            cartridge.comment = effectiveComment
        }

        val saved = cartridgeRepository.save(cartridge)
        actionLogService.log(
            ActionLogType.CARTRIDGE_WRITTEN_OFF,
            saved.cartridgeModel.name,
            "Списан. Комментарий: ${safeText(effectiveComment)}",
            "Система",
        )
        return toResponse(saved)
    }

    @Transactional
    fun markInstalledAsEmpty(id: Long, comment: String?): CartridgeResponse {
        val cartridge = getCartridgeEntity(id)

        if (cartridge.status == CartridgeStatus.ON_REFILL || cartridge.status == CartridgeStatus.WRITTEN_OFF) {
            throw ConflictException("Нельзя пометить пустым картридж в текущем статусе")
        }
        if (getInstalledQuantity(id) <= 0) {
            throw ConflictException("Пометить пустым можно только установленный картридж")
        }

        cartridge.empty = true
        if (!comment.isNullOrBlank()) {
            cartridge.comment = comment
        }

        val saved = cartridgeRepository.save(cartridge)
        actionLogService.log(
            ActionLogType.CARTRIDGE_MARKED_EMPTY,
            saved.cartridgeModel.name,
            "Помечен как пустой. Комментарий: ${safeText(comment)}",
            "Система",
        )
        return toResponse(saved)
    }

    private fun defaultDisposableWriteOffComment(comment: String?): String =
        if (comment.isNullOrBlank()) DISPOSABLE_WRITE_OFF_COMMENT else comment

    @Transactional
    fun delete(id: Long) {
        val cartridge = getCartridgeEntity(id)

        if (cartridge.status == CartridgeStatus.ON_REFILL) {
            throw ConflictException("Нельзя удалить картриджный остаток, пока он на заправке")
        }
        if (getInstalledQuantity(id) > 0) {
            throw ConflictException("Нельзя удалить картриджный остаток, пока часть количества установлена")
        }

        actionLogService.log(
            ActionLogType.CARTRIDGE_DELETED,
            cartridge.cartridgeModel.name,
            "Остаток удален из системы",
            "Система",
        )
        cartridgeRepository.delete(cartridge)
    }

    private fun getCartridgeEntity(id: Long): Cartridge =
        cartridgeRepository.findById(id).orElseThrow { NotFoundException("Картридж не найден: $id") }

    private fun buildInstallRequest(printerId: Long, comment: String?): InstallCartridgeRequest =
        InstallCartridgeRequest().apply {
            this.printerId = printerId
            quantity = 1
            this.comment = comment
        }

    private fun buildRemoveRequest(
        printerId: Long,
        returnToStock: Boolean,
        comment: String?,
    ): RemoveCartridgeInstallationRequest =
        RemoveCartridgeInstallationRequest().apply {
            this.printerId = printerId
            quantity = 1
            this.returnToStock = returnToStock
            this.comment = comment
        }

    private fun createInstalledCartridge(source: Cartridge, comment: String?): Cartridge =
        cartridgeRepository.save(
            Cartridge().apply {
                inventoryCode = resolveInventoryCode(null)
                cartridgeModel = source.cartridgeModel
                department = source.department
                quantity = 0
                refillable = source.refillable
                empty = source.empty
                status = CartridgeStatus.INSTALLED
                refillCount = source.refillCount
                lastRefillDate = source.lastRefillDate
                this.comment = if (!comment.isNullOrBlank()) comment else source.comment
            },
        )

    private fun buildSendToRefillRequest(source: ReplaceCartridgeRequest): SendToRefillRequest =
        SendToRefillRequest().apply {
            sentAt = source.actionDate ?: LocalDate.now()
            createdBy = source.createdBy
            quantity = 1
            comment = source.comment
        }

    private fun moveReturnedBatchToStock(refillBatch: Cartridge, request: ReturnFromRefillRequest): Cartridge {
        val totalInBatch = refillBatch.quantity
        if (request.quantity == totalInBatch) {
            val saved = moveQuantityToStock(
                source = refillBatch,
                quantity = request.quantity!!,
                empty = false,
                comment = request.comment,
                lastRefillDate = request.returnedAt,
                incrementRefillCount = true,
            )
            if (saved.id != refillBatch.id) {
                cartridgeRepository.delete(refillBatch)
            }
            return saved
        }

        refillBatch.quantity = totalInBatch - request.quantity!!
        cartridgeRepository.save(refillBatch)

        return moveQuantityToStock(
            source = createBatchCartridge(
                source = refillBatch,
                quantity = request.quantity!!,
                status = CartridgeStatus.IN_STOCK,
                empty = false,
                comment = request.comment,
            ),
            quantity = request.quantity!!,
            empty = false,
            comment = request.comment,
            lastRefillDate = request.returnedAt,
            incrementRefillCount = true,
        )
    }

    private fun moveQuantityToStock(
        source: Cartridge,
        quantity: Int,
        empty: Boolean,
        comment: String?,
        lastRefillDate: LocalDate?,
        incrementRefillCount: Boolean,
    ): Cartridge {
        val existingStock = findCompatibleAvailableRow(
            departmentId = source.department.id!!,
            cartridgeModelId = source.cartridgeModel.id!!,
            status = CartridgeStatus.IN_STOCK,
            refillable = source.refillable,
            empty = empty,
            excludeId = source.id,
        )

        if (existingStock != null) {
            existingStock.quantity = existingStock.quantity + quantity
            existingStock.status = CartridgeStatus.IN_STOCK
            existingStock.empty = empty
            if (lastRefillDate != null) {
                existingStock.lastRefillDate = lastRefillDate
            }
            if (incrementRefillCount) {
                existingStock.refillCount = maxOf(existingStock.refillCount, source.refillCount + 1)
            }
            if (existingStock.comment.isNullOrBlank() && !comment.isNullOrBlank()) {
                existingStock.comment = comment
            }
            return cartridgeRepository.save(existingStock)
        }

        source.quantity = quantity
        source.status = CartridgeStatus.IN_STOCK
        source.empty = empty
        if (lastRefillDate != null) {
            source.lastRefillDate = lastRefillDate
        }
        if (incrementRefillCount) {
            source.refillCount = source.refillCount + 1
        }
        if (!comment.isNullOrBlank()) {
            source.comment = comment
        }
        return cartridgeRepository.save(source)
    }

    private fun findCompatibleAvailableRow(
        departmentId: Long,
        cartridgeModelId: Long,
        status: CartridgeStatus,
        refillable: Boolean?,
        empty: Boolean,
        excludeId: Long?,
    ): Cartridge? =
        cartridgeRepository.findCompatibleStockRows(
            departmentId = departmentId,
            cartridgeModelId = cartridgeModelId,
            status = status,
            refillable = refillable ?: true,
            empty = empty,
        ).firstOrNull { excludeId == null || it.id != excludeId }

    private fun createBatchCartridge(
        source: Cartridge,
        quantity: Int,
        status: CartridgeStatus,
        empty: Boolean,
        comment: String?,
    ): Cartridge =
        Cartridge().apply {
            inventoryCode = resolveInventoryCode(null)
            cartridgeModel = source.cartridgeModel
            department = source.department
            this.quantity = quantity
            refillable = source.refillable
            this.empty = empty
            this.status = status
            refillCount = source.refillCount
            lastRefillDate = source.lastRefillDate
            this.comment = if (!comment.isNullOrBlank()) comment else source.comment
        }

    private fun mergeComments(oldComment: String?, newComment: String?): String? =
        when {
            newComment.isNullOrBlank() -> oldComment
            oldComment.isNullOrBlank() -> newComment
            else -> "$oldComment | $newComment"
        }

    private fun resolveInventoryCode(requestedCode: String?): String {
        if (!requestedCode.isNullOrBlank()) {
            if (cartridgeRepository.existsByInventoryCodeIgnoreCase(requestedCode)) {
                throw ConflictException("Картридж с таким inventoryCode уже существует: $requestedCode")
            }
            return requestedCode.trim()
        }

        var generatedCode: String
        do {
            generatedCode = "CRT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase(Locale.ROOT)
        } while (cartridgeRepository.existsByInventoryCodeIgnoreCase(generatedCode))

        return generatedCode
    }

    private fun safeText(text: String?): String = if (text.isNullOrBlank()) "-" else text

    private fun toResponse(cartridge: Cartridge): CartridgeResponse {
        val installedQuantity = getInstalledQuantity(cartridge.id!!)
        return CartridgeResponse(
            id = cartridge.id,
            inventoryCode = cartridge.inventoryCode,
            cartridgeModelId = cartridge.cartridgeModel?.id,
            cartridgeModelName = cartridge.cartridgeModel?.name,
            departmentId = cartridge.department?.id,
            departmentName = cartridge.department?.name,
            quantity = cartridge.quantity,
            installedQuantity = installedQuantity,
            refillable = cartridge.refillable,
            empty = cartridge.empty,
            status = resolveDisplayStatus(cartridge, installedQuantity),
            refillCount = cartridge.refillCount,
            lastRefillDate = cartridge.lastRefillDate,
            comment = cartridge.comment,
            createdAt = cartridge.createdAt,
            updatedAt = cartridge.updatedAt,
        )
    }

    private fun getOrCreateStockDepartment(): Department =
        departmentRepository.findByNameIgnoreCase(STOCK_DEPARTMENT_NAME).orElseGet {
            departmentRepository.save(
                Department().apply {
                    name = STOCK_DEPARTMENT_NAME
                    description = "Системный отдел для общего остатка"
                },
            )
        }

    private fun getPrinterSlotEntity(printerSlotId: Long): PrinterSlot =
        printerSlotRepository.findById(printerSlotId)
            .orElseThrow { NotFoundException("Слот принтера не найден: $printerSlotId") }

    private fun getInstalledQuantity(cartridgeId: Long): Int =
        printerInstallationRepository.findByCartridgeId(cartridgeId).sumOf(PrinterInstallation::quantity)

    private fun resolveDisplayStatus(cartridge: Cartridge, installedQuantity: Int): CartridgeStatus =
        when {
            cartridge.status == CartridgeStatus.WRITTEN_OFF ||
                cartridge.status == CartridgeStatus.ON_REFILL ||
                cartridge.status == CartridgeStatus.RESERVE -> cartridge.status
            installedQuantity > 0 && cartridge.quantity == 0 -> CartridgeStatus.INSTALLED
            else -> CartridgeStatus.IN_STOCK
        }

    companion object {
        private const val STOCK_DEPARTMENT_NAME = "Склад"
        private const val DISPOSABLE_WRITE_OFF_COMMENT = "Израсходованы"
    }
}
