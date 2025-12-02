package com.waqiti.familyaccount.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * User Service Feign Client
 *
 * Feign client for interacting with the user-service (customer-service) microservice.
 * Provides methods for user validation, age verification, and profile retrieval.
 *
 * Circuit Breaker Configuration:
 * - Name: user-service
 * - Failure Rate Threshold: 50%
 * - Sliding Window Size: 10 requests
 * - Wait Duration in Open State: 10 seconds
 * - Slow Call Duration Threshold: 2 seconds
 *
 * Retry Configuration:
 * - Max Attempts: 3
 * - Wait Duration: 1 second
 * - Exponential Backoff Multiplier: 2
 *
 * Timeout Configuration:
 * - Connect Timeout: 3 seconds
 * - Read Timeout: 3 seconds
 *
 * @author Waqiti Family Account Team
 * @version 1.0.0
 * @since 2025-11-19
 */
@FeignClient(
    name = "user-service",
    url = "${feign.client.config.user-service.url}",
    configuration = UserServiceClientConfig.class
)
public interface UserServiceClient {

    /**
     * Check if a user exists in the system
     *
     * @param userId The unique identifier of the user
     * @return true if the user exists, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/exists")
    Boolean userExists(@PathVariable("userId") String userId);

    /**
     * Get the age of a user
     *
     * This method retrieves the user's age calculated from their date of birth.
     * Used for age-based validation in family account operations (e.g., minimum parent age,
     * appropriate controls for children).
     *
     * @param userId The unique identifier of the user
     * @return The user's age in years, or null if date of birth not available
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/age")
    Integer getUserAge(@PathVariable("userId") String userId);

    /**
     * Check if a user is eligible to create or manage a family account
     *
     * Eligibility criteria may include:
     * - User is at least 18 years old
     * - User has completed KYC verification
     * - User's account is active and in good standing
     * - User is not already a primary parent in another family
     * - User meets any jurisdiction-specific requirements
     *
     * @param userId The unique identifier of the user
     * @return true if the user is eligible for family account management, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/family-eligible")
    Boolean isUserEligibleForFamilyAccount(@PathVariable("userId") String userId);

    /**
     * Get comprehensive user profile information
     *
     * Retrieves detailed user profile information including:
     * - Full name
     * - Email address
     * - Phone number
     * - Date of birth
     * - KYC verification status
     * - Account status
     * - Preferred language
     * - Timezone
     *
     * The returned map contains dynamic key-value pairs based on available profile data.
     *
     * @param userId The unique identifier of the user
     * @return Map containing user profile data with keys as field names and values as field data
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/profile")
    Map<String, Object> getUserProfile(@PathVariable("userId") String userId);

    /**
     * Verify if a user has completed KYC (Know Your Customer) verification
     *
     * This is critical for family account operations as all parents must be
     * KYC-verified to comply with financial regulations.
     *
     * @param userId The unique identifier of the user
     * @return true if KYC is verified, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/kyc-status")
    Boolean isKycVerified(@PathVariable("userId") String userId);

    /**
     * Get user's full name for display purposes
     *
     * @param userId The unique identifier of the user
     * @return The user's full name (first + last name)
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/name")
    String getUserFullName(@PathVariable("userId") String userId);

    /**
     * Get user's email address for notifications
     *
     * @param userId The unique identifier of the user
     * @return The user's primary email address
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/email")
    String getUserEmail(@PathVariable("userId") String userId);

    /**
     * Get user's phone number for notifications
     *
     * @param userId The unique identifier of the user
     * @return The user's primary phone number in E.164 format
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/phone")
    String getUserPhoneNumber(@PathVariable("userId") String userId);

    /**
     * Check if a user's account is active and not suspended/closed
     *
     * @param userId The unique identifier of the user
     * @return true if the account is active, false otherwise
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/is-active")
    Boolean isUserAccountActive(@PathVariable("userId") String userId);

    /**
     * Validate that multiple users exist (bulk validation)
     *
     * Useful when adding multiple family members at once.
     *
     * @param userIds Comma-separated list of user IDs to validate
     * @return Map with userId as key and existence boolean as value
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/validate-bulk")
    Map<String, Boolean> validateUsersExist(@RequestParam("userIds") String userIds);

    /**
     * Get user's preferred language for localized notifications
     *
     * @param userId The unique identifier of the user
     * @return ISO 639-1 language code (e.g., "en", "es", "fr")
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/language")
    String getUserPreferredLanguage(@PathVariable("userId") String userId);

    /**
     * Get user's timezone for time-sensitive operations
     *
     * @param userId The unique identifier of the user
     * @return Timezone identifier (e.g., "America/New_York", "Europe/London")
     * @throws feign.FeignException if the service call fails
     */
    @GetMapping("/api/v1/users/{userId}/timezone")
    String getUserTimezone(@PathVariable("userId") String userId);
}
