package com.karim.util;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * JwtUtil
 *
 * Responsibilities: - Generate access token (short-lived, e.g. 15 min) -
 * Generate refresh token (long-lived, e.g. 7 days) - Parse & validate any token
 * - Extract individual claims (userId, email, role)
 *
 * Tokens carry: sub → userId (UUID string) email → user's email role → user's
 * role (ADMIN / CLIENT / DELIVERY) type → "access" or "refresh"
 */
@Component
public class JwtUtil {

	private final SecretKey secretKey;
	private final long accessTokenExpiryMs;
	private final long refreshTokenExpiryMs;

	// ----------------------------------------------------------------
	// Values come from application.properties / application.yml:
	// jwt.secret=<base64-encoded-256-bit-key>
	// jwt.access-token-expiry-ms=900000 # 15 minutes
	// jwt.refresh-token-expiry-ms=604800000 # 7 days
	// ----------------------------------------------------------------
	public JwtUtil(@Value("${jwt.secret}") String secret,
			@Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
			@Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {

		// Keys.hmacShaKeyFor expects raw bytes — secret must be ≥ 32 chars
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.accessTokenExpiryMs = accessTokenExpiryMs;
		this.refreshTokenExpiryMs = refreshTokenExpiryMs;
	}

	// ----------------------------------------------------------------
	// TOKEN GENERATION
	// ----------------------------------------------------------------

	/**
	 * Generate a short-lived ACCESS token. Stored nowhere — stateless.
	 */
	public String generateAccessToken(UUID userId, String email, String role) {
		return buildToken(userId, email, role, "access", accessTokenExpiryMs);
	}

	/**
	 * Generate a long-lived REFRESH token. In a production setup you'd store its
	 * jti in DB for revocation.
	 */
	public String generateRefreshToken(UUID userId, String email, String role) {
		return buildToken(userId, email, role, "refresh", refreshTokenExpiryMs);
	}

	private String buildToken(UUID userId, String email, String role, String type, long expiryMs) {
		long now = System.currentTimeMillis();

		return Jwts.builder().setSubject(userId.toString()) // use setSubject instead of subject()
				.claim("email", email).claim("role", role).claim("type", type).setIssuedAt(new Date(now))
				.setExpiration(new Date(now + expiryMs)).signWith(secretKey, SignatureAlgorithm.HS256) // <- specify
																										// algorithm
				.compact();
	}

	// ----------------------------------------------------------------
	// TOKEN VALIDATION
	// ----------------------------------------------------------------

	/**
	 * Returns true only if the token is signed correctly, not expired, and matches
	 * the expected type ("access" or "refresh").
	 */
	public boolean isTokenValid(String token, String expectedType) {
		try {
			Claims claims = extractAllClaims(token);

			String type = claims.get("type", String.class);

			// ✅ Check type (case-insensitive)
			if (type == null || !expectedType.equalsIgnoreCase(type)) {
				System.out.println("❌ Token type mismatch: expected=" + expectedType + ", actual=" + type);
				return false;
			}

			// ✅ Check expiration
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

	// ----------------------------------------------------------------
	// CLAIM EXTRACTION
	// ----------------------------------------------------------------

	public UUID extractUserId(String token) {
		return UUID.fromString(extractAllClaims(token).getSubject());
	}

	public String extractEmail(String token) {
		return extractAllClaims(token).get("email", String.class);
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

	// ----------------------------------------------------------------
	// INTERNAL HELPERS
	// ----------------------------------------------------------------

	/**
	 * Parses the JWT and returns all claims. Throws JwtException subtypes on any
	 * failure — callers decide how to handle.
	 */
	public Claims extractAllClaims(String token) {
		return Jwts.parserBuilder().setSigningKey(secretKey).build().parseClaimsJws(token).getBody();
	}
}