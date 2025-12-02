package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.IPBlacklistEvent;
import com.waqiti.frauddetection.domain.BlacklistedIP;
import com.waqiti.frauddetection.repository.BlacklistedIPRepository;
import com.waqiti.frauddetection.service.IPBlacklistService;
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
public class IPBlacklistEventsConsumer {
    
    private final BlacklistedIPRepository blacklistRepository;
    private final IPBlacklistService blacklistService;
    private final SecurityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"ip-blacklist-events", "ip-reputation-events", "blocklist-updates"},
        groupId = "fraud-ipblacklist-service-group",
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
    public void handleIPBlacklistEvent(
            @Payload IPBlacklistEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("ipbl-%s-p%d-o%d", event.getIpAddress(), partition, offset);
        
        log.info("Processing IP blacklist event: ip={}, action={}, reason={}", 
            event.getIpAddress(), event.getAction(), event.getReason());
        
        try {
            switch (event.getAction()) {
                case "ADD":
                    processIPBlacklisted(event, correlationId);
                    break;
                case "REMOVE":
                    processIPWhitelisted(event, correlationId);
                    break;
                case "UPDATE":
                    processIPReputationUpdated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown IP blacklist action: {}", event.getAction());
                    break;
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process IP blacklist event: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
    
    private void processIPBlacklisted(IPBlacklistEvent event, String correlationId) {
        log.warn("Blacklisting IP: {} - Reason: {}", event.getIpAddress(), event.getReason());
        
        BlacklistedIP blacklistedIP = BlacklistedIP.builder()
            .id(UUID.randomUUID().toString())
            .ipAddress(event.getIpAddress())
            .reason(event.getReason())
            .severity(event.getSeverity())
            .source(event.getSource())
            .blacklistedAt(LocalDateTime.now())
            .blacklistedBy(event.getBlacklistedBy())
            .expiresAt(event.getExpiresAt())
            .correlationId(correlationId)
            .build();
        
        blacklistRepository.save(blacklistedIP);
        blacklistService.addToBlacklist(event.getIpAddress(), event.getExpiresAt());
        
        metricsService.recordIPBlacklisted(event.getReason());
    }
    
    private void processIPWhitelisted(IPBlacklistEvent event, String correlationId) {
        log.info("Removing IP from blacklist: {}", event.getIpAddress());
        
        blacklistRepository.findByIpAddress(event.getIpAddress())
            .ifPresent(ip -> {
                ip.setRemovedAt(LocalDateTime.now());
                ip.setRemovalReason(event.getRemovalReason());
                blacklistRepository.save(ip);
            });
        
        blacklistService.removeFromBlacklist(event.getIpAddress());
        metricsService.recordIPWhitelisted();
    }
    
    private void processIPReputationUpdated(IPBlacklistEvent event, String correlationId) {
        log.info("Updating IP reputation: {} - Score: {}", 
            event.getIpAddress(), event.getReputationScore());
        
        blacklistService.updateReputation(event.getIpAddress(), event.getReputationScore());
        metricsService.recordIPReputationUpdated(event.getReputationScore());
    }
}