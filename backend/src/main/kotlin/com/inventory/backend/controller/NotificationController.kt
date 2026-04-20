package com.inventory.backend.controller

import com.inventory.backend.dto.NotificationAlertResponse
import com.inventory.backend.dto.NotificationThresholdResponse
import com.inventory.backend.dto.UpsertNotificationThresholdRequest
import com.inventory.backend.service.NotificationService
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
@RequestMapping("/api/notifications")
@CrossOrigin
class NotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping("/alerts")
    @PreAuthorize("@authz.canViewCatalog()")
    fun getAlerts(): List<NotificationAlertResponse> = notificationService.getAlerts()

    @GetMapping("/thresholds")
    @PreAuthorize("@authz.canManageThresholds()")
    fun getThresholds(): List<NotificationThresholdResponse> = notificationService.getThresholds()

    @PostMapping("/thresholds")
    @PreAuthorize("@authz.canManageThresholds()")
    fun createThreshold(@Valid @RequestBody request: UpsertNotificationThresholdRequest): NotificationThresholdResponse =
        notificationService.createThreshold(request)

    @PutMapping("/thresholds/{id}")
    @PreAuthorize("@authz.canManageThresholds()")
    fun updateThreshold(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpsertNotificationThresholdRequest,
    ): NotificationThresholdResponse = notificationService.updateThreshold(id, request)

    @DeleteMapping("/thresholds/{id}")
    @PreAuthorize("@authz.canManageThresholds()")
    fun deleteThreshold(@PathVariable id: Long) = notificationService.deleteThreshold(id)
}
