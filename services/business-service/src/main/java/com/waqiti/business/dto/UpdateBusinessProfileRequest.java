package com.waqiti.business.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request to update business profile information")
public class UpdateBusinessProfileRequest {
    
    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 100, message = "Business name must be between 2 and 100 characters")
    @Schema(description = "Business name", example = "Acme Corporation", required = true)
    private String businessName;
    
    @Size(max = 100, message = "Industry must not exceed 100 characters")
    @Schema(description = "Business industry", example = "Technology")
    private String industry;
    
    @NotBlank(message = "Address is required")
    @Size(max = 500, message = "Address must not exceed 500 characters")
    @Schema(description = "Business address", required = true)
    private String address;
    
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
    @Schema(description = "Phone number in international format", example = "+1234567890")
    private String phoneNumber;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Business email address", required = true)
    private String email;
    
    @Pattern(regexp = "^(https?://)?(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)$", 
             message = "Invalid website URL format")
    @Size(max = 255, message = "Website URL must not exceed 255 characters")
    @Schema(description = "Business website URL", example = "https://www.example.com")
    private String website;
    
    @Size(max = 50, message = "Business type must not exceed 50 characters")
    @Schema(description = "Type of business entity", example = "LLC")
    private String businessType;
    
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(description = "Business description")
    private String description;
    
    @Schema(description = "Primary contact information")
    private ContactUpdateInfo primaryContact;
    
    @Schema(description = "Secondary contact information")
    private ContactUpdateInfo secondaryContact;
    
    @Schema(description = "Social media profiles")
    private SocialMediaProfiles socialMedia;
    
    @Schema(description = "Business hours of operation")
    private Map<String, String> businessHours;
    
    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
    
    @Schema(description = "Notification preferences")
    private NotificationPreferences notificationPreferences;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactUpdateInfo {
        @NotBlank(message = "Contact name is required")
        @Size(max = 100, message = "Contact name must not exceed 100 characters")
        private String fullName;
        
        @Size(max = 100, message = "Title must not exceed 100 characters")
        private String title;
        
        @Email(message = "Invalid email format")
        private String email;
        
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        private String phoneNumber;
        
        @Size(max = 50, message = "Extension must not exceed 50 characters")
        private String extension;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialMediaProfiles {
        @Pattern(regexp = "^https?://(www\\.)?linkedin\\.com/.*", message = "Invalid LinkedIn URL")
        private String linkedin;
        
        @Pattern(regexp = "^https?://(www\\.)?twitter\\.com/.*", message = "Invalid Twitter URL")
        private String twitter;
        
        @Pattern(regexp = "^https?://(www\\.)?facebook\\.com/.*", message = "Invalid Facebook URL")
        private String facebook;
        
        @Pattern(regexp = "^https?://(www\\.)?instagram\\.com/.*", message = "Invalid Instagram URL")
        private String instagram;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationPreferences {
        private boolean emailNotifications;
        private boolean smsNotifications;
        private boolean pushNotifications;
        private boolean marketingEmails;
        private boolean transactionAlerts;
        private boolean monthlyStatements;
        private boolean securityAlerts;
        private Map<String, Boolean> customPreferences;
    }
}