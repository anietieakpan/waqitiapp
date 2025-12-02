package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.event.UserAccountDeactivatedEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.ComplianceReportingService;
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
 * Production-grade Kafka consumer for user account deactivation events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountDeactivatedConsumer {

    private final UserService userService;
    private final NotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final UserActivityLogService activityLogService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "user-account-deactivated", groupId = "user-deactivation-processor")
    public void processAccountDeactivation(@Payload UserAccountDeactivatedEvent event,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment) {
        try {
            log.info("Processing account deactivation event for user: {} reason: {} type: {}", 
                    event.getUserId(), event.getDeactivationReason(), event.getDeactivationType());
            
            // Validate event
            validateDeactivationEvent(event);
            
            // Process deactivation based on type
            if ("PERMANENT".equals(event.getDeactivationType())) {
                handlePermanentDeactivation(event);
            } else {
                handleTemporaryDeactivation(event);
            }
            
            // Handle refunds if applicable
            if (event.isRefundPending()) {
                userService.initiateRefundProcess(
                    event.getUserId(),
                    event.getFinalBalance(),
                    event.getCurrency()
                );
            }
            
            // Revoke all active sessions and tokens
            userService.revokeAllUserTokens(event.getUserId());
            
            // Send deactivation notification
            notificationService.sendDeactivationEmail(
                event.getUserId(),
                event.getEmail(),
                event.getDeactivationReason(),
                event.getDeactivationType(),
                event.getScheduledReactivation()
            );
            
            // Report to compliance if required
            if (isComplianceReportingRequired(event)) {
                complianceService.reportAccountDeactivation(
                    event.getUserId(),
                    event.getDeactivationReason(),
                    event.getDeactivatedBy(),
                    event.getTicketId()
                );
            }
            
            // Log deactivation activity
            activityLogService.logAccountDeactivation(
                event.getUserId(),
                event.getDeactivationReason(),
                event.getDeactivatedBy(),
                event.getIpAddress(),
                event.getDeviceId(),
                event.getDeactivatedAt()
            );
            
            // Schedule data retention/deletion based on policy
            if (!event.isPreserveData()) {
                userService.scheduleDataDeletion(
                    event.getUserId(),
                    event.getDeactivatedAt().plusDays(30)
                );
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed account deactivation for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process account deactivation for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Account deactivation processing failed", e);
        }
    }

    private void validateDeactivationEvent(UserAccountDeactivatedEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for account deactivation");
        }
        
        if (event.getDeactivationReason() == null || event.getDeactivationReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Deactivation reason is required");
        }
        
        if (event.getDeactivationType() == null || event.getDeactivationType().trim().isEmpty()) {
            throw new IllegalArgumentException("Deactivation type is required");
        }
    }

    private void handlePermanentDeactivation(UserAccountDeactivatedEvent event) {
        // Permanently deactivate account
        userService.permanentlyDeactivateAccount(
            event.getUserId(),
            event.getDeactivationReason(),
            event.getNotes()
        );
        
        // Archive user data
        userService.archiveUserData(event.getUserId());
        
        // Remove from all marketing lists
        notificationService.removeFromAllLists(event.getEmail());
        
        // Generate compliance report
        complianceService.generateDeactivationReport(
            event.getUserId(),
            event.getDeactivationReason(),
            event.getDeactivatedAt()
        );
    }

    private void handleTemporaryDeactivation(UserAccountDeactivatedEvent event) {
        // Temporarily deactivate account
        userService.temporarilyDeactivateAccount(
            event.getUserId(),
            event.getDeactivationReason(),
            event.getScheduledReactivation()
        );
        
        // Suspend all services
        userService.suspendUserServices(event.getUserId());
        
        // Schedule reactivation reminder if date is set
        if (event.getScheduledReactivation() != null) {
            notificationService.scheduleReactivationReminder(
                event.getUserId(),
                event.getEmail(),
                event.getScheduledReactivation()
            );
        }
    }

    private boolean isComplianceReportingRequired(UserAccountDeactivatedEvent event) {
        return "SECURITY_VIOLATION".equals(event.getDeactivationReason()) ||
               "COMPLIANCE_ISSUE".equals(event.getDeactivationReason()) ||
               "FRAUD".equals(event.getDeactivationReason()) ||
               "COMPLIANCE_TEAM".equals(event.getDeactivatedBy());
    }
}