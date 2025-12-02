package com.waqiti.wallet.client;

import com.waqiti.wallet.client.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * Feign client for User Service integration
 * 
 * Provides access to user information from the centralized user-service.
 * Used for user validation and retrieving user details during wallet operations.
 * 
 * @author Waqiti Development Team
 * @since 1.0.0
 */
@FeignClient(
    name = "user-service",
    fallback = UserServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface UserServiceClient {
    
    /**
     * Get user by ID
     * 
     * @param userId User ID
     * @return User response
     */
    @GetMapping("/api/v1/users/{userId}")
    UserResponse getUserById(@PathVariable("userId") UUID userId);
}