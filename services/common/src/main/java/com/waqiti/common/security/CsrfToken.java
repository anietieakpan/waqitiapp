package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;

/**
 * CSRF Token data model
 */
@Data
@Builder
public class CsrfToken {
    private String token;
    private String sessionId;
    private String userId;
    private long expiration;
    private boolean enabled;
    
    public static CsrfToken disabled() {
        return CsrfToken.builder()
            .enabled(false)
            .build();
    }
    
    public boolean isExpired() {
        return System.currentTimeMillis() > expiration;
    }
}