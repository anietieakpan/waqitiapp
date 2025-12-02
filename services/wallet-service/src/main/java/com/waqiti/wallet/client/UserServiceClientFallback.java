package com.waqiti.wallet.client;

import com.waqiti.wallet.client.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback implementation for UserServiceClient
 * 
 * Provides circuit breaker fallback logic when user-service is unavailable.
 * Returns null to indicate service unavailability, allowing calling code
 * to handle the failure appropriately.
 * 
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {
    
    @Override
    public UserResponse getUserById(UUID userId) {
        log.error("FALLBACK: User service unavailable for getUserById - userId: {}", userId);
        return null;
    }
}