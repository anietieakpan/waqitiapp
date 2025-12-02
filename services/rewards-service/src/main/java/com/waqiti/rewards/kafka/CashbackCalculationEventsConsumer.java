package com.waqiti.rewards.kafka;

import com.waqiti.common.events.CashbackEvent;
import com.waqiti.rewards.domain.CashbackTransaction;
import com.waqiti.rewards.repository.CashbackTransactionRepository;
import com.waqiti.rewards.service.CashbackService;
import com.waqiti.rewards.service.RewardsWalletService;
import com.waqiti.rewards.metrics.RewardsMetricsService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class CashbackCalculationEventsConsumer {
    
    private final CashbackTransactionRepository cashbackRepository;
    private final CashbackService cashbackService;
    private final RewardsWalletService walletService;
    private final RewardsMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MINIMUM_CASHBACK_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAXIMUM_CASHBACK_PER_TRANSACTION = new BigDecimal("500.00");
    
    @KafkaListener(
        topics = {"cashback-calculation-events", "cashback-earned-events", "transaction-cashback-events"},
        groupId = "cashback-calculation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCashbackCalculationEvent(
            @Payload CashbackEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("cashback-%s-%s-p%d-o%d", 
            event.getUserId(), event.getTransactionId(), partition, offset);
        
        log.info("Processing cashback calculation: userId={}, transactionId={}, amount={}, rate={}%",
            event.getUserId(), event.getTransactionId(), event.getTransactionAmount(), event.getCashbackRate());
        
        try {
            switch (event.getEventType()) {
                case CASHBACK_CALCULATED:
                    processCashbackCalculation(event, correlationId);
                    break;
                    
                case CASHBACK_EARNED:
                    processCashbackEarned(event, correlationId);
                    break;
                    
                case CASHBACK_POSTED:
                    processCashbackPosted(event, correlationId);
                    break;
                    
                case CASHBACK_EXPIRED:
                    processCashbackExpiration(event, correlationId);
                    break;
                    
                case CASHBACK_REVERSED:
                    processCashbackReversal(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown cashback event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logRewardsEvent("CASHBACK_EVENT_PROCESSED", event.getUserId(),
                Map.of("eventType", event.getEventType(), "transactionId", event.getTransactionId(),
                    "cashbackAmount", event.getCashbackAmount(), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process cashback calculation event: {}", e.getMessage(), e);
            kafkaTemplate.send("cashback-calculation-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processCashbackCalculation(CashbackEvent event, String correlationId) {
        BigDecimal cashbackAmount = cashbackService.calculateCashback(
            event.getTransactionAmount(), 
            event.getCashbackRate(),
            event.getMerchantCategory()
        );
        
        if (cashbackAmount.compareTo(MINIMUM_CASHBACK_AMOUNT) < 0) {
            log.info("Cashback amount {} below minimum threshold for userId={}", 
                cashbackAmount, event.getUserId());
            return;
        }
        
        if (cashbackAmount.compareTo(MAXIMUM_CASHBACK_PER_TRANSACTION) > 0) {
            log.warn("Cashback amount {} exceeds maximum, capping at {} for userId={}", 
                cashbackAmount, MAXIMUM_CASHBACK_PER_TRANSACTION, event.getUserId());
            cashbackAmount = MAXIMUM_CASHBACK_PER_TRANSACTION;
        }
        
        CashbackTransaction cashback = CashbackTransaction.builder()
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .transactionAmount(event.getTransactionAmount())
            .cashbackRate(event.getCashbackRate())
            .cashbackAmount(cashbackAmount.setScale(2, RoundingMode.HALF_UP))
            .merchantCategory(event.getMerchantCategory())
            .merchantName(event.getMerchantName())
            .status("CALCULATED")
            .calculatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMonths(12))
            .correlationId(correlationId)
            .build();
        cashbackRepository.save(cashback);
        
        metricsService.recordCashbackCalculated(cashbackAmount, event.getMerchantCategory());
        
        kafkaTemplate.send("cashback-earned-events", Map.of(
            "userId", event.getUserId(),
            "transactionId", event.getTransactionId(),
            "cashbackAmount", cashbackAmount,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Cashback calculated: userId={}, amount={}, transactionId={}", 
            event.getUserId(), cashbackAmount, event.getTransactionId());
    }
    
    private void processCashbackEarned(CashbackEvent event, String correlationId) {
        CashbackTransaction cashback = cashbackRepository.findByTransactionId(event.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Cashback transaction not found"));
        
        cashback.setStatus("EARNED");
        cashback.setEarnedAt(LocalDateTime.now());
        cashbackRepository.save(cashback);
        
        walletService.creditCashback(event.getUserId(), event.getCashbackAmount());
        
        notificationService.sendNotification(event.getUserId(), "Cashback Earned!",
            String.format("You've earned $%.2f cashback on your recent purchase at %s!", 
                event.getCashbackAmount(), event.getMerchantName()),
            correlationId);
        
        metricsService.recordCashbackEarned(event.getCashbackAmount());
        
        log.info("Cashback earned: userId={}, amount={}, merchantName={}", 
            event.getUserId(), event.getCashbackAmount(), event.getMerchantName());
    }
    
    private void processCashbackPosted(CashbackEvent event, String correlationId) {
        CashbackTransaction cashback = cashbackRepository.findByTransactionId(event.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Cashback transaction not found"));
        
        cashback.setStatus("POSTED");
        cashback.setPostedAt(LocalDateTime.now());
        cashbackRepository.save(cashback);
        
        metricsService.recordCashbackPosted(event.getCashbackAmount());
        
        log.info("Cashback posted: userId={}, amount={}", event.getUserId(), event.getCashbackAmount());
    }
    
    private void processCashbackExpiration(CashbackEvent event, String correlationId) {
        CashbackTransaction cashback = cashbackRepository.findByTransactionId(event.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Cashback transaction not found"));
        
        if (cashback.getStatus().equals("EARNED") && !cashback.getStatus().equals("POSTED")) {
            cashback.setStatus("EXPIRED");
            cashback.setExpiredAt(LocalDateTime.now());
            cashbackRepository.save(cashback);
            
            walletService.debitExpiredCashback(event.getUserId(), event.getCashbackAmount());
            
            notificationService.sendNotification(event.getUserId(), "Cashback Expired",
                String.format("Your unclaimed cashback of $%.2f has expired.", event.getCashbackAmount()),
                correlationId);
            
            metricsService.recordCashbackExpired(event.getCashbackAmount());
            
            log.warn("Cashback expired: userId={}, amount={}", event.getUserId(), event.getCashbackAmount());
        }
    }
    
    private void processCashbackReversal(CashbackEvent event, String correlationId) {
        CashbackTransaction cashback = cashbackRepository.findByTransactionId(event.getTransactionId())
            .orElseThrow(() -> new RuntimeException("Cashback transaction not found"));
        
        cashback.setStatus("REVERSED");
        cashback.setReversedAt(LocalDateTime.now());
        cashback.setReversalReason(event.getReversalReason());
        cashbackRepository.save(cashback);
        
        walletService.debitCashback(event.getUserId(), event.getCashbackAmount(), "Cashback reversal");
        
        notificationService.sendNotification(event.getUserId(), "Cashback Reversed",
            String.format("Your cashback of $%.2f has been reversed due to: %s", 
                event.getCashbackAmount(), event.getReversalReason()),
            correlationId);
        
        metricsService.recordCashbackReversed(event.getCashbackAmount());
        
        log.warn("Cashback reversed: userId={}, amount={}, reason={}", 
            event.getUserId(), event.getCashbackAmount(), event.getReversalReason());
    }
}