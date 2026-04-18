package com.inventory.backend.controller

import com.inventory.backend.dto.RoomResponse
import com.inventory.backend.dto.UpsertRoomRequest
import com.inventory.backend.service.RoomService
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
@RequestMapping("/api/rooms")
@CrossOrigin
class RoomController(
    private val roomService: RoomService,
) {

    @GetMapping
    fun getAll(@RequestParam(required = false) departmentId: Long?): List<RoomResponse> = roomService.getAll(departmentId)

    @PostMapping
    fun create(@Valid @RequestBody request: UpsertRoomRequest): RoomResponse = roomService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpsertRoomRequest): RoomResponse =
        roomService.update(id, request)

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = roomService.delete(id)
}
