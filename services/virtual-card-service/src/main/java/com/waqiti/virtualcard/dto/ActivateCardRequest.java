package com.waqiti.virtualcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for activating a physical card
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivateCardRequest {
    
    @NotBlank(message = "Activation code is required")
    @Size(min = 4, max = 12, message = "Activation code must be between 4 and 12 characters")
    private String activationCode;
    
    @NotBlank(message = "PIN is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4-6 digits")
    private String pin;
    
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN confirmation must match PIN format")
    private String pinConfirmation;
    
    @Size(max = 20, message = "Device ID cannot exceed 20 characters")
    private String deviceId;
    
    private String userAgent;
    
    private String ipAddress;
    
    /**
     * Validates that PIN and confirmation match
     */
    public boolean isPinConfirmed() {
        return pin != null && pin.equals(pinConfirmation);
    }
    
    /**
     * Validates the activation request
     */
    public boolean isValid() {
        if (activationCode == null || activationCode.trim().isEmpty()) {
            return false;
        }
        
        if (pin == null || !pin.matches("^\\d{4,6}$")) {
            return false;
        }
        
        if (pinConfirmation != null && !isPinConfirmed()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Gets sanitized activation code (uppercase, no spaces)
     */
    public String getSanitizedActivationCode() {
        return activationCode != null ? activationCode.trim().toUpperCase() : null;
    }
}