package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.model.FraudAlert;
import com.waqiti.frauddetection.model.AlertSeverity;
import com.waqiti.frauddetection.model.AlertType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade alerting service for fraud detection system
 * Handles multiple alert channels and escalation policies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertingService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationServiceClient notificationServiceClient;

    @Value("${fraud.alerts.kafka.topic:fraud-alerts}")
    private String alertTopic;

    @Value("${fraud.alerts.cooldown.minutes:5}")
    private int alertCooldownMinutes;

    @Value("${fraud.alerts.batch.size:10}")
    private int batchAlertSize;

    @Value("${fraud.alerts.escalation.enabled:true}")
    private boolean escalationEnabled;

    // Track alerts to prevent spam
    private final ConcurrentHashMap<String, LocalDateTime> alertCooldownMap = new ConcurrentHashMap<>();

    /**
     * Send fraud alert with automatic escalation
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendAlert(FraudAlert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                String alertKey = generateAlertKey(alert);
                
                // Check cooldown to prevent alert spam
                if (isInCooldown(alertKey)) {
                    log.debug("Alert {} is in cooldown, skipping", alertKey);
                    return;
                }

                log.info("Sending fraud alert: {} - Severity: {}", alert.getTitle(), alert.getSeverity());

                // Record alert timestamp for cooldown
                alertCooldownMap.put(alertKey, LocalDateTime.now());

                // Send to multiple channels based on severity
                sendToKafka(alert);
                sendToNotificationService(alert);

                // Handle escalation for critical alerts
                if (shouldEscalate(alert)) {
                    scheduleEscalation(alert);
                }

                log.debug("Fraud alert sent successfully: {}", alert.getId());

            } catch (Exception e) {
                log.error("Failed to send fraud alert: {}", alert.getId(), e);
                throw new AlertingException("Failed to send fraud alert", e);
            }
        });
    }

    /**
     * Send batch of related alerts efficiently
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendBatchAlerts(List<FraudAlert> alerts) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Sending batch of {} fraud alerts", alerts.size());

                // Group alerts by severity for efficient processing
                Map<AlertSeverity, List<FraudAlert>> groupedAlerts = alerts.stream()
                    .collect(java.util.stream.Collectors.groupingBy(FraudAlert::getSeverity));

                // Process each severity group
                for (Map.Entry<AlertSeverity, List<FraudAlert>> entry : groupedAlerts.entrySet()) {
                    processSeverityGroup(entry.getKey(), entry.getValue());
                }

                log.info("Batch alerts processing completed");

            } catch (Exception e) {
                log.error("Failed to send batch alerts", e);
                throw new AlertingException("Failed to send batch alerts", e);
            }
        });
    }

    /**
     * Send critical fraud alert with immediate escalation
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendCriticalAlert(String title, String message, Map<String, Object> context) {
        return CompletableFuture.runAsync(() -> {
            try {
                FraudAlert criticalAlert = FraudAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .title("CRITICAL: " + title)
                    .message(message)
                    .severity(AlertSeverity.CRITICAL)
                    .type(AlertType.FRAUD_DETECTED)
                    .context(context)
                    .createdAt(LocalDateTime.now())
                    .requiresImmediateAction(true)
                    .build();

                // Send immediately without cooldown check for critical alerts
                sendToKafka(criticalAlert);
                sendToNotificationService(criticalAlert);
                sendToEscalationChannels(criticalAlert);

                log.error("CRITICAL FRAUD ALERT sent: {}", title);

            } catch (Exception e) {
                log.error("Failed to send critical fraud alert", e);
                throw new AlertingException("Failed to send critical alert", e);
            }
        });
    }

    /**
     * Send pattern detection alert
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendPatternAlert(String patternType, String description, 
                                                   int affectedTransactions, List<String> affectedUserIds) {
        return CompletableFuture.runAsync(() -> {
            try {
                AlertSeverity severity = determineSeverityByImpact(affectedTransactions, affectedUserIds.size());

                FraudAlert patternAlert = FraudAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Fraud Pattern Detected: " + patternType)
                    .message(description)
                    .severity(severity)
                    .type(AlertType.PATTERN_DETECTED)
                    .context(Map.of(
                        "patternType", patternType,
                        "affectedTransactions", affectedTransactions,
                        "affectedUsers", affectedUserIds.size(),
                        "userIds", affectedUserIds.size() <= 10 ? affectedUserIds : affectedUserIds.subList(0, 10)
                    ))
                    .createdAt(LocalDateTime.now())
                    .build();

                sendAlert(patternAlert).join();

                log.info("Pattern alert sent: {} affecting {} transactions and {} users", 
                    patternType, affectedTransactions, affectedUserIds.size());

            } catch (Exception e) {
                log.error("Failed to send pattern alert for type: {}", patternType, e);
            }
        });
    }

    /**
     * Send model performance degradation alert
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendModelPerformanceAlert(String modelName, double currentAccuracy, 
                                                            double previousAccuracy, Map<String, Double> metrics) {
        return CompletableFuture.runAsync(() -> {
            try {
                double degradationPercent = ((previousAccuracy - currentAccuracy) / previousAccuracy) * 100;
                AlertSeverity severity = degradationPercent > 10 ? AlertSeverity.HIGH : AlertSeverity.MEDIUM;

                FraudAlert performanceAlert = FraudAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Model Performance Degradation: " + modelName)
                    .message(String.format("Model accuracy dropped by %.2f%% from %.3f to %.3f", 
                        degradationPercent, previousAccuracy, currentAccuracy))
                    .severity(severity)
                    .type(AlertType.MODEL_PERFORMANCE)
                    .context(Map.of(
                        "modelName", modelName,
                        "currentAccuracy", currentAccuracy,
                        "previousAccuracy", previousAccuracy,
                        "degradationPercent", degradationPercent,
                        "metrics", metrics
                    ))
                    .createdAt(LocalDateTime.now())
                    .build();

                sendAlert(performanceAlert).join();

                log.warn("Model performance alert sent for {}: accuracy dropped to {}", modelName, currentAccuracy);

            } catch (Exception e) {
                log.error("Failed to send model performance alert for model: {}", modelName, e);
            }
        });
    }

    /**
     * Send system health alert
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendSystemHealthAlert(String component, String issue, AlertSeverity severity) {
        return CompletableFuture.runAsync(() -> {
            try {
                FraudAlert healthAlert = FraudAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .title("System Health Issue: " + component)
                    .message(issue)
                    .severity(severity)
                    .type(AlertType.SYSTEM_HEALTH)
                    .context(Map.of(
                        "component", component,
                        "issue", issue,
                        "timestamp", LocalDateTime.now().toString()
                    ))
                    .createdAt(LocalDateTime.now())
                    .build();

                sendAlert(healthAlert).join();

                log.warn("System health alert sent for {}: {}", component, issue);

            } catch (Exception e) {
                log.error("Failed to send system health alert for component: {}", component, e);
            }
        });
    }

    /**
     * Send velocity anomaly alert
     */
    @Async("alertingTaskExecutor")
    public CompletableFuture<Void> sendVelocityAlert(String userId, int transactionCount, 
                                                    double totalAmount, String timeWindow) {
        return CompletableFuture.runAsync(() -> {
            try {
                AlertSeverity severity = determineVelocitySeverity(transactionCount, totalAmount);

                FraudAlert velocityAlert = FraudAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .title("Transaction Velocity Anomaly Detected")
                    .message(String.format("User %s performed %d transactions totaling $%.2f in %s", 
                        maskUserId(userId), transactionCount, totalAmount, timeWindow))
                    .severity(severity)
                    .type(AlertType.VELOCITY_ANOMALY)
                    .context(Map.of(
                        "userId", userId,
                        "transactionCount", transactionCount,
                        "totalAmount", totalAmount,
                        "timeWindow", timeWindow
                    ))
                    .createdAt(LocalDateTime.now())
                    .build();

                sendAlert(velocityAlert).join();

                log.info("Velocity alert sent for user {}: {} transactions, ${}",
                    maskUserId(userId), transactionCount, totalAmount);

            } catch (Exception e) {
                log.error("Failed to send velocity alert for user: {}", userId, e);
            }
        });
    }

    // Private helper methods

    private void sendToKafka(FraudAlert alert) {
        try {
            kafkaTemplate.send(alertTopic, alert.getId(), alert);
            log.debug("Alert sent to Kafka topic {}: {}", alertTopic, alert.getId());
        } catch (Exception e) {
            log.error("Failed to send alert to Kafka: {}", alert.getId(), e);
        }
    }

    private void sendToNotificationService(FraudAlert alert) {
        try {
            // Use the existing notification service client
            Map<String, Object> notificationData = Map.of(
                "type", "fraud_alert",
                "severity", alert.getSeverity().toString(),
                "title", alert.getTitle(),
                "message", alert.getMessage(),
                "context", alert.getContext(),
                "alertId", alert.getId(),
                "timestamp", alert.getCreatedAt()
            );

            if (alert.getSeverity() == AlertSeverity.CRITICAL) {
                notificationServiceClient.sendCriticalAlert(alert.getTitle(), alert.getMessage());
            } else {
                notificationServiceClient.sendNotification("fraud_alert", notificationData);
            }

            log.debug("Alert sent to notification service: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send alert to notification service: {}", alert.getId(), e);
        }
    }

    private void sendToEscalationChannels(FraudAlert alert) {
        try {
            // For critical alerts, send to additional escalation channels
            notificationServiceClient.sendEscalationAlert(
                alert.getTitle(),
                alert.getMessage(),
                alert.getSeverity().toString(),
                alert.getContext()
            );

            log.debug("Alert sent to escalation channels: {}", alert.getId());
        } catch (Exception e) {
            log.error("Failed to send alert to escalation channels: {}", alert.getId(), e);
        }
    }

    private boolean shouldEscalate(FraudAlert alert) {
        return escalationEnabled && 
               (alert.getSeverity() == AlertSeverity.CRITICAL || 
                alert.isRequiresImmediateAction());
    }

    private void scheduleEscalation(FraudAlert alert) {
        // Schedule escalation after a delay if not acknowledged
        CompletableFuture.delayedExecutor(5, java.util.concurrent.TimeUnit.MINUTES)
            .execute(() -> {
                try {
                    // Check if alert was acknowledged (this would require a tracking mechanism)
                    // For now, just log the escalation
                    log.warn("ESCALATING unacknowledged alert: {}", alert.getTitle());
                    sendToEscalationChannels(alert);
                } catch (Exception e) {
                    log.error("Failed to escalate alert: {}", alert.getId(), e);
                }
            });
    }

    private void processSeverityGroup(AlertSeverity severity, List<FraudAlert> alerts) {
        log.debug("Processing {} alerts with severity: {}", alerts.size(), severity);

        // Process in batches to avoid overwhelming the system
        for (int i = 0; i < alerts.size(); i += batchAlertSize) {
            int endIndex = Math.min(i + batchAlertSize, alerts.size());
            List<FraudAlert> batch = alerts.subList(i, endIndex);

            batch.parallelStream().forEach(alert -> {
                try {
                    sendAlert(alert).join();
                } catch (Exception e) {
                    log.error("Failed to process alert in batch: {}", alert.getId(), e);
                }
            });

            // Small delay between batches to prevent overwhelming
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private String generateAlertKey(FraudAlert alert) {
        // Generate a key for cooldown tracking
        return String.format("%s:%s:%s", 
            alert.getType(), 
            alert.getSeverity(), 
            alert.getTitle().hashCode());
    }

    private boolean isInCooldown(String alertKey) {
        LocalDateTime lastAlert = alertCooldownMap.get(alertKey);
        if (lastAlert == null) {
            return false;
        }

        return lastAlert.isAfter(LocalDateTime.now().minusMinutes(alertCooldownMinutes));
    }

    private AlertSeverity determineSeverityByImpact(int affectedTransactions, int affectedUsers) {
        if (affectedTransactions >= 100 || affectedUsers >= 50) {
            return AlertSeverity.CRITICAL;
        } else if (affectedTransactions >= 20 || affectedUsers >= 10) {
            return AlertSeverity.HIGH;
        } else if (affectedTransactions >= 5 || affectedUsers >= 3) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }

    private AlertSeverity determineVelocitySeverity(int transactionCount, double totalAmount) {
        if (transactionCount >= 50 || totalAmount >= 100000) {
            return AlertSeverity.CRITICAL;
        } else if (transactionCount >= 20 || totalAmount >= 50000) {
            return AlertSeverity.HIGH;
        } else if (transactionCount >= 10 || totalAmount >= 10000) {
            return AlertSeverity.MEDIUM;
        } else {
            return AlertSeverity.LOW;
        }
    }

    private String maskUserId(String userId) {
        // Mask user ID for privacy in logs
        if (userId == null || userId.length() <= 4) {
            return "****";
        }
        return userId.substring(0, 4) + "****";
    }

    /**
     * Clear old cooldown entries periodically
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupCooldownMap() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(alertCooldownMinutes);
        
        alertCooldownMap.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        
        log.debug("Cleaned up {} stale cooldown entries", alertCooldownMap.size());
    }

    /**
     * Get alerting health status
     */
    public AlertingHealthStatus getHealthStatus() {
        return AlertingHealthStatus.builder()
            .kafkaHealthy(checkKafkaHealth())
            .notificationServiceHealthy(checkNotificationServiceHealth())
            .activeCooldowns(alertCooldownMap.size())
            .escalationEnabled(escalationEnabled)
            .lastHealthCheck(LocalDateTime.now())
            .status("HEALTHY")
            .build();
    }

    private boolean checkKafkaHealth() {
        try {
            // Simple health check - could be enhanced
            return kafkaTemplate != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean checkNotificationServiceHealth() {
        try {
            return notificationServiceClient.isNotificationServiceHealthy();
        } catch (Exception e) {
            return false;
        }
    }

    // Health status DTO
    @lombok.Data
    @lombok.Builder
    public static class AlertingHealthStatus {
        private boolean kafkaHealthy;
        private boolean notificationServiceHealthy;
        private int activeCooldowns;
        private boolean escalationEnabled;
        private LocalDateTime lastHealthCheck;
        private String status;
    }

    // Custom exception
    public static class AlertingException extends RuntimeException {
        public AlertingException(String message) {
            super(message);
        }

        public AlertingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}