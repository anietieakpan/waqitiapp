package com.waqiti.common.fraud.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

/**
 * Blacklist check request
 */
@Data
@Builder
@Jacksonized
public class BlacklistCheckRequest {
    private String entityId;
    private String entityType;
    private String ipAddress;
    private String email;
    private String phoneNumber;
    private String deviceId;
    private String checkType;
    private String identifier;
    private Map<String, String> additionalData;
    
    /**
     * Get the type of blacklist check to perform
     */
    public String getCheckType() {
        return checkType != null ? checkType : entityType;
    }
    
    /**
     * Get the identifier to check against blacklists
     */
    public String getIdentifier() {
        if (identifier != null) {
            return identifier;
        }
        
        // Return the appropriate identifier based on check type
        if ("IP".equals(getCheckType())) {
            return ipAddress;
        } else if ("EMAIL".equals(getCheckType())) {
            return email;
        } else if ("PHONE".equals(getCheckType())) {
            return phoneNumber;
        } else if ("DEVICE".equals(getCheckType())) {
            return deviceId;
        } else {
            return entityId;
        }
    }
}