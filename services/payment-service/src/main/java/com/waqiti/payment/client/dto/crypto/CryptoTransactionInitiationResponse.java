package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for cryptocurrency transaction initiation
 * May require MFA or return immediate transaction result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTransactionInitiationResponse {
    private UUID transactionId;
    private String status; // MFA_REQUIRED, COMPLETED, FAILED
    private boolean mfaRequired;
    private MfaChallenge mfaChallenge;
    private CryptoTransactionResponse transaction;
    private String message;
    private boolean available;
    private String errorMessage;

    public static CryptoTransactionInitiationResponse unavailable(String message) {
        return CryptoTransactionInitiationResponse.builder()
                .available(false)
                .status("UNAVAILABLE")
                .mfaRequired(false)
                .errorMessage(message)
                .build();
    }
}
