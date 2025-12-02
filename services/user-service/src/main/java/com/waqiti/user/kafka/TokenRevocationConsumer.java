package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.event.TokenRevocationEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.TokenService;
import com.waqiti.user.service.SessionManagementService;
import com.waqiti.user.service.SecurityAuditService;
import com.waqiti.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Production-grade Kafka consumer for token revocation events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationConsumer {

    private final UserService userService;
    private final TokenService tokenService;
    private final SessionManagementService sessionService;
    private final SecurityAuditService securityAuditService;
    private final NotificationService notificationService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "token-revocations", groupId = "token-revocation-processor")
    public void processTokenRevocation(@Payload TokenRevocationEvent event,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     Acknowledgment acknowledgment) {
        try {
            log.info("Processing token revocation for user: {} type: {} reason: {} scope: {}", 
                    event.getUserId(), event.getTokenType(), 
                    event.getRevocationReason(), event.getRevocationScope());
            
            // Validate event
            validateTokenRevocationEvent(event);
            
            // Process revocation based on scope
            switch (event.getRevocationScope()) {
                case "SINGLE" -> revokeSingleToken(event);
                case "TYPE" -> revokeTokensByType(event);
                case "DEVICE" -> revokeDeviceTokens(event);
                case "USER" -> revokeAllUserTokens(event);
                case "SESSION" -> revokeSessionTokens(event);
                case "APPLICATION" -> revokeApplicationTokens(event);
                default -> log.warn("Unknown revocation scope: {}", event.getRevocationScope());
            }
            
            // Add to revocation list/blacklist
            addToRevocationList(event);
            
            // Invalidate related caches
            invalidateTokenCaches(event);
            
            // Handle security implications
            if (isSecurityRelated(event)) {
                handleSecurityRevocation(event);
            }
            
            // Send notifications if required
            if (event.isNotifyUser()) {
                sendRevocationNotification(event);
            }
            
            // Log revocation for audit
            securityAuditService.logTokenRevocation(
                event.getUserId(),
                event.getTokenId(),
                event.getTokenType(),
                event.getRevocationReason(),
                event.getRevokedBy(),
                event.getRevokedAt(),
                event.getIpAddress(),
                event.getDeviceId()
            );
            
            // Track revocation metrics
            tokenService.trackRevocationMetrics(
                event.getTokenType(),
                event.getRevocationReason(),
                event.getRevocationScope()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed token revocation for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process token revocation for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Token revocation processing failed", e);
        }
    }

    private void validateTokenRevocationEvent(TokenRevocationEvent event) {
        if (event.getRevocationScope() == null || event.getRevocationScope().trim().isEmpty()) {
            throw new IllegalArgumentException("Revocation scope is required");
        }
        
        if (event.getRevocationReason() == null || event.getRevocationReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Revocation reason is required");
        }
        
        if ("SINGLE".equals(event.getRevocationScope()) && 
            (event.getTokenId() == null || event.getTokenId().trim().isEmpty())) {
            throw new IllegalArgumentException("Token ID is required for single token revocation");
        }
    }

    private void revokeSingleToken(TokenRevocationEvent event) {
        // Revoke specific token
        tokenService.revokeToken(
            event.getTokenId(),
            event.getRevocationReason(),
            event.getRevokedBy()
        );
        
        // Terminate associated session if exists
        if (event.getSessionId() != null) {
            sessionService.terminateSession(
                event.getSessionId(),
                "TOKEN_REVOKED"
            );
        }
        
        log.info("Revoked single token: {} for user: {}", 
                event.getTokenId(), event.getUserId());
    }

    private void revokeTokensByType(TokenRevocationEvent event) {
        // Get all tokens of specified type
        List<String> tokenIds = tokenService.getTokenIdsByType(
            event.getUserId(),
            event.getTokenType()
        );
        
        // Revoke each token
        for (String tokenId : tokenIds) {
            tokenService.revokeToken(
                tokenId,
                event.getRevocationReason(),
                event.getRevokedBy()
            );
        }
        
        // Handle type-specific actions
        switch (event.getTokenType()) {
            case "ACCESS_TOKEN" -> {
                sessionService.terminateActiveSessions(event.getUserId());
            }
            case "REFRESH_TOKEN" -> {
                userService.forceReauthentication(event.getUserId());
            }
            case "API_KEY" -> {
                userService.disableApiAccess(event.getUserId());
            }
            case "SESSION_TOKEN" -> {
                sessionService.clearAllSessions(event.getUserId());
            }
        }
        
        log.info("Revoked {} tokens of type {} for user: {}", 
                tokenIds.size(), event.getTokenType(), event.getUserId());
    }

    private void revokeDeviceTokens(TokenRevocationEvent event) {
        // Get all tokens for device
        List<String> tokenIds = tokenService.getTokenIdsByDevice(
            event.getUserId(),
            event.getDeviceId()
        );
        
        // Revoke each token
        for (String tokenId : tokenIds) {
            tokenService.revokeToken(
                tokenId,
                event.getRevocationReason(),
                event.getRevokedBy()
            );
        }
        
        // Terminate device sessions
        sessionService.terminateDeviceSessions(
            event.getUserId(),
            event.getDeviceId()
        );
        
        // Remove device trust if compromised
        if ("DEVICE_COMPROMISED".equals(event.getRevocationReason())) {
            userService.removeTrustedDevice(
                event.getUserId(),
                event.getDeviceId()
            );
        }
        
        log.info("Revoked {} tokens for device {} of user: {}", 
                tokenIds.size(), event.getDeviceId(), event.getUserId());
    }

    private void revokeAllUserTokens(TokenRevocationEvent event) {
        // Revoke all tokens for user
        int revokedCount = tokenService.revokeAllUserTokens(
            event.getUserId(),
            event.getRevocationReason(),
            event.getRevokedBy()
        );
        
        // Terminate all sessions
        sessionService.terminateAllUserSessions(event.getUserId());
        
        // Force re-authentication
        userService.forceReauthentication(event.getUserId());
        
        // Clear all cached credentials
        tokenService.clearUserTokenCache(event.getUserId());
        
        // Disable API access temporarily
        if ("SECURITY_BREACH".equals(event.getRevocationReason())) {
            userService.temporarilyDisableAccount(
                event.getUserId(),
                "SECURITY_BREACH_TOKEN_REVOCATION"
            );
        }
        
        log.info("Revoked all {} tokens for user: {}", revokedCount, event.getUserId());
    }

    private void revokeSessionTokens(TokenRevocationEvent event) {
        // Get all tokens for session
        List<String> tokenIds = tokenService.getTokenIdsBySession(
            event.getSessionId()
        );
        
        // Revoke each token
        for (String tokenId : tokenIds) {
            tokenService.revokeToken(
                tokenId,
                event.getRevocationReason(),
                event.getRevokedBy()
            );
        }
        
        // Terminate the session
        sessionService.terminateSession(
            event.getSessionId(),
            event.getRevocationReason()
        );
        
        log.info("Revoked {} tokens for session {} of user: {}", 
                tokenIds.size(), event.getSessionId(), event.getUserId());
    }

    private void revokeApplicationTokens(TokenRevocationEvent event) {
        // Get all tokens for application
        List<String> tokenIds = tokenService.getTokenIdsByApplication(
            event.getApplicationId()
        );
        
        // Revoke each token
        for (String tokenId : tokenIds) {
            tokenService.revokeToken(
                tokenId,
                event.getRevocationReason(),
                event.getRevokedBy()
            );
        }
        
        // Disable application if compromised
        if ("APPLICATION_COMPROMISED".equals(event.getRevocationReason())) {
            userService.disableApplication(
                event.getApplicationId(),
                "COMPROMISED"
            );
        }
        
        log.info("Revoked {} tokens for application: {}", 
                tokenIds.size(), event.getApplicationId());
    }

    private void addToRevocationList(TokenRevocationEvent event) {
        // Add to distributed revocation list
        if (event.getTokenId() != null) {
            tokenService.addToRevocationList(
                event.getTokenId(),
                event.getTokenType(),
                event.getRevokedAt(),
                event.getExpiresAt()
            );
        }
        
        // Add to user's revocation history
        if (event.getUserId() != null) {
            tokenService.addToUserRevocationHistory(
                event.getUserId(),
                event.getTokenId(),
                event.getRevocationReason(),
                event.getRevokedAt()
            );
        }
        
        // Propagate to edge servers
        tokenService.propagateRevocationToEdgeServers(
            event.getTokenId(),
            event.getTokenType()
        );
    }

    private void invalidateTokenCaches(TokenRevocationEvent event) {
        // Clear token from cache
        if (event.getTokenId() != null) {
            tokenService.removeFromCache(event.getTokenId());
        }
        
        // Clear user token cache
        if (event.getUserId() != null) {
            tokenService.clearUserTokenCache(event.getUserId());
        }
        
        // Clear session cache
        if (event.getSessionId() != null) {
            sessionService.clearSessionCache(event.getSessionId());
        }
        
        // Clear device cache
        if (event.getDeviceId() != null) {
            tokenService.clearDeviceTokenCache(event.getDeviceId());
        }
    }

    private boolean isSecurityRelated(TokenRevocationEvent event) {
        return "SECURITY_BREACH".equals(event.getRevocationReason()) ||
               "SUSPICIOUS_ACTIVITY".equals(event.getRevocationReason()) ||
               "UNAUTHORIZED_ACCESS".equals(event.getRevocationReason()) ||
               "COMPROMISED".equals(event.getRevocationReason()) ||
               "FRAUD_DETECTED".equals(event.getRevocationReason());
    }

    private void handleSecurityRevocation(TokenRevocationEvent event) {
        // Log security incident
        securityAuditService.logSecurityIncident(
            event.getUserId(),
            "TOKEN_REVOCATION_SECURITY",
            event.getRevocationReason(),
            event.getSecurityDetails(),
            event.getRiskLevel()
        );
        
        // Trigger security alert
        securityAuditService.triggerSecurityAlert(
            event.getUserId(),
            "TOKEN_REVOKED_SECURITY",
            event.getRevocationReason(),
            event.getRiskLevel()
        );
        
        // Increase user risk score
        if (event.getUserId() != null) {
            userService.increaseRiskScore(
                event.getUserId(),
                "TOKEN_SECURITY_REVOCATION",
                event.getRiskLevel() != null ? event.getRiskLevel() : "MEDIUM"
            );
        }
        
        // Enable additional security measures
        if ("HIGH".equals(event.getRiskLevel()) || "CRITICAL".equals(event.getRiskLevel())) {
            userService.enableEnhancedSecurity(
                event.getUserId(),
                "TOKEN_REVOCATION_HIGH_RISK"
            );
        }
    }

    private void sendRevocationNotification(TokenRevocationEvent event) {
        // Determine notification urgency
        String priority = isSecurityRelated(event) ? "HIGH" : "NORMAL";
        
        // Send immediate notification for security issues
        if (isSecurityRelated(event)) {
            notificationService.sendSecurityTokenRevocationAlert(
                event.getUserId(),
                event.getTokenType(),
                event.getRevocationReason(),
                event.getDeviceId(),
                event.getLocation(),
                event.getRevokedAt()
            );
        } else {
            notificationService.sendTokenRevocationNotification(
                event.getUserId(),
                event.getTokenType(),
                event.getRevocationReason(),
                event.getRevocationScope(),
                event.getRevokedAt()
            );
        }
        
        // Send to all registered devices if critical
        if ("CRITICAL".equals(event.getRiskLevel())) {
            notificationService.broadcastToAllDevices(
                event.getUserId(),
                "CRITICAL_TOKEN_REVOCATION",
                event.getRevocationReason()
            );
        }
    }
}