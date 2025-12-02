package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewCategorizationRequest {
    
    @NotBlank(message = "Subject is required")
    @Size(max = 500, message = "Subject cannot exceed 500 characters")
    private String subject;
    
    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;
    
    // Optional context for better categorization
    private String userId;
    private String userType; // VIP, REGULAR, BUSINESS, etc.
    private String channel; // EMAIL, CHAT, PHONE, etc.
    private String previousTicketId; // For related tickets
    private String attachmentCount;
    
    // Additional context that might help categorization
    private String customerTier;
    private String productVersion;
    private String deviceInfo;
    private String browserInfo;
    
    // Flags for special processing
    private boolean urgent;
    private boolean followUp;
    private boolean escalated;
}