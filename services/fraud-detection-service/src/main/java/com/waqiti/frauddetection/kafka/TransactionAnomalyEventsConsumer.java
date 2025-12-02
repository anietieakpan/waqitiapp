package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.TransactionAnomalyEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.TransactionAnomaly;
import com.waqiti.frauddetection.repository.TransactionAnomalyRepository;
import com.waqiti.frauddetection.service.AnomalyDetectionService;
import com.waqiti.frauddetection.service.TransactionBlockingService;
import com.waqiti.frauddetection.metrics.AnomalyMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionAnomalyEventsConsumer {
    
    private final TransactionAnomalyRepository anomalyRepository;
    private final AnomalyDetectionService anomalyDetectionService;
    private final TransactionBlockingService blockingService;
    private final AnomalyMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"transaction-anomaly-events", "anomaly-detection-events", "ml-anomaly-events"},
        groupId = "fraud-anomaly-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleTransactionAnomalyEvent(
            @Payload TransactionAnomalyEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("anomaly-%s-p%d-o%d", 
            event.getTransactionId(), partition, offset);
        
        log.warn("Processing transaction anomaly: txId={}, anomalyType={}, score={}", 
            event.getTransactionId(), event.getAnomalyType(), event.getAnomalyScore());
        
        try {
            TransactionAnomaly anomaly = TransactionAnomaly.builder()
                .id(UUID.randomUUID().toString())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .anomalyType(event.getAnomalyType())
                .anomalyScore(event.getAnomalyScore())
                .features(event.getAnomalyFeatures())
                .expectedBehavior(event.getExpectedBehavior())
                .actualBehavior(event.getActualBehavior())
                .detectionMethod(event.getDetectionMethod())
                .detectedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            anomalyRepository.save(anomaly);
            
            String action = determineAction(event.getAnomalyScore(), event.getAnomalyType());
            anomaly.setAction(action);
            anomalyRepository.save(anomaly);
            
            switch (action) {
                case "BLOCK":
                    blockingService.blockTransaction(event.getTransactionId(), 
                        "High anomaly score: " + event.getAnomalyScore());
                    break;
                case "CHALLENGE":
                    blockingService.challengeTransaction(event.getTransactionId());
                    break;
                case "REVIEW":
                    anomalyDetectionService.flagForReview(event.getTransactionId());
                    break;
            }
            
            if (event.getAnomalyScore() > 0.8) {
                FraudAlertEvent alert = FraudAlertEvent.builder()
                    .alertId(UUID.randomUUID())
                    .userId(event.getUserId())
                    .transactionId(event.getTransactionId())
                    .alertType("TRANSACTION_ANOMALY")
                    .severity(event.getAnomalyScore() > 0.9 ? "CRITICAL" : "HIGH")
                    .riskScore(event.getAnomalyScore() * 100)
                    .riskFactors(List.of(
                        String.format("Anomaly type: %s", event.getAnomalyType()),
                        String.format("Anomaly score: %.2f", event.getAnomalyScore()),
                        String.format("Detection method: %s", event.getDetectionMethod())
                    ))
                    .timestamp(Instant.now())
                    .correlationId(correlationId)
                    .build();
                
                kafkaTemplate.send("fraud-alert-events", alert);
                
                if ("BLOCK".equals(action)) {
                    notificationService.sendNotification(
                        event.getUserId(),
                        "Transaction Blocked",
                        "A suspicious transaction was blocked for your security.",
                        correlationId
                    );
                }
            }
            
            metricsService.recordAnomaly(event.getAnomalyType(), event.getAnomalyScore(), action);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process anomaly event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private String determineAction(double anomalyScore, String anomalyType) {
        if (anomalyScore >= 0.90) return "BLOCK";
        if (anomalyScore >= 0.75) return "CHALLENGE";
        if (anomalyScore >= 0.60) return "REVIEW";
        return "MONITOR";
    }
}