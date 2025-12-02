package com.waqiti.tokenization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detokenization Response DTO
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetokenizationResponse {

    /**
     * Decrypted card number (HIGHLY SENSITIVE - handle with extreme care)
     */
    private String cardNumber;

    /**
     * Last 4 digits (for verification)
     */
    private String last4Digits;

    /**
     * Card type
     */
    private String cardType;

    /**
     * Success indicator
     */
    private boolean success;

    /**
     * Error message (if any)
     */
    private String errorMessage;

    @Override
    public String toString() {
        return "DetokenizationResponse{" +
                "last4Digits='" + last4Digits + '\'' +
                ", cardType='" + cardType + '\'' +
                ", success=" + success +
                ", cardNumber=REDACTED" +
                '}';
    }
}
