package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for processing payments through payment links
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentLinkRequest {
    
    @NotBlank(message = "Link ID is required")
    private String linkId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    @Digits(integer = 17, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    // Payer information (optional for anonymous payments)
    private UUID payerId; // null for anonymous payments
    
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String payerEmail;
    
    @Size(max = 100, message = "Payer name cannot exceed 100 characters")
    private String payerName;
    
    @Size(max = 500, message = "Payment note cannot exceed 500 characters")
    private String paymentNote;
    
    @NotBlank(message = "Payment method is required")
    private String paymentMethod; // "CARD", "BANK_TRANSFER", "DIGITAL_WALLET", etc.
    
    // Payment method specific data
    private Map<String, String> paymentMethodData;
    
    // Metadata for tracking and analytics
    private String ipAddress;
    private String userAgent;
    private String deviceId;
    private String sessionId;
    private Map<String, String> metadata;
    
    // Security and fraud prevention
    @Size(max = 10, message = "Country code cannot exceed 10 characters")
    private String countryCode;
    
    private String timeZone;
    private String referrer;
    
    // For recurring/subscription payments
    private Boolean isRecurring = false;
    private String recurringFrequency; // "MONTHLY", "YEARLY", etc.
    
    // Client-side verification token (for additional security)
    private String verificationToken;
    
    // Source tracking
    private String sourceChannel; // "DIRECT", "SOCIAL", "EMAIL", "QR", etc.
    private String campaignId;
    
    // Custom validation methods
    
    @AssertTrue(message = "Either payer ID or payer email must be provided")
    private boolean hasValidPayerInfo() {
        return payerId != null || payerEmail != null;
    }
    
    @AssertTrue(message = "Payment note is required when link requires note")
    private boolean hasPaymentNoteWhenRequired() {
        // This will be validated in the service layer based on link configuration
        return true;
    }
    
    public boolean isAnonymousPayment() {
        return payerId == null;
    }
    
    public boolean hasPaymentNote() {
        return paymentNote != null && !paymentNote.trim().isEmpty();
    }
}