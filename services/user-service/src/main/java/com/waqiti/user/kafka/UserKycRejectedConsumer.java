package com.waqiti.user.kafka;

import com.waqiti.user.event.UserKycRejectedEvent;
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
 * Production-grade Kafka consumer for KYC rejection events
 * Note: This extends the existing KycRejectedConsumer with additional functionality
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserKycRejectedConsumer {

    private final UserService userService;
    private final NotificationService notificationService;
    private final ComplianceReportingService complianceService;
    private final UserActivityLogService activityLogService;

    @KafkaListener(topics = "user-kyc-rejected", groupId = "kyc-rejection-processor")
    public void processKycRejection(@Payload UserKycRejectedEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            log.info("Processing KYC rejection for user: {} reason: {} attempt: {}/{}", 
                    event.getUserId(), event.getRejectionReason(), 
                    event.getAttemptNumber(), event.getMaxAttempts());
            
            // Validate event
            validateKycRejectionEvent(event);
            
            // Update KYC status
            userService.updateKycStatus(
                event.getUserId(),
                "REJECTED",
                event.getRejectionReason(),
                event.getReviewedBy()
            );
            
            // Apply account restrictions
            userService.applyKycRejectionRestrictions(
                event.getUserId(),
                event.getCanRetry()
            );
            
            // Send rejection notification with details
            notificationService.sendKycRejectionEmail(
                event.getUserId(),
                event.getEmail(),
                event.getRejectionReason(),
                event.getFailedChecks(),
                event.getRequiredDocuments(),
                event.getCanRetry(),
                event.getRetryAfter()
            );
            
            // Handle based on next action
            switch (event.getNextAction()) {
                case "RESUBMIT_DOCUMENTS" -> handleResubmitDocuments(event);
                case "CONTACT_SUPPORT" -> handleContactSupport(event);
                case "PERMANENTLY_REJECTED" -> handlePermanentRejection(event);
                default -> log.warn("Unknown next action: {}", event.getNextAction());
            }
            
            // Report to compliance
            complianceService.reportKycRejection(
                event.getUserId(),
                event.getKycRequestId(),
                event.getRejectionReason(),
                event.getFailedChecks(),
                event.getRiskScore(),
                event.getComplianceNotes()
            );
            
            // Log KYC rejection activity
            activityLogService.logKycRejection(
                event.getUserId(),
                event.getKycRequestId(),
                event.getRejectionReason(),
                event.getAttemptNumber(),
                event.getRejectedAt()
            );
            
            // Schedule follow-up if can retry
            if (event.getCanRetry() && event.getRetryAfter() != null) {
                notificationService.scheduleKycRetryReminder(
                    event.getUserId(),
                    event.getEmail(),
                    event.getRetryAfter()
                );
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed KYC rejection for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process KYC rejection for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("KYC rejection processing failed", e);
        }
    }

    private void validateKycRejectionEvent(UserKycRejectedEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for KYC rejection");
        }
        
        if (event.getKycRequestId() == null || event.getKycRequestId().trim().isEmpty()) {
            throw new IllegalArgumentException("KYC request ID is required");
        }
        
        if (event.getRejectionReason() == null || event.getRejectionReason().trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
    }

    private void handleResubmitDocuments(UserKycRejectedEvent event) {
        // Enable document resubmission
        userService.enableKycResubmission(
            event.getUserId(),
            event.getRequiredDocuments()
        );
        
        // Send detailed instructions
        notificationService.sendDocumentResubmissionInstructions(
            event.getUserId(),
            event.getEmail(),
            event.getRequiredDocuments(),
            event.getFailedChecks()
        );
        
        // Track resubmission attempt
        complianceService.trackKycResubmission(
            event.getUserId(),
            event.getKycRequestId(),
            event.getAttemptNumber()
        );
    }

    private void handleContactSupport(UserKycRejectedEvent event) {
        // Create support ticket
        String ticketId = userService.createSupportTicket(
            event.getUserId(),
            "KYC_REJECTION",
            event.getRejectionReason(),
            event.getRejectionDetails()
        );
        
        // Send support contact information
        notificationService.sendSupportContactEmail(
            event.getUserId(),
            event.getEmail(),
            ticketId,
            event.getRejectionReason()
        );
        
        // Escalate to compliance team if high risk
        if (event.getRiskScore() != null && Integer.parseInt(event.getRiskScore()) > 80) {
            complianceService.escalateHighRiskKycRejection(
                event.getUserId(),
                event.getKycRequestId(),
                event.getRiskScore()
            );
        }
    }

    private void handlePermanentRejection(UserKycRejectedEvent event) {
        // Permanently restrict account
        userService.permanentlyRestrictAccount(
            event.getUserId(),
            "KYC_PERMANENTLY_REJECTED"
        );
        
        // Send final notification
        notificationService.sendPermanentKycRejectionEmail(
            event.getUserId(),
            event.getEmail(),
            event.getRejectionReason()
        );
        
        // Report to regulatory authorities if required
        complianceService.reportPermanentKycRejection(
            event.getUserId(),
            event.getKycRequestId(),
            event.getRejectionReason(),
            event.getComplianceNotes()
        );
        
        // Archive KYC attempt history
        userService.archiveKycHistory(
            event.getUserId(),
            event.getKycRequestId()
        );
    }
}