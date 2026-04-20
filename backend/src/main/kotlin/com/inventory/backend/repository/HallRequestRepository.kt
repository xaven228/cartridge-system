package com.inventory.backend.repository

import com.inventory.backend.entity.HallRequest
import com.inventory.backend.entity.HallRequestStatus
import org.springframework.data.jpa.repository.JpaRepository

interface HallRequestRepository : JpaRepository<HallRequest, Long> {
    fun findAllByOrderByRequestedAtDesc(): List<HallRequest>
    fun findByRoomIdOrderByRequestedAtDesc(roomId: Long): List<HallRequest>
    fun findByStatusOrderByRequestedAtDesc(status: HallRequestStatus): List<HallRequest>
    fun findByStatusInOrderByRequestedAtDesc(statuses: Collection<HallRequestStatus>): List<HallRequest>
}
