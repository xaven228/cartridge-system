package com.inventory.backend.service

import com.inventory.backend.dto.HallRequestResponse
import com.inventory.backend.dto.UpsertHallRequestRequest
import com.inventory.backend.entity.ActionLogType
import com.inventory.backend.entity.HallRequest
import com.inventory.backend.entity.HallRequestPriority
import com.inventory.backend.entity.HallRequestStatus
import com.inventory.backend.exception.NotFoundException
import com.inventory.backend.repository.HallRequestRepository
import com.inventory.backend.repository.RoomRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class HallRequestService(
    private val hallRequestRepository: HallRequestRepository,
    private val roomRepository: RoomRepository,
    private val actionLogService: ActionLogService,
    @Value("\${app.hall-request.sla.low-hours:72}")
    private val slaLowHours: Long,
    @Value("\${app.hall-request.sla.medium-hours:24}")
    private val slaMediumHours: Long,
    @Value("\${app.hall-request.sla.high-hours:8}")
    private val slaHighHours: Long,
    @Value("\${app.hall-request.sla.urgent-hours:2}")
    private val slaUrgentHours: Long,
) {

    @Transactional(readOnly = true)
    fun getAll(roomId: Long?, status: HallRequestStatus?, overdue: Boolean?): List<HallRequestResponse> {
        val requests = when {
            roomId != null -> hallRequestRepository.findByRoomIdOrderByRequestedAtDesc(roomId)
            status != null -> hallRequestRepository.findByStatusOrderByRequestedAtDesc(status)
            else -> hallRequestRepository.findAllByOrderByRequestedAtDesc()
        }

        return requests
            .asSequence()
            .filter { status == null || it.status == status }
            .filter { overdue == null || isOverdue(it, LocalDateTime.now()) == overdue }
            .map(::toResponse)
            .toList()
    }

    @Transactional
    fun create(request: UpsertHallRequestRequest): HallRequestResponse {
        val room = roomRepository.findById(request.roomId)
            .orElseThrow { NotFoundException("Кабинет не найден: ${request.roomId}") }

        val hallRequest = HallRequest.builder()
            .room(room)
            .requesterName(request.requesterName.trim())
            .title(request.title.trim())
            .description(request.description?.trim()?.ifBlank { null })
            .priority(request.priority)
            .status(request.status)
            .requestedAt(LocalDateTime.now())
            .plannedAt(request.plannedAt)
            .completedAt(if (request.status == HallRequestStatus.DONE) LocalDateTime.now() else null)
            .build()

        val saved = hallRequestRepository.save(hallRequest)
        actionLogService.log(
            ActionLogType.HALL_REQUEST_CREATED,
            saved.title,
            "Создана заявка для кабинета ${room.name}, статус: ${saved.status}, приоритет: ${saved.priority}",
            "Система"
        )

        return toResponse(saved)
    }

    @Transactional
    fun update(id: Long, request: UpsertHallRequestRequest): HallRequestResponse {
        val hallRequest = hallRequestRepository.findById(id)
            .orElseThrow { NotFoundException("Заявка не найдена: $id") }
        val room = roomRepository.findById(request.roomId)
            .orElseThrow { NotFoundException("Кабинет не найден: ${request.roomId}") }

        hallRequest.room = room
        hallRequest.requesterName = request.requesterName.trim()
        hallRequest.title = request.title.trim()
        hallRequest.description = request.description?.trim()?.ifBlank { null }
        hallRequest.priority = request.priority
        hallRequest.status = request.status
        hallRequest.plannedAt = request.plannedAt
        hallRequest.completedAt = if (request.status == HallRequestStatus.DONE) {
            hallRequest.completedAt ?: LocalDateTime.now()
        } else {
            null
        }

        val saved = hallRequestRepository.save(hallRequest)
        actionLogService.log(
            ActionLogType.HALL_REQUEST_UPDATED,
            saved.title,
            "Обновлена заявка. Статус: ${saved.status}, приоритет: ${saved.priority}",
            "Система"
        )

        return toResponse(saved)
    }

    @Transactional
    fun delete(id: Long) {
        val hallRequest = hallRequestRepository.findById(id)
            .orElseThrow { NotFoundException("Заявка не найдена: $id") }

        actionLogService.log(
            ActionLogType.HALL_REQUEST_DELETED,
            hallRequest.title,
            "Заявка удалена",
            "Система"
        )

        hallRequestRepository.delete(hallRequest)
    }

    @Scheduled(fixedDelayString = "\${app.hall-request.escalation-interval-ms:300000}")
    @Transactional
    fun escalateOverdueRequests() {
        val now = LocalDateTime.now()
        val activeStatuses = listOf(HallRequestStatus.OPEN, HallRequestStatus.IN_PROGRESS)
        val requests = hallRequestRepository.findByStatusInOrderByRequestedAtDesc(activeStatuses)
        val escalated = requests.filter { it.priority != HallRequestPriority.URGENT && isOverdue(it, now) }
        if (escalated.isEmpty()) {
            return
        }

        escalated.forEach { request ->
            val previousPriority = request.priority
            request.priority = HallRequestPriority.URGENT
            actionLogService.log(
                ActionLogType.HALL_REQUEST_ESCALATED,
                request.title,
                "SLA просрочен, приоритет изменен: $previousPriority -> ${request.priority}",
                "SLA Scheduler"
            )
        }

        hallRequestRepository.saveAll(escalated)
    }

    private fun toResponse(request: HallRequest): HallRequestResponse = HallRequestResponse(
        id = request.id,
        roomId = request.room.id,
        roomName = request.room.name,
        departmentId = request.room.department.id,
        departmentName = request.room.department.name,
        requesterName = request.requesterName,
        title = request.title,
        description = request.description,
        priority = request.priority,
        status = request.status,
        requestedAt = request.requestedAt,
        plannedAt = request.plannedAt,
        completedAt = request.completedAt,
        slaDueAt = resolveSlaDueAt(request),
        slaOverdue = isOverdue(request, LocalDateTime.now()),
        slaMinutesRemaining = calculateSlaMinutesRemaining(request, LocalDateTime.now()),
    )

    private fun resolveSlaDueAt(request: HallRequest): LocalDateTime {
        val hours = when (request.priority) {
            HallRequestPriority.LOW -> slaLowHours
            HallRequestPriority.MEDIUM -> slaMediumHours
            HallRequestPriority.HIGH -> slaHighHours
            HallRequestPriority.URGENT -> slaUrgentHours
        }
        return request.requestedAt.plusHours(hours)
    }

    private fun isOverdue(request: HallRequest, now: LocalDateTime): Boolean {
        if (request.status == HallRequestStatus.DONE || request.status == HallRequestStatus.CANCELLED) {
            return false
        }
        return now.isAfter(resolveSlaDueAt(request))
    }

    private fun calculateSlaMinutesRemaining(request: HallRequest, now: LocalDateTime): Long {
        if (request.status == HallRequestStatus.DONE || request.status == HallRequestStatus.CANCELLED) {
            return 0
        }
        return Duration.between(now, resolveSlaDueAt(request)).toMinutes()
    }
}
