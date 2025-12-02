package com.waqiti.voice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for initiating voice-based payment
 *
 * Security:
 * - Contains audio data (validate size and format)
 * - Requires user authentication
 * - Subject to fraud detection
 * - Rate limiting enforced
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoicePaymentRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Session ID is required")
    @Size(max = 100)
    private String sessionId;

    /**
     * Payment amount
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Amount exceeds maximum limit")
    private BigDecimal amount;

    /**
     * Currency code (ISO 4217)
     */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    @Builder.Default
    private String currency = "USD";

    /**
     * Recipient identifier (phone, email, username, or userId)
     */
    @Size(max = 200)
    private String recipientIdentifier;

    /**
     * Recipient user ID (if known)
     */
    private UUID recipientId;

    /**
     * Payment purpose/description
     */
    @Size(max = 500)
    private String purpose;

    /**
     * Voice sample for biometric verification
     * Base64 encoded audio data or URL to audio file
     */
    private String voiceSample;

    /**
     * Transcribed command text (if already processed)
     */
    @Size(max = 1000)
    private String transcribedText;

    /**
     * Language code
     */
    @NotBlank
    @Size(max = 20)
    @Builder.Default
    private String language = "en-US";

    /**
     * Device information
     */
    @Size(max = 500)
    private String deviceInfo;

    /**
     * Device ID for fraud detection
     */
    @Size(max = 100)
    private String deviceId;

    /**
     * IP address (for fraud detection)
     */
    @Size(max = 45)
    private String ipAddress;

    /**
     * Geographic location
     */
    @Size(max = 200)
    private String location;

    /**
     * Whether confirmation is required before execution
     */
    @Builder.Default
    private Boolean requiresConfirmation = true;

    /**
     * Idempotency key (client-provided to prevent duplicate submissions)
     */
    @Size(max = 100)
    private String idempotencyKey;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Payment type/category
     */
    @Size(max = 50)
    private String paymentType;

    /**
     * Validate request has minimum required fields
     */
    public boolean isValid() {
        return userId != null &&
               amount != null &&
               amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null &&
               !currency.isBlank() &&
               (recipientId != null || (recipientIdentifier != null && !recipientIdentifier.isBlank()));
    }

    /**
     * Check if voice biometric is provided
     */
    public boolean hasVoiceSample() {
        return voiceSample != null && !voiceSample.isBlank();
    }

    /**
     * Generate idempotency key if not provided
     */
    public String getOrGenerateIdempotencyKey() {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        // Generate based on request characteristics
        return String.format("VP-%s-%s-%s-%d",
                userId,
                recipientId != null ? recipientId : recipientIdentifier.hashCode(),
                amount.toPlainString(),
                System.currentTimeMillis() / 60000); // 1-minute window
    }
}
