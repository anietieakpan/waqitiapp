package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.BotDetectionEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.BotActivity;
import com.waqiti.frauddetection.repository.BotActivityRepository;
import com.waqiti.frauddetection.service.BotDetectionService;
import com.waqiti.frauddetection.service.CaptchaService;
import com.waqiti.frauddetection.metrics.BotMetricsService;
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
public class BotDetectionEventsConsumer {
    
    private final BotActivityRepository botActivityRepository;
    private final BotDetectionService botDetectionService;
    private final CaptchaService captchaService;
    private final BotMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"bot-detection-events", "automated-activity-events", "captcha-events"},
        groupId = "fraud-bot-service-group",
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
    public void handleBotDetectionEvent(
            @Payload BotDetectionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("bot-%s-p%d-o%d", event.getSessionId(), partition, offset);
        
        log.warn("Processing bot detection event: sessionId={}, botScore={}, indicators={}", 
            event.getSessionId(), event.getBotScore(), event.getBotIndicators());
        
        try {
            BotActivity activity = BotActivity.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(event.getSessionId())
                .userId(event.getUserId())
                .botScore(event.getBotScore())
                .botIndicators(event.getBotIndicators())
                .detectionMethod(event.getDetectionMethod())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .detectedAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            
            botActivityRepository.save(activity);
            
            if (event.getBotScore() > 0.85) {
                botDetectionService.blockSession(event.getSessionId());
                botDetectionService.blockIpAddress(event.getIpAddress(), 1440);
                
                FraudAlertEvent alert = FraudAlertEvent.builder()
                    .alertId(UUID.randomUUID())
                    .userId(event.getUserId())
                    .alertType("BOT_ACTIVITY_DETECTED")
                    .severity("HIGH")
                    .riskScore(event.getBotScore() * 100)
                    .riskFactors(event.getBotIndicators())
                    .timestamp(Instant.now())
                    .correlationId(correlationId)
                    .build();
                
                kafkaTemplate.send("fraud-alert-events", alert);
                
                notificationService.sendSecurityAlert(
                    "Bot Activity Detected",
                    String.format("Bot activity detected: score=%.2f, session=%s",
                        event.getBotScore(), event.getSessionId()),
                    NotificationService.Priority.HIGH
                );
                
            } else if (event.getBotScore() > 0.60) {
                captchaService.requireCaptcha(event.getSessionId());
            }
            
            metricsService.recordBotDetection(event.getBotScore(), event.getDetectionMethod());
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process bot detection event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}