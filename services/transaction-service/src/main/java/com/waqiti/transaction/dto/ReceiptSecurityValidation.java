package com.waqiti.transaction.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of receipt security validation
 */
@Data
@Builder
public class ReceiptSecurityValidation {

    /**
     * Overall validation status
     */
    private boolean valid;

    /**
     * Validation timestamp
     */
    private LocalDateTime validatedAt;

    /**
     * Individual validation checks
     */
    private List<ValidationCheck> checks;

    /**
     * Security score (0-100)
     */
    private int securityScore;

    /**
     * Any security warnings
     */
    private List<String> warnings;

    /**
     * Any security errors
     */
    private List<String> errors;

    /**
     * Receipt version validated
     */
    private String receiptVersion;

    /**
     * Validation method used
     */
    private String validationMethod;

    @Data
    @Builder
    public static class ValidationCheck {
        private String checkType;
        private boolean passed;
        private String message;
        private int weight; // How important this check is (1-10)
    }
}