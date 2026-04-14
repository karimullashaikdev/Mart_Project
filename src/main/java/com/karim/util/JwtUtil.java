package com.karim.util;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            @Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    // ----------------------------------------------------------------
    // TOKEN GENERATION
    // ----------------------------------------------------------------

    public String generateAccessToken(UUID userId, String email, String role) {
        return buildToken(userId, email, role, "access", accessTokenExpiryMs);
    }

    public String generateRefreshToken(UUID userId, String email, String role) {
        return buildToken(userId, email, role, "refresh", refreshTokenExpiryMs);
    }

    private String buildToken(UUID userId, String email, String role, String type, long expiryMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("type", type)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiryMs))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ----------------------------------------------------------------
    // TOKEN VALIDATION
    // ----------------------------------------------------------------

    public boolean isTokenValid(String token, String expectedType) {
        try {
            Claims claims = extractAllClaims(token);

            String type = claims.get("type", String.class);
            if (type == null || !expectedType.equalsIgnoreCase(type)) {
                System.out.println("❌ Token type mismatch: expected=" + expectedType + ", actual=" + type);
                return false;
            }

            Date expiration = claims.getExpiration();
            if (expiration.before(new Date())) {
                System.out.println("❌ Token expired");
                return false;
            }

            System.out.println("✅ Token validated successfully. Type=" + type);
            return true;

        } catch (ExpiredJwtException e) {
            System.out.println("❌ Token expired (exception)");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            System.out.println("❌ Invalid token: " + e.getMessage());
            return false;
        }
    }

    /**
     * Alias used by JwtAuthFilter and any code expecting Spring-style validation.
     * Checks signature, expiry, and that it is an access token.
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            // email in token must match the UserDetails username
            String email = extractEmail(token);
            return email != null
                    && email.equals(userDetails.getUsername())
                    && isTokenValid(token, "access");
        } catch (Exception e) {
            return false;
        }
    }

    // ----------------------------------------------------------------
    // CLAIM EXTRACTION
    // ----------------------------------------------------------------

    public UUID extractUserId(String token) {
        return UUID.fromString(extractAllClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    /**
     * Alias for extractEmail() — used by JwtAuthFilter and WebSocketConfig
     * when they call extractUsername(token).
     * Your JWT stores email as the logical "username".
     */
    public String extractUsername(String token) {
        return extractEmail(token);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractType(String token) {
        return extractAllClaims(token).get("type", String.class);
    }

    public Date extractExpiry(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}