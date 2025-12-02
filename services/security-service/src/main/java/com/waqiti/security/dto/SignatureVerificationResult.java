package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DTO representing the result of API signature verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureVerificationResult {
    
    private boolean valid;
    private String clientId;
    private String keyId;
    private String algorithm;
    private LocalDateTime verifiedAt;
    private String errorMessage;
    private List<String> errorDetails;
    private Map<String, Object> metadata;
    
    // Convenience factory methods
    
    /**
     * Create a successful verification result
     */
    public static SignatureVerificationResult success(String clientId, String keyId, String algorithm) {
        return SignatureVerificationResult.builder()
                .valid(true)
                .clientId(clientId)
                .keyId(keyId)
                .algorithm(algorithm)
                .verifiedAt(LocalDateTime.now())
                .errorDetails(new ArrayList<>())
                .build();
    }
    
    /**
     * Create a successful verification result with metadata
     */
    public static SignatureVerificationResult success(String clientId, String keyId, String algorithm, 
                                                    Map<String, Object> metadata) {
        return SignatureVerificationResult.builder()
                .valid(true)
                .clientId(clientId)
                .keyId(keyId)
                .algorithm(algorithm)
                .verifiedAt(LocalDateTime.now())
                .metadata(metadata)
                .errorDetails(new ArrayList<>())
                .build();
    }
    
    /**
     * Create a failed verification result
     */
    public static SignatureVerificationResult failure(String errorMessage) {
        return SignatureVerificationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .verifiedAt(LocalDateTime.now())
                .errorDetails(new ArrayList<>())
                .build();
    }
    
    /**
     * Create a failed verification result with details
     */
    public static SignatureVerificationResult failure(String errorMessage, List<String> errorDetails) {
        return SignatureVerificationResult.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .errorDetails(errorDetails != null ? errorDetails : new ArrayList<>())
                .verifiedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Create a failed verification result with client context
     */
    public static SignatureVerificationResult failure(String clientId, String errorMessage) {
        return SignatureVerificationResult.builder()
                .valid(false)
                .clientId(clientId)
                .errorMessage(errorMessage)
                .verifiedAt(LocalDateTime.now())
                .errorDetails(new ArrayList<>())
                .build();
    }
    
    /**
     * Create an invalid verification result (alias for failure)
     */
    public static SignatureVerificationResult invalid(String errorMessage) {
        return failure(errorMessage);
    }
    
    /**
     * Create a valid verification result (alias for success) 
     */
    public static SignatureVerificationResult valid(String clientId) {
        return success(clientId, null, null);
    }
    
    // Utility methods
    
    /**
     * Add an error detail to the result
     */
    public void addErrorDetail(String detail) {
        if (errorDetails == null) {
            errorDetails = new ArrayList<>();
        }
        errorDetails.add(detail);
    }
    
    /**
     * Check if this is a successful verification
     */
    public boolean isSuccess() {
        return valid;
    }
    
    /**
     * Check if this is a failed verification
     */
    public boolean isFailure() {
        return !valid;
    }
    
    /**
     * Get a summary of the verification result
     */
    public String getSummary() {
        if (valid) {
            return String.format("Signature verification successful for client '%s' using algorithm '%s'", 
                               clientId, algorithm);
        } else {
            return String.format("Signature verification failed: %s", errorMessage);
        }
    }
    
    /**
     * Check if there are any error details
     */
    public boolean hasErrorDetails() {
        return errorDetails != null && !errorDetails.isEmpty();
    }
    
    /**
     * Get the number of error details
     */
    public int getErrorDetailCount() {
        return errorDetails != null ? errorDetails.size() : 0;
    }
    
    /**
     * Check if the result has metadata
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
    
    /**
     * Get a metadata value by key
     */
    public Object getMetadata(String key) {
        return metadata != null ? metadata.get(key) : null;
    }
    
    /**
     * Add metadata to the result
     */
    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }
    
    /**
     * Create a string representation for logging
     */
    public String toLogString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SignatureVerificationResult{");
        sb.append("valid=").append(valid);
        sb.append(", clientId='").append(clientId).append('\'');
        sb.append(", algorithm='").append(algorithm).append('\'');
        sb.append(", verifiedAt=").append(verifiedAt);
        
        if (!valid && errorMessage != null) {
            sb.append(", errorMessage='").append(errorMessage).append('\'');
        }
        
        if (hasErrorDetails()) {
            sb.append(", errorDetailCount=").append(getErrorDetailCount());
        }
        
        sb.append('}');
        return sb.toString();
    }
}