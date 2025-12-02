package com.waqiti.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for 2FA configuration requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Configure2FARequest {
    
    @NotNull(message = "2FA method is required")
    private TwoFactorMethod method;
    
    @NotNull(message = "Enabled status is required")
    private Boolean enabled;
    
    private String phoneNumber; // Required for SMS
    private String email; // Required for EMAIL
    private String totpSecret; // For TOTP setup
    private String backupEmail; // For backup codes
    
    public enum TwoFactorMethod {
        SMS, EMAIL, TOTP, BACKUP_CODES
    }
}