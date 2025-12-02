package com.waqiti.wallet.kafka;

import com.waqiti.common.events.MobileWalletTopUpEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.domain.TopUpTransaction;
import com.waqiti.wallet.repository.TopUpTransactionRepository;
import com.waqiti.wallet.service.WalletTopUpService;
import com.waqiti.wallet.service.ProviderIntegrationService;
import com.waqiti.wallet.metrics.WalletMetricsService;
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
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class MobileWalletTopUpEventsConsumer {
    
    private final TopUpTransactionRepository topUpRepository;
    private final WalletTopUpService topUpService;
    private final ProviderIntegrationService providerService;
    private final WalletMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"mobile-wallet-topup-events", "airtime-topup-events", "data-bundle-purchase-events"},
        groupId = "wallet-topup-service-group",
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
    public void handleMobileWalletTopUpEvent(
            @Payload MobileWalletTopUpEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("topup-%s-p%d-o%d", 
            event.getTransactionId(), partition, offset);
        
        log.info("Processing mobile wallet top-up event: txId={}, type={}, provider={}", 
            event.getTransactionId(), event.getEventType(), event.getProvider());
        
        try {
            switch (event.getEventType()) {
                case TOP_UP_INITIATED:
                    processTopUpInitiated(event, correlationId);
                    break;
                case TOP_UP_PROCESSING:
                    processTopUpProcessing(event, correlationId);
                    break;
                case TOP_UP_COMPLETED:
                    processTopUpCompleted(event, correlationId);
                    break;
                case TOP_UP_FAILED:
                    processTopUpFailed(event, correlationId);
                    break;
                case PROVIDER_CONFIRMATION_RECEIVED:
                    processProviderConfirmationReceived(event, correlationId);
                    break;
                case REFUND_INITIATED:
                    processRefundInitiated(event, correlationId);
                    break;
                default:
                    log.warn("Unknown mobile wallet top-up event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logWalletEvent(
                "TOPUP_EVENT_PROCESSED",
                event.getTransactionId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "provider", event.getProvider(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process top-up event: txId={}, partition={}, offset={}, error={}",
                    event.getTransactionId(), partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, "mobile-wallet-topup-events", partition, offset, e)
                .thenAccept(result -> log.info("Mobile wallet top-up event sent to DLQ: txId={}, destination={}, category={}",
                        event.getTransactionId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for mobile wallet top-up - MESSAGE MAY BE LOST! " +
                            "txId={}, partition={}, offset={}, error={}",
                            event.getTransactionId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Mobile wallet top-up event processing failed", e);
        }
    }
    
    private void processTopUpInitiated(MobileWalletTopUpEvent event, String correlationId) {
        log.info("Top-up initiated: txId={}, provider={}, phoneNumber={}, amount={}", 
            event.getTransactionId(), event.getProvider(), 
            maskPhoneNumber(event.getPhoneNumber()), event.getAmount());
        
        TopUpTransaction transaction = TopUpTransaction.builder()
            .id(event.getTransactionId())
            .userId(event.getUserId())
            .provider(event.getProvider())
            .phoneNumber(event.getPhoneNumber())
            .topUpType(event.getTopUpType())
            .amount(event.getAmount())
            .status("INITIATED")
            .initiatedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        topUpRepository.save(transaction);
        topUpService.processTopUp(event.getTransactionId());
        
        metricsService.recordTopUpInitiated(event.getProvider(), event.getTopUpType());
    }
    
    private void processTopUpProcessing(MobileWalletTopUpEvent event, String correlationId) {
        log.info("Top-up processing: txId={}, provider={}", 
            event.getTransactionId(), event.getProvider());
        
        TopUpTransaction transaction = topUpRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setStatus("PROCESSING");
        transaction.setProcessingStartedAt(LocalDateTime.now());
        transaction.setProviderReferenceId(event.getProviderReferenceId());
        topUpRepository.save(transaction);
        
        metricsService.recordTopUpProcessing(event.getProvider());
    }
    
    private void processTopUpCompleted(MobileWalletTopUpEvent event, String correlationId) {
        log.info("Top-up completed: txId={}, provider={}, confirmationCode={}", 
            event.getTransactionId(), event.getProvider(), event.getConfirmationCode());
        
        TopUpTransaction transaction = topUpRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setStatus("COMPLETED");
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setConfirmationCode(event.getConfirmationCode());
        topUpRepository.save(transaction);
        
        String message = "AIRTIME".equals(event.getTopUpType()) 
            ? String.format("Airtime top-up of %.2f to %s completed successfully. Confirmation: %s", 
                event.getAmount(), maskPhoneNumber(event.getPhoneNumber()), event.getConfirmationCode())
            : String.format("Data bundle purchase of %.2f completed successfully. Confirmation: %s", 
                event.getAmount(), event.getConfirmationCode());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Top-Up Complete",
            message,
            correlationId
        );
        
        metricsService.recordTopUpCompleted(
            event.getProvider(),
            event.getTopUpType(),
            event.getAmount()
        );
    }
    
    private void processTopUpFailed(MobileWalletTopUpEvent event, String correlationId) {
        log.error("Top-up failed: txId={}, provider={}, reason={}", 
            event.getTransactionId(), event.getProvider(), event.getFailureReason());
        
        TopUpTransaction transaction = topUpRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setStatus("FAILED");
        transaction.setFailedAt(LocalDateTime.now());
        transaction.setFailureReason(event.getFailureReason());
        topUpRepository.save(transaction);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Top-Up Failed",
            String.format("Top-up failed: %s. Your account has not been charged.", 
                event.getFailureReason()),
            correlationId
        );
        
        metricsService.recordTopUpFailed(event.getProvider(), event.getFailureReason());
    }
    
    private void processProviderConfirmationReceived(MobileWalletTopUpEvent event, String correlationId) {
        log.info("Provider confirmation received: txId={}, providerRef={}", 
            event.getTransactionId(), event.getProviderReferenceId());
        
        TopUpTransaction transaction = topUpRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setProviderConfirmationReceived(true);
        transaction.setProviderConfirmationAt(LocalDateTime.now());
        topUpRepository.save(transaction);
        
        metricsService.recordProviderConfirmation(event.getProvider());
    }
    
    private void processRefundInitiated(MobileWalletTopUpEvent event, String correlationId) {
        log.info("Top-up refund initiated: txId={}, reason={}", 
            event.getTransactionId(), event.getRefundReason());
        
        TopUpTransaction transaction = topUpRepository.findById(event.getTransactionId())
            .orElseThrow();
        
        transaction.setRefundInitiated(true);
        transaction.setRefundInitiatedAt(LocalDateTime.now());
        transaction.setRefundReason(event.getRefundReason());
        topUpRepository.save(transaction);
        
        topUpService.processRefund(event.getTransactionId());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Refund Initiated",
            String.format("A refund of %.2f has been initiated for your failed top-up.", 
                event.getAmount()),
            correlationId
        );
        
        metricsService.recordRefundInitiated(event.getProvider());
    }
    
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}