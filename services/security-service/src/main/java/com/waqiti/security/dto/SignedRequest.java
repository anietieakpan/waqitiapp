package com.waqiti.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO representing a signed API request for signature verification
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedRequest {
    
    @NotBlank(message = "Client ID is required")
    private String clientId;
    
    @NotBlank(message = "HTTP method is required")
    private String method;
    
    @NotBlank(message = "URI is required")
    private String uri;
    
    private String body;
    
    @NotNull(message = "Headers map is required")
    private Map<String, String> headers;
    
    @NotBlank(message = "Timestamp is required")
    private String timestamp;
    
    @NotBlank(message = "Nonce is required")
    private String nonce;
    
    @NotBlank(message = "Algorithm is required")
    private String algorithm;
    
    @NotBlank(message = "Signature is required")
    private String signature;
    
    private String keyId;
    
    // Convenience methods for accessing parsed values
    
    /**
     * Get parsed timestamp as long
     */
    public long getTimestampLong() {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid timestamp format: " + timestamp);
        }
    }
    
    /**
     * Get timestamp as LocalDateTime (assuming timestamp is epoch millis)
     */
    public LocalDateTime getTimestampAsDateTime() {
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(getTimestampLong()),
            java.time.ZoneId.systemDefault()
        );
    }
    
    /**
     * Get the full URL including query parameters if any
     */
    public String getUrl() {
        return uri; // For now, just return URI. In production, might need to include host
    }
    
    /**
     * Check if the request has a body
     */
    public boolean hasBody() {
        return body != null && !body.trim().isEmpty();
    }
    
    /**
     * Get header value by name (case insensitive)
     */
    public String getHeader(String headerName) {
        if (headers == null) {
            return null;
        }
        
        // Try exact match first
        String value = headers.get(headerName);
        if (value != null) {
            return value;
        }
        
        // Try case-insensitive match
        return headers.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(headerName))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Validate that all required fields are present and valid
     */
    public void validate() {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID is required");
        }
        
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("HTTP method is required");
        }
        
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("URI is required");
        }
        
        if (timestamp == null || timestamp.trim().isEmpty()) {
            throw new IllegalArgumentException("Timestamp is required");
        }
        
        if (nonce == null || nonce.trim().isEmpty()) {
            throw new IllegalArgumentException("Nonce is required");
        }
        
        if (algorithm == null || algorithm.trim().isEmpty()) {
            throw new IllegalArgumentException("Algorithm is required");
        }
        
        if (signature == null || signature.trim().isEmpty()) {
            throw new IllegalArgumentException("Signature is required");
        }
        
        if (headers == null) {
            throw new IllegalArgumentException("Headers map is required");
        }
        
        // Validate timestamp format
        try {
            getTimestampLong();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid timestamp format: " + timestamp);
        }
        
        // Validate that timestamp is not too old or in the future
        long timestampValue = getTimestampLong();
        long currentTime = System.currentTimeMillis();
        long maxAge = 300000; // 5 minutes
        
        if (timestampValue < currentTime - maxAge) {
            throw new IllegalArgumentException("Request timestamp is too old");
        }
        
        if (timestampValue > currentTime + maxAge) {
            throw new IllegalArgumentException("Request timestamp is in the future");
        }
    }
    
    /**
     * Create a string representation for debugging (without sensitive data)
     */
    public String toDebugString() {
        return String.format(
            "SignedRequest{clientId='%s', method='%s', uri='%s', algorithm='%s', timestamp='%s', keyId='%s'}",
            clientId, method, uri, algorithm, timestamp, keyId
        );
    }
}