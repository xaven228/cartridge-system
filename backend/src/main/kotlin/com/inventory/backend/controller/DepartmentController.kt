package com.inventory.backend.controller

import com.inventory.backend.dto.DepartmentResponse
import com.inventory.backend.dto.UpdateDepartmentRequest
import com.inventory.backend.service.DepartmentService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/departments")
@CrossOrigin
class DepartmentController(
    private val departmentService: DepartmentService,
) {
    @GetMapping
    @PreAuthorize("@authz.canViewCatalog()")
    fun getAll(): List<DepartmentResponse> = departmentService.getAll()

    @PostMapping
    @PreAuthorize("@authz.canEditCatalog()")
    fun create(@Valid @RequestBody request: UpdateDepartmentRequest): DepartmentResponse =
        departmentService.create(request)

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canEditCatalog()")
    fun update(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateDepartmentRequest,
    ): DepartmentResponse = departmentService.update(id, request)

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canEditCatalog()")
    fun delete(@PathVariable id: Long) {
        departmentService.delete(id)
    }
}
