package com.waqiti.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Response from user service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String phoneNumber;
    private String status;
    private String kycStatus;
    private Set<String> roles;
    private LocalDateTime createdAt;
    private UserProfileResponse profile;
    
    /**
     * Gets the user's display name (first name + last name, or username if profile not available)
     */
    public String getDisplayName() {
        if (profile != null && profile.getFirstName() != null) {
            if (profile.getLastName() != null) {
                return profile.getFirstName() + " " + profile.getLastName();
            }
            return profile.getFirstName();
        }
        return username;
    }
}

