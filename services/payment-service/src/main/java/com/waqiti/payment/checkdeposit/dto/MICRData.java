package com.waqiti.payment.checkdeposit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MICR (Magnetic Ink Character Recognition) Data
 * Contains the three main components of a check's MICR line
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MICRData {

    /**
     * ABA routing number (9 digits)
     * First digit: 0-3 (Federal Reserve routing)
     */
    private String routingNumber;

    /**
     * Bank account number (4-17 digits typically)
     */
    private String accountNumber;

    /**
     * Check number (variable length, typically 1-10 digits)
     */
    private String checkNumber;

    /**
     * Raw MICR line as extracted from image
     */
    private String rawMicr;

    /**
     * Alternative raw MICR line representation
     */
    private String rawMicrLine;

    /**
     * Confidence score for MICR extraction (0.0 - 1.0)
     */
    @Builder.Default
    private double confidence = 0.0;

    /**
     * Whether MICR data passed validation
     */
    @Builder.Default
    private boolean valid = false;

    /**
     * Error code if validation failed
     */
    private String errorCode;

    /**
     * Error message if validation failed
     */
    private String errorMessage;

    /**
     * Processing status
     */
    private String processingStatus;

    /**
     * Exception type if error occurred
     */
    private String exception;

    /**
     * Check if MICR data is valid
     */
    public boolean isValid() {
        // If explicitly marked invalid with error code, return false
        if (!valid && errorCode != null) {
            return false;
        }

        // Standard validation rules
        return routingNumber != null && !routingNumber.isEmpty() &&
               accountNumber != null && !accountNumber.isEmpty() &&
               routingNumber.length() == 9 &&
               accountNumber.length() >= 4 && accountNumber.length() <= 17;
    }
}
