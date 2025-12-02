package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.event.UserPasswordChangeEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.PasswordService;
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

/**
 * Production-grade Kafka consumer for user password change events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPasswordChangeConsumer {

    private final UserService userService;
    private final PasswordService passwordService;
    private final SecurityAuditService securityAuditService;
    private final NotificationService notificationService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "user-password-changes", groupId = "password-change-processor")
    public void processPasswordChange(@Payload UserPasswordChangeEvent event,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                    @Header(KafkaHeaders.OFFSET) long offset,
                                    Acknowledgment acknowledgment) {
        try {
            log.info("Processing password change for user: {} type: {} forced: {}", 
                    event.getUserId(), event.getChangeType(), event.isForced());
            
            // Validate event
            validatePasswordChangeEvent(event);
            
            // Process based on change type
            switch (event.getChangeType()) {
                case "USER_INITIATED" -> handleUserInitiatedChange(event);
                case "FORCED_RESET" -> handleForcedReset(event);
                case "EXPIRED" -> handlePasswordExpiry(event);
                case "COMPROMISED" -> handleCompromisedPassword(event);
                case "ADMIN_RESET" -> handleAdminReset(event);
                default -> log.warn("Unknown password change type: {}", event.getChangeType());
            }
            
            // Update password history
            passwordService.addToPasswordHistory(
                event.getUserId(),
                event.getPasswordHash(),
                event.getChangedAt()
            );
            
            // Revoke existing tokens
            if (event.isRevokeTokens()) {
                userService.revokeAllTokens(event.getUserId());
            }
            
            // Send notifications
            sendPasswordChangeNotifications(event);
            
            // Log password change
            securityAuditService.logPasswordChange(
                event.getUserId(),
                event.getChangeType(),
                event.getChangedBy(),
                event.getIpAddress(),
                event.getDeviceId(),
                event.getChangedAt()
            );
            
            // Schedule next password expiry
            if (event.getPasswordExpiryDays() > 0) {
                schedulePasswordExpiry(event);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed password change for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process password change for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Password change processing failed", e);
        }
    }

    private void validatePasswordChangeEvent(UserPasswordChangeEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for password change");
        }
        
        if (event.getChangeType() == null || event.getChangeType().trim().isEmpty()) {
            throw new IllegalArgumentException("Change type is required");
        }
    }

    private void handleUserInitiatedChange(UserPasswordChangeEvent event) {
        // Validate password strength
        if (!passwordService.validatePasswordStrength(event.getPasswordHash())) {
            log.warn("Weak password detected for user: {}", event.getUserId());
            notificationService.sendWeakPasswordWarning(event.getUserId());
        }
        
        // Check password reuse
        if (passwordService.isPasswordReused(
                event.getUserId(),
                event.getPasswordHash(),
                event.getPasswordHistoryDepth())) {
            log.error("Password reuse detected for user: {}", event.getUserId());
            throw new IllegalArgumentException("Password has been used recently");
        }
        
        // Update password
        userService.updatePassword(
            event.getUserId(),
            event.getPasswordHash(),
            event.getChangedAt()
        );
        
        // Clear password reset tokens
        passwordService.clearPasswordResetTokens(event.getUserId());
        
        // Update security timestamp
        userService.updateSecurityTimestamp(event.getUserId());
    }

    private void handleForcedReset(UserPasswordChangeEvent event) {
        // Mark account as requiring password change
        userService.markPasswordChangeRequired(
            event.getUserId(),
            event.getResetReason()
        );
        
        // Generate reset token
        String resetToken = passwordService.generateResetToken(
            event.getUserId(),
            event.getTokenExpiry()
        );
        
        // Send reset instructions
        notificationService.sendPasswordResetInstructions(
            event.getUserId(),
            resetToken,
            event.getResetReason()
        );
        
        // Restrict account until reset
        userService.restrictAccountUntilPasswordReset(event.getUserId());
    }

    private void handlePasswordExpiry(UserPasswordChangeEvent event) {
        // Mark password as expired
        passwordService.markPasswordExpired(
            event.getUserId(),
            event.getExpiredAt()
        );
        
        // Force password change on next login
        userService.forcePasswordChangeOnNextLogin(event.getUserId());
        
        // Send expiry notification
        notificationService.sendPasswordExpiryNotice(
            event.getUserId(),
            event.getExpiredAt()
        );
        
        // Apply grace period if configured
        if (event.getGracePeriodDays() > 0) {
            passwordService.applyGracePeriod(
                event.getUserId(),
                event.getGracePeriodDays()
            );
        }
    }

    private void handleCompromisedPassword(UserPasswordChangeEvent event) {
        // Immediate security response
        log.error("Compromised password detected for user: {}", event.getUserId());
        
        // Lock account
        userService.lockAccount(
            event.getUserId(),
            "COMPROMISED_PASSWORD"
        );
        
        // Force immediate password reset
        passwordService.forceImmediateReset(
            event.getUserId(),
            "SECURITY_BREACH"
        );
        
        // Revoke all sessions and tokens
        userService.revokeAllAccess(event.getUserId());
        
        // Send security alert
        notificationService.sendCompromisedPasswordAlert(
            event.getUserId(),
            event.getCompromiseDetails(),
            event.getDetectedAt()
        );
        
        // Create security incident
        securityAuditService.createSecurityIncident(
            event.getUserId(),
            "COMPROMISED_PASSWORD",
            event.getCompromiseDetails(),
            "CRITICAL"
        );
    }

    private void handleAdminReset(UserPasswordChangeEvent event) {
        // Generate temporary password
        String tempPassword = passwordService.generateTemporaryPassword();
        
        // Set temporary password
        userService.setTemporaryPassword(
            event.getUserId(),
            tempPassword,
            event.getTempPasswordExpiry()
        );
        
        // Mark as requiring change
        userService.markPasswordChangeRequired(
            event.getUserId(),
            "ADMIN_RESET"
        );
        
        // Send temporary password
        notificationService.sendTemporaryPassword(
            event.getUserId(),
            tempPassword,
            event.getResetBy()
        );
        
        // Log admin action
        securityAuditService.logAdminPasswordReset(
            event.getUserId(),
            event.getResetBy(),
            event.getResetReason()
        );
    }

    private void sendPasswordChangeNotifications(UserPasswordChangeEvent event) {
        // Send confirmation to user
        notificationService.sendPasswordChangeConfirmation(
            event.getUserId(),
            event.getChangeType(),
            event.getChangedAt()
        );
        
        // Send to all devices if security-related
        if ("COMPROMISED".equals(event.getChangeType()) || 
            "FORCED_RESET".equals(event.getChangeType())) {
            notificationService.broadcastSecurityAlert(
                event.getUserId(),
                "PASSWORD_CHANGED",
                event.getChangeType()
            );
        }
        
        // Send to alternate email if configured
        if (event.getAlternateEmail() != null) {
            notificationService.sendToAlternateEmail(
                event.getAlternateEmail(),
                "PASSWORD_CHANGE_NOTICE",
                event.getUserId()
            );
        }
    }

    private void schedulePasswordExpiry(UserPasswordChangeEvent event) {
        LocalDateTime expiryDate = event.getChangedAt()
            .plusDays(event.getPasswordExpiryDays());
        
        passwordService.schedulePasswordExpiry(
            event.getUserId(),
            expiryDate
        );
        
        // Schedule expiry warnings
        passwordService.scheduleExpiryWarnings(
            event.getUserId(),
            expiryDate,
            event.getWarningDays()
        );
    }
}