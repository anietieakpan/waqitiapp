package com.waqiti.payment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

/**
 * Response DTO for NFC signature validation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NFCSignatureValidationResponse {

    private boolean valid;
    private String dataType;
    private String algorithm;
    private String keyId;
    private Instant validatedAt;
    private String validationResult;
    
    // Additional validation details
    private boolean keyTrusted;
    private boolean timestampValid;
    private boolean deviceAuthorized;
    private String trustLevel; // HIGH, MEDIUM, LOW
    
    // Error information (if validation failed)
    private String errorCode;
    private String errorMessage;
    private String[] validationErrors;
    
    // Security metrics
    private String keyStrength;
    private String signatureStrength;
    private boolean hardwareBacked;
    
    // Additional metadata
    private String metadata;

    /**
     * Creates a successful validation response
     */
    public static NFCSignatureValidationResponse valid(String dataType, String algorithm) {
        return NFCSignatureValidationResponse.builder()
                .valid(true)
                .dataType(dataType)
                .algorithm(algorithm)
                .validatedAt(Instant.now())
                .validationResult("SIGNATURE_VALID")
                .keyTrusted(true)
                .timestampValid(true)
                .trustLevel("HIGH")
                .build();
    }

    /**
     * Creates a failed validation response
     */
    public static NFCSignatureValidationResponse invalid(String errorCode, String errorMessage) {
        return NFCSignatureValidationResponse.builder()
                .valid(false)
                .validatedAt(Instant.now())
                .validationResult("SIGNATURE_INVALID")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .trustLevel("LOW")
                .build();
    }

    /**
     * Checks if the signature is valid and trusted
     */
    public boolean isValidAndTrusted() {
        return valid && keyTrusted && timestampValid;
    }

    /**
     * Checks if this is a high-trust validation
     */
    public boolean isHighTrust() {
        return "HIGH".equals(trustLevel) && hardwareBacked;
    }
}