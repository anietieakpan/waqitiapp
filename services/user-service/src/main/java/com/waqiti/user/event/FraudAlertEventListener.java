package com.waqiti.user.event;

import com.waqiti.user.domain.User;
import com.waqiti.user.domain.UserStatus;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.service.NotificationService;
import com.waqiti.user.service.UserActivityLogService;
import com.waqiti.user.dto.FraudAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready fraud alert event listener for automatic user suspension
 * 
 * Features:
 * - Automatic user suspension based on fraud severity
 * - Graduated response based on risk levels
 * - Account freezing for critical cases
 * - Transaction velocity monitoring
 * - Fraud history tracking
 * - Automated notification to users and security team
 * - Audit trail of all actions
 * - Idempotent processing to prevent duplicate actions
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudAlertEventListener {
    
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UserActivityLogService activityLogService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Track processed events to ensure idempotency
    private final Map<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();
    
    // Configuration constants
    private static final int CRITICAL_SUSPENSION_DAYS = 30;
    private static final int HIGH_SUSPENSION_DAYS = 7;
    private static final int MEDIUM_SUSPENSION_DAYS = 3;
    private static final BigDecimal HIGH_RISK_AMOUNT_THRESHOLD = new BigDecimal("5000");
    private static final double AUTO_SUSPEND_RISK_THRESHOLD = 0.8;
    private static final int MAX_FRAUD_ATTEMPTS_PER_DAY = 3;
    
    /**
     * Process fraud alert events from Kafka
     */
    @KafkaListener(
        topics = "${kafka.topics.fraud-alerts:fraud-alerts}",
        groupId = "${spring.kafka.consumer.group-id:user-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleFraudAlert(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Received fraud alert for user: {} with severity: {} and risk score: {}",
            event.getUserId(), event.getSeverity(), event.getRiskScore());
        
        try {
            // Check for duplicate processing
            if (isDuplicateEvent(event.getEventId())) {
                log.warn("Duplicate fraud alert event detected: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Process the fraud alert
            processFraudAlert(event);
            
            // Mark event as processed
            markEventProcessed(event.getEventId());
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fraud alert event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Error processing fraud alert event: {}", event.getEventId(), e);
            // Don't acknowledge - let it retry
            publishErrorEvent(event, e);
            throw e;
        }
    }
    
    /**
     * Process fraud alert with graduated response
     */
    private void processFraudAlert(FraudAlertEvent event) {
        // Find user
        Optional<User> userOpt = userRepository.findById(UUID.fromString(event.getUserId()));
        
        if (userOpt.isEmpty()) {
            log.error("User not found for fraud alert: {}", event.getUserId());
            publishUserNotFoundEvent(event);
            return;
        }
        
        User user = userOpt.get();
        
        // Determine action based on severity and risk
        FraudResponseAction action = determineResponseAction(event, user);
        
        // Execute the action
        executeResponseAction(user, event, action);
        
        // Log the action
        logFraudResponse(user, event, action);
        
        // Send notifications
        sendFraudNotifications(user, event, action);
        
        // Publish response event for other services
        publishFraudResponseEvent(user, event, action);
    }
    
    /**
     * Determine appropriate response action based on fraud severity and user history
     */
    private FraudResponseAction determineResponseAction(FraudAlertEvent event, User user) {
        // Critical severity - immediate suspension
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.CRITICAL) {
            return FraudResponseAction.IMMEDIATE_SUSPENSION;
        }
        
        // High severity with high risk score - suspension
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.HIGH && 
            event.getRiskScore() >= AUTO_SUSPEND_RISK_THRESHOLD) {
            return FraudResponseAction.SUSPENSION;
        }
        
        // Financial crime - immediate suspension and report
        if (event.isFinancialCrime()) {
            return FraudResponseAction.FREEZE_AND_REPORT;
        }
        
        // Large amount fraud - temporary restriction
        if (event.getAmount().compareTo(HIGH_RISK_AMOUNT_THRESHOLD) > 0) {
            return FraudResponseAction.TEMPORARY_RESTRICTION;
        }
        
        // Repeat offender - escalate response
        if (Boolean.TRUE.equals(event.getRepeatOffender())) {
            return FraudResponseAction.ESCALATED_SUSPENSION;
        }
        
        // Medium severity - enhanced monitoring
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.MEDIUM) {
            return FraudResponseAction.ENHANCED_MONITORING;
        }
        
        // Low severity - warning only
        if (event.getSeverity() == FraudAlertEvent.FraudSeverity.LOW) {
            return FraudResponseAction.WARNING;
        }
        
        // Default - monitoring
        return FraudResponseAction.MONITORING;
    }
    
    /**
     * Execute the determined response action
     */
    private void executeResponseAction(User user, FraudAlertEvent event, FraudResponseAction action) {
        String suspensionReason;
        
        switch (action) {
            case IMMEDIATE_SUSPENSION:
                suspensionReason = String.format("Critical fraud detected: %s (Risk: %.2f, Amount: %s %s)",
                    event.getFraudType(), event.getRiskScore(), event.getCurrency(), event.getAmount());
                user.suspend(suspensionReason);
                user.setAccountLocked(true);
                user.setLockReason("Critical fraud alert - automatic suspension");
                user.setLockedUntil(LocalDateTime.now().plusDays(CRITICAL_SUSPENSION_DAYS));
                userRepository.save(user);
                log.warn("User {} immediately suspended due to critical fraud alert", user.getId());
                break;
                
            case SUSPENSION:
                suspensionReason = String.format("High-risk fraud detected: %s (Risk: %.2f)",
                    event.getFraudType(), event.getRiskScore());
                user.suspend(suspensionReason);
                user.setLockedUntil(LocalDateTime.now().plusDays(HIGH_SUSPENSION_DAYS));
                userRepository.save(user);
                log.warn("User {} suspended due to high-risk fraud alert", user.getId());
                break;
                
            case FREEZE_AND_REPORT:
                suspensionReason = String.format("Financial crime detected: %s - Account frozen pending investigation",
                    event.getFraudType());
                user.suspend(suspensionReason);
                user.setAccountLocked(true);
                user.setLockReason("Financial crime alert - regulatory freeze");
                user.setRequiresManualReview(true);
                userRepository.save(user);
                
                // Trigger SAR filing if required
                if (Boolean.TRUE.equals(event.getSarRequired())) {
                    triggerSARFiling(user, event);
                }
                log.error("User {} account frozen due to financial crime alert", user.getId());
                break;
                
            case TEMPORARY_RESTRICTION:
                user.setTransactionRestricted(true);
                user.setRestrictionReason("High-value fraud alert - temporary restriction");
                user.setRestrictedUntil(LocalDateTime.now().plusDays(MEDIUM_SUSPENSION_DAYS));
                user.setDailyTransactionLimit(new BigDecimal("500"));
                userRepository.save(user);
                log.warn("User {} temporarily restricted due to fraud alert", user.getId());
                break;
                
            case ESCALATED_SUSPENSION:
                suspensionReason = String.format("Repeat fraud offender: %s (Previous count: %d)",
                    event.getFraudType(), event.getPreviousFraudCount());
                user.suspend(suspensionReason);
                user.setAccountLocked(true);
                user.setLockReason("Repeat fraud offender - extended suspension");
                user.setLockedUntil(LocalDateTime.now().plusDays(CRITICAL_SUSPENSION_DAYS * 2));
                user.setRequiresManualReview(true);
                userRepository.save(user);
                log.error("User {} suspended with escalation due to repeat fraud", user.getId());
                break;
                
            case ENHANCED_MONITORING:
                user.setEnhancedMonitoring(true);
                user.setMonitoringReason("Medium fraud risk detected");
                user.setMonitoringUntil(LocalDateTime.now().plusDays(30));
                user.setRequiresTwoFactorAuth(true);
                userRepository.save(user);
                log.info("User {} placed under enhanced monitoring", user.getId());
                break;
                
            case WARNING:
                user.incrementWarningCount();
                user.setLastWarningDate(LocalDateTime.now());
                userRepository.save(user);
                log.info("Warning issued to user {} for low-risk fraud alert", user.getId());
                break;
                
            case MONITORING:
            default:
                // Just log and monitor
                log.info("User {} fraud alert logged for monitoring", user.getId());
                break;
        }
        
        // Update fraud tracking fields
        user.setLastFraudAlertDate(LocalDateTime.now());
        user.incrementFraudAlertCount();
        user.updateFraudRiskScore(event.getRiskScore());
        userRepository.save(user);
    }
    
    /**
     * Log fraud response action for audit trail
     */
    private void logFraudResponse(User user, FraudAlertEvent event, FraudResponseAction action) {
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("userId", user.getId());
        auditData.put("eventId", event.getEventId());
        auditData.put("fraudType", event.getFraudType());
        auditData.put("severity", event.getSeverity());
        auditData.put("riskScore", event.getRiskScore());
        auditData.put("amount", event.getAmount());
        auditData.put("currency", event.getCurrency());
        auditData.put("action", action);
        auditData.put("timestamp", LocalDateTime.now());
        auditData.put("transactionId", event.getTransactionId());
        
        activityLogService.logActivity(
            user.getId().toString(),
            "FRAUD_ALERT_RESPONSE",
            String.format("Fraud alert processed: %s - Action: %s", event.getFraudType(), action),
            auditData
        );
    }
    
    /**
     * Send notifications about fraud alert and action taken
     */
    private void sendFraudNotifications(User user, FraudAlertEvent event, FraudResponseAction action) {
        // Notify user if required
        if (Boolean.TRUE.equals(event.getNotifyUser()) && shouldNotifyUser(action)) {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("fraudType", event.getFraudType());
            notificationData.put("action", action);
            notificationData.put("amount", event.getAmount());
            notificationData.put("currency", event.getCurrency());
            notificationData.put("timestamp", event.getTimestamp());
            
            notificationService.sendFraudAlertNotification(
                user,
                "Suspicious Activity Detected",
                generateUserNotificationMessage(event, action),
                notificationData
            );
        }
        
        // Always notify security team for high-risk events
        if (event.isHighRisk() || action.requiresSecurityTeamNotification()) {
            notifySecurityTeam(user, event, action);
        }
        
        // Notify compliance team for financial crimes
        if (event.isFinancialCrime()) {
            notifyComplianceTeam(user, event, action);
        }
    }
    
    /**
     * Generate user notification message
     */
    private String generateUserNotificationMessage(FraudAlertEvent event, FraudResponseAction action) {
        StringBuilder message = new StringBuilder();
        message.append("We've detected suspicious activity on your account. ");
        
        switch (action) {
            case IMMEDIATE_SUSPENSION:
            case SUSPENSION:
                message.append("Your account has been temporarily suspended for your protection. ");
                message.append("Please contact our support team to verify your identity and restore access.");
                break;
                
            case FREEZE_AND_REPORT:
                message.append("Your account has been frozen pending investigation. ");
                message.append("Our security team will contact you within 24 hours.");
                break;
                
            case TEMPORARY_RESTRICTION:
                message.append("We've temporarily limited your transaction capabilities. ");
                message.append("You can still use your account with reduced limits.");
                break;
                
            case ENHANCED_MONITORING:
                message.append("We've enhanced security monitoring on your account. ");
                message.append("You may be asked for additional verification for certain transactions.");
                break;
                
            case WARNING:
                message.append("Please review your recent activity and ensure your account is secure. ");
                message.append("Consider changing your password and enabling two-factor authentication.");
                break;
                
            default:
                message.append("We're monitoring your account for any further suspicious activity.");
                break;
        }
        
        return message.toString();
    }
    
    /**
     * Publish fraud response event for other services
     */
    private void publishFraudResponseEvent(User user, FraudAlertEvent originalEvent, FraudResponseAction action) {
        Map<String, Object> responseEvent = new HashMap<>();
        responseEvent.put("eventId", UUID.randomUUID().toString());
        responseEvent.put("timestamp", LocalDateTime.now());
        responseEvent.put("originalEventId", originalEvent.getEventId());
        responseEvent.put("userId", user.getId());
        responseEvent.put("action", action);
        responseEvent.put("userStatus", user.getStatus());
        responseEvent.put("accountLocked", user.isAccountLocked());
        responseEvent.put("restrictedUntil", user.getRestrictedUntil());
        responseEvent.put("service", "user-service");
        
        kafkaTemplate.send("fraud-response-events", responseEvent);
        
        log.debug("Published fraud response event for user: {}", user.getId());
    }
    
    /**
     * Notify security team about high-risk fraud
     */
    private void notifySecurityTeam(User user, FraudAlertEvent event, FraudResponseAction action) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "HIGH_RISK_FRAUD");
        alert.put("userId", user.getId());
        alert.put("username", user.getUsername());
        alert.put("fraudEvent", event);
        alert.put("actionTaken", action);
        alert.put("priority", event.getResponsePriority());
        alert.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("security-team-alerts", alert);
    }
    
    /**
     * Notify compliance team about financial crimes
     */
    private void notifyComplianceTeam(User user, FraudAlertEvent event, FraudResponseAction action) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "FINANCIAL_CRIME");
        alert.put("userId", user.getId());
        alert.put("fraudEvent", event);
        alert.put("actionTaken", action);
        alert.put("sarRequired", event.getSarRequired());
        alert.put("amlFlagged", event.getAmlFlagged());
        alert.put("sanctionsHit", event.getSanctionsHit());
        alert.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("compliance-alerts", alert);
    }
    
    /**
     * Trigger SAR filing process
     */
    private void triggerSARFiling(User user, FraudAlertEvent event) {
        Map<String, Object> sarRequest = new HashMap<>();
        sarRequest.put("userId", user.getId());
        sarRequest.put("transactionId", event.getTransactionId());
        sarRequest.put("amount", event.getAmount());
        sarRequest.put("currency", event.getCurrency());
        sarRequest.put("fraudType", event.getFraudType());
        sarRequest.put("reason", event.getReason());
        sarRequest.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("sar-filing-requests", sarRequest);
        
        log.info("SAR filing triggered for user: {}", user.getId());
    }
    
    /**
     * Check if event is duplicate
     */
    private boolean isDuplicateEvent(String eventId) {
        // Clean old entries (older than 1 hour)
        cleanOldProcessedEvents();
        
        return processedEvents.containsKey(eventId);
    }
    
    /**
     * Mark event as processed
     */
    private void markEventProcessed(String eventId) {
        processedEvents.put(eventId, LocalDateTime.now());
    }
    
    /**
     * Clean old processed events from memory
     */
    private void cleanOldProcessedEvents() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(oneHourAgo));
    }
    
    /**
     * Determine if user should be notified
     */
    private boolean shouldNotifyUser(FraudResponseAction action) {
        return action != FraudResponseAction.MONITORING;
    }
    
    /**
     * Publish error event for monitoring
     */
    private void publishErrorEvent(FraudAlertEvent event, Exception error) {
        Map<String, Object> errorEvent = new HashMap<>();
        errorEvent.put("eventId", event.getEventId());
        errorEvent.put("userId", event.getUserId());
        errorEvent.put("error", error.getMessage());
        errorEvent.put("timestamp", LocalDateTime.now());
        errorEvent.put("service", "user-service");
        
        kafkaTemplate.send("fraud-processing-errors", errorEvent);
    }
    
    /**
     * Publish user not found event
     */
    private void publishUserNotFoundEvent(FraudAlertEvent event) {
        Map<String, Object> notFoundEvent = new HashMap<>();
        notFoundEvent.put("eventId", event.getEventId());
        notFoundEvent.put("userId", event.getUserId());
        notFoundEvent.put("timestamp", LocalDateTime.now());
        notFoundEvent.put("service", "user-service");
        
        kafkaTemplate.send("fraud-user-not-found", notFoundEvent);
    }
    
    /**
     * Fraud response actions enum
     */
    public enum FraudResponseAction {
        IMMEDIATE_SUSPENSION(true),
        SUSPENSION(true),
        FREEZE_AND_REPORT(true),
        TEMPORARY_RESTRICTION(false),
        ESCALATED_SUSPENSION(true),
        ENHANCED_MONITORING(false),
        WARNING(false),
        MONITORING(false);
        
        private final boolean requiresSecurityNotification;
        
        FraudResponseAction(boolean requiresSecurityNotification) {
            this.requiresSecurityNotification = requiresSecurityNotification;
        }
        
        public boolean requiresSecurityTeamNotification() {
            return requiresSecurityNotification;
        }
    }
}