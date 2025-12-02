package com.waqiti.voice.client;

import com.waqiti.voice.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Feign client for User Service integration
 *
 * CRITICAL: Replaces UUID.randomUUID() mock in recipient resolution
 *
 * Handles:
 * - User lookup by various identifiers
 * - Contact management
 * - User profile information
 * - Recipient verification
 *
 * Resilience:
 * - Circuit breaker configured
 * - Retry logic for transient failures
 * - Fallback to UserServiceFallback
 */
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url:http://user-service}",
    path = "/api/v1",
    configuration = FeignConfig.class,
    fallback = UserServiceFallback.class
)
public interface UserServiceClient {

    /**
     * Resolve recipient by name (replaces UUID.randomUUID() mock)
     * CRITICAL: This is the primary fix for the random recipient bug
     *
     * Searches user's contacts and global users by name
     *
     * @param userId User ID (for contact search)
     * @param recipientName Recipient name
     * @return Recipient resolution result with user ID
     */
    @GetMapping("/users/resolve")
    RecipientResolution resolveRecipientByName(
            @RequestParam UUID userId,
            @RequestParam String recipientName);

    /**
     * Resolve recipient by phone number
     *
     * @param phoneNumber Phone number (E.164 format)
     * @return User information
     */
    @GetMapping("/users/by-phone")
    UserInfo findByPhoneNumber(@RequestParam String phoneNumber);

    /**
     * Resolve recipient by email
     *
     * @param email Email address
     * @return User information
     */
    @GetMapping("/users/by-email")
    UserInfo findByEmail(@RequestParam String email);

    /**
     * Resolve recipient by username
     *
     * @param username Username
     * @return User information
     */
    @GetMapping("/users/by-username")
    UserInfo findByUsername(@RequestParam String username);

    /**
     * Get user information by user ID
     *
     * @param userId User ID
     * @return User information
     */
    @GetMapping("/users/{userId}")
    UserInfo getUserById(@PathVariable UUID userId);

    /**
     * Get user's contacts
     *
     * @param userId User ID
     * @return List of contacts
     */
    @GetMapping("/users/{userId}/contacts")
    List<Contact> getUserContacts(@PathVariable UUID userId);

    /**
     * Search contacts by name
     *
     * @param userId User ID
     * @param query Search query
     * @return Matching contacts
     */
    @GetMapping("/users/{userId}/contacts/search")
    List<Contact> searchContacts(
            @PathVariable UUID userId,
            @RequestParam String query);

    /**
     * Verify recipient exists and can receive payments
     *
     * @param recipientIdentifier Phone, email, username, or UUID
     * @return Verification result
     */
    @PostMapping("/users/verify-recipient")
    RecipientVerificationResult verifyRecipient(
            @RequestBody RecipientVerificationRequest recipientIdentifier);

    /**
     * Get multiple users by IDs (batch lookup)
     *
     * @param userIds List of user IDs
     * @return User information list
     */
    @PostMapping("/users/batch")
    List<UserInfo> getUsersByIds(@RequestBody List<UUID> userIds);

    /**
     * Check if user exists
     *
     * @param userId User ID
     * @return true if user exists
     */
    @GetMapping("/users/{userId}/exists")
    Boolean userExists(@PathVariable UUID userId);

    /**
     * Get user's preferred language
     *
     * @param userId User ID
     * @return Language code (e.g., "en-US")
     */
    @GetMapping("/users/{userId}/language")
    String getUserLanguage(@PathVariable UUID userId);

    /**
     * Add contact
     *
     * @param userId User ID
     * @param request Contact details
     * @return Created contact
     */
    @PostMapping("/users/{userId}/contacts")
    Contact addContact(
            @PathVariable UUID userId,
            @RequestBody AddContactRequest request);

    /**
     * Health check
     */
    @GetMapping("/health")
    String healthCheck();
}
