package com.waqiti.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * SECURITY FIX: Replaced @Data with @Getter/@Setter to prevent PII exposure in toString()
 * GDPR Article 32: Personal data (email, phone) must be protected in logs
 */
@lombok.Getter
@lombok.Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "Source account is required")
    private String sourceAccountId;

    @NotBlank(message = "Destination account is required")
    private String destinationAccountId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    private String description;
    private String reference;
    private String paymentType; // TRANSFER, PAYMENT, WITHDRAWAL, DEPOSIT

    // Optional fields for external transfers - GDPR protected
    private String destinationEmail; // Sensitive - mask in logs
    private String destinationPhone; // Sensitive - mask in logs

    /**
     * Custom toString() that masks PII (email, phone) per GDPR requirements
     */
    @Override
    public String toString() {
        return "PaymentRequest{" +
            "sourceAccountId='" + sourceAccountId + '\'' +
            ", destinationAccountId='" + destinationAccountId + '\'' +
            ", amount=" + amount +
            ", currency='" + currency + '\'' +
            ", description='" + description + '\'' +
            ", reference='" + reference + '\'' +
            ", paymentType='" + paymentType + '\'' +
            ", destinationEmail='[REDACTED-GDPR]'" +
            ", destinationPhone='[REDACTED-GDPR]'" +
            '}';
    }
}