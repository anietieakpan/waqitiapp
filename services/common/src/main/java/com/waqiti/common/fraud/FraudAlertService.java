package com.waqiti.common.fraud;

import com.waqiti.common.audit.AuditService;
import com.waqiti.common.audit.SecurityAuditLog;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.common.fraud.ml.MLPredictionResult;
import com.waqiti.common.fraud.model.*;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.model.NotificationRequest;
import com.waqiti.common.notification.model.StandardNotificationRequest; // PRODUCTION FIX
import com.waqiti.common.notification.model.NotificationType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Enterprise-grade fraud alert service for real-time fraud detection and alerting.
 * Handles alert generation, routing, escalation, and notification with comprehensive
 * monitoring and compliance features.
 */
@Slf4j
@Service
public class FraudAlertService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    // Alert configuration
    @Value("${fraud.alert.critical.threshold:0.9}")
    private double criticalThreshold;
    
    @Value("${fraud.alert.high.threshold:0.7}")
    private double highThreshold;
    
    @Value("${fraud.alert.medium.threshold:0.5}")
    private double mediumThreshold;
    
    @Value("${fraud.alert.escalation.timeout:300000}")
    private long escalationTimeoutMs;
    
    @Value("${fraud.alert.rate.limit:100}")
    private int alertRateLimit;
    
    // Kafka topics
    private static final String FRAUD_ALERT_TOPIC = "fraud-alerts";
    private static final String CRITICAL_ALERT_TOPIC = "critical-fraud-alerts";
    private static final String ALERT_AUDIT_TOPIC = "fraud-alert-audit";
    
    // Redis keys
    private static final String ALERT_RATE_LIMIT_KEY = "fraud:alert:rate:";
    private static final String ALERT_DEDUP_KEY = "fraud:alert:dedup:";
    private static final String ACTIVE_ALERTS_KEY = "fraud:alerts:active";
    private static final String ALERT_STATS_KEY = "fraud:alerts:stats";
    
    // Metrics
    private final Counter alertsGeneratedCounter;
    private final Counter criticalAlertsCounter;
    private final Counter alertsDeduplicatedCounter;
    private final Counter alertNotificationsSentCounter;
    private final Timer alertProcessingTimer;
    
    // Alert management
    private final Map<String, FraudAlert> activeAlerts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService escalationExecutor = Executors.newScheduledThreadPool(5);
    private final ExecutorService alertProcessor = Executors.newFixedThreadPool(10);
    
    // Alert templates
    private final Map<AlertSeverity, AlertTemplate> alertTemplates = new ConcurrentHashMap<>();
    
    public FraudAlertService(KafkaTemplate<String, Object> kafkaTemplate,
                            RedisTemplate<String, Object> redisTemplate,
                            NotificationService notificationService,
                            AuditService auditService,
                            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.alertsGeneratedCounter = Counter.builder("fraud.alerts.generated")
                .description("Total fraud alerts generated")
                .register(meterRegistry);
        
        this.criticalAlertsCounter = Counter.builder("fraud.alerts.critical")
                .description("Critical fraud alerts")
                .register(meterRegistry);
        
        this.alertsDeduplicatedCounter = Counter.builder("fraud.alerts.deduplicated")
                .description("Duplicate alerts filtered")
                .register(meterRegistry);
        
        this.alertNotificationsSentCounter = Counter.builder("fraud.alerts.notifications")
                .description("Alert notifications sent")
                .register(meterRegistry);
        
        this.alertProcessingTimer = Timer.builder("fraud.alerts.processing.time")
                .description("Alert processing time")
                .register(meterRegistry);
        
        initializeAlertTemplates();
    }
    
    /**
     * Generate fraud alert based on ML prediction
     */
    public FraudAlert generateAlert(MLPredictionResult prediction, FraudAssessmentRequest request) {
        return alertProcessingTimer.record(() -> {
            try {
                // Determine alert severity
                AlertSeverity severity = determineAlertSeverity(prediction.getFraudProbability());
                
                // Create fraud alert
                FraudAlert alert = FraudAlert.builder()
                        .alertId(UUID.randomUUID().toString())
                        .transactionId(request.getTransactionId())
                        .userId(request.getUserId())
                        .fraudProbability(prediction.getFraudProbability())
                        .confidence(prediction.getConfidence())
                        .severity(severity)
                        .riskLevel(prediction.getRiskLevel())
                        .alertType(determineAlertType(prediction))
                        .riskFactors(prediction.getRiskFactors())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .merchantInfo(request.getMerchantInfo() != null ?
                            Map.of("merchantId", request.getMerchantInfo()) : Map.of())
                        .timestamp(LocalDateTime.now())
                        .status(AlertStatus.NEW)
                        .recommendedActions(generateRecommendedActions(prediction, severity))
                        .metadata(enrichAlertMetadata(prediction, request))
                        .build();
                
                // Check for duplicate alerts
                if (!isDuplicateAlert(alert)) {
                    processAlert(alert);
                    alertsGeneratedCounter.increment();
                    
                    if (severity == AlertSeverity.CRITICAL) {
                        criticalAlertsCounter.increment();
                        processCriticalAlert(alert);
                    }
                } else {
                    alertsDeduplicatedCounter.increment();
                    log.debug("Duplicate alert filtered: {}", alert.getAlertId());
                }
                
                return alert;
                
            } catch (Exception e) {
                log.error("Error generating fraud alert", e);
                throw new FraudAlertException("Failed to generate fraud alert", e);
            }
        });
    }
    
    /**
     * Process fraud alert with routing and notification
     */
    @Async
    public void processAlert(FraudAlert alert) {
        try {
            // Store active alert
            activeAlerts.put(alert.getAlertId(), alert);
            storeAlertInRedis(alert);
            
            // Audit alert generation
            auditAlertGeneration(alert);
            
            // Publish to Kafka for downstream processing
            publishAlertToKafka(alert);
            
            // Send notifications based on severity
            sendAlertNotifications(alert);
            
            // Schedule escalation if needed
            if (alert.getSeverity().ordinal() >= AlertSeverity.HIGH.ordinal()) {
                scheduleAlertEscalation(alert);
            }
            
            // Update statistics
            updateAlertStatistics(alert);
            
            log.info("Processed fraud alert: {} - Severity: {} - User: {} - Amount: {} {}",
                    alert.getAlertId(), alert.getSeverity(), alert.getUserId(),
                    alert.getAmount(), alert.getCurrency());
            
        } catch (Exception e) {
            log.error("Error processing fraud alert: {}", alert.getAlertId(), e);
            handleAlertProcessingFailure(alert, e);
        }
    }
    
    /**
     * Process critical alerts with immediate escalation
     */
    private void processCriticalAlert(FraudAlert alert) {
        try {
            // PRODUCTION FIX: Use StandardNotificationRequest for builder pattern
            // Immediate notification to security team
            StandardNotificationRequest urgentNotification = StandardNotificationRequest.builder()
                    .type(NotificationType.URGENT_SECURITY)
                    .recipient(getSecurityTeamContacts())
                    .subject("CRITICAL FRAUD ALERT - Immediate Action Required")
                    .message(formatCriticalAlertMessage(alert))
                    .priority(NotificationRequest.Priority.CRITICAL)
                    .metadata(Map.of(
                            "alertId", alert.getAlertId(),
                            "severity", "CRITICAL",
                            "amount", alert.getAmount().toString()
                    ))
                    .build();
            
            notificationService.sendUrgentNotification(urgentNotification);
            
            // Publish to critical alerts topic
            kafkaTemplate.send(CRITICAL_ALERT_TOPIC, alert.getAlertId(), alert);
            
            // Auto-block transaction if confidence is high
            if (alert.getConfidence() > 0.95) {
                blockTransaction(alert);
            }
            
            // Create incident ticket
            createIncidentTicket(alert);
            
        } catch (Exception e) {
            log.error("Error processing critical alert: {}", alert.getAlertId(), e);
        }
    }
    
    /**
     * Acknowledge alert by analyst
     */
    public void acknowledgeAlert(String alertId, String analystId, String notes) {
        FraudAlert alert = activeAlerts.get(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found: " + alertId);
        }
        
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(analystId);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.addNote(notes);
        
        // Update in storage
        storeAlertInRedis(alert);
        
        // Audit acknowledgment
        auditAlertAction(alert, "ACKNOWLEDGED", analystId, notes);
        
        // Cancel escalation
        cancelEscalation(alertId);
        
        log.info("Alert {} acknowledged by {}", alertId, analystId);
    }
    
    /**
     * Investigate alert with detailed analysis
     */
    public AlertInvestigationResult investigateAlert(String alertId, String investigatorId) {
        FraudAlert alert = activeAlerts.get(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found: " + alertId);
        }
        
        alert.setStatus(AlertStatus.INVESTIGATING);
        alert.setInvestigatorId(investigatorId);
        alert.setInvestigationStartTime(LocalDateTime.now());
        
        // Gather additional evidence
        Map<String, Object> evidence = gatherEvidence(alert);
        
        // Analyze patterns
        List<String> patterns = analyzePatterns(alert);
        
        // Get historical context
        List<FraudAlert> relatedAlerts = findRelatedAlerts(alert);
        
        // Calculate final risk score
        double finalRiskScore = calculateFinalRiskScore(alert, evidence, patterns);
        
        AlertInvestigationResult result = AlertInvestigationResult.builder()
                .alertId(alertId)
                .investigatorId(investigatorId)
                .evidence(evidence)
                .patterns(patterns)
                .relatedAlerts(relatedAlerts)
                .finalRiskScore(finalRiskScore)
                .recommendation(generateInvestigationRecommendation(finalRiskScore))
                .timestamp(LocalDateTime.now())
                .build();
        
        // Update alert
        alert.setInvestigationResult(result);
        storeAlertInRedis(alert);
        
        // Audit investigation
        auditAlertAction(alert, "INVESTIGATED", investigatorId, result.getRecommendation());
        
        return result;
    }
    
    /**
     * Resolve alert with final decision
     */
    public void resolveAlert(String alertId, AlertResolution resolution, String resolvedBy) {
        FraudAlert alert = activeAlerts.get(alertId);
        if (alert == null) {
            throw new IllegalArgumentException("Alert not found: " + alertId);
        }
        
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolution(resolution);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(LocalDateTime.now());
        
        // Calculate resolution time
        Duration resolutionTime = Duration.between(alert.getTimestamp(), alert.getResolvedAt());
        alert.setResolutionTimeMinutes(resolutionTime.toMinutes());
        
        // Update metrics
        updateResolutionMetrics(alert, resolution);
        
        // Remove from active alerts
        activeAlerts.remove(alertId);
        
        // Archive alert
        archiveAlert(alert);
        
        // Audit resolution
        auditAlertAction(alert, "RESOLVED", resolvedBy, resolution.toString());
        
        // Send resolution notification
        sendResolutionNotification(alert);
        
        log.info("Alert {} resolved as {} by {}", alertId, resolution, resolvedBy);
    }
    
    /**
     * Get active alerts summary
     */
    public AlertsSummary getActiveAlertsSummary() {
        Map<AlertSeverity, Long> severityCount = activeAlerts.values().stream()
                .collect(Collectors.groupingBy(FraudAlert::getSeverity, Collectors.counting()));
        
        Map<AlertStatus, Long> statusCount = activeAlerts.values().stream()
                .collect(Collectors.groupingBy(FraudAlert::getStatus, Collectors.counting()));
        
        return AlertsSummary.builder()
                .totalActive(activeAlerts.size())
                .bySeverity(severityCount)
                .byStatus(statusCount)
                .criticalCount(severityCount.getOrDefault(AlertSeverity.CRITICAL, 0L))
                .awaitingAction(countAlertsAwaitingAction())
                .averageResolutionTime(calculateAverageResolutionTime())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    // Private helper methods
    
    private void initializeAlertTemplates() {
        alertTemplates.put(AlertSeverity.CRITICAL, AlertTemplate.builder()
                .severity(AlertSeverity.CRITICAL)
                .notificationChannels(List.of("SMS", "EMAIL", "PUSH", "PHONE"))
                .recipientGroups(List.of("SECURITY_TEAM", "FRAUD_ANALYSTS", "MANAGEMENT"))
                .escalationTimeMinutes(5)
                .autoBlock(true)
                .requiresAcknowledgment(true)
                .build());
        
        alertTemplates.put(AlertSeverity.HIGH, AlertTemplate.builder()
                .severity(AlertSeverity.HIGH)
                .notificationChannels(List.of("EMAIL", "PUSH"))
                .recipientGroups(List.of("FRAUD_ANALYSTS", "SUPERVISORS"))
                .escalationTimeMinutes(15)
                .autoBlock(false)
                .requiresAcknowledgment(true)
                .build());
        
        alertTemplates.put(AlertSeverity.MEDIUM, AlertTemplate.builder()
                .severity(AlertSeverity.MEDIUM)
                .notificationChannels(List.of("EMAIL"))
                .recipientGroups(List.of("FRAUD_ANALYSTS"))
                .escalationTimeMinutes(30)
                .autoBlock(false)
                .requiresAcknowledgment(false)
                .build());
    }
    
    private AlertSeverity determineAlertSeverity(double fraudProbability) {
        if (fraudProbability >= criticalThreshold) {
            return AlertSeverity.CRITICAL;
        } else if (fraudProbability >= highThreshold) {
            return AlertSeverity.HIGH;
        } else if (fraudProbability >= mediumThreshold) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }
    
    private FraudAlertType determineAlertType(MLPredictionResult prediction) {
        // Analyze risk factors to determine alert type
        List<String> riskFactors = prediction.getRiskFactors();
        
        if (riskFactors.contains("account_takeover")) {
            return FraudAlertType.ACCOUNT_TAKEOVER;
        } else if (riskFactors.contains("card_not_present")) {
            return FraudAlertType.CARD_FRAUD;
        } else if (riskFactors.contains("identity_theft")) {
            return FraudAlertType.IDENTITY_FRAUD;
        } else if (riskFactors.contains("money_laundering")) {
            return FraudAlertType.MONEY_LAUNDERING;
        } else {
            return FraudAlertType.TRANSACTION_FRAUD;
        }
    }
    
    private List<String> generateRecommendedActions(MLPredictionResult prediction, AlertSeverity severity) {
        List<String> actions = new ArrayList<>();
        
        if (severity == AlertSeverity.CRITICAL) {
            actions.add("BLOCK_TRANSACTION");
            actions.add("FREEZE_ACCOUNT");
            actions.add("CONTACT_CUSTOMER");
            actions.add("NOTIFY_AUTHORITIES");
        } else if (severity == AlertSeverity.HIGH) {
            actions.add("REVIEW_TRANSACTION");
            actions.add("REQUEST_ADDITIONAL_VERIFICATION");
            actions.add("MONITOR_ACCOUNT");
        } else {
            actions.add("FLAG_FOR_REVIEW");
            actions.add("MONITOR_PATTERN");
        }
        
        return actions;
    }
    
    private Map<String, Object> enrichAlertMetadata(MLPredictionResult prediction, FraudAssessmentRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("modelUsed", prediction.getModelUsed());
        metadata.put("processingTimeMs", prediction.getProcessingTimeMs());
        metadata.put("ipAddress", request.getIpAddress());
        metadata.put("sessionId", request.getSessionId());
        metadata.put("alertGeneratedAt", LocalDateTime.now().toString());
        return metadata;
    }
    
    private boolean isDuplicateAlert(FraudAlert alert) {
        String dedupKey = ALERT_DEDUP_KEY + alert.getUserId() + ":" + alert.getTransactionId();
        Boolean exists = redisTemplate.hasKey(dedupKey);
        
        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForValue().set(dedupKey, alert.getAlertId(), Duration.ofMinutes(5));
            return false;
        }
        
        return true;
    }
    
    private void storeAlertInRedis(FraudAlert alert) {
        String key = ACTIVE_ALERTS_KEY + ":" + alert.getAlertId();
        redisTemplate.opsForValue().set(key, alert, Duration.ofHours(24));
    }
    
    private void publishAlertToKafka(FraudAlert alert) {
        try {
            FraudAlertEvent event = FraudAlertEvent.builder()
                    .alertId(alert.getAlertId())
                    .transactionId(alert.getTransactionId() != null ? UUID.fromString(alert.getTransactionId()) : null)
                    .userId(alert.getUserId() != null ? UUID.fromString(alert.getUserId()) : null)
                    .severity(FraudAlertEvent.FraudSeverity.valueOf(alert.getSeverity().name()))
                    .fraudProbability(alert.getFraudProbability())
                    .amount(alert.getAmount())
                    .currency(alert.getCurrency())
                    .timestamp(alert.getTimestamp())
                    .build();
            
            kafkaTemplate.send(FRAUD_ALERT_TOPIC, alert.getAlertId(), event);
        } catch (Exception e) {
            log.error("Failed to publish alert to Kafka: {}", alert.getAlertId(), e);
        }
    }
    
    private void sendAlertNotifications(FraudAlert alert) {
        AlertTemplate template = alertTemplates.get(alert.getSeverity());
        if (template == null) return;
        
        for (String channel : template.getNotificationChannels()) {
            try {
                // PRODUCTION FIX: Use StandardNotificationRequest
                StandardNotificationRequest notification = StandardNotificationRequest.builder()
                        .type(mapToNotificationType(channel))
                        .recipient(getRecipients(template.getRecipientGroups()))
                        .subject(formatAlertSubject(alert))
                        .message(formatAlertMessage(alert))
                        .priority(mapToPriority(alert.getSeverity()))
                        .metadata(Map.of("alertId", alert.getAlertId()))
                        .build();
                
                notificationService.sendNotification(notification);
                alertNotificationsSentCounter.increment();
                
            } catch (Exception e) {
                log.error("Failed to send {} notification for alert {}", channel, alert.getAlertId(), e);
            }
        }
    }
    
    private void scheduleAlertEscalation(FraudAlert alert) {
        AlertTemplate template = alertTemplates.get(alert.getSeverity());
        if (template == null || !template.isRequiresAcknowledgment()) return;
        
        escalationExecutor.schedule(() -> {
            if (alert.getStatus() == AlertStatus.NEW) {
                escalateAlert(alert);
            }
        }, template.getEscalationTimeMinutes(), TimeUnit.MINUTES);
    }
    
    private void escalateAlert(FraudAlert alert) {
        log.warn("Escalating unacknowledged alert: {}", alert.getAlertId());
        
        alert.setStatus(AlertStatus.ESCALATED);
        alert.setEscalatedAt(LocalDateTime.now());

        // PRODUCTION FIX: Use StandardNotificationRequest
        // Send escalation notifications
        StandardNotificationRequest escalation = StandardNotificationRequest.builder()
                .type(NotificationType.ESCALATION)
                .recipient(getEscalationContacts())
                .subject("ESCALATED ALERT - " + alert.getAlertId())
                .message(formatEscalationMessage(alert))
                .priority(NotificationRequest.Priority.URGENT)
                .build();
        
        notificationService.sendUrgentNotification(escalation);
        
        // Update storage
        storeAlertInRedis(alert);
    }
    
    private void auditAlertGeneration(FraudAlert alert) {
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .eventType("FRAUD_ALERT_GENERATED")
                .userId(alert.getUserId())
                .category(SecurityAuditLog.SecurityEventCategory.FRAUD_DETECTION)
                .severity(mapToAuditSeverity(alert.getSeverity()))
                .description(String.format("Fraud alert generated: %s - Probability: %.2f",
                        alert.getAlertId(), alert.getFraudProbability()))
                .resource(alert.getTransactionId())
                .outcome(SecurityAuditLog.SecurityOutcome.SUCCESS)
                .details(Map.of(
                        "alertId", alert.getAlertId(),
                        "severity", alert.getSeverity().name(),
                        "amount", alert.getAmount().toString()
                ))
                .timestamp(LocalDateTime.now())
                .build();
        
        auditService.logSecurityEvent(auditLog);
    }
    
    private void auditAlertAction(FraudAlert alert, String action, String userId, String notes) {
        Map<String, Object> details = new HashMap<>();
        details.put("alertId", alert.getAlertId());
        details.put("action", action);
        details.put("notes", notes);
        
        SecurityAuditLog auditLog = SecurityAuditLog.builder()
                .eventType("FRAUD_ALERT_ACTION")
                .userId(userId)
                .category(SecurityAuditLog.SecurityEventCategory.FRAUD_DETECTION)
                .severity(SecurityAuditLog.SecuritySeverity.INFO)
                .description(String.format("Alert %s: %s", action.toLowerCase(), alert.getAlertId()))
                .resource(alert.getAlertId())
                .action(action)
                .outcome(SecurityAuditLog.SecurityOutcome.SUCCESS)
                .details(details)
                .timestamp(LocalDateTime.now())
                .build();
        
        auditService.logSecurityEvent(auditLog);
    }
    
    // Stub implementations for dependent methods
    
    private void blockTransaction(FraudAlert alert) {
        log.info("Blocking transaction for alert: {}", alert.getAlertId());
    }
    
    private void createIncidentTicket(FraudAlert alert) {
        log.info("Creating incident ticket for alert: {}", alert.getAlertId());
    }
    
    private void cancelEscalation(String alertId) {
        log.debug("Cancelling escalation for alert: {}", alertId);
    }
    
    private void handleAlertProcessingFailure(FraudAlert alert, Exception e) {
        log.error("Alert processing failed for: {}", alert.getAlertId(), e);
    }
    
    private void updateAlertStatistics(FraudAlert alert) {
        // Update Redis statistics
    }
    
    private void updateResolutionMetrics(FraudAlert alert, AlertResolution resolution) {
        // Update resolution metrics
    }
    
    private void archiveAlert(FraudAlert alert) {
        // Archive to long-term storage
    }
    
    private void sendResolutionNotification(FraudAlert alert) {
        // Send resolution notification
    }
    
    private Map<String, Object> gatherEvidence(FraudAlert alert) {
        return Map.of("evidence", "gathered");
    }
    
    private List<String> analyzePatterns(FraudAlert alert) {
        return List.of("pattern1", "pattern2");
    }
    
    private List<FraudAlert> findRelatedAlerts(FraudAlert alert) {
        return new ArrayList<>();
    }
    
    private double calculateFinalRiskScore(FraudAlert alert, Map<String, Object> evidence, List<String> patterns) {
        return alert.getFraudProbability();
    }
    
    private String generateInvestigationRecommendation(double riskScore) {
        if (riskScore > 0.8) return "Block and investigate";
        if (riskScore > 0.5) return "Review and monitor";
        return "Monitor";
    }
    
    private long countAlertsAwaitingAction() {
        return activeAlerts.values().stream()
                .filter(a -> a.getStatus() == AlertStatus.NEW || a.getStatus() == AlertStatus.ESCALATED)
                .count();
    }
    
    private double calculateAverageResolutionTime() {
        return 15.5; // Placeholder
    }
    
    private String getSecurityTeamContacts() {
        return "security-team@company.com";
    }
    
    private String getRecipients(List<String> groups) {
        return "fraud-team@company.com";
    }
    
    private String getEscalationContacts() {
        return "management@company.com";
    }
    
    private String formatCriticalAlertMessage(FraudAlert alert) {
        return String.format("CRITICAL FRAUD ALERT\n" +
                "Alert ID: %s\n" +
                "User: %s\n" +
                "Amount: %s %s\n" +
                "Probability: %.2f%%\n" +
                "Risk Factors: %s",
                alert.getAlertId(),
                alert.getUserId(),
                alert.getAmount(),
                alert.getCurrency(),
                alert.getFraudProbability() * 100,
                String.join(", ", alert.getRiskFactors()));
    }
    
    private String formatAlertSubject(FraudAlert alert) {
        return String.format("Fraud Alert [%s] - User: %s", alert.getSeverity(), alert.getUserId());
    }
    
    private String formatAlertMessage(FraudAlert alert) {
        return String.format("Fraud alert detected:\n" +
                "Transaction: %s\n" +
                "Amount: %s %s\n" +
                "Risk Level: %s\n" +
                "Recommended Actions: %s",
                alert.getTransactionId(),
                alert.getAmount(),
                alert.getCurrency(),
                alert.getRiskLevel(),
                String.join(", ", alert.getRecommendedActions()));
    }
    
    private String formatEscalationMessage(FraudAlert alert) {
        return String.format("Unacknowledged alert requires immediate attention:\n%s",
                formatAlertMessage(alert));
    }
    
    private NotificationType mapToNotificationType(String channel) {
        return switch (channel) {
            case "SMS" -> NotificationType.SMS;
            case "EMAIL" -> NotificationType.EMAIL;
            case "PUSH" -> NotificationType.PUSH_NOTIFICATION;
            case "PHONE" -> NotificationType.VOICE_CALL;
            default -> NotificationType.EMAIL;
        };
    }
    
    private NotificationRequest.Priority mapToPriority(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> NotificationRequest.Priority.CRITICAL;
            case HIGH -> NotificationRequest.Priority.HIGH;
            case MEDIUM -> NotificationRequest.Priority.MEDIUM;
            default -> NotificationRequest.Priority.LOW;
        };
    }
    
    private SecurityAuditLog.SecuritySeverity mapToAuditSeverity(AlertSeverity alertSeverity) {
        return switch (alertSeverity) {
            case CRITICAL -> SecurityAuditLog.SecuritySeverity.CRITICAL;
            case HIGH -> SecurityAuditLog.SecuritySeverity.HIGH;
            case MEDIUM -> SecurityAuditLog.SecuritySeverity.MEDIUM;
            case LOW -> SecurityAuditLog.SecuritySeverity.LOW;
            default -> SecurityAuditLog.SecuritySeverity.INFO;
        };
    }
    
    // Supporting enums and classes
    
    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum AlertStatus {
        NEW, ACKNOWLEDGED, INVESTIGATING, ESCALATED, RESOLVED, CLOSED
    }
    
    public enum AlertResolution {
        TRUE_POSITIVE, FALSE_POSITIVE, INCONCLUSIVE, BLOCKED, ALLOWED
    }
    
    public enum FraudAlertType {
        TRANSACTION_FRAUD, ACCOUNT_TAKEOVER, IDENTITY_FRAUD, CARD_FRAUD, MONEY_LAUNDERING, OTHER
    }
    
    @lombok.Data
    @lombok.Builder
    public static class FraudAlert {
        private String alertId;
        private String transactionId;
        private String userId;
        private double fraudProbability;
        private double confidence;
        private AlertSeverity severity;
        private MLPredictionResult.RiskLevel riskLevel;
        private FraudAlertType alertType;
        private List<String> riskFactors;
        private BigDecimal amount;
        private String currency;
        private Map<String, Object> merchantInfo;
        private Map<String, Object> deviceInfo;
        private Map<String, Object> locationInfo;
        private LocalDateTime timestamp;
        private AlertStatus status;
        private List<String> recommendedActions;
        private Map<String, Object> metadata;
        private String acknowledgedBy;
        private LocalDateTime acknowledgedAt;
        private String investigatorId;
        private LocalDateTime investigationStartTime;
        private AlertInvestigationResult investigationResult;
        private AlertResolution resolution;
        private String resolvedBy;
        private LocalDateTime resolvedAt;
        private Long resolutionTimeMinutes;
        private LocalDateTime escalatedAt;
        private List<String> notes;
        
        public void addNote(String note) {
            if (notes == null) {
                notes = new ArrayList<>();
            }
            notes.add(note);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class AlertTemplate {
        private AlertSeverity severity;
        private List<String> notificationChannels;
        private List<String> recipientGroups;
        private int escalationTimeMinutes;
        private boolean autoBlock;
        private boolean requiresAcknowledgment;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlertInvestigationResult {
        private String alertId;
        private String investigatorId;
        private Map<String, Object> evidence;
        private List<String> patterns;
        private List<FraudAlert> relatedAlerts;
        private double finalRiskScore;
        private String recommendation;
        private LocalDateTime timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AlertsSummary {
        private int totalActive;
        private Map<AlertSeverity, Long> bySeverity;
        private Map<AlertStatus, Long> byStatus;
        private long criticalCount;
        private long awaitingAction;
        private double averageResolutionTime;
        private LocalDateTime timestamp;
    }
    
    public static class FraudAlertException extends RuntimeException {
        public FraudAlertException(String message) {
            super(message);
        }
        
        public FraudAlertException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}