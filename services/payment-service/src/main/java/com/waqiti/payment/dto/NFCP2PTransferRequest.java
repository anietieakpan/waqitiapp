package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request DTO for NFC P2P transfers
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCP2PTransferRequest {

    @NotBlank(message = "Transfer ID is required")
    @Size(max = 64, message = "Transfer ID must not exceed 64 characters")
    private String transferId;

    @NotBlank(message = "Sender ID is required")
    @Size(max = 64, message = "Sender ID must not exceed 64 characters")
    private String senderId;

    @NotBlank(message = "Recipient ID is required")
    @Size(max = 64, message = "Recipient ID must not exceed 64 characters")
    private String recipientId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "5000.00", message = "Amount cannot exceed $5,000")
    @Digits(integer = 6, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO code")
    private String currency;

    @Size(max = 255, message = "Message must not exceed 255 characters")
    private String message;

    @NotNull(message = "Timestamp is required")
    @PastOrPresent(message = "Timestamp cannot be in the future")
    private Instant timestamp;

    @NotBlank(message = "Signature is required")
    @Size(max = 512, message = "Signature must not exceed 512 characters")
    private String signature;

    @NotBlank(message = "Sender device ID is required")
    @Size(max = 128, message = "Sender device ID must not exceed 128 characters")
    private String senderDeviceId;

    @NotBlank(message = "Recipient device ID is required")
    @Size(max = 128, message = "Recipient device ID must not exceed 128 characters")
    private String recipientDeviceId;

    @Size(max = 64, message = "NFC session ID must not exceed 64 characters")
    private String nfcSessionId;

    @Size(max = 32, message = "NFC protocol version must not exceed 32 characters")
    private String nfcProtocolVersion;

    // Security fields
    @Size(max = 255, message = "Sender device fingerprint must not exceed 255 characters")
    private String senderDeviceFingerprint;

    @Size(max = 255, message = "Recipient device fingerprint must not exceed 255 characters")
    private String recipientDeviceFingerprint;

    // Location data for both devices
    private Double senderLatitude;
    private Double senderLongitude;
    private Double recipientLatitude;
    private Double recipientLongitude;
    
    @Size(max = 10, message = "Location accuracy must not exceed 10 characters")
    private String locationAccuracy;

    // Transfer type
    @Size(max = 32, message = "Transfer type must not exceed 32 characters")
    private String transferType; // INSTANT, SCHEDULED, REQUEST

    // Additional metadata
    @Size(max = 1000, message = "Metadata must not exceed 1000 characters")
    private String metadata;

    /**
     * Validates if the transfer request has expired
     */
    public boolean isExpired(int expirationMinutes) {
        if (timestamp == null) {
            return true;
        }
        
        Instant expirationTime = timestamp.plusSeconds(expirationMinutes * 60L);
        return Instant.now().isAfter(expirationTime);
    }

    /**
     * Gets the transfer amount in cents
     */
    public long getAmountInCents() {
        return amount != null ? amount.multiply(BigDecimal.valueOf(100)).longValue() : 0L;
    }

    /**
     * Validates if both devices are properly identified
     */
    public boolean hasValidDeviceIds() {
        return senderDeviceId != null && !senderDeviceId.trim().isEmpty() &&
               recipientDeviceId != null && !recipientDeviceId.trim().isEmpty() &&
               !senderDeviceId.equals(recipientDeviceId);
    }

    /**
     * Checks if this is an instant transfer
     */
    public boolean isInstantTransfer() {
        return "INSTANT".equals(transferType) || transferType == null;
    }
}