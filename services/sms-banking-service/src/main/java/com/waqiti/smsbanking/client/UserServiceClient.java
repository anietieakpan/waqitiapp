package com.waqiti.smsbanking.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign Client for User Service
 *
 * Handles communication with the user-service microservice for
 * user information retrieval and authentication.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 */
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url:http://user-service:8080}"
)
public interface UserServiceClient {

    /**
     * Get user by phone number
     *
     * @param phoneNumber User's phone number
     * @return User DTO with account information
     */
    @GetMapping("/api/v1/users/by-phone")
    UserDTO getUserByPhoneNumber(@RequestParam("phoneNumber") String phoneNumber);

    /**
     * Verify user PIN
     *
     * @param userId User ID
     * @param pin PIN to verify
     * @return Verification result
     */
    @GetMapping("/api/v1/users/{userId}/verify-pin")
    PinVerificationResult verifyPin(
        @PathVariable("userId") String userId,
        @RequestParam("pin") String pin
    );

    /**
     * Check if user is active
     *
     * @param userId User ID
     * @return true if user is active
     */
    @GetMapping("/api/v1/users/{userId}/is-active")
    boolean isUserActive(@PathVariable("userId") String userId);

    /**
     * User Data Transfer Object
     */
    record UserDTO(
        String userId,
        String phoneNumber,
        String accountNumber,
        String name,
        String status,
        boolean isActive
    ) {}

    /**
     * PIN Verification Result
     */
    record PinVerificationResult(
        boolean verified,
        int remainingAttempts,
        boolean accountLocked
    ) {}
}
