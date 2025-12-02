package com.waqiti.notification.kafka;

import com.waqiti.notification.event.NotificationEvent;
import com.waqiti.notification.service.NotificationDeliveryService;
import com.waqiti.notification.service.SlackNotificationService;
import com.waqiti.notification.service.WebsocketNotificationService;
import com.waqiti.notification.service.MerchantNotificationService;
import com.waqiti.notification.service.CustomerNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for comprehensive notification events
 * Handles: customer-notifications, merchant-notifications, slack-notifications, websocket-notifications,
 * user-notifications, approval-notifications, merchant-dispute-notifications, merchant-critical-notifications,
 * security-team-notifications, pagerduty-events, lock-release-notifications
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComprehensiveNotificationConsumer {

    private final NotificationDeliveryService deliveryService;
    private final SlackNotificationService slackService;
    private final WebsocketNotificationService websocketService;
    private final MerchantNotificationService merchantService;
    private final CustomerNotificationService customerService;

    @KafkaListener(topics = {"customer-notifications", "merchant-notifications", "slack-notifications", 
                             "websocket-notifications", "user-notifications", "approval-notifications",
                             "merchant-dispute-notifications", "merchant-critical-notifications",
                             "security-team-notifications", "pagerduty-events", "lock-release-notifications"}, 
                   groupId = "comprehensive-notification-processor")
    @Transactional
    public void processNotificationEvent(@Payload NotificationEvent event,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment) {
        try {
            log.info("Processing notification event: {} - Type: {} - Recipient: {} - Channel: {}", 
                    event.getNotificationId(), event.getNotificationType(), 
                    event.getRecipientId(), event.getChannel());
            
            // Process based on topic
            switch (topic) {
                case "customer-notifications" -> handleCustomerNotification(event);
                case "merchant-notifications" -> handleMerchantNotification(event);
                case "slack-notifications" -> handleSlackNotification(event);
                case "websocket-notifications" -> handleWebsocketNotification(event);
                case "user-notifications" -> handleUserNotification(event);
                case "approval-notifications" -> handleApprovalNotification(event);
                case "merchant-dispute-notifications" -> handleMerchantDisputeNotification(event);
                case "merchant-critical-notifications" -> handleMerchantCriticalNotification(event);
                case "security-team-notifications" -> handleSecurityTeamNotification(event);
                case "pagerduty-events" -> handlePagerDutyEvent(event);
                case "lock-release-notifications" -> handleLockReleaseNotification(event);
            }
            
            // Update notification metrics
            updateNotificationMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed notification event: {}", event.getNotificationId());
            
        } catch (Exception e) {
            log.error("Failed to process notification event {}: {}", 
                    event.getNotificationId(), e.getMessage(), e);
            throw new RuntimeException("Notification processing failed", e);
        }
    }

    private void handleCustomerNotification(NotificationEvent event) {
        String customerId = event.getRecipientId();
        String notificationType = event.getNotificationType();
        
        switch (notificationType) {
            case "PAYMENT_CONFIRMATION" -> {
                customerService.sendPaymentConfirmation(
                    customerId,
                    event.getPaymentId(),
                    event.getAmount(),
                    event.getMerchantName(),
                    event.getTransactionDetails()
                );
            }
            case "PAYMENT_FAILED" -> {
                customerService.sendPaymentFailure(
                    customerId,
                    event.getPaymentId(),
                    event.getFailureReason(),
                    event.getRecommendedActions()
                );
            }
            case "SECURITY_ALERT" -> {
                customerService.sendSecurityAlert(
                    customerId,
                    event.getSecurityEventType(),
                    event.getAlertMessage(),
                    event.getSecurityRecommendations()
                );
            }
            case "ACCOUNT_UPDATE" -> {
                customerService.sendAccountUpdate(
                    customerId,
                    event.getUpdateType(),
                    event.getUpdateDetails(),
                    event.getEffectiveDate()
                );
            }
            case "PROMOTION_OFFER" -> {
                customerService.sendPromotionOffer(
                    customerId,
                    event.getPromotionId(),
                    event.getOfferDetails(),
                    event.getExpiryDate()
                );
            }
            case "COMPLIANCE_NOTIFICATION" -> {
                customerService.sendComplianceNotification(
                    customerId,
                    event.getComplianceType(),
                    event.getRequiredActions(),
                    event.getDeadline()
                );
            }
        }
        
        // Track customer engagement
        customerService.trackCustomerEngagement(
            customerId,
            notificationType,
            event.getNotificationId()
        );
    }

    private void handleMerchantNotification(NotificationEvent event) {
        String merchantId = event.getRecipientId();
        String notificationType = event.getNotificationType();
        
        switch (notificationType) {
            case "SETTLEMENT_COMPLETED" -> {
                merchantService.sendSettlementNotification(
                    merchantId,
                    event.getSettlementId(),
                    event.getSettlementAmount(),
                    event.getSettlementDate(),
                    event.getSettlementDetails()
                );
            }
            case "CHARGEBACK_RECEIVED" -> {
                merchantService.sendChargebackNotification(
                    merchantId,
                    event.getChargebackId(),
                    event.getTransactionId(),
                    event.getChargebackReason(),
                    event.getResponseDeadline()
                );
            }
            case "RISK_ALERT" -> {
                merchantService.sendRiskAlert(
                    merchantId,
                    event.getRiskType(),
                    event.getRiskScore(),
                    event.getRiskDetails(),
                    event.getMitigationActions()
                );
            }
            case "INTEGRATION_UPDATE" -> {
                merchantService.sendIntegrationUpdate(
                    merchantId,
                    event.getIntegrationType(),
                    event.getUpdateDetails(),
                    event.getMaintenanceWindow()
                );
            }
            case "VOLUME_THRESHOLD" -> {
                merchantService.sendVolumeThresholdAlert(
                    merchantId,
                    event.getThresholdType(),
                    event.getCurrentVolume(),
                    event.getThresholdLimit(),
                    event.getTimeframe()
                );
            }
            case "COMPLIANCE_REQUIREMENT" -> {
                merchantService.sendComplianceRequirement(
                    merchantId,
                    event.getComplianceType(),
                    event.getRequirementDetails(),
                    event.getComplianceDeadline()
                );
            }
        }
        
        // Update merchant notification preferences
        merchantService.updateNotificationPreferences(
            merchantId,
            event.getNotificationChannel(),
            event.getDeliveryStatus()
        );
    }

    private void handleSlackNotification(NotificationEvent event) {
        String channel = event.getSlackChannel();
        String messageType = event.getSlackMessageType();
        
        switch (messageType) {
            case "SYSTEM_ALERT" -> {
                slackService.sendSystemAlert(
                    channel,
                    event.getAlertTitle(),
                    event.getAlertMessage(),
                    event.getSeverity(),
                    event.getAlertDetails()
                );
            }
            case "INCIDENT_NOTIFICATION" -> {
                slackService.sendIncidentNotification(
                    channel,
                    event.getIncidentId(),
                    event.getIncidentTitle(),
                    event.getIncidentSeverity(),
                    event.getIncidentStatus(),
                    event.getResponseTeam()
                );
            }
            case "DEPLOYMENT_STATUS" -> {
                slackService.sendDeploymentStatus(
                    channel,
                    event.getDeploymentId(),
                    event.getServiceName(),
                    event.getDeploymentStatus(),
                    event.getDeploymentDetails()
                );
            }
            case "METRIC_THRESHOLD" -> {
                slackService.sendMetricThreshold(
                    channel,
                    event.getMetricName(),
                    event.getCurrentValue(),
                    event.getThresholdValue(),
                    event.getMetricChart()
                );
            }
            case "COMPLIANCE_ALERT" -> {
                slackService.sendComplianceAlert(
                    channel,
                    event.getComplianceType(),
                    event.getViolationDetails(),
                    event.getRequiredActions()
                );
            }
            case "TEAM_UPDATE" -> {
                slackService.sendTeamUpdate(
                    channel,
                    event.getUpdateTitle(),
                    event.getUpdateMessage(),
                    event.getUpdateAttachments()
                );
            }
        }
        
        // Track Slack engagement
        slackService.trackSlackEngagement(
            channel,
            event.getNotificationId(),
            messageType
        );
    }

    private void handleWebsocketNotification(NotificationEvent event) {
        String userId = event.getRecipientId();
        String messageType = event.getWebsocketMessageType();
        
        // Check if user is connected
        if (!websocketService.isUserConnected(userId)) {
            log.info("User {} not connected, storing notification for later delivery", userId);
            websocketService.storeOfflineNotification(
                userId,
                event.getNotificationId(),
                event.getMessage()
            );
            return;
        }
        
        switch (messageType) {
            case "REAL_TIME_UPDATE" -> {
                websocketService.sendRealTimeUpdate(
                    userId,
                    event.getUpdateType(),
                    event.getUpdateData(),
                    event.getTimestamp()
                );
            }
            case "TRANSACTION_STATUS" -> {
                websocketService.sendTransactionStatus(
                    userId,
                    event.getTransactionId(),
                    event.getTransactionStatus(),
                    event.getStatusDetails()
                );
            }
            case "BALANCE_UPDATE" -> {
                websocketService.sendBalanceUpdate(
                    userId,
                    event.getAccountId(),
                    event.getNewBalance(),
                    event.getBalanceChange()
                );
            }
            case "SYSTEM_NOTIFICATION" -> {
                websocketService.sendSystemNotification(
                    userId,
                    event.getNotificationTitle(),
                    event.getNotificationMessage(),
                    event.getNotificationPriority()
                );
            }
            case "CHAT_MESSAGE" -> {
                websocketService.sendChatMessage(
                    userId,
                    event.getChatId(),
                    event.getSenderId(),
                    event.getChatMessage(),
                    event.getMessageType()
                );
            }
        }
        
        // Track websocket delivery
        websocketService.trackWebsocketDelivery(
            userId,
            event.getNotificationId(),
            messageType,
            LocalDateTime.now()
        );
    }

    private void handleUserNotification(NotificationEvent event) {
        String userId = event.getRecipientId();
        String notificationType = event.getNotificationType();
        
        // Determine delivery channels based on user preferences
        List<String> channels = deliveryService.determineDeliveryChannels(
            userId,
            notificationType,
            event.getPriority()
        );
        
        // Send through multiple channels
        CompletableFuture.allOf(
            channels.stream()
                .map(channel -> CompletableFuture.runAsync(() -> 
                    deliverNotificationViaChannel(userId, event, channel)
                ))
                .toArray(CompletableFuture[]::new)
        ).join();
        
        // Track multi-channel delivery
        deliveryService.trackMultiChannelDelivery(
            event.getNotificationId(),
            userId,
            channels,
            event.getDeliveryStatus()
        );
    }

    private void handleApprovalNotification(NotificationEvent event) {
        String approverId = event.getApproverId();
        String approvalType = event.getApprovalType();
        
        switch (approvalType) {
            case "TRANSACTION_APPROVAL" -> {
                deliveryService.sendTransactionApprovalRequest(
                    approverId,
                    event.getTransactionId(),
                    event.getTransactionAmount(),
                    event.getRequestingUser(),
                    event.getApprovalDeadline()
                );
            }
            case "LIMIT_INCREASE_APPROVAL" -> {
                deliveryService.sendLimitIncreaseApproval(
                    approverId,
                    event.getUserId(),
                    event.getCurrentLimit(),
                    event.getRequestedLimit(),
                    event.getJustification()
                );
            }
            case "COMPLIANCE_APPROVAL" -> {
                deliveryService.sendComplianceApproval(
                    approverId,
                    event.getComplianceType(),
                    event.getComplianceDetails(),
                    event.getRiskAssessment()
                );
            }
            case "POLICY_EXCEPTION_APPROVAL" -> {
                deliveryService.sendPolicyExceptionApproval(
                    approverId,
                    event.getPolicyName(),
                    event.getExceptionReason(),
                    event.getExceptionDuration()
                );
            }
        }
        
        // Create approval workflow
        deliveryService.createApprovalWorkflow(
            event.getNotificationId(),
            approverId,
            approvalType,
            event.getWorkflowConfig()
        );
    }

    private void handleMerchantDisputeNotification(NotificationEvent event) {
        String merchantId = event.getMerchantId();
        String disputeType = event.getDisputeType();
        
        switch (disputeType) {
            case "CHARGEBACK_DISPUTE" -> {
                merchantService.sendChargebackDispute(
                    merchantId,
                    event.getDisputeId(),
                    event.getTransactionId(),
                    event.getDisputeReason(),
                    event.getResponseDeadline(),
                    event.getEvidenceRequirements()
                );
            }
            case "RETRIEVAL_REQUEST" -> {
                merchantService.sendRetrievalRequest(
                    merchantId,
                    event.getRetrievalId(),
                    event.getTransactionId(),
                    event.getRequestedDocuments(),
                    event.getResponseDeadline()
                );
            }
            case "PRE_ARBITRATION" -> {
                merchantService.sendPreArbitrationNotice(
                    merchantId,
                    event.getCaseId(),
                    event.getArbitrationReason(),
                    event.getArbitrationAmount(),
                    event.getResponseOptions()
                );
            }
        }
        
        // Track dispute communication
        merchantService.trackDisputeCommunication(
            merchantId,
            event.getDisputeId(),
            disputeType,
            event.getNotificationId()
        );
    }

    private void handleMerchantCriticalNotification(NotificationEvent event) {
        String merchantId = event.getMerchantId();
        String criticalEventType = event.getCriticalEventType();
        
        // Send via multiple urgent channels
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> 
                merchantService.sendUrgentEmail(merchantId, event)
            ),
            CompletableFuture.runAsync(() -> 
                merchantService.sendUrgentSms(merchantId, event)
            ),
            CompletableFuture.runAsync(() -> 
                merchantService.sendUrgentPush(merchantId, event)
            )
        ).join();
        
        // Log critical notification
        merchantService.logCriticalNotification(
            merchantId,
            criticalEventType,
            event.getNotificationId(),
            LocalDateTime.now()
        );
    }

    private void handleSecurityTeamNotification(NotificationEvent event) {
        String teamId = event.getSecurityTeamId();
        String securityEventType = event.getSecurityEventType();
        
        switch (securityEventType) {
            case "SECURITY_BREACH" -> {
                deliveryService.sendSecurityBreach(
                    teamId,
                    event.getBreachType(),
                    event.getSeverity(),
                    event.getAffectedSystems(),
                    event.getImmediateActions()
                );
            }
            case "THREAT_DETECTED" -> {
                deliveryService.sendThreatDetection(
                    teamId,
                    event.getThreatType(),
                    event.getThreatSource(),
                    event.getRiskLevel(),
                    event.getMitigationSteps()
                );
            }
            case "VULNERABILITY_ALERT" -> {
                deliveryService.sendVulnerabilityAlert(
                    teamId,
                    event.getVulnerabilityId(),
                    event.getCvssScore(),
                    event.getAffectedComponents(),
                    event.getPatchAvailability()
                );
            }
            case "COMPLIANCE_VIOLATION" -> {
                deliveryService.sendComplianceViolation(
                    teamId,
                    event.getViolationType(),
                    event.getComplianceFramework(),
                    event.getViolationDetails(),
                    event.getRemediationSteps()
                );
            }
        }
        
        // Escalate if needed
        if (event.isEscalationRequired()) {
            deliveryService.escalateSecurityNotification(
                event.getNotificationId(),
                event.getEscalationLevel(),
                event.getEscalationTargets()
            );
        }
    }

    private void handlePagerDutyEvent(NotificationEvent event) {
        String incidentType = event.getIncidentType();
        String severity = event.getSeverity();
        
        // Create PagerDuty incident
        String incidentId = deliveryService.createPagerDutyIncident(
            event.getServiceKey(),
            incidentType,
            event.getIncidentTitle(),
            event.getIncidentDetails(),
            severity
        );
        
        // Add incident context
        deliveryService.addPagerDutyContext(
            incidentId,
            event.getContextData(),
            event.getRunbookUrl(),
            event.getDashboardUrl()
        );
        
        // Assign to team
        if (event.getAssignedTeam() != null) {
            deliveryService.assignPagerDutyIncident(
                incidentId,
                event.getAssignedTeam(),
                event.getEscalationPolicy()
            );
        }
        
        // Track PagerDuty response
        deliveryService.trackPagerDutyResponse(
            incidentId,
            event.getNotificationId(),
            LocalDateTime.now()
        );
    }

    private void handleLockReleaseNotification(NotificationEvent event) {
        String userId = event.getUserId();
        String lockType = event.getLockType();
        
        switch (lockType) {
            case "ACCOUNT_LOCK" -> {
                deliveryService.sendAccountUnlockNotification(
                    userId,
                    event.getLockId(),
                    event.getLockReason(),
                    event.getUnlockReason(),
                    event.getUnlockTime()
                );
            }
            case "TRANSACTION_LOCK" -> {
                deliveryService.sendTransactionUnlockNotification(
                    userId,
                    event.getTransactionId(),
                    event.getLockDuration(),
                    event.getUnlockConditions()
                );
            }
            case "FEATURE_LOCK" -> {
                deliveryService.sendFeatureUnlockNotification(
                    userId,
                    event.getFeatureName(),
                    event.getLockReason(),
                    event.getRestoreActions()
                );
            }
            case "SECURITY_LOCK" -> {
                deliveryService.sendSecurityUnlockNotification(
                    userId,
                    event.getSecurityLockType(),
                    event.getVerificationSteps(),
                    event.getSecurityRecommendations()
                );
            }
        }
        
        // Log lock release
        deliveryService.logLockRelease(
            userId,
            lockType,
            event.getLockId(),
            event.getUnlockReason(),
            LocalDateTime.now()
        );
    }

    private void deliverNotificationViaChannel(String userId, NotificationEvent event, String channel) {
        try {
            switch (channel) {
                case "EMAIL" -> deliveryService.sendEmail(userId, event);
                case "SMS" -> deliveryService.sendSms(userId, event);
                case "PUSH" -> deliveryService.sendPush(userId, event);
                case "IN_APP" -> deliveryService.sendInApp(userId, event);
                case "WEBSOCKET" -> websocketService.sendWebsocketMessage(userId, event);
                default -> log.warn("Unknown delivery channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("Failed to deliver notification via {}: {}", channel, e.getMessage());
            deliveryService.logDeliveryFailure(
                event.getNotificationId(),
                userId,
                channel,
                e.getMessage()
            );
        }
    }

    private void updateNotificationMetrics(NotificationEvent event) {
        // Update delivery metrics
        deliveryService.updateDeliveryMetrics(
            event.getNotificationType(),
            event.getChannel(),
            event.getDeliveryTime(),
            event.isDelivered()
        );
        
        // Update engagement metrics
        deliveryService.updateEngagementMetrics(
            event.getRecipientType(),
            event.getNotificationType(),
            event.getEngagementRate()
        );
        
        // Update channel performance
        deliveryService.updateChannelPerformance(
            event.getChannel(),
            event.getDeliveryStatus(),
            event.getResponseTime()
        );
    }
}