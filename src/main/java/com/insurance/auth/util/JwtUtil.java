package com.insurance.auth.util;

import com.insurance.auth.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;
    private final long resetTokenExpiryMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs,
            @Value("${jwt.reset-token-expiry-ms}") long resetTokenExpiryMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
        this.resetTokenExpiryMs = resetTokenExpiryMs;
    }

    // ── Access Token ─────────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        return generateAccessToken(user, java.util.Collections.emptyList());
    }

    /**
     * @param permissionCodes the flat list of permission codes granted to the user's
     *                        role at the moment of token issuance (see RbacService).
     *                        Embedded so downstream services can enforce fine-grained
     *                        authorization without an extra network call back to auth-service.
     */
    public String generateAccessToken(User user, java.util.List<String> permissionCodes) {

        Map<String, Object> claims = new HashMap<>();

        claims.put("role", user.getRole().name());
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());

        claims.put(
                "permissions",
                permissionCodes == null
                        ? java.util.List.of()
                        : permissionCodes
        );

        if (user.getBranchId() != null) {
            claims.put("branchId", user.getBranchId().toString());
        }

        if (user.getCompanyId() != null) {
            claims.put("companyId", user.getCompanyId().toString());
        }

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getId().toString())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + accessTokenExpiryMs)
                )
                .signWith(signingKey)
                .compact();
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────
// ── Refresh Token ─────────────────────────────────────────────────────────

    public String generateRefreshToken(User user) {

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .setId(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + refreshTokenExpiryMs)
                )
                .signWith(signingKey)
                .compact();
    }


// ── Password Reset Token ──────────────────────────────────────────────────

    public String generateResetToken(User user) {

        return Jwts.builder()
                .setSubject(user.getId().toString())
                .setId(UUID.randomUUID().toString())
                .claim("type", "reset")
                .claim("email", user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + resetTokenExpiryMs)
                )
                .signWith(signingKey)
                .compact();
    }


// ── Parsing & Validation ──────────────────────────────────────────────────

    public Claims extractAllClaims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public java.util.List<String> extractPermissions(String token) {
        Object raw = extractAllClaims(token).get("permissions");
        if (raw instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return java.util.List.of();
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
        }
        return false;
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractAllClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }
}
