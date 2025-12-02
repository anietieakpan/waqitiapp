package com.waqiti.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for MFA setup
 *
 * SECURITY NOTES:
 * - Secret is only returned during initial setup
 * - Should be transmitted over HTTPS only
 * - Client should display secret only once and ask user to store it
 * - QR code URL contains the secret - handle with care
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MFASetupResponse {

    /**
     * TOTP secret (Base32 encoded)
     * SECURITY: Only returned during initial setup, not stored client-side
     */
    private String secret;

    /**
     * QR code URL for scanning with authenticator apps
     * Format: otpauth://totp/Issuer:Account?secret=...&issuer=...
     */
    private String qrCodeUrl;

    /**
     * Backup codes for account recovery
     * SECURITY: Single-use only, must be stored securely by user
     */
    private List<String> backupCodes;

    /**
     * Number of backup codes provided
     */
    private Integer backupCodesCount;

    /**
     * When the secret expires (for rotation)
     */
    private LocalDateTime expiresAt;

    /**
     * Whether MFA is now enabled (true after first successful verification)
     */
    private Boolean enabled;

    /**
     * Instructions for user
     */
    private String instructions;

    /**
     * Recommended authenticator apps
     */
    private List<String> recommendedApps;

    public static MFASetupResponse withInstructions(MFASetupResponse response) {
        response.setInstructions(
            "1. Scan the QR code with your authenticator app (Google Authenticator, Authy, etc.)\n" +
            "2. Or manually enter the secret code in your app\n" +
            "3. Enter the 6-digit code from your app to verify setup\n" +
            "4. Save your backup codes in a secure location"
        );
        response.setRecommendedApps(List.of(
            "Google Authenticator",
            "Authy",
            "Microsoft Authenticator",
            "1Password",
            "Duo Mobile"
        ));
        response.setBackupCodesCount(response.getBackupCodes() != null ?
            response.getBackupCodes().size() : 0);
        return response;
    }
}
