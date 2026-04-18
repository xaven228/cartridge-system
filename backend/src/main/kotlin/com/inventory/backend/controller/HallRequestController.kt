package com.inventory.backend.controller

import com.inventory.backend.dto.HallRequestResponse
import com.inventory.backend.dto.UpsertHallRequestRequest
import com.inventory.backend.entity.HallRequestStatus
import com.inventory.backend.service.HallRequestService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/hall-requests")
@CrossOrigin
class HallRequestController(
    private val hallRequestService: HallRequestService,
) {

    @GetMapping
    fun getAll(
        @RequestParam(required = false) roomId: Long?,
        @RequestParam(required = false) status: HallRequestStatus?,
        @RequestParam(required = false) overdue: Boolean?,
    ): List<HallRequestResponse> = hallRequestService.getAll(roomId, status, overdue)

    @PostMapping
    fun create(@Valid @RequestBody request: UpsertHallRequestRequest): HallRequestResponse =
        hallRequestService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpsertHallRequestRequest): HallRequestResponse =
        hallRequestService.update(id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = hallRequestService.delete(id)
}
