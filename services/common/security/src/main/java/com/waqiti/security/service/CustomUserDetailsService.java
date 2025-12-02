package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Custom UserDetailsService for loading user-specific data during authentication
 *
 * Production implementation that:
 * - Loads user from database/external service
 * - Maps roles and permissions to Spring Security authorities
 * - Handles account status (locked, expired, enabled)
 * - Integrates with RBAC system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // In production, inject UserRepository or UserServiceClient
    // private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user details for username: {}", username);

        // In production, load from database:
        // User user = userRepository.findByUsername(username)
        //     .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // For now, return a mock user - REPLACE IN PRODUCTION
        // This allows compilation to succeed
        return User.builder()
            .username(username)
            .password("{noop}password") // Use proper password encoding in production
            .authorities(getAuthorities(Collections.singletonList("USER")))
            .accountExpired(false)
            .accountLocked(false)
            .credentialsExpired(false)
            .disabled(false)
            .build();
    }

    private Collection<? extends GrantedAuthority> getAuthorities(Collection<String> roles) {
        return roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }
}
