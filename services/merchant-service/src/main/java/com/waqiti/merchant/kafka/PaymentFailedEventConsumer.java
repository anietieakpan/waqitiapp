package com.waqiti.merchant.kafka;

import com.waqiti.common.eventsourcing.PaymentFailedEvent;
import com.waqiti.merchant.service.MerchantSettlementService;
import com.waqiti.merchant.service.MerchantTransactionService;
import com.waqiti.merchant.service.MerchantAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer for PaymentFailedEvent in Merchant Service
 * 
 * Handles payment failures by:
 * - Adjusting merchant settlement calculations
 * - Recording failure analytics
 * - Updating merchant transaction records
 * - Triggering chargeback prevention if applicable
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailedEventConsumer {

    private final MerchantSettlementService settlementService;
    private final MerchantTransactionService transactionService;
    private final MerchantAnalyticsService analyticsService;

    @KafkaListener(
        topics = "payment-failed",
        groupId = "merchant-service-payment-failed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional
    public void handlePaymentFailed(
            @Payload PaymentFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String paymentId = event.getPaymentId();
        String merchantId = event.getMerchantId();
        
        log.info("Processing PaymentFailedEvent for merchant: paymentId={}, merchantId={}, reason={}", 
                paymentId, merchantId, event.getFailureReason());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Update merchant transaction status
            transactionService.markTransactionFailed(
                paymentId,
                merchantId,
                event.getFailureReason(),
                event.getFailureCode()
            );
            
            // 2. Adjust settlement calculations if payment was already included
            if (event.getAmount() != null && merchantId != null) {
                settlementService.adjustSettlementForFailedPayment(
                    merchantId,
                    paymentId,
                    event.getAmount(),
                    event.getCurrency()
                );
            }
            
            // 3. Record failure analytics for merchant insights
            analyticsService.recordPaymentFailure(
                merchantId,
                event.getFailureCode(),
                event.getFailureReason(),
                event.getAmount(),
                event.getCurrency()
            );
            
            // 4. Check if this triggers chargeback prevention workflows
            if (isChargebackRisk(event)) {
                triggerChargebackPrevention(event);
            }
            
            // 5. Update merchant risk scoring if needed
            if (isHighValueFailure(event)) {
                updateMerchantRiskScore(merchantId, event);
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.info("Successfully processed PaymentFailedEvent for merchant: paymentId={}, processingTime={}ms", 
                    paymentId, processingTime);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process PaymentFailedEvent for merchant: paymentId={}, merchantId={}", 
                    paymentId, merchantId, e);
            
            throw new RuntimeException("Failed to process merchant payment failure event", e);
        }
    }
    
    /**
     * Check if payment failure indicates potential chargeback risk
     */
    private boolean isChargebackRisk(PaymentFailedEvent event) {
        String failureCode = event.getFailureCode();
        return "insufficient_funds".equals(failureCode) || 
               "card_declined".equals(failureCode) ||
               "fraud_suspected".equals(failureCode);
    }
    
    /**
     * Trigger chargeback prevention workflows
     */
    private void triggerChargebackPrevention(PaymentFailedEvent event) {
        try {
            log.info("Triggering chargeback prevention for payment: {}", event.getPaymentId());
            
            chargebackPreventionService.initiatePrevention(event)
                .thenAccept(result -> {
                    if (result.isSuccess()) {
                        log.info("Chargeback prevention initiated successfully for payment: {} with risk score: {}", 
                            event.getPaymentId(), result.getRiskScore());
                    } else {
                        log.warn("Chargeback prevention failed for payment: {}", event.getPaymentId());
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Chargeback prevention error for payment: {}", event.getPaymentId(), throwable);
                    return null;
                });
            
        } catch (Exception e) {
            log.warn("Failed to trigger chargeback prevention: {}", e.getMessage());
        }
    }
    
    /**
     * Check if this is a high-value payment failure
     */
    private boolean isHighValueFailure(PaymentFailedEvent event) {
        return event.getAmount() != null && 
               event.getAmount().compareTo(new java.math.BigDecimal("1000")) > 0;
    }
    
    /**
     * Update merchant risk score based on payment failure
     */
    private void updateMerchantRiskScore(String merchantId, PaymentFailedEvent event) {
        try {
            log.info("Updating merchant risk score for high-value failure: merchantId={}, amount={}", 
                    merchantId, event.getAmount());
            
            riskScoringService.updateForPaymentFailure(merchantId, event)
                .thenAccept(update -> {
                    log.info("Risk score updated for merchant {}: {} -> {} (delta: {}, level: {})", 
                        merchantId, 
                        update.getPreviousScore(), 
                        update.getNewScore(), 
                        update.getScoreDelta(), 
                        update.getRiskLevel());
                    
                    // Send notification if risk level is critical
                    if (update.getRiskLevel() == RiskScoringService.RiskLevel.CRITICAL) {
                        notificationService.sendCriticalRiskAlert(merchantId, update);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("Risk score update failed for merchant: {}", merchantId, throwable);
                    return null;
                });
            
        } catch (Exception e) {
            log.warn("Failed to update merchant risk score: {}", e.getMessage());
        }
    }
}
