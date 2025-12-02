package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.merchant.service.MerchantPayoutService;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.merchant.service.MerchantFeeService;
import com.waqiti.common.exception.MerchantProcessingException;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Payout Events
 * Handles merchant payouts, instant payouts, and payout status updates
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PayoutEventsConsumer {
    
    private final MerchantPayoutService payoutService;
    private final MerchantRiskService riskService;
    private final MerchantNotificationService notificationService;
    private final MerchantFeeService feeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"payout-events", "instant-payout-requested", "payout-completed", "payout-failed"},
        groupId = "merchant-service-payout-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handlePayoutEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID payoutId = null;
        UUID merchantId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            payoutId = UUID.fromString((String) event.get("payoutId"));
            merchantId = UUID.fromString((String) event.get("merchantId"));
            eventType = (String) event.get("eventType");
            String payoutType = (String) event.get("payoutType"); // STANDARD, INSTANT, SCHEDULED
            BigDecimal amount = new BigDecimal((String) event.get("amount"));
            String currency = (String) event.get("currency");
            String destinationType = (String) event.get("destinationType"); // BANK_ACCOUNT, DEBIT_CARD, DIGITAL_WALLET
            String destinationId = (String) event.get("destinationId");
            String routingNumber = (String) event.get("routingNumber");
            String accountNumber = (String) event.get("accountNumber");
            LocalDateTime requestedDate = LocalDateTime.parse((String) event.get("requestedDate"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Payout details
            String payoutStatus = (String) event.get("payoutStatus"); // PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED
            BigDecimal payoutFee = new BigDecimal((String) event.get("payoutFee"));
            BigDecimal netAmount = amount.subtract(payoutFee);
            String description = (String) event.get("description");
            String reference = (String) event.get("reference");
            Boolean isManualPayout = (Boolean) event.getOrDefault("isManualPayout", false);
            
            // Timing information
            LocalDateTime expectedDelivery = event.containsKey("expectedDelivery") ?
                    LocalDateTime.parse((String) event.get("expectedDelivery")) : null;
            LocalDateTime actualDelivery = event.containsKey("actualDelivery") ?
                    LocalDateTime.parse((String) event.get("actualDelivery")) : null;
            
            // Failure information
            String failureReason = (String) event.get("failureReason");
            String failureCode = (String) event.get("failureCode");
            Integer retryAttempt = (Integer) event.getOrDefault("retryAttempt", 0);
            
            log.info("Processing payout event - PayoutId: {}, MerchantId: {}, Type: {}, Amount: {} {}", 
                    payoutId, merchantId, eventType, amount, currency);
            
            // Step 1: Validate payout request
            Map<String, Object> validationResult = payoutService.validatePayoutRequest(
                    payoutId, merchantId, amount, currency, payoutType, destinationType,
                    destinationId, routingNumber, accountNumber, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                payoutService.rejectPayout(payoutId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Payout validation failed: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Check merchant eligibility and balance
            Map<String, Object> eligibilityCheck = payoutService.checkMerchantEligibility(
                    merchantId, amount, payoutType, timestamp);
            
            if ("INELIGIBLE".equals(eligibilityCheck.get("status"))) {
                payoutService.rejectPayout(payoutId, 
                        (String) eligibilityCheck.get("reason"), timestamp);
                log.warn("Merchant ineligible for payout: {}", eligibilityCheck.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Risk assessment for payout
            Map<String, Object> riskAssessment = riskService.assessPayoutRisk(
                    payoutId, merchantId, amount, payoutType, destinationType,
                    isManualPayout, retryAttempt, timestamp);
            
            String riskLevel = (String) riskAssessment.get("riskLevel");
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                payoutService.holdPayoutForReview(payoutId, riskAssessment, timestamp);
                log.warn("Payout held for risk review - Risk Level: {}", riskLevel);
                
                if ("CRITICAL".equals(riskLevel)) {
                    acknowledgment.acknowledge();
                    return;
                }
            }
            
            // Step 4: Calculate payout fees
            BigDecimal calculatedFee = feeService.calculatePayoutFee(merchantId, amount, 
                    payoutType, destinationType, currency, timestamp);
            
            if (calculatedFee.compareTo(payoutFee) != 0) {
                log.warn("Payout fee mismatch - calculated: {}, provided: {}", calculatedFee, payoutFee);
                payoutFee = calculatedFee;
                netAmount = amount.subtract(payoutFee);
            }
            
            // Step 5: Process based on event type
            switch (eventType) {
                case "INSTANT_PAYOUT_REQUESTED":
                    payoutService.processInstantPayout(payoutId, merchantId, amount, currency,
                            destinationType, destinationId, routingNumber, accountNumber,
                            payoutFee, netAmount, description, reference, timestamp);
                    break;
                    
                case "PAYOUT_COMPLETED":
                    payoutService.completePayout(payoutId, actualDelivery, 
                            (String) event.get("transactionId"), timestamp);
                    break;
                    
                case "PAYOUT_FAILED":
                    payoutService.handlePayoutFailure(payoutId, failureReason, failureCode,
                            retryAttempt, timestamp);
                    break;
                    
                case "PAYOUT_SCHEDULED":
                    LocalDateTime scheduledDate = LocalDateTime.parse((String) event.get("scheduledDate"));
                    payoutService.schedulePayout(payoutId, merchantId, amount, currency,
                            destinationType, destinationId, routingNumber, accountNumber,
                            payoutFee, netAmount, scheduledDate, timestamp);
                    break;
                    
                default:
                    payoutService.processGenericPayoutEvent(payoutId, eventType, event, timestamp);
            }
            
            // Step 6: Update merchant balances
            if ("PAYOUT_COMPLETED".equals(eventType)) {
                payoutService.updateMerchantBalance(merchantId, amount.negate(), 
                        currency, payoutId, timestamp);
                
                // Update available balance for instant payouts
                if ("INSTANT".equals(payoutType)) {
                    payoutService.updateInstantPayoutLimits(merchantId, amount, timestamp);
                }
            }
            
            // Step 7: Handle payout retries for failures
            if ("PAYOUT_FAILED".equals(eventType) && retryAttempt < 3) {
                boolean shouldRetry = payoutService.shouldRetryPayout(payoutId, failureCode, 
                        failureReason, retryAttempt, timestamp);
                
                if (shouldRetry) {
                    payoutService.schedulePayoutRetry(payoutId, retryAttempt + 1, timestamp);
                    log.info("Scheduled payout retry #{}", retryAttempt + 1);
                }
            }
            
            // Step 8: Compliance and reporting
            payoutService.updatePayoutReporting(payoutId, merchantId, amount, currency,
                    payoutType, destinationType, payoutStatus, timestamp);
            
            // Step 9: Tax implications for instant payouts
            if ("INSTANT".equals(payoutType) && "PAYOUT_COMPLETED".equals(eventType)) {
                payoutService.updateTaxReportingForPayout(merchantId, payoutId, amount,
                    payoutFee, actualDelivery, timestamp);
            }
            
            // Step 10: Send notifications
            notificationService.sendPayoutNotification(payoutId, merchantId, eventType,
                    payoutType, amount, currency, payoutStatus, expectedDelivery, 
                    actualDelivery, timestamp);
            
            // Step 11: Update merchant analytics
            payoutService.updatePayoutAnalytics(merchantId, payoutType, amount, currency,
                    payoutFee, payoutStatus, timestamp);
            
            // Step 12: Audit logging
            auditService.auditFinancialEvent(
                    "PAYOUT_EVENT_PROCESSED",
                    merchantId.toString(),
                    String.format("Payout event processed - Type: %s, Amount: %s %s, Status: %s", 
                            eventType, amount, currency, payoutStatus),
                    Map.of(
                            "payoutId", payoutId.toString(),
                            "merchantId", merchantId.toString(),
                            "eventType", eventType,
                            "payoutType", payoutType,
                            "amount", amount.toString(),
                            "currency", currency,
                            "netAmount", netAmount.toString(),
                            "payoutFee", payoutFee.toString(),
                            "destinationType", destinationType,
                            "payoutStatus", payoutStatus,
                            "riskLevel", riskLevel,
                            "isManualPayout", isManualPayout.toString(),
                            "retryAttempt", retryAttempt.toString(),
                            "expectedDelivery", expectedDelivery != null ? expectedDelivery.toString() : "N/A",
                            "actualDelivery", actualDelivery != null ? actualDelivery.toString() : "N/A",
                            "failureReason", failureReason != null ? failureReason : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed payout event - PayoutId: {}, EventType: {}, Status: {}", 
                    payoutId, eventType, payoutStatus);
            
        } catch (Exception e) {
            log.error("Payout event processing failed - PayoutId: {}, MerchantId: {}, Error: {}", 
                    payoutId, merchantId, e.getMessage(), e);
            throw new MerchantProcessingException("Payout event processing failed", e);
        }
    }
}