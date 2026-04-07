package com.karim.service.impl;


import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.karim.entity.User;
import com.karim.repository.UserRepository;

/**
 * CustomUserDetailsService
 *
 * Spring Security calls loadUserByUsername() during authentication.
 * We use EMAIL as the "username" throughout this app.
 *
 * Returns a Spring UserDetails wrapping:
 *   - email        → username
 *   - passwordHash → password (BCrypt)
 *   - role         → GrantedAuthority  (e.g. ROLE_ADMIN, ROLE_CLIENT, ROLE_DELIVERY)
 *   - isActive     → enabled flag
 *
 * NOTE: We also expose loadUserById() for use in JwtAuthFilter,
 * where we already have the userId from the token and don't need
 * another DB round-trip by email.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Fetch user from database
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        // 2. Return a Spring Security 'UserDetails' object
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                // Maps your roles/permissions to GrantedAuthority
                Collections.emptyList() 
        );
    }
    
    // ✅ Custom method to load by UUID
    public UserDetails loadUserById(UUID userId) {
        return (UserDetails) userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));
    }
}