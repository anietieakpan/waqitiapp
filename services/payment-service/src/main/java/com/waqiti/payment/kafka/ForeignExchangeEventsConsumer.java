package com.waqiti.payment.kafka;

import com.waqiti.common.events.ForeignExchangeEvent;
import com.waqiti.payment.domain.FXTransaction;
import com.waqiti.payment.domain.FXRateSnapshot;
import com.waqiti.payment.repository.FXTransactionRepository;
import com.waqiti.payment.repository.FXRateSnapshotRepository;
import com.waqiti.payment.service.FXRateService;
import com.waqiti.payment.service.FXExecutionService;
import com.waqiti.payment.metrics.FXMetricsService;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class ForeignExchangeEventsConsumer {
    
    private final FXTransactionRepository fxTransactionRepository;
    private final FXRateSnapshotRepository fxRateSnapshotRepository;
    private final FXRateService fxRateService;
    private final FXExecutionService fxExecutionService;
    private final FXMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"fx-rate-updates", "currency-exchange-events", "fx-transaction-events"},
        groupId = "payment-fx-service-group",
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
    public void handleForeignExchangeEvent(
            @Payload ForeignExchangeEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("fx-%s-%s-p%d-o%d", 
            event.getFromCurrency(), event.getToCurrency(), partition, offset);
        
        log.info("Processing FX event: type={}, pair={}/{}", 
            event.getEventType(), event.getFromCurrency(), event.getToCurrency());
        
        try {
            switch (event.getEventType()) {
                case RATE_UPDATED:
                    processRateUpdated(event, correlationId);
                    break;
                case FX_TRANSACTION_INITIATED:
                    processFXTransactionInitiated(event, correlationId);
                    break;
                case FX_RATE_LOCKED:
                    processFXRateLocked(event, correlationId);
                    break;
                case FX_TRANSACTION_EXECUTED:
                    processFXTransactionExecuted(event, correlationId);
                    break;
                case FX_TRANSACTION_FAILED:
                    processFXTransactionFailed(event, correlationId);
                    break;
                case RATE_ALERT_TRIGGERED:
                    processRateAlertTriggered(event, correlationId);
                    break;
                case SPREAD_INCREASED:
                    processSpreadIncreased(event, correlationId);
                    break;
                case LIQUIDITY_LOW:
                    processLiquidityLow(event, correlationId);
                    break;
                default:
                    log.warn("Unknown FX event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logFinancialEvent(
                "FX_EVENT_PROCESSED",
                correlationId,
                Map.of(
                    "eventType", event.getEventType(),
                    "currencyPair", event.getFromCurrency() + "/" + event.getToCurrency(),
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process FX event: {}", e.getMessage(), e);
            kafkaTemplate.send("fx-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processRateUpdated(ForeignExchangeEvent event, String correlationId) {
        log.debug("FX rate updated: {}/{} = {}", 
            event.getFromCurrency(), event.getToCurrency(), event.getExchangeRate());
        
        FXRateSnapshot snapshot = FXRateSnapshot.builder()
            .id(UUID.randomUUID().toString())
            .fromCurrency(event.getFromCurrency())
            .toCurrency(event.getToCurrency())
            .exchangeRate(event.getExchangeRate())
            .bidRate(event.getBidRate())
            .askRate(event.getAskRate())
            .spread(event.getSpread())
            .provider(event.getRateProvider())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        fxRateSnapshotRepository.save(snapshot);
        fxRateService.updateCachedRate(event.getFromCurrency(), event.getToCurrency(), event.getExchangeRate());
        
        metricsService.recordRateUpdate(
            event.getFromCurrency(),
            event.getToCurrency(),
            event.getExchangeRate()
        );
    }
    
    private void processFXTransactionInitiated(ForeignExchangeEvent event, String correlationId) {
        log.info("FX transaction initiated: txId={}, {}/{}, amount={}", 
            event.getTransactionId(), event.getFromCurrency(), event.getToCurrency(), event.getFromAmount());
        
        FXTransaction transaction = FXTransaction.builder()
            .id(event.getTransactionId())
            .userId(event.getUserId())
            .fromCurrency(event.getFromCurrency())
            .toCurrency(event.getToCurrency())
            .fromAmount(event.getFromAmount())
            .status("INITIATED")
            .initiatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        fxTransactionRepository.save(transaction);
        fxExecutionService.processTransaction(event.getTransactionId());
        
        metricsService.recordTransactionInitiated(event.getFromCurrency(), event.getToCurrency());
    }
    
    private void processFXRateLocked(ForeignExchangeEvent event, String correlationId) {
        log.info("FX rate locked: txId={}, rate={}, validUntil={}", 
            event.getTransactionId(), event.getLockedRate(), event.getRateValidUntil());
        
        FXTransaction transaction = fxTransactionRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setLockedRate(event.getLockedRate());
        transaction.setRateLockedAt(LocalDateTime.now());
        transaction.setRateValidUntil(event.getRateValidUntil());
        transaction.setToAmount(event.getFromAmount().multiply(event.getLockedRate()));
        fxTransactionRepository.save(transaction);
        
        metricsService.recordRateLocked();
    }
    
    private void processFXTransactionExecuted(ForeignExchangeEvent event, String correlationId) {
        log.info("FX transaction executed: txId={}, executedRate={}, toAmount={}", 
            event.getTransactionId(), event.getExecutedRate(), event.getToAmount());
        
        FXTransaction transaction = fxTransactionRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setStatus("EXECUTED");
        transaction.setExecutedAt(LocalDateTime.now());
        transaction.setExecutedRate(event.getExecutedRate());
        transaction.setToAmount(event.getToAmount());
        transaction.setFeeAmount(event.getFeeAmount());
        fxTransactionRepository.save(transaction);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Exchange Complete",
            String.format("Exchanged %.2f %s to %.2f %s at rate %.4f", 
                event.getFromAmount(), event.getFromCurrency(),
                event.getToAmount(), event.getToCurrency(),
                event.getExecutedRate()),
            correlationId
        );
        
        metricsService.recordTransactionExecuted(
            event.getFromCurrency(),
            event.getToCurrency(),
            event.getFromAmount(),
            event.getToAmount()
        );
    }
    
    private void processFXTransactionFailed(ForeignExchangeEvent event, String correlationId) {
        log.error("FX transaction failed: txId={}, reason={}", 
            event.getTransactionId(), event.getFailureReason());
        
        FXTransaction transaction = fxTransactionRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setStatus("FAILED");
        transaction.setFailedAt(LocalDateTime.now());
        transaction.setFailureReason(event.getFailureReason());
        fxTransactionRepository.save(transaction);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Currency Exchange Failed",
            String.format("Your currency exchange failed: %s", event.getFailureReason()),
            correlationId
        );
        
        metricsService.recordTransactionFailed(event.getFailureReason());
    }
    
    private void processRateAlertTriggered(ForeignExchangeEvent event, String correlationId) {
        log.info("FX rate alert triggered: {}/{}, targetRate={}, currentRate={}", 
            event.getFromCurrency(), event.getToCurrency(), 
            event.getTargetRate(), event.getExchangeRate());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Rate Alert",
            String.format("%s/%s reached your target rate of %.4f (current: %.4f)", 
                event.getFromCurrency(), event.getToCurrency(),
                event.getTargetRate(), event.getExchangeRate()),
            correlationId
        );
        
        metricsService.recordRateAlert(event.getFromCurrency(), event.getToCurrency());
    }
    
    private void processSpreadIncreased(ForeignExchangeEvent event, String correlationId) {
        log.warn("FX spread increased: {}/{}, spread={}", 
            event.getFromCurrency(), event.getToCurrency(), event.getSpread());
        
        metricsService.recordSpreadIncreased(
            event.getFromCurrency(),
            event.getToCurrency(),
            event.getSpread()
        );
    }
    
    private void processLiquidityLow(ForeignExchangeEvent event, String correlationId) {
        log.error("FX liquidity low: {}/{}", 
            event.getFromCurrency(), event.getToCurrency());
        
        fxRateService.sourceLiquidity(event.getFromCurrency(), event.getToCurrency());
        
        metricsService.recordLiquidityLow(event.getFromCurrency(), event.getToCurrency());
    }
}