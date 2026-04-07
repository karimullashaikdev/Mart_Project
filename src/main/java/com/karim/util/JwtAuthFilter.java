package com.karim.util;


import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karim.service.impl.CustomUserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JwtAuthFilter
 *
 * Runs once per request (extends OncePerRequestFilter).
 *
 * Flow:
 *  1. Read "Authorization: Bearer <token>" header
 *  2. If missing/blank → pass through (public endpoints will succeed,
 *     protected ones will be rejected by SecurityConfig)
 *  3. Validate token — must be a valid ACCESS token
 *  4. Load user from DB via CustomUserDetailsService.loadUserById()
 *  5. Set Authentication in SecurityContextHolder
 *  6. Continue filter chain
 *
 * On any token error → respond with 401 JSON immediately.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil,
                         CustomUserDetailsService userDetailsService,
                         ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // 1. No Authorization header — skip, let SecurityConfig decide
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7); // strip "Bearer "

        try {
            // 2. Must be an access token (not a refresh token)
            if (!jwtUtil.isTokenValid(token, "access")) {
                sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired access token");
                return;
            }

            // 3. Only set auth if not already set (avoid re-processing)
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UUID userId = jwtUtil.extractUserId(token);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                // 4. Check account is enabled (isActive = true)
                if (!userDetails.isEnabled()) {
                    sendError(response, HttpStatus.UNAUTHORIZED,
                            "Account is inactive. Please verify your email.");
                    return;
                }

                // 5. Build authentication object and set in context
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                          // credentials — null after auth
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } catch (Exception ex) {
            // Catches malformed tokens, UUID parse errors, DB errors etc.
            sendError(response, HttpStatus.UNAUTHORIZED, "Token validation failed: " + ex.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Write a clean JSON error — consistent with your ApiResponse shape
    // { "success": false, "error": "...", "statusCode": 401 }
    // ----------------------------------------------------------------
    private void sendError(HttpServletResponse response,
                           HttpStatus status,
                           String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String body = objectMapper.writeValueAsString(
                java.util.Map.of(
                        "success", false,
                        "error", message,
                        "statusCode", status.value()
                ));

        response.getWriter().write(body);
    }
}
