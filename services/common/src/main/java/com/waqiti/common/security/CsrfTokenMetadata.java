package com.waqiti.common.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

/**
 * Metadata associated with CSRF tokens
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CsrfTokenMetadata {
    private String sessionId;
    private String userId;
    private long issueTime;
    private long expirationTime;
    private String userAgent;
    private String clientIp;
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize CSRF token metadata", e);
        }
    }
    
    public static CsrfTokenMetadata fromJson(String json) {
        try {
            return objectMapper.readValue(json, CsrfTokenMetadata.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize CSRF token metadata", e);
        }
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}