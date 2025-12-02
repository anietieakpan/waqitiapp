package com.waqiti.dlq.kafka;

import com.waqiti.dlq.event.DlqEvent;
import com.waqiti.dlq.service.DlqProcessingService;
import com.waqiti.dlq.service.ErrorAnalysisService;
import com.waqiti.dlq.service.RecoveryService;
import com.waqiti.dlq.service.AlertingService;
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

/**
 * Production-grade Kafka consumer for specialized DLQ events
 * Handles: dlq-events, payment-chargebacks-dlq, payment-disputes-dlq, kyc-rejected-dlq,
 * kyc-completed-dlq, security-alerts-dlq, sms-retry-queue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpecializedDlqConsumer {

    private final DlqProcessingService dlqProcessingService;
    private final ErrorAnalysisService errorAnalysisService;
    private final RecoveryService recoveryService;
    private final AlertingService alertingService;

    @KafkaListener(topics = {"dlq-events", "payment-chargebacks-dlq", "payment-disputes-dlq", 
                             "kyc-rejected-dlq", "kyc-completed-dlq", "security-alerts-dlq", 
                             "sms-retry-queue"}, 
                   groupId = "specialized-dlq-processor")
    @Transactional
    public void processDlqEvent(@Payload DlqEvent event,
                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                              @Header(KafkaHeaders.OFFSET) long offset,
                              Acknowledgment acknowledgment) {
        try {
            log.info("Processing DLQ event: {} - Type: {} - Source Topic: {} - Retry Count: {}", 
                    event.getEventId(), event.getDlqType(), event.getSourceTopic(), event.getRetryCount());
            
            // Process based on topic
            switch (topic) {
                case "dlq-events" -> handleGenericDlq(event);
                case "payment-chargebacks-dlq" -> handleChargebackDlq(event);
                case "payment-disputes-dlq" -> handleDisputeDlq(event);
                case "kyc-rejected-dlq" -> handleKycRejectedDlq(event);
                case "kyc-completed-dlq" -> handleKycCompletedDlq(event);
                case "security-alerts-dlq" -> handleSecurityAlertsDlq(event);
                case "sms-retry-queue" -> handleSmsRetryQueue(event);
            }
            
            // Update DLQ metrics
            updateDlqMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed DLQ event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process DLQ event {}: {}", 
                    event.getEventId(), e.getMessage(), e);
            
            // Critical DLQ failure - log to separate monitoring system
            alertingService.sendCriticalDlqAlert(event, e);
            
            // Still acknowledge to prevent infinite loops
            acknowledgment.acknowledge();
        }
    }

    private void handleGenericDlq(DlqEvent event) {
        String dlqReason = event.getDlqReason();
        
        // Analyze DLQ event
        Map<String, Object> analysis = errorAnalysisService.analyzeDlqEvent(
            event.getSourceTopic(),
            event.getOriginalPayload(),
            dlqReason,
            event.getErrorDetails()
        );
        
        // Determine recovery strategy
        String recoveryStrategy = recoveryService.determineRecoveryStrategy(
            event.getSourceTopic(),
            dlqReason,
            event.getRetryCount(),
            analysis
        );
        
        switch (recoveryStrategy) {
            case "RETRY" -> {
                // Schedule retry with backoff
                if (event.getRetryCount() < event.getMaxRetries()) {
                    recoveryService.scheduleRetry(
                        event.getEventId(),
                        event.getSourceTopic(),
                        event.getOriginalPayload(),
                        event.getRetryCount() + 1,
                        calculateRetryDelay(event.getRetryCount())
                    );
                } else {
                    // Max retries exceeded - send to manual review
                    dlqProcessingService.sendToManualReview(
                        event.getEventId(),
                        event.getSourceTopic(),
                        event.getOriginalPayload(),
                        "MAX_RETRIES_EXCEEDED"
                    );
                }
            }
            case "MANUAL_INTERVENTION" -> {
                // Requires manual intervention
                dlqProcessingService.createManualInterventionCase(
                    event.getEventId(),
                    event.getSourceTopic(),
                    event.getOriginalPayload(),
                    dlqReason,
                    analysis
                );
            }
            case "DROP" -> {
                // Drop the event (poison message)
                dlqProcessingService.dropPoisonMessage(
                    event.getEventId(),
                    event.getSourceTopic(),
                    dlqReason,
                    "POISON_MESSAGE_DETECTED"
                );
            }
            case "TRANSFORM_AND_RETRY" -> {
                // Transform payload and retry
                Map<String, Object> transformedPayload = recoveryService.transformPayload(
                    event.getOriginalPayload(),
                    event.getTransformationRules()
                );
                
                recoveryService.retryWithTransformedPayload(
                    event.getEventId(),
                    event.getSourceTopic(),
                    transformedPayload
                );
            }
        }
        
        // Store DLQ analytics
        dlqProcessingService.storeDlqAnalytics(
            event.getSourceTopic(),
            dlqReason,
            recoveryStrategy,
            analysis,
            LocalDateTime.now()
        );
    }

    private void handleChargebackDlq(DlqEvent event) {
        // Handle failed chargeback processing
        String chargebackId = event.getChargebackId();
        String failureReason = event.getDlqReason();
        
        log.error("Chargeback processing failed: {} - Reason: {}", chargebackId, failureReason);
        
        switch (failureReason) {
            case "MERCHANT_NOT_FOUND" -> {
                // Merchant lookup failed
                dlqProcessingService.handleMerchantNotFound(
                    chargebackId,
                    event.getMerchantId(),
                    event.getOriginalPayload()
                );
            }
            case "INVALID_TRANSACTION_DATA" -> {
                // Transaction data validation failed
                dlqProcessingService.handleInvalidTransactionData(
                    chargebackId,
                    event.getTransactionId(),
                    event.getValidationErrors()
                );
            }
            case "PROCESSING_TIMEOUT" -> {
                // Chargeback processing timed out
                if (event.getRetryCount() < 3) {
                    recoveryService.scheduleChargebackRetry(
                        chargebackId,
                        event.getOriginalPayload(),
                        event.getRetryCount() + 1,
                        300000 // 5 minute delay
                    );
                } else {
                    dlqProcessingService.escalateChargebackFailure(
                        chargebackId,
                        "PROCESSING_TIMEOUT_MAX_RETRIES",
                        event.getOriginalPayload()
                    );
                }
            }
            case "EXTERNAL_API_FAILURE" -> {
                // External payment processor API failure
                dlqProcessingService.handleExternalApiFailure(
                    chargebackId,
                    event.getExternalSystem(),
                    event.getApiErrorDetails()
                );
            }
        }
        
        // Create chargeback failure case
        dlqProcessingService.createChargebackFailureCase(
            chargebackId,
            failureReason,
            event.getOriginalPayload(),
            event.getRetryCount()
        );
    }

    private void handleDisputeDlq(DlqEvent event) {
        // Handle failed dispute processing
        String disputeId = event.getDisputeId();
        String failureReason = event.getDlqReason();
        
        log.error("Dispute processing failed: {} - Reason: {}", disputeId, failureReason);
        
        switch (failureReason) {
            case "EVIDENCE_VALIDATION_FAILED" -> {
                // Evidence validation failed
                dlqProcessingService.handleEvidenceValidationFailure(
                    disputeId,
                    event.getEvidenceErrors(),
                    event.getOriginalPayload()
                );
            }
            case "DEADLINE_EXCEEDED" -> {
                // Response deadline exceeded
                dlqProcessingService.handleDisputeDeadlineExceeded(
                    disputeId,
                    event.getDeadline(),
                    event.getCurrentTime()
                );
            }
            case "DOCUMENT_PROCESSING_ERROR" -> {
                // Document processing failed
                dlqProcessingService.handleDocumentProcessingError(
                    disputeId,
                    event.getDocumentErrors(),
                    event.getOriginalPayload()
                );
            }
            case "NETWORK_CONNECTIVITY_ISSUE" -> {
                // Network issue during processing
                if (event.getRetryCount() < 5) {
                    recoveryService.scheduleDisputeRetry(
                        disputeId,
                        event.getOriginalPayload(),
                        event.getRetryCount() + 1,
                        60000 // 1 minute delay
                    );
                } else {
                    dlqProcessingService.escalateDisputeNetworkFailure(
                        disputeId,
                        event.getNetworkErrorDetails()
                    );
                }
            }
        }
        
        // Update dispute status
        dlqProcessingService.updateDisputeStatus(
            disputeId,
            "PROCESSING_FAILED",
            failureReason,
            LocalDateTime.now()
        );
    }

    private void handleKycRejectedDlq(DlqEvent event) {
        // Handle failed KYC rejection processing
        String kycId = event.getKycId();
        String userId = event.getUserId();
        String failureReason = event.getDlqReason();
        
        log.error("KYC rejection processing failed: {} - User: {} - Reason: {}", 
                kycId, userId, failureReason);
        
        switch (failureReason) {
            case "USER_NOTIFICATION_FAILED" -> {
                // Failed to notify user of rejection
                if (event.getRetryCount() < 3) {
                    recoveryService.retryUserNotification(
                        userId,
                        kycId,
                        event.getRejectionReason(),
                        event.getRetryCount() + 1
                    );
                } else {
                    dlqProcessingService.createManualNotificationTask(
                        userId,
                        kycId,
                        event.getRejectionReason(),
                        "KYC_REJECTION_NOTIFICATION_FAILED"
                    );
                }
            }
            case "ACCOUNT_STATUS_UPDATE_FAILED" -> {
                // Failed to update account status
                dlqProcessingService.scheduleAccountStatusRetry(
                    userId,
                    "KYC_REJECTED",
                    event.getRejectionReason()
                );
            }
            case "COMPLIANCE_REPORTING_FAILED" -> {
                // Failed to report to compliance system
                dlqProcessingService.escalateComplianceReportingFailure(
                    kycId,
                    userId,
                    event.getComplianceDetails()
                );
            }
            case "DOCUMENT_ARCHIVAL_FAILED" -> {
                // Failed to archive KYC documents
                dlqProcessingService.scheduleDocumentArchivalRetry(
                    kycId,
                    event.getDocumentIds(),
                    event.getArchivalLocation()
                );
            }
        }
        
        // Create KYC failure investigation
        dlqProcessingService.createKycFailureInvestigation(
            kycId,
            userId,
            failureReason,
            event.getOriginalPayload()
        );
    }

    private void handleKycCompletedDlq(DlqEvent event) {
        // Handle failed KYC completion processing
        String kycId = event.getKycId();
        String userId = event.getUserId();
        String failureReason = event.getDlqReason();
        
        log.error("KYC completion processing failed: {} - User: {} - Reason: {}", 
                kycId, userId, failureReason);
        
        switch (failureReason) {
            case "ACCOUNT_ACTIVATION_FAILED" -> {
                // Failed to activate account
                recoveryService.retryAccountActivation(
                    userId,
                    kycId,
                    event.getKycLevel(),
                    event.getActivationDetails()
                );
            }
            case "LIMIT_ASSIGNMENT_FAILED" -> {
                // Failed to assign account limits
                recoveryService.retryLimitAssignment(
                    userId,
                    event.getKycLevel(),
                    event.getAssignedLimits()
                );
            }
            case "WELCOME_PACKAGE_FAILED" -> {
                // Failed to send welcome package
                recoveryService.scheduleWelcomePackageRetry(
                    userId,
                    kycId,
                    event.getWelcomePackageConfig()
                );
            }
            case "CREDIT_SCORING_FAILED" -> {
                // Failed to initiate credit scoring
                dlqProcessingService.escalateCreditScoringFailure(
                    userId,
                    kycId,
                    event.getCreditScoringDetails()
                );
            }
        }
        
        // Ensure KYC completion is properly recorded
        dlqProcessingService.ensureKycCompletionRecording(
            kycId,
            userId,
            event.getKycLevel(),
            event.getCompletionTimestamp()
        );
    }

    private void handleSecurityAlertsDlq(DlqEvent event) {
        // Handle failed security alert processing
        String alertId = event.getAlertId();
        String alertType = event.getSecurityAlertType();
        String failureReason = event.getDlqReason();
        
        log.error("Security alert processing failed: {} - Type: {} - Reason: {}", 
                alertId, alertType, failureReason);
        
        // Security alerts are critical - always escalate failures
        switch (failureReason) {
            case "SOC_NOTIFICATION_FAILED" -> {
                // Failed to notify Security Operations Center
                alertingService.escalateSecurityNotificationFailure(
                    alertId,
                    alertType,
                    event.getSocTeam(),
                    event.getAlertDetails()
                );
                
                // Try alternative notification channels
                alertingService.sendAlternativeSecurityNotification(
                    alertId,
                    alertType,
                    event.getAlertDetails()
                );
            }
            case "INCIDENT_CREATION_FAILED" -> {
                // Failed to create security incident
                alertingService.manuallyCreateSecurityIncident(
                    alertId,
                    alertType,
                    event.getIncidentDetails()
                );
            }
            case "THREAT_RESPONSE_FAILED" -> {
                // Failed to execute threat response
                alertingService.escalateThreatResponseFailure(
                    alertId,
                    alertType,
                    event.getThreatResponseActions(),
                    event.getFailureDetails()
                );
            }
            case "COMPLIANCE_NOTIFICATION_FAILED" -> {
                // Failed to notify compliance team
                alertingService.sendEmergencyComplianceAlert(
                    alertId,
                    alertType,
                    event.getComplianceDetails()
                );
            }
        }
        
        // All security alert failures require immediate attention
        alertingService.createCriticalSecurityFailureCase(
            alertId,
            alertType,
            failureReason,
            event.getOriginalPayload(),
            LocalDateTime.now()
        );
    }

    private void handleSmsRetryQueue(DlqEvent event) {
        // Handle SMS delivery retry queue
        String smsId = event.getSmsId();
        String phoneNumber = event.getPhoneNumber();
        String failureReason = event.getDlqReason();
        
        log.info("Processing SMS retry: {} - Phone: {} - Attempt: {}", 
                smsId, maskPhoneNumber(phoneNumber), event.getRetryCount());
        
        switch (failureReason) {
            case "NETWORK_TIMEOUT" -> {
                // Network timeout - retry with different provider
                if (event.getRetryCount() < 5) {
                    recoveryService.retrySmsWithBackupProvider(
                        smsId,
                        phoneNumber,
                        event.getSmsContent(),
                        event.getBackupProvider(),
                        event.getRetryCount() + 1
                    );
                } else {
                    dlqProcessingService.markSmsAsPermanentFailure(
                        smsId,
                        phoneNumber,
                        "NETWORK_TIMEOUT_MAX_RETRIES"
                    );
                }
            }
            case "INVALID_PHONE_NUMBER" -> {
                // Invalid phone number - validate and correct if possible
                String correctedNumber = recoveryService.validateAndCorrectPhoneNumber(
                    phoneNumber,
                    event.getCountryCode()
                );
                
                if (correctedNumber != null) {
                    recoveryService.retrySmsWithCorrectedNumber(
                        smsId,
                        correctedNumber,
                        event.getSmsContent()
                    );
                } else {
                    dlqProcessingService.markSmsAsInvalidNumber(
                        smsId,
                        phoneNumber,
                        "INVALID_PHONE_NUMBER_UNCORRECTABLE"
                    );
                }
            }
            case "CARRIER_REJECTION" -> {
                // Carrier rejected message - try alternative content
                if (event.getRetryCount() < 2) {
                    String alternativeContent = recoveryService.generateAlternativeSmsContent(
                        event.getSmsContent(),
                        event.getContentRules()
                    );
                    
                    recoveryService.retrySmsWithAlternativeContent(
                        smsId,
                        phoneNumber,
                        alternativeContent,
                        event.getRetryCount() + 1
                    );
                } else {
                    dlqProcessingService.escalateCarrierRejection(
                        smsId,
                        phoneNumber,
                        event.getCarrierCode(),
                        event.getRejectionReason()
                    );
                }
            }
            case "RATE_LIMIT_EXCEEDED" -> {
                // Rate limit exceeded - schedule for later delivery
                recoveryService.scheduleDelayedSmsDelivery(
                    smsId,
                    phoneNumber,
                    event.getSmsContent(),
                    event.getRateLimitResetTime()
                );
            }
        }
        
        // Update SMS delivery metrics
        dlqProcessingService.updateSmsDeliveryMetrics(
            event.getSmsProvider(),
            failureReason,
            event.getRetryCount(),
            event.getDeliveryAttemptTime()
        );
    }

    private long calculateRetryDelay(int retryCount) {
        // Exponential backoff: 2^retryCount * 1000ms, max 5 minutes
        long delay = (long) Math.pow(2, retryCount) * 1000;
        return Math.min(delay, 300000); // Max 5 minutes
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private void updateDlqMetrics(DlqEvent event) {
        // Update DLQ processing metrics
        dlqProcessingService.updateDlqMetrics(
            event.getSourceTopic(),
            event.getDlqReason(),
            event.getRetryCount(),
            event.getProcessingTime()
        );
        
        // Update error analysis metrics
        errorAnalysisService.updateErrorAnalysisMetrics(
            event.getSourceTopic(),
            event.getDlqReason(),
            event.getErrorCategory(),
            LocalDateTime.now()
        );
        
        // Update recovery success metrics
        recoveryService.updateRecoveryMetrics(
            event.getSourceTopic(),
            event.getRecoveryStrategy(),
            event.isRecoverySuccessful()
        );
    }
}