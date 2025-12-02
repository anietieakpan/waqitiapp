package com.waqiti.payment.kafka;

import com.waqiti.payment.event.PaymentEvent;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.GatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Production-grade Kafka consumer for payment processing events
 * Handles: payment-tracking, payment-gateway-health, settlement-completed, payment-alerts,
 * payment-analytics, payment-provider-status-changes, payment-fallback-events,
 * payment-failure-analytics, scheduled-payments, fund-release-events, batch-payment-completion,
 * virtual-card-events, bank-integration-events, refund-requests, manual-refund-queue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessingConsumer {

    private final PaymentService paymentService;
    private final SettlementService settlementService;
    private final RefundService refundService;
    private final GatewayService gatewayService;

    @KafkaListener(topics = {"payment-tracking", "payment-gateway-health", "settlement-completed",
                             "payment-alerts", "payment-analytics", "payment-provider-status-changes",
                             "payment-fallback-events", "payment-failure-analytics", "scheduled-payments",
                             "fund-release-events", "batch-payment-completion", "virtual-card-events",
                             "bank-integration-events", "refund-requests", "manual-refund-queue"}, 
                   groupId = "payment-processing-group")
    @Transactional
    public void processPaymentEvent(@Payload PaymentEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            log.info("Processing payment event: {} - Topic: {} - Type: {} - Amount: {}", 
                    event.getPaymentId(), topic, event.getEventType(), event.getAmount());
            
            // Process based on topic
            switch (topic) {
                case "payment-tracking" -> trackPayment(event);
                case "payment-gateway-health" -> checkGatewayHealth(event);
                case "settlement-completed" -> handleSettlementCompleted(event);
                case "payment-alerts" -> handlePaymentAlert(event);
                case "payment-analytics" -> processPaymentAnalytics(event);
                case "payment-provider-status-changes" -> handleProviderStatusChange(event);
                case "payment-fallback-events" -> handlePaymentFallback(event);
                case "payment-failure-analytics" -> analyzePaymentFailure(event);
                case "scheduled-payments" -> processScheduledPayment(event);
                case "fund-release-events" -> releaseFunds(event);
                case "batch-payment-completion" -> handleBatchCompletion(event);
                case "virtual-card-events" -> handleVirtualCardEvent(event);
                case "bank-integration-events" -> handleBankIntegration(event);
                case "refund-requests" -> processRefundRequest(event);
                case "manual-refund-queue" -> handleManualRefund(event);
            }
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed payment event: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to process payment event {}: {}", 
                    event.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Payment processing failed", e);
        }
    }

    private void trackPayment(PaymentEvent event) {
        // Update payment tracking
        paymentService.updateTracking(
            event.getPaymentId(),
            event.getStatus(),
            event.getLocation(),
            event.getTimestamp()
        );
        
        // Check for delays
        if (event.isDelayed()) {
            paymentService.handleDelay(
                event.getPaymentId(),
                event.getDelayReason(),
                event.getExpectedCompletion()
            );
        }
    }

    private void checkGatewayHealth(PaymentEvent event) {
        // Check gateway health
        String gateway = event.getGateway();
        boolean isHealthy = gatewayService.checkHealth(gateway);
        
        if (!isHealthy) {
            // Switch to backup gateway
            gatewayService.switchToBackup(gateway);
            
            // Reroute pending payments
            gatewayService.reroutePendingPayments(
                gateway,
                event.getBackupGateway()
            );
        }
        
        // Update health metrics
        gatewayService.updateHealthMetrics(
            gateway,
            event.getHealthMetrics()
        );
    }

    private void handleSettlementCompleted(PaymentEvent event) {
        // Process settlement completion
        settlementService.markAsCompleted(
            event.getSettlementId(),
            event.getSettledAmount(),
            event.getSettlementDate()
        );
        
        // Distribute funds
        settlementService.distributeFunds(
            event.getSettlementId(),
            event.getDistributionDetails()
        );
        
        // Update merchant balance
        paymentService.updateMerchantBalance(
            event.getMerchantId(),
            event.getSettledAmount()
        );
        
        // Send settlement confirmation
        paymentService.sendSettlementConfirmation(
            event.getMerchantId(),
            event.getSettlementId(),
            event.getSettledAmount()
        );
    }

    private void handlePaymentAlert(PaymentEvent event) {
        // Process payment alert
        String alertType = event.getAlertType();
        
        switch (alertType) {
            case "HIGH_VALUE" -> paymentService.handleHighValuePayment(event);
            case "SUSPICIOUS" -> paymentService.flagSuspiciousPayment(event);
            case "FAILED" -> paymentService.handleFailedPayment(event);
            case "TIMEOUT" -> paymentService.handleTimeout(event);
            case "DUPLICATE" -> paymentService.handleDuplicate(event);
        }
        
        // Send alert notifications
        paymentService.sendAlertNotification(
            event.getAlertType(),
            event.getPaymentId(),
            event.getAlertDetails()
        );
    }

    private void processPaymentAnalytics(PaymentEvent event) {
        // Update payment analytics
        paymentService.updateAnalytics(
            event.getPaymentMethod(),
            event.getAmount(),
            event.getCurrency(),
            event.getMerchantCategory(),
            event.getSuccessRate(),
            event.getProcessingTime()
        );
        
        // Generate insights
        paymentService.generateInsights(
            event.getTimeRange(),
            event.getMetrics()
        );
    }

    private void handleProviderStatusChange(PaymentEvent event) {
        // Update provider status
        String provider = event.getProvider();
        String newStatus = event.getNewStatus();
        
        gatewayService.updateProviderStatus(provider, newStatus);
        
        if ("DOWN".equals(newStatus)) {
            // Activate contingency plan
            gatewayService.activateContingency(provider);
            
            // Notify affected merchants
            paymentService.notifyAffectedMerchants(
                provider,
                event.getAffectedMerchants()
            );
        } else if ("DEGRADED".equals(newStatus)) {
            // Reduce traffic to provider
            gatewayService.reduceTraffic(provider, 50);
        }
    }

    private void handlePaymentFallback(PaymentEvent event) {
        // Execute fallback strategy
        String fallbackMethod = event.getFallbackMethod();
        
        boolean success = paymentService.executeFallback(
            event.getPaymentId(),
            fallbackMethod,
            event.getFallbackParams()
        );
        
        if (!success) {
            // Try next fallback
            if (event.hasNextFallback()) {
                paymentService.tryNextFallback(
                    event.getPaymentId(),
                    event.getNextFallback()
                );
            } else {
                // Mark as failed
                paymentService.markAsFailed(
                    event.getPaymentId(),
                    "ALL_FALLBACKS_EXHAUSTED"
                );
            }
        }
    }

    private void analyzePaymentFailure(PaymentEvent event) {
        // Analyze failure patterns
        paymentService.analyzeFailure(
            event.getPaymentId(),
            event.getFailureReason(),
            event.getFailureCode(),
            event.getFailureContext()
        );
        
        // Update failure metrics
        paymentService.updateFailureMetrics(
            event.getFailureReason(),
            event.getProvider(),
            event.getPaymentMethod()
        );
        
        // Suggest improvements
        paymentService.generateImprovementSuggestions(
            event.getFailurePatterns()
        );
    }

    private void processScheduledPayment(PaymentEvent event) {
        // Execute scheduled payment
        LocalDateTime scheduledTime = event.getScheduledTime();
        
        if (LocalDateTime.now().isAfter(scheduledTime)) {
            // Process payment
            String result = paymentService.processPayment(
                event.getPaymentId(),
                event.getAmount(),
                event.getRecipient(),
                event.getPaymentMethod()
            );
            
            if ("SUCCESS".equals(result)) {
                // Mark schedule as completed
                paymentService.markScheduleCompleted(
                    event.getScheduleId(),
                    event.getPaymentId()
                );
            } else {
                // Reschedule if configured
                if (event.isAutoReschedule()) {
                    paymentService.reschedulePayment(
                        event.getScheduleId(),
                        LocalDateTime.now().plusHours(1)
                    );
                }
            }
        }
    }

    private void releaseFunds(PaymentEvent event) {
        // Release held funds
        BigDecimal amount = event.getAmount();
        String holdId = event.getHoldId();
        
        // Verify release authorization
        if (paymentService.verifyReleaseAuthorization(holdId, event.getAuthorizationCode())) {
            // Release funds
            paymentService.releaseFunds(
                holdId,
                amount,
                event.getRecipient()
            );
            
            // Update hold status
            paymentService.updateHoldStatus(
                holdId,
                "RELEASED",
                LocalDateTime.now()
            );
        } else {
            log.error("Unauthorized fund release attempt: {}", holdId);
            paymentService.flagUnauthorizedRelease(holdId);
        }
    }

    private void handleBatchCompletion(PaymentEvent event) {
        // Process batch completion
        String batchId = event.getBatchId();
        
        // Calculate batch statistics
        var stats = paymentService.calculateBatchStats(batchId);
        
        // Mark batch as completed
        paymentService.markBatchCompleted(
            batchId,
            stats.getSuccessCount(),
            stats.getFailureCount(),
            stats.getTotalAmount()
        );
        
        // Process failed items
        if (stats.getFailureCount() > 0) {
            paymentService.handleBatchFailures(
                batchId,
                event.getFailedItems(),
                event.getRetryStrategy()
            );
        }
        
        // Send batch report
        paymentService.sendBatchReport(
            batchId,
            stats,
            event.getReportRecipients()
        );
    }

    private void handleVirtualCardEvent(PaymentEvent event) {
        // Process virtual card event
        String cardId = event.getVirtualCardId();
        String eventType = event.getCardEventType();
        
        switch (eventType) {
            case "CREATED" -> paymentService.activateVirtualCard(cardId, event.getCardDetails());
            case "USED" -> paymentService.recordCardUsage(cardId, event.getTransactionDetails());
            case "EXPIRED" -> paymentService.expireVirtualCard(cardId);
            case "BLOCKED" -> paymentService.blockVirtualCard(cardId, event.getBlockReason());
            case "LIMIT_UPDATE" -> paymentService.updateCardLimit(cardId, event.getNewLimit());
        }
    }

    private void handleBankIntegration(PaymentEvent event) {
        // Handle bank integration event
        String bank = event.getBankCode();
        String integrationType = event.getIntegrationType();
        
        switch (integrationType) {
            case "ACCOUNT_VERIFICATION" -> {
                paymentService.verifyBankAccount(
                    event.getAccountNumber(),
                    event.getRoutingNumber(),
                    bank
                );
            }
            case "BALANCE_CHECK" -> {
                paymentService.checkAccountBalance(
                    event.getAccountId(),
                    bank
                );
            }
            case "ACH_TRANSFER" -> {
                paymentService.initiateAchTransfer(
                    event.getTransferId(),
                    event.getAmount(),
                    event.getDirection()
                );
            }
            case "WIRE_TRANSFER" -> {
                paymentService.initiateWireTransfer(
                    event.getTransferId(),
                    event.getAmount(),
                    event.getWireDetails()
                );
            }
        }
    }

    private void processRefundRequest(PaymentEvent event) {
        // Process refund request
        String refundId = refundService.createRefund(
            event.getOriginalPaymentId(),
            event.getRefundAmount(),
            event.getRefundReason()
        );
        
        // Validate refund
        if (refundService.validateRefund(refundId, event)) {
            // Process refund
            refundService.processRefund(
                refundId,
                event.getRefundMethod()
            );
        } else {
            // Escalate to manual review
            refundService.escalateToManualReview(
                refundId,
                event.getValidationFailureReason()
            );
        }
    }

    private void handleManualRefund(PaymentEvent event) {
        // Manual refund processing
        refundService.processManualRefund(
            event.getRefundId(),
            event.getApprovedBy(),
            event.getApprovalNotes(),
            event.getRefundMethod()
        );
    }
}