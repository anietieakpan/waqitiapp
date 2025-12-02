package com.waqiti.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publisher for security-related events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityEventPublisher {
    
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Publish a security event
     */
    public void publishSecurityEvent(SecurityEvent event) {
        log.debug("Publishing security event: {}", event.getEventType());
        eventPublisher.publishEvent(event);
    }
    
    /**
     * Publish authentication success event
     */
    public void publishAuthenticationSuccess(String userId, String authenticationType) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType("AUTHENTICATION_SUCCESS")
            .userId(userId)
            .details("Authentication type: " + authenticationType)
            .timestamp(System.currentTimeMillis())
            .build();
        publishSecurityEvent(event);
    }
    
    /**
     * Publish authentication failure event
     */
    public void publishAuthenticationFailure(String userId, String reason) {
        SecurityEvent event = SecurityEvent.builder()
            .eventType("AUTHENTICATION_FAILURE")
            .userId(userId)
            .details("Failure reason: " + reason)
            .timestamp(System.currentTimeMillis())
            .build();
        publishSecurityEvent(event);
    }
}