package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.FraudAlert;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
import com.waqiti.frauddetection.service.FraudDetectionService;
import com.waqiti.frauddetection.service.RiskScoringService;
import com.waqiti.frauddetection.service.AlertTriageService;
import com.waqiti.frauddetection.metrics.FraudMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class FraudAlertsConsumer {
    
    private final FraudAlertRepository alertRepository;
    private final FraudDetectionService detectionService;
    private final RiskScoringService riskScoringService;
    private final AlertTriageService triageService;
    private final FraudMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int CRITICAL_RISK_THRESHOLD = 85;
    private static final int HIGH_RISK_THRESHOLD = 70;
    
    @KafkaListener(
        topics = {"fraud-alerts", "suspicious-activity-alerts", "transaction-fraud-alerts"},
        groupId = "fraud-alerts-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleFraudAlert(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fraud-alert-%s-p%d-o%d", 
            event.getAlertId(), partition, offset);
        
        log.warn("Processing fraud alert: id={}, accountId={}, alertType={}, riskScore={}",
            event.getAlertId(), event.getAccountId(), event.getAlertType(), event.getRiskScore());
        
        try {
            FraudAlert alert = FraudAlert.builder()
                .alertId(event.getAlertId())
                .accountId(event.getAccountId())
                .transactionId(event.getTransactionId())
                .alertType(event.getAlertType())
                .riskScore(event.getRiskScore())
                .description(event.getDescription())
                .detectedAt(LocalDateTime.now())
                .status("DETECTED")
                .severity(calculateSeverity(event.getRiskScore()))
                .correlationId(correlationId)
                .build();
            alertRepository.save(alert);
            
            int enrichedRiskScore = riskScoringService.calculateEnrichedRiskScore(
                event.getAccountId(),
                event.getTransactionId(),
                event.getAlertType()
            );
            
            alert.setEnrichedRiskScore(enrichedRiskScore);
            alertRepository.save(alert);
            
            if (enrichedRiskScore >= CRITICAL_RISK_THRESHOLD) {
                handleCriticalAlert(event, enrichedRiskScore, correlationId);
            } else if (enrichedRiskScore >= HIGH_RISK_THRESHOLD) {
                handleHighRiskAlert(event, enrichedRiskScore, correlationId);
            } else {
                handleMediumRiskAlert(event, enrichedRiskScore, correlationId);
            }
            
            triageService.triageAlert(event.getAlertId(), enrichedRiskScore);
            
            metricsService.recordFraudAlert(event.getAlertType(), enrichedRiskScore);
            
            auditService.logFraudEvent("FRAUD_ALERT_DETECTED", event.getAlertId(),
                Map.of("accountId", event.getAccountId(), "alertType", event.getAlertType(),
                    "riskScore", enrichedRiskScore, "transactionId", event.getTransactionId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process fraud alert: {}", e.getMessage(), e);
            kafkaTemplate.send("fraud-alerts-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void handleCriticalAlert(FraudAlertEvent event, int riskScore, String correlationId) {
        detectionService.immediatelyBlockTransaction(event.getTransactionId());
        detectionService.freezeAccount(event.getAccountId());
        
        kafkaTemplate.send("fraud-investigations", Map.of(
            "investigationId", UUID.randomUUID().toString(),
            "accountId", event.getAccountId(),
            "transactionId", event.getTransactionId(),
            "fraudType", event.getAlertType(),
            "fraudScore", riskScore,
            "status", "INVESTIGATION_OPENED",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification("FRAUD_TEAM", "CRITICAL Fraud Alert",
            String.format("Critical fraud detected (Score: %d). Account %s frozen. Transaction %s blocked.", 
                riskScore, event.getAccountId(), event.getTransactionId()),
            correlationId);
        
        notificationService.sendNotification(event.getAccountId(), "Account Security Alert",
            "Suspicious activity detected on your account. Your account has been temporarily secured. We'll contact you shortly.",
            correlationId);
        
        metricsService.recordCriticalFraudAlert(event.getAlertType());
        
        log.error("CRITICAL fraud alert: alertId={}, accountId={}, riskScore={}", 
            event.getAlertId(), event.getAccountId(), riskScore);
    }
    
    private void handleHighRiskAlert(FraudAlertEvent event, int riskScore, String correlationId) {
        detectionService.flagTransactionForReview(event.getTransactionId());
        detectionService.addAccountMonitoring(event.getAccountId());
        
        kafkaTemplate.send("fraud-review-queue", Map.of(
            "alertId", event.getAlertId(),
            "accountId", event.getAccountId(),
            "transactionId", event.getTransactionId(),
            "alertType", event.getAlertType(),
            "riskScore", riskScore,
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification("FRAUD_TEAM", "High Risk Fraud Alert",
            String.format("High risk activity detected (Score: %d) for account %s", 
                riskScore, event.getAccountId()),
            correlationId);
        
        metricsService.recordHighRiskAlert(event.getAlertType());
        
        log.warn("HIGH risk fraud alert: alertId={}, accountId={}, riskScore={}", 
            event.getAlertId(), event.getAccountId(), riskScore);
    }
    
    private void handleMediumRiskAlert(FraudAlertEvent event, int riskScore, String correlationId) {
        detectionService.enhanceMonitoring(event.getAccountId());
        
        kafkaTemplate.send("fraud-monitoring-queue", Map.of(
            "alertId", event.getAlertId(),
            "accountId", event.getAccountId(),
            "transactionId", event.getTransactionId(),
            "alertType", event.getAlertType(),
            "riskScore", riskScore,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordMediumRiskAlert(event.getAlertType());
        
        log.info("Medium risk fraud alert: alertId={}, accountId={}, riskScore={}", 
            event.getAlertId(), event.getAccountId(), riskScore);
    }
    
    private String calculateSeverity(int riskScore) {
        if (riskScore >= CRITICAL_RISK_THRESHOLD) return "CRITICAL";
        if (riskScore >= HIGH_RISK_THRESHOLD) return "HIGH";
        if (riskScore >= 50) return "MEDIUM";
        return "LOW";
    }
}