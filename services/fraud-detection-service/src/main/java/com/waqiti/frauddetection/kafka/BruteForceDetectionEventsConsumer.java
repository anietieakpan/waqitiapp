package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.BruteForceDetectionEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.BruteForceAttempt;
import com.waqiti.frauddetection.repository.BruteForceAttemptRepository;
import com.waqiti.frauddetection.service.BruteForceProtectionService;
import com.waqiti.frauddetection.metrics.SecurityMetricsService;
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
public class BruteForceDetectionEventsConsumer {
    
    private final BruteForceAttemptRepository attemptRepository;
    private final BruteForceProtectionService protectionService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"brute-force-detection-events", "login-attack-events", "password-attack-events"},
        groupId = "fraud-bruteforce-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleBruteForceDetectionEvent(
            @Payload BruteForceDetectionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("brute-%s-p%d-o%d", event.getTargetId(), partition, offset);
        
        log.error("Processing brute force event: targetId={}, attempts={}, window={}sec", 
            event.getTargetId(), event.getAttemptCount(), event.getTimeWindowSeconds());
        
        try {
            BruteForceAttempt attempt = BruteForceAttempt.builder()
                .id(UUID.randomUUID().toString())
                .targetId(event.getTargetId())
                .targetType(event.getTargetType())
                .attackType(event.getAttackType())
                .attemptCount(event.getAttemptCount())
                .timeWindowSeconds(event.getTimeWindowSeconds())
                .sourceIpAddress(event.getSourceIpAddress())
                .detectedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            attemptRepository.save(attempt);
            
            if (event.getAttemptCount() >= 5) {
                protectionService.blockIpAddress(event.getSourceIpAddress(), 1440);
                protectionService.lockAccount(event.getTargetId(), 30);
                
                FraudAlertEvent alert = FraudAlertEvent.builder()
                    .alertId(UUID.randomUUID())
                    .userId(event.getTargetId())
                    .alertType("BRUTE_FORCE_ATTACK")
                    .severity("CRITICAL")
                    .riskScore(95.0)
                    .riskFactors(List.of(
                        String.format("%d attempts in %d seconds", 
                            event.getAttemptCount(), event.getTimeWindowSeconds()),
                        String.format("Source IP: %s", event.getSourceIpAddress())
                    ))
                    .timestamp(Instant.now())
                    .correlationId(correlationId)
                    .build();
                
                kafkaTemplate.send("fraud-alert-events", alert);
                
                notificationService.sendSecurityAlert(
                    "Brute Force Attack Detected",
                    String.format("Brute force attack on account %s from IP %s",
                        event.getTargetId(), event.getSourceIpAddress()),
                    NotificationService.Priority.CRITICAL
                );
            }
            
            metricsService.recordBruteForceAttempt(event.getAttackType(), event.getAttemptCount());
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process brute force event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}