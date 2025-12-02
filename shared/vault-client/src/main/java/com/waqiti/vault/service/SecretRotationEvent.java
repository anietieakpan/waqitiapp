package com.waqiti.vault.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * CRITICAL SECURITY: Secret Rotation Event for Monitoring
 * PRODUCTION-READY: Event published when secrets are rotated
 */
@Data
@Builder
public class SecretRotationEvent {
    
    private final String secretType;
    private final String status; // SUCCESS, FAILED, WARNING
    private final String message;
    private final LocalDateTime timestamp;
    private final String rotationVersion;
    
    /**
     * Convert to Spring ApplicationEvent for event publishing
     */
    public ApplicationEvent toApplicationEvent(Object source) {
        return new ApplicationEvent(source) {
            public SecretRotationEvent getRotationEvent() {
                return SecretRotationEvent.this;
            }
        };
    }
}