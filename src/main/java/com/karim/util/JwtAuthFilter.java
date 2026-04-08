package com.karim.util;


import java.io.IOException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
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

    public JwtAuthFilter(JwtUtil jwtUtil,
                         CustomUserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // ✅ 1. No token → continue (public APIs will work)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        try {
            // ✅ 2. Validate token
            if (!jwtUtil.isTokenValid(token, "access")) {
                throw new BadCredentialsException("Invalid or expired access token");
            }

            // ✅ 3. Set authentication only if not already set
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                UUID userId = jwtUtil.extractUserId(token);
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                // ✅ 4. Check if user is active
                if (!userDetails.isEnabled()) {
                    throw new BadCredentialsException("Account is inactive");
                }

                // ✅ 5. Create authentication object
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            // ✅ 6. Continue filter chain
            filterChain.doFilter(request, response);

        } catch (Exception ex) {

            // ✅ VERY IMPORTANT
            SecurityContextHolder.clearContext();

            // ✅ Let Spring Security handle response
            throw new BadCredentialsException("JWT Error: " + ex.getMessage(), ex);
        }
    }
}