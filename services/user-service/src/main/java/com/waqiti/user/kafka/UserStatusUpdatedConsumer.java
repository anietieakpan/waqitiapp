package com.waqiti.user.kafka;

import com.waqiti.user.event.UserStatusUpdatedEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.UserRiskAssessmentService;
import com.waqiti.user.service.UserActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for user status update events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusUpdatedConsumer {

    private final UserService userService;
    private final NotificationService notificationService;
    private final UserRiskAssessmentService riskAssessmentService;
    private final UserActivityLogService activityLogService;

    @KafkaListener(topics = "user-status-updated", groupId = "user-status-processor")
    public void processStatusUpdate(@Payload UserStatusUpdatedEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            log.info("Processing status update for user: {} from {} to {}", 
                    event.getUserId(), event.getPreviousStatus(), event.getNewStatus());
            
            // Validate event
            validateStatusUpdateEvent(event);
            
            // Update user status
            userService.updateUserStatus(
                event.getUserId(),
                event.getNewStatus(),
                event.getUpdateReason(),
                event.getUpdatedBy(),
                event.getEffectiveDate()
            );
            
            // Handle status-specific actions
            handleStatusTransition(event);
            
            // Update risk assessment if risk level changed
            if (event.getRiskLevel() != null) {
                riskAssessmentService.updateUserRiskLevel(
                    event.getUserId(),
                    event.getRiskLevel()
                );
            }
            
            // Update KYC status if changed
            if (event.getKycStatus() != null) {
                userService.updateKycStatus(
                    event.getUserId(),
                    event.getKycStatus()
                );
            }
            
            // Update account tier if changed
            if (event.getAccountTier() != null) {
                userService.updateAccountTier(
                    event.getUserId(),
                    event.getAccountTier()
                );
            }
            
            // Send notification about status change
            notificationService.sendStatusChangeNotification(
                event.getUserId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getUpdateReason()
            );
            
            // Log status change activity
            activityLogService.logStatusChange(
                event.getUserId(),
                event.getPreviousStatus(),
                event.getNewStatus(),
                event.getUpdateReason(),
                event.getUpdatedBy(),
                event.getEffectiveDate()
            );
            
            // Handle expiry date if set
            if (event.getExpiryDate() != null) {
                userService.scheduleStatusExpiry(
                    event.getUserId(),
                    event.getNewStatus(),
                    event.getExpiryDate()
                );
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed status update for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process status update for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Status update processing failed", e);
        }
    }

    private void validateStatusUpdateEvent(UserStatusUpdatedEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for status update");
        }
        
        if (event.getNewStatus() == null || event.getNewStatus().trim().isEmpty()) {
            throw new IllegalArgumentException("New status is required");
        }
    }

    private void handleStatusTransition(UserStatusUpdatedEvent event) {
        switch (event.getNewStatus()) {
            case "ACTIVE" -> handleAccountActivation(event);
            case "SUSPENDED" -> handleAccountSuspension(event);
            case "LOCKED" -> handleAccountLocked(event);
            case "PENDING_VERIFICATION" -> handlePendingVerification(event);
            case "INACTIVE" -> handleAccountInactive(event);
            case "DELETED" -> handleAccountDeleted(event);
            default -> log.debug("No specific action for status: {}", event.getNewStatus());
        }
    }

    private void handleAccountActivation(UserStatusUpdatedEvent event) {
        // Restore user services
        userService.restoreUserServices(event.getUserId());
        
        // Send reactivation welcome
        notificationService.sendReactivationWelcome(
            event.getUserId(),
            event.getEffectiveDate()
        );
        
        // Clear any restrictions
        userService.clearAccountRestrictions(event.getUserId());
    }

    private void handleAccountSuspension(UserStatusUpdatedEvent event) {
        // Suspend user services
        userService.suspendUserServices(event.getUserId());
        
        // Revoke active sessions
        userService.revokeActiveSessions(event.getUserId());
        
        // Apply restrictions based on reason
        if (event.getRestrictionLevel() != null) {
            userService.applyAccountRestrictions(
                event.getUserId(),
                event.getRestrictionLevel()
            );
        }
        
        // Schedule review if expiry date is set
        if (event.getExpiryDate() != null) {
            userService.scheduleAccountReview(
                event.getUserId(),
                event.getExpiryDate()
            );
        }
    }

    private void handleAccountLocked(UserStatusUpdatedEvent event) {
        // Immediately revoke all access
        userService.revokeAllAccess(event.getUserId());
        
        // Trigger security alert
        riskAssessmentService.triggerSecurityAlert(
            event.getUserId(),
            "ACCOUNT_LOCKED",
            event.getUpdateReason()
        );
        
        // Send urgent notification
        notificationService.sendAccountLockedAlert(
            event.getUserId(),
            event.getUpdateReason()
        );
    }

    private void handlePendingVerification(UserStatusUpdatedEvent event) {
        // Send verification reminder
        notificationService.sendVerificationReminder(
            event.getUserId()
        );
        
        // Schedule follow-up reminders
        notificationService.scheduleVerificationFollowUp(
            event.getUserId(),
            event.getEffectiveDate()
        );
    }

    private void handleAccountInactive(UserStatusUpdatedEvent event) {
        // Reduce service limits
        userService.applyInactiveAccountLimits(event.getUserId());
        
        // Send reengagement campaign
        notificationService.startReengagementCampaign(
            event.getUserId()
        );
    }

    private void handleAccountDeleted(UserStatusUpdatedEvent event) {
        // Archive all user data
        userService.archiveUserData(event.getUserId());
        
        // Schedule permanent deletion
        userService.schedulePermanentDeletion(
            event.getUserId(),
            event.getEffectiveDate().plusDays(30)
        );
        
        // Remove from all systems
        userService.removeFromAllSystems(event.getUserId());
    }
}