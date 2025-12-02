package com.waqiti.account.kafka;

import com.waqiti.common.events.AccountActivatedEvent;
import com.waqiti.account.domain.AccountActivation;
import com.waqiti.account.repository.AccountActivationRepository;
import com.waqiti.account.service.AccountActivationService;
import com.waqiti.account.service.AccountFeatureService;
import com.waqiti.account.metrics.AccountMetricsService;
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
public class AccountActivatedEventsConsumer {
    
    private final AccountActivationRepository activationRepository;
    private final AccountActivationService activationService;
    private final AccountFeatureService featureService;
    private final AccountMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"account-activated-events", "account-activation-completed"},
        groupId = "account-activated-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "5"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAccountActivatedEvent(
            @Payload AccountActivatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("acct-activate-%s-p%d-o%d", 
            event.getAccountId(), partition, offset);
        
        log.info("Processing account activated event: accountId={}, userId={}, tier={}",
            event.getAccountId(), event.getUserId(), event.getAccountTier());
        
        try {
            AccountActivation activation = AccountActivation.builder()
                .accountId(event.getAccountId())
                .userId(event.getUserId())
                .accountTier(event.getAccountTier())
                .activationMethod(event.getActivationMethod())
                .activatedAt(LocalDateTime.now())
                .verificationLevel(event.getVerificationLevel())
                .correlationId(correlationId)
                .build();
            activationRepository.save(activation);
            
            activationService.enableAccountFeatures(event.getAccountId(), event.getAccountTier());
            featureService.applyTierLimits(event.getAccountId(), event.getAccountTier());
            
            notificationService.sendNotification(event.getUserId(), "Account Activated",
                String.format("Your %s account has been activated successfully!", event.getAccountTier()),
                correlationId);
            
            metricsService.recordAccountActivated(event.getAccountTier());
            
            auditService.logAccountEvent("ACCOUNT_ACTIVATED", event.getAccountId(),
                Map.of("userId", event.getUserId(), "tier", event.getAccountTier(),
                    "verificationLevel", event.getVerificationLevel(), "correlationId", correlationId,
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
            log.info("Account activated successfully: accountId={}, tier={}", 
                event.getAccountId(), event.getAccountTier());
            
        } catch (Exception e) {
            log.error("Failed to process account activated event: {}", e.getMessage(), e);
            kafkaTemplate.send("account-activated-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
}