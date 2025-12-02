package com.waqiti.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for privacy settings update requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacySettingsRequest {
    
    @NotNull(message = "Profile visibility setting is required")
    private ProfileVisibility profileVisibility;
    
    @NotNull(message = "Data sharing setting is required")
    private Boolean allowDataSharing;
    
    @NotNull(message = "Analytics tracking setting is required")
    private Boolean allowAnalyticsTracking;
    
    @NotNull(message = "Third-party integration setting is required")
    private Boolean allowThirdPartyIntegrations;
    
    @NotNull(message = "Location tracking setting is required")
    private Boolean allowLocationTracking;
    
    @NotNull(message = "Transaction history visibility setting is required")
    private Boolean showTransactionHistory;
    
    private String dataRetentionPreference;
    
    public enum ProfileVisibility {
        PUBLIC, FRIENDS_ONLY, PRIVATE
    }
}