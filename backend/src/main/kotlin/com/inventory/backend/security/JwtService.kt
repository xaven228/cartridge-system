package com.inventory.backend.security

import com.inventory.backend.entity.AppUser
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${app.jwt.secret:replace-with-strong-secret-please}") secret: String,
    @Value("\${app.jwt.ttl-minutes:30}") private val ttlMinutes: Long,
) {
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(secret.padEnd(64, 'x').toByteArray())

    fun generateToken(user: AppUser): Pair<String, Long> {
        val expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes))
        val token = Jwts.builder()
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("fullName", user.fullName)
            .claim("role", user.role.name)
            .issuedAt(Date())
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact()

        return token to expiresAt.toEpochMilli()
    }

    fun parseClaims(token: String): Claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .payload
}
