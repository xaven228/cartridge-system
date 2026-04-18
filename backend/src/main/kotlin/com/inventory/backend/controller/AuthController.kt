package com.inventory.backend.controller

import com.inventory.backend.dto.AuthResponse
import com.inventory.backend.dto.AuthUserResponse
import com.inventory.backend.dto.LoginRequest
import com.inventory.backend.service.AuthService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
@CrossOrigin
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse = authService.login(request)

    @GetMapping("/me")
    fun me(): AuthUserResponse = authService.me()
}
