package com.waqiti.customer.client.fallback;

import com.waqiti.customer.client.UserServiceClient;
import com.waqiti.customer.client.dto.UserProfileResponse;
import com.waqiti.customer.client.dto.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for UserServiceClient.
 * Provides circuit breaker pattern implementation with safe default values
 * when user-service is unavailable or experiencing issues.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Component
@Slf4j
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserResponse getUser(String userId) {
        log.error("UserServiceClient.getUser fallback triggered for userId: {}", userId);
        return null;
    }

    @Override
    public UserResponse getUserByCustomerId(String customerId) {
        log.error("UserServiceClient.getUserByCustomerId fallback triggered for customerId: {}", customerId);
        return null;
    }

    @Override
    public void deactivateUser(String userId) {
        log.error("UserServiceClient.deactivateUser fallback triggered for userId: {}. User deactivation operation failed.", userId);
    }

    @Override
    public UserProfileResponse getUserProfile(String userId) {
        log.error("UserServiceClient.getUserProfile fallback triggered for userId: {}", userId);
        return null;
    }
}
