package com.waqiti.wallet.security;

import com.waqiti.payment.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Security component to validate user identity for authorization.
 * Used by Spring Security @PreAuthorize annotations.
 */
@Component("userOwnershipValidator")
@RequiredArgsConstructor
@Slf4j
public class UserOwnershipValidator {
    
    private final UserServiceClient userServiceClient;
    
    /**
     * Checks if the authenticated user is the same as the requested user.
     * 
     * @param username The authenticated user's username
     * @param userId The user ID being accessed
     * @return true if the authenticated user matches the requested user
     */
    public boolean isCurrentUser(String username, UUID userId) {
        try {
            if (username == null || userId == null) {
                log.warn("Null parameters provided for user validation");
                return false;
            }
            
            // Convert username to UUID for comparison
            UUID authenticatedUserId = getUserIdFromUsername(username);
            
            boolean isMatch = authenticatedUserId.equals(userId);
            if (!isMatch) {
                log.warn("User {} attempted to access resources of user {}", username, userId);
            }
            
            return isMatch;
            
        } catch (Exception e) {
            log.error("Error checking user identity for {} and userId {}", username, userId, e);
            return false; // Fail secure - deny access on error
        }
    }
    
    /**
     * Converts username to user ID.
     * Integrates with user service to get actual user ID.
     */
    private UUID getUserIdFromUsername(String username) {
        // First try to parse as UUID if the username is already a UUID
        try {
            return UUID.fromString(username);
        } catch (IllegalArgumentException e) {
            // Username is not a UUID, look it up from user service
            log.debug("Username {} is not a UUID, looking up user ID from user service", username);
            
            try {
                // Check if user exists by email (username is typically email)
                var userExistsResponse = userServiceClient.userExistsByEmail(username)
                    .get(5, TimeUnit.SECONDS);
                
                if (userExistsResponse.isSuccess() && Boolean.TRUE.equals(userExistsResponse.getData())) {
                    // Search for user by email to get the user ID
                    var searchRequest = UserServiceClient.UserSearchRequest.builder()
                        .email(username)
                        .page(0)
                        .size(1)
                        .build();
                    
                    var searchResponse = userServiceClient.searchUsers(searchRequest)
                        .get(5, TimeUnit.SECONDS);
                    
                    if (searchResponse.isSuccess() && 
                        searchResponse.getData() != null && 
                        !searchResponse.getData().isEmpty()) {
                        return searchResponse.getData().get(0).getId();
                    }
                }
                
                log.warn("User not found for username: {}", username);
                throw new IllegalArgumentException("User not found: " + username);
                
            } catch (Exception ex) {
                log.error("Failed to lookup user ID for username: {}", username, ex);
                throw new RuntimeException("Failed to lookup user: " + ex.getMessage(), ex);
            }
        }
    }
}