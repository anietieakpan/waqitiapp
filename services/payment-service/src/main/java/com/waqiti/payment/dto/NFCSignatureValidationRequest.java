package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;

/**
 * Request DTO for NFC signature validation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCSignatureValidationRequest {

    @NotBlank(message = "Data is required")
    @Size(max = 10000, message = "Data must not exceed 10000 characters")
    private String data;

    @NotBlank(message = "Signature is required")
    @Size(max = 1024, message = "Signature must not exceed 1024 characters")
    private String signature;

    @NotBlank(message = "Public key is required")
    @Size(max = 1024, message = "Public key must not exceed 1024 characters")
    private String publicKey;

    @NotBlank(message = "Data type is required")
    @Size(max = 32, message = "Data type must not exceed 32 characters")
    private String dataType; // PAYMENT, TRANSFER, CONTACT_EXCHANGE

    @Size(max = 32, message = "Algorithm must not exceed 32 characters")
    private String algorithm; // RSA, ECDSA, etc.

    @Size(max = 64, message = "Key ID must not exceed 64 characters")
    private String keyId;

    private Long timestamp;
    
    @Size(max = 128, message = "Device ID must not exceed 128 characters")
    private String deviceId;

    @Size(max = 64, message = "User ID must not exceed 64 characters")
    private String userId;

    /**
     * Gets the default algorithm if not specified
     */
    public String getAlgorithmOrDefault() {
        return algorithm != null ? algorithm : "ECDSA";
    }

    /**
     * Validates if the request has all required fields for signature validation
     */
    public boolean hasRequiredFields() {
        return data != null && !data.trim().isEmpty() &&
               signature != null && !signature.trim().isEmpty() &&
               publicKey != null && !publicKey.trim().isEmpty();
    }
}