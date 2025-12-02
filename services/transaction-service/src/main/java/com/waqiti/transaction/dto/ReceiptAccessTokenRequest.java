package com.waqiti.transaction.dto;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for generating receipt access tokens
 */
@Data
public class ReceiptAccessTokenRequest {

    @Email(message = "Email must be valid")
    private String email;

    @Min(value = 5, message = "Expiration minutes must be at least 5")
    @Max(value = 10080, message = "Expiration minutes cannot exceed 10080 (7 days)")
    private int expirationMinutes = 1440; // 24 hours default

    @NotBlank(message = "Access level is required")
    private String accessLevel = "READ_ONLY";

    private String purpose = "receipt_access";

    private boolean allowMultipleUse = false;

    // Legacy support
    @Min(value = 1, message = "Validity hours must be at least 1")
    @Max(value = 168, message = "Validity hours cannot exceed 168 (7 days)")
    private int validityHours = 24;
}