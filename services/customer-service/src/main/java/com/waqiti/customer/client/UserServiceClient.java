package com.waqiti.customer.client;

import com.waqiti.customer.client.dto.UserProfileResponse;
import com.waqiti.customer.client.dto.UserResponse;
import com.waqiti.customer.client.fallback.UserServiceClientFallback;
import com.waqiti.customer.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign client for inter-service communication with user-service.
 * Provides methods to retrieve user information and manage user status.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@FeignClient(
    name = "user-service",
    configuration = FeignClientConfig.class,
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    /**
     * Retrieves user information by user ID.
     *
     * @param userId The unique user identifier
     * @return User details
     */
    @GetMapping("/api/v1/users/{userId}")
    UserResponse getUser(@PathVariable("userId") String userId);

    /**
     * Retrieves user information by customer ID.
     *
     * @param customerId The unique customer identifier
     * @return User details
     */
    @GetMapping("/api/v1/users/customer/{customerId}")
    UserResponse getUserByCustomerId(@PathVariable("customerId") String customerId);

    /**
     * Deactivates a user account.
     *
     * @param userId The unique user identifier
     */
    @PostMapping("/api/v1/users/{userId}/deactivate")
    void deactivateUser(@PathVariable("userId") String userId);

    /**
     * Retrieves detailed user profile information.
     *
     * @param userId The unique user identifier
     * @return User profile details
     */
    @GetMapping("/api/v1/users/{userId}/profile")
    UserProfileResponse getUserProfile(@PathVariable("userId") String userId);
}
