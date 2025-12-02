package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback implementation for UserServiceClient
 * Provides graceful degradation when user service is unavailable
 */
@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {
    
    @Override
    public UserResponse getUser(UUID userId) {
        log.warn("User service unavailable, returning fallback user for ID: {}", userId);
        return createFallbackUser(userId);
    }
    
    @Override
    public List<UserResponse> getUsers(List<UUID> userIds) {
        log.warn("User service unavailable, returning empty user list for {} user IDs", userIds.size());
        return Collections.emptyList();
    }
    
    private UserResponse createFallbackUser(UUID userId) {
        return UserResponse.builder()
                .id(userId)
                .username("user-unavailable")
                .displayName("User Unavailable")
                .email("unavailable@example.com")
                .status("UNAVAILABLE")
                .createdAt(LocalDateTime.now())
                .build();
    }
}