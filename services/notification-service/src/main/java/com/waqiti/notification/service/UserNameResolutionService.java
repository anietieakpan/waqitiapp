package com.waqiti.notification.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL P1 FIX: User Name Resolution Service
 *
 * Resolves user IDs to actual user names for notifications, improving UX
 * by displaying friendly names instead of UUIDs.
 *
 * Features:
 * - Circuit breaker pattern for resilience
 * - Caching to reduce API calls
 * - Graceful fallback to userID if resolution fails
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserNameResolutionService {

    private final RestTemplate restTemplate;

    private static final String USER_SERVICE_URL = "http://user-service:8080";

    /**
     * CRITICAL P1 FIX: Resolve user ID to display name
     *
     * Queries user-service to get actual user name instead of showing UUID.
     * Uses caching and circuit breaker for performance and resilience.
     *
     * @param userId User ID (UUID string)
     * @return User's display name, or userId if resolution fails
     */
    @Cacheable(value = "userNames", key = "#userId")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserNameFallback")
    public String getUserName(String userId) {
        try {
            log.debug("Resolving user name for userId: {}", userId);

            // Call user-service API to get user details
            String url = String.format("%s/api/v1/users/%s/profile", USER_SERVICE_URL, userId);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null) {
                // Try different name field combinations
                String displayName = extractDisplayName(response);

                if (displayName != null && !displayName.isEmpty()) {
                    log.debug("Resolved user name for userId {}: {}", userId, displayName);
                    return displayName;
                }
            }

            log.warn("User name resolution returned empty for userId: {}", userId);
            return userId; // Fallback to userId

        } catch (Exception e) {
            log.error("Failed to resolve user name for userId: {}", userId, e);
            return userId; // Fallback to userId
        }
    }

    /**
     * CRITICAL P1 FIX: Extract display name from user profile response
     *
     * Tries multiple fields in priority order:
     * 1. displayName
     * 2. fullName
     * 3. firstName + lastName
     * 4. firstName
     * 5. username
     * 6. email (first part before @)
     */
    private String extractDisplayName(Map<String, Object> profile) {
        // Priority 1: displayName
        if (profile.containsKey("displayName") && profile.get("displayName") != null) {
            return (String) profile.get("displayName");
        }

        // Priority 2: fullName
        if (profile.containsKey("fullName") && profile.get("fullName") != null) {
            return (String) profile.get("fullName");
        }

        // Priority 3: firstName + lastName
        String firstName = profile.containsKey("firstName") ? (String) profile.get("firstName") : null;
        String lastName = profile.containsKey("lastName") ? (String) profile.get("lastName") : null;

        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }

        // Priority 4: firstName only
        if (firstName != null) {
            return firstName;
        }

        // Priority 5: username
        if (profile.containsKey("username") && profile.get("username") != null) {
            return (String) profile.get("username");
        }

        // Priority 6: email (first part)
        if (profile.containsKey("email") && profile.get("email") != null) {
            String email = (String) profile.get("email");
            int atIndex = email.indexOf('@');
            if (atIndex > 0) {
                return email.substring(0, atIndex);
            }
        }

        return null;
    }

    /**
     * CRITICAL P1 FIX: Fallback method when user-service is unavailable
     *
     * Returns userId to prevent notification failures when user-service is down.
     *
     * @param userId User ID
     * @param ex Exception from circuit breaker
     * @return userId (fallback)
     */
    private String getUserNameFallback(String userId, Exception ex) {
        log.warn("User-service unavailable for userId: {} - Falling back to userId. Error: {}",
            userId, ex.getMessage());

        // Return userId as fallback to prevent notification failures
        return userId;
    }

    /**
     * CRITICAL P1 FIX: Resolve user name with UUID parameter
     *
     * Convenience method for UUID parameters.
     *
     * @param userId User ID (UUID)
     * @return User's display name
     */
    public String getUserName(UUID userId) {
        if (userId == null) {
            return "Unknown User";
        }
        return getUserName(userId.toString());
    }

    /**
     * Get formatted display name for notifications
     *
     * @param userId User ID
     * @return Formatted name (e.g., "John D." or full name)
     */
    public String getFormattedDisplayName(String userId) {
        String fullName = getUserName(userId);

        // If it's still a UUID (fallback case), return "User"
        if (fullName != null && fullName.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return "User";
        }

        // If it has spaces (first + last name), shorten last name
        if (fullName != null && fullName.contains(" ")) {
            String[] parts = fullName.split(" ");
            if (parts.length >= 2) {
                return parts[0] + " " + parts[1].charAt(0) + ".";
            }
        }

        return fullName;
    }
}
