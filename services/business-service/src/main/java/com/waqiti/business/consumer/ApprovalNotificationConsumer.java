package com.waqiti.business.consumer;

import com.waqiti.business.event.ApprovalNotificationEvent;
import com.waqiti.business.service.BusinessNotificationService;
import com.waqiti.business.service.BusinessAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Production-grade Kafka consumer for approval notification events
 * Handles approval workflow notifications and business process approvals
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNotificationConsumer {

    private final BusinessNotificationService notificationService;
    private final BusinessAnalyticsService analyticsService;

    @KafkaListener(topics = "approval-notifications", groupId = "approval-notification-processor")
    public void processApprovalNotification(@Payload ApprovalNotificationEvent event,
                                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                          @Header(KafkaHeaders.OFFSET) long offset,
                                          Acknowledgment acknowledgment) {
        try {
            log.info("Processing approval notification: {} action: {} type: {} amount: {}", 
                    event.getEventId(), event.getAction(), event.getRequestType(), event.getAmount());
            
            // Validate event
            validateApprovalNotificationEvent(event);
            
            // Process based on action type
            switch (event.getAction()) {
                case "APPROVAL_REQUIRED" -> handleApprovalRequired(event);
                case "APPROVED" -> handleRequestApproved(event);
                case "REJECTED" -> handleRequestRejected(event);
                case "ESCALATED" -> handleRequestEscalated(event);
                case "DELEGATED" -> handleRequestDelegated(event);
                default -> {
                    log.warn("Unknown approval action: {} for event: {}", event.getAction(), event.getEventId());
                    // Don't throw exception for unknown actions to avoid DLQ
                }
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed approval notification: {} action: {}", 
                    event.getEventId(), event.getAction());
            
        } catch (Exception e) {
            log.error("Failed to process approval notification: {} error: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Don't acknowledge - let retry mechanism handle it
            throw new RuntimeException("Approval notification processing failed", e);
        }
    }

    private void validateApprovalNotificationEvent(ApprovalNotificationEvent event) {
        if (event.getBusinessId() == null || event.getBusinessId().trim().isEmpty()) {
            throw new IllegalArgumentException("Business ID is required for approval notification");
        }
        
        if (event.getApprovalId() == null || event.getApprovalId().trim().isEmpty()) {
            throw new IllegalArgumentException("Approval ID is required for approval notification");
        }
        
        if (event.getAction() == null || event.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Action is required for approval notification");
        }
        
        if (event.getRequestType() == null || event.getRequestType().trim().isEmpty()) {
            throw new IllegalArgumentException("Request type is required for approval notification");
        }
    }

    private void handleApprovalRequired(ApprovalNotificationEvent event) {
        try {
            log.info("Handling approval required: {} type: {} requester: {} approver: {} amount: {}", 
                    event.getApprovalId(), event.getRequestType(), event.getRequesterName(), 
                    event.getApproverEmail(), event.getAmount());
            
            // Send approval request notification
            notificationService.sendApprovalNotificationEmail(
                event.getBusinessId(),
                event.getApproverEmail(),
                String.format("Approval Required: %s", event.getRequestTitle()),
                generateApprovalRequestMessage(event),
                event.getApprovalId(),
                event.getRequestType(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Send in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getApproverId(),
                "APPROVAL_REQUIRED",
                String.format("Approval Required: %s", event.getRequestType()),
                String.format("Request from %s requires your approval", event.getRequesterName()),
                event.getPriority(),
                event.getApprovalId()
            );
            
            // Send urgent alert if high priority
            if ("HIGH".equals(event.getPriority()) || "URGENT".equals(event.getPriority())) {
                notificationService.sendUrgentApprovalAlert(
                    event.getBusinessId(),
                    event.getApproverId(),
                    event.getApprovalId(),
                    event.getRequestType(),
                    event.getAmount(),
                    event.getUrgencyReason()
                );
            }
            
            // Schedule reminder notifications
            if (event.getDueDate() != null) {
                analyticsService.scheduleApprovalReminders(
                    event.getBusinessId(),
                    event.getApprovalId(),
                    event.getApproverEmail(),
                    event.getDueDate(),
                    event.getPriority()
                );
            }
            
            // Track approval request
            notificationService.updateApprovalTracking(
                event.getBusinessId(),
                event.getApprovalId(),
                "APPROVAL_REQUIRED",
                event.getApproverId()
            );
            
            // Update approval analytics
            analyticsService.updateApprovalAnalytics(
                event.getBusinessId(),
                event.getRequestType(),
                event.getAmount(),
                event.getCurrency(),
                "APPROVAL_REQUIRED",
                event.getApprovalLevel()
            );
            
            log.info("Approval required notification processed successfully: {}", event.getApprovalId());
            
        } catch (Exception e) {
            log.error("Failed to handle approval required for {}: {}", event.getApprovalId(), e.getMessage(), e);
            throw new RuntimeException("Approval required processing failed", e);
        }
    }

    private void handleRequestApproved(ApprovalNotificationEvent event) {
        try {
            log.info("Handling request approved: {} by approver: {} amount: {} comments: {}", 
                    event.getApprovalId(), event.getApproverName(), event.getAmount(), event.getComments());
            
            // Send approval confirmation to requester
            notificationService.sendApprovalNotificationEmail(
                event.getBusinessId(),
                event.getRequesterEmail(),
                String.format("Request Approved: %s", event.getRequestType()),
                generateApprovalConfirmationMessage(event),
                event.getApprovalId(),
                event.getRequestType(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Send in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getRequesterId(),
                "REQUEST_APPROVED",
                "Request Approved",
                String.format("Your %s request has been approved by %s", 
                            event.getRequestType(), event.getApproverName()),
                "LOW",
                event.getApprovalId()
            );
            
            // Cancel pending reminders
            analyticsService.cancelPendingApprovalReminders(
                event.getBusinessId(),
                event.getApprovalId()
            );
            
            // Update approval tracking
            notificationService.updateApprovalTracking(
                event.getBusinessId(),
                event.getApprovalId(),
                "APPROVED",
                event.getApproverId()
            );
            
            // Trigger next workflow step if applicable
            analyticsService.triggerNextWorkflowStep(
                event.getBusinessId(),
                event.getApprovalId(),
                event.getRequestType(),
                "APPROVED"
            );
            
            // Update approval analytics
            analyticsService.updateApprovalAnalytics(
                event.getBusinessId(),
                event.getRequestType(),
                event.getAmount(),
                event.getCurrency(),
                "APPROVED",
                event.getApprovalLevel()
            );
            
            log.info("Request approved notification processed successfully: {}", event.getApprovalId());
            
        } catch (Exception e) {
            log.error("Failed to handle request approved for {}: {}", event.getApprovalId(), e.getMessage(), e);
            throw new RuntimeException("Request approved processing failed", e);
        }
    }

    private void handleRequestRejected(ApprovalNotificationEvent event) {
        try {
            log.info("Handling request rejected: {} by approver: {} reason: {}", 
                    event.getApprovalId(), event.getApproverName(), event.getRejectionReason());
            
            // Send rejection notification to requester
            notificationService.sendApprovalNotificationEmail(
                event.getBusinessId(),
                event.getRequesterEmail(),
                String.format("Request Rejected: %s", event.getRequestType()),
                generateRejectionMessage(event),
                event.getApprovalId(),
                event.getRequestType(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // Send in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getRequesterId(),
                "REQUEST_REJECTED",
                "Request Rejected",
                String.format("Your %s request has been rejected by %s", 
                            event.getRequestType(), event.getApproverName()),
                "MEDIUM",
                event.getApprovalId()
            );
            
            // Cancel pending reminders
            analyticsService.cancelPendingApprovalReminders(
                event.getBusinessId(),
                event.getApprovalId()
            );
            
            // Update approval tracking
            notificationService.updateApprovalTracking(
                event.getBusinessId(),
                event.getApprovalId(),
                "REJECTED",
                event.getApproverId()
            );
            
            // Analyze rejection patterns
            analyticsService.analyzeRejectionPatterns(
                event.getBusinessId(),
                event.getRequestType(),
                event.getRejectionReason(),
                event.getApproverId()
            );
            
            // Update approval analytics
            analyticsService.updateApprovalAnalytics(
                event.getBusinessId(),
                event.getRequestType(),
                event.getAmount(),
                event.getCurrency(),
                "REJECTED",
                event.getApprovalLevel()
            );
            
            log.info("Request rejected notification processed successfully: {}", event.getApprovalId());
            
        } catch (Exception e) {
            log.error("Failed to handle request rejected for {}: {}", event.getApprovalId(), e.getMessage(), e);
            throw new RuntimeException("Request rejected processing failed", e);
        }
    }

    private void handleRequestEscalated(ApprovalNotificationEvent event) {
        try {
            log.info("Handling request escalated: {} to: {} reason: {}", 
                    event.getApprovalId(), event.getEscalatedTo(), event.getUrgencyReason());
            
            // Send escalation notification
            notificationService.sendEscalationNotification(
                event.getBusinessId(),
                event.getEscalatedTo(),
                event.getApprovalId(),
                event.getRequestType(),
                event.getAmount(),
                event.getUrgencyReason()
            );
            
            // Send urgent in-app notification
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getEscalatedTo(),
                "APPROVAL_ESCALATED",
                "Urgent Approval Escalation",
                String.format("Request %s has been escalated to you for urgent approval", 
                            event.getRequestType()),
                "URGENT",
                event.getApprovalId()
            );
            
            // Update approval tracking with escalation
            notificationService.updateApprovalTracking(
                event.getBusinessId(),
                event.getApprovalId(),
                "ESCALATED",
                event.getEscalatedTo()
            );
            
            // Set urgent priority reminders
            analyticsService.setUrgentApprovalReminders(
                event.getBusinessId(),
                event.getApprovalId(),
                event.getEscalatedTo()
            );
            
            // Track escalation analytics
            analyticsService.trackApprovalEscalation(
                event.getBusinessId(),
                event.getApprovalId(),
                event.getRequestType(),
                event.getUrgencyReason()
            );
            
            log.info("Request escalated notification processed successfully: {}", event.getApprovalId());
            
        } catch (Exception e) {
            log.error("Failed to handle request escalated for {}: {}", event.getApprovalId(), e.getMessage(), e);
            throw new RuntimeException("Request escalated processing failed", e);
        }
    }

    private void handleRequestDelegated(ApprovalNotificationEvent event) {
        try {
            log.info("Handling request delegated: {} from: {} to: {} reason: {}", 
                    event.getApprovalId(), event.getApproverName(), event.getDelegatedTo(), event.getComments());
            
            // Send delegation notification
            notificationService.sendDelegationNotification(
                event.getBusinessId(),
                event.getDelegatedTo(),
                event.getApprovalId(),
                event.getRequestType(),
                event.getComments()
            );
            
            // Send in-app notification to delegate
            notificationService.sendInAppNotification(
                event.getBusinessId(),
                event.getDelegatedTo(),
                "APPROVAL_DELEGATED",
                "Approval Delegated",
                String.format("Approval for %s has been delegated to you by %s", 
                            event.getRequestType(), event.getApproverName()),
                event.getPriority(),
                event.getApprovalId()
            );
            
            // Update approval tracking with delegation
            notificationService.updateApprovalTracking(
                event.getBusinessId(),
                event.getApprovalId(),
                "DELEGATED",
                event.getDelegatedTo()
            );
            
            // Transfer reminders to delegate
            analyticsService.transferApprovalReminders(
                event.getBusinessId(),
                event.getApprovalId(),
                event.getApproverId(),
                event.getDelegatedTo()
            );
            
            // Track delegation analytics
            analyticsService.trackApprovalDelegation(
                event.getBusinessId(),
                event.getApprovalId(),
                event.getRequestType(),
                event.getApproverId(),
                event.getDelegatedTo()
            );
            
            log.info("Request delegated notification processed successfully: {}", event.getApprovalId());
            
        } catch (Exception e) {
            log.error("Failed to handle request delegated for {}: {}", event.getApprovalId(), e.getMessage(), e);
            throw new RuntimeException("Request delegated processing failed", e);
        }
    }

    private String generateApprovalRequestMessage(ApprovalNotificationEvent event) {
        return String.format(
            "A %s request from %s requires your approval.\n\n" +
            "Request Details:\n" +
            "- Title: %s\n" +
            "- Amount: %s %s\n" +
            "- Priority: %s\n" +
            "- Due Date: %s\n\n" +
            "Please review and take action as soon as possible.",
            event.getRequestType(),
            event.getRequesterName(),
            event.getRequestTitle(),
            event.getAmount(),
            event.getCurrency(),
            event.getPriority(),
            event.getDueDate()
        );
    }

    private String generateApprovalConfirmationMessage(ApprovalNotificationEvent event) {
        return String.format(
            "Your %s request has been approved by %s.\n\n" +
            "Approval Details:\n" +
            "- Approved Amount: %s %s\n" +
            "- Approver Comments: %s\n\n" +
            "The request will now proceed to the next step in the workflow.",
            event.getRequestType(),
            event.getApproverName(),
            event.getAmount(),
            event.getCurrency(),
            event.getComments() != null ? event.getComments() : "No comments provided"
        );
    }

    private String generateRejectionMessage(ApprovalNotificationEvent event) {
        return String.format(
            "Your %s request has been rejected by %s.\n\n" +
            "Rejection Details:\n" +
            "- Reason: %s\n" +
            "- Amount: %s %s\n\n" +
            "Please review the rejection reason and resubmit if appropriate.",
            event.getRequestType(),
            event.getApproverName(),
            event.getRejectionReason(),
            event.getAmount(),
            event.getCurrency()
        );
    }
}