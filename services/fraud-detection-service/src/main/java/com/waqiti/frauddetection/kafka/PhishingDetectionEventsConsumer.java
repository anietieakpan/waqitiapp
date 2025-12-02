package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.PhishingDetectionEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.PhishingAttempt;
import com.waqiti.frauddetection.repository.PhishingAttemptRepository;
import com.waqiti.frauddetection.service.PhishingDetectionService;
import com.waqiti.frauddetection.metrics.PhishingMetricsService;
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
public class PhishingDetectionEventsConsumer {
    
    private final PhishingAttemptRepository phishingRepository;
    private final PhishingDetectionService phishingService;
    private final PhishingMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"phishing-detection-events", "email-phishing-events", "sms-phishing-events"},
        groupId = "fraud-phishing-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handlePhishingDetectionEvent(
            @Payload PhishingDetectionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("phishing-%s-p%d-o%d", event.getTargetId(), partition, offset);
        
        log.warn("Processing phishing event: targetId={}, type={}, confidence={}", 
            event.getTargetId(), event.getPhishingType(), event.getConfidenceScore());
        
        try {
            PhishingAttempt attempt = PhishingAttempt.builder()
                .id(UUID.randomUUID().toString())
                .targetId(event.getTargetId())
                .phishingType(event.getPhishingType())
                .channel(event.getChannel())
                .sourceIdentifier(event.getSourceIdentifier())
                .confidenceScore(event.getConfidenceScore())
                .indicators(event.getPhishingIndicators())
                .detectedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            phishingRepository.save(attempt);
            
            if (event.getConfidenceScore() > 0.8) {
                phishingService.blockSource(event.getSourceIdentifier(), event.getChannel());
                
                FraudAlertEvent alert = FraudAlertEvent.builder()
                    .alertId(UUID.randomUUID())
                    .userId(event.getTargetId())
                    .alertType("PHISHING_ATTEMPT")
                    .severity("HIGH")
                    .riskScore(event.getConfidenceScore() * 100)
                    .riskFactors(event.getPhishingIndicators())
                    .timestamp(Instant.now())
                    .correlationId(correlationId)
                    .build();
                
                kafkaTemplate.send("fraud-alert-events", alert);
                
                notificationService.sendNotification(
                    event.getTargetId(),
                    "Phishing Attempt Blocked",
                    "A phishing attempt targeting your account was detected and blocked.",
                    correlationId
                );
            }
            
            metricsService.recordPhishingAttempt(event.getPhishingType(), event.getConfidenceScore());
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process phishing event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}