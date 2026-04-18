package com.inventory.backend.controller

import com.inventory.backend.dto.UpsertUserRequest
import com.inventory.backend.dto.UserAdminResponse
import com.inventory.backend.service.UserAdminService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
@CrossOrigin
@PreAuthorize("hasRole('ADMIN')")
class UserAdminController(
    private val userAdminService: UserAdminService,
) {

    @GetMapping
    fun getAll(): List<UserAdminResponse> = userAdminService.getAll()

    @PostMapping
    fun create(@Valid @RequestBody request: UpsertUserRequest): UserAdminResponse = userAdminService.create(request)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: UpsertUserRequest): UserAdminResponse =
        userAdminService.update(id, request)
}
