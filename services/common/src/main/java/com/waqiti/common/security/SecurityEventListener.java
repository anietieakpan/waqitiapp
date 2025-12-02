package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.*;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Security Event Listener for monitoring and alerting on security events
 */
@Component
@Slf4j
public class SecurityEventListener {

    /**
     * Handle successful authentication events
     */
    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String username = auth.getName();
        String ipAddress = getIpAddress(auth);
        
        log.info("Successful authentication for user: {} from IP: {}", username, ipAddress);
        
        // Could send metrics to monitoring system
        // metricsCollector.incrementSuccessfulAuth(username, ipAddress);
    }

    /**
     * Handle failed authentication events
     */
    @EventListener
    public void handleAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        String reason = event.getException().getMessage();
        
        log.warn("Failed authentication attempt for user: {} from IP: {} reason: {}", 
            username, ipAddress, reason);
        
        // Could trigger rate limiting or alerting
        // securityMetrics.incrementFailedAuth(username, ipAddress);
    }

    /**
     * Handle bad credentials events
     */
    @EventListener
    public void handleBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        
        log.warn("Bad credentials attempt for user: {} from IP: {}", username, ipAddress);
        
        // Track potential brute force attempts
    }

    /**
     * Handle account locked events
     */
    @EventListener
    public void handleAccountLocked(AuthenticationFailureLockedEvent event) {
        String username = event.getAuthentication().getName();
        String ipAddress = getIpAddress(event.getAuthentication());
        
        log.warn("Account locked authentication attempt for user: {} from IP: {}", username, ipAddress);
    }

    /**
     * Handle authorization denied events
     */
    @EventListener
    public void handleAuthorizationDenied(AuthorizationDeniedEvent event) {
        String username = event.getAuthentication().get().getName();
        Object resource = event.getAuthorizationDecision();
        
        log.warn("Authorization denied for user: {} accessing resource: {}", username, resource);
        
        // Track unauthorized access attempts
    }

    /**
     * Extract IP address from authentication details
     */
    private String getIpAddress(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails details = (WebAuthenticationDetails) authentication.getDetails();
            return details.getRemoteAddress();
        }
        return "unknown";
    }
}