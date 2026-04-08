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

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList(), // later we add roles
                user.getIsActive()
        );
    }
    
    public UserDetails loadUserById(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with ID: " + userId));

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                Collections.emptyList(),
                user.getIsActive()
        );
    }
}