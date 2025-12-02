package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MFA challenge details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaChallenge {
    private String challengeId;
    private String method; // TOTP, SMS, EMAIL
    private String maskedDestination; // For SMS/EMAIL
    private LocalDateTime expiresAt;
    private int attemptsRemaining;
}
