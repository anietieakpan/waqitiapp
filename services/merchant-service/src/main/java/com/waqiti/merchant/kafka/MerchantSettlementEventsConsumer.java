package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.merchant.service.MerchantSettlementService;
import com.waqiti.merchant.service.MerchantFeeService;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.merchant.service.MerchantRiskService;
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
 * Kafka Consumer for Merchant Settlement Events
 * Handles merchant payment settlements, fee calculations, and payout processing
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantSettlementEventsConsumer {
    
    private final MerchantSettlementService settlementService;
    private final MerchantFeeService feeService;
    private final MerchantNotificationService notificationService;
    private final MerchantRiskService riskService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"merchant-settlement-events", "settlement-initiated", "settlement-completed", "settlement-failed"},
        groupId = "merchant-service-settlement-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleMerchantSettlementEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID settlementId = null;
        UUID merchantId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            settlementId = UUID.fromString((String) event.get("settlementId"));
            merchantId = UUID.fromString((String) event.get("merchantId"));
            eventType = (String) event.get("eventType");
            String settlementType = (String) event.get("settlementType"); // DAILY, WEEKLY, MONTHLY, INSTANT
            BigDecimal grossAmount = new BigDecimal((String) event.get("grossAmount"));
            BigDecimal netAmount = new BigDecimal((String) event.get("netAmount"));
            String currency = (String) event.get("currency");
            LocalDateTime settlementPeriodStart = LocalDateTime.parse((String) event.get("settlementPeriodStart"));
            LocalDateTime settlementPeriodEnd = LocalDateTime.parse((String) event.get("settlementPeriodEnd"));
            Integer transactionCount = (Integer) event.get("transactionCount");
            String bankAccountId = (String) event.get("bankAccountId");
            String routingNumber = (String) event.get("routingNumber");
            String accountNumber = (String) event.get("accountNumber");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Fee breakdown
            BigDecimal processingFees = new BigDecimal((String) event.get("processingFees"));
            BigDecimal chargebackFees = new BigDecimal((String) event.get("chargebackFees"));
            BigDecimal monthlyFees = new BigDecimal((String) event.get("monthlyFees"));
            BigDecimal reserveAmount = new BigDecimal((String) event.get("reserveAmount"));
            BigDecimal adjustments = new BigDecimal((String) event.get("adjustments"));
            
            // Settlement status information
            String settlementStatus = (String) event.get("settlementStatus"); // PENDING, PROCESSING, COMPLETED, FAILED
            String failureReason = (String) event.get("failureReason");
            String achTransactionId = (String) event.get("achTransactionId");
            LocalDateTime estimatedDelivery = event.containsKey("estimatedDelivery") ?
                    LocalDateTime.parse((String) event.get("estimatedDelivery")) : null;
            
            log.info("Processing merchant settlement event - SettlementId: {}, MerchantId: {}, Type: {}, Net Amount: {} {}", 
                    settlementId, merchantId, eventType, netAmount, currency);
            
            // Step 1: Validate settlement data and merchant eligibility
            Map<String, Object> validationResult = settlementService.validateSettlement(
                    settlementId, merchantId, grossAmount, netAmount, settlementType, 
                    bankAccountId, transactionCount, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                settlementService.failSettlement(settlementId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Settlement validation failed: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Risk assessment for settlement
            Map<String, Object> riskAssessment = riskService.assessSettlementRisk(
                    settlementId, merchantId, grossAmount, netAmount, transactionCount,
                    settlementType, settlementPeriodStart, settlementPeriodEnd, timestamp);
            
            String riskLevel = (String) riskAssessment.get("riskLevel");
            if ("HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel)) {
                settlementService.holdSettlementForReview(settlementId, riskAssessment, timestamp);
                log.warn("Settlement held for risk review - Risk Level: {}", riskLevel);
                
                if ("CRITICAL".equals(riskLevel)) {
                    acknowledgment.acknowledge();
                    return;
                }
            }
            
            // Step 3: Calculate and verify fees
            Map<String, BigDecimal> calculatedFees = feeService.calculateSettlementFees(
                    merchantId, grossAmount, transactionCount, settlementType, 
                    settlementPeriodStart, settlementPeriodEnd, timestamp);
            
            // Verify fee calculations match
            boolean feesMatch = feeService.verifyFeeCalculations(processingFees, chargebackFees,
                    monthlyFees, calculatedFees, timestamp);
            
            if (!feesMatch) {
                log.warn("Fee calculation mismatch detected - recalculating fees");
                processingFees = calculatedFees.get("processingFees");
                chargebackFees = calculatedFees.get("chargebackFees");
                monthlyFees = calculatedFees.get("monthlyFees");
                netAmount = grossAmount.subtract(processingFees).subtract(chargebackFees)
                          .subtract(monthlyFees).subtract(reserveAmount).add(adjustments);
            }
            
            // Step 4: Process based on event type
            switch (eventType) {
                case "SETTLEMENT_INITIATED":
                    settlementService.initiateSettlement(settlementId, merchantId, settlementType,
                            grossAmount, netAmount, currency, settlementPeriodStart, settlementPeriodEnd,
                            transactionCount, bankAccountId, processingFees, chargebackFees,
                            monthlyFees, reserveAmount, adjustments, estimatedDelivery, timestamp);
                    break;
                    
                case "SETTLEMENT_COMPLETED":
                    settlementService.completeSettlement(settlementId, achTransactionId,
                            (LocalDateTime) event.get("actualDelivery"), timestamp);
                    break;
                    
                case "SETTLEMENT_FAILED":
                    settlementService.handleSettlementFailure(settlementId, failureReason,
                            (String) event.get("achReturnCode"), timestamp);
                    break;
                    
                default:
                    settlementService.processGenericSettlementEvent(settlementId, eventType, 
                            event, timestamp);
            }
            
            // Step 5: Update merchant reserves and balances
            if ("SETTLEMENT_COMPLETED".equals(eventType)) {
                settlementService.updateMerchantBalances(merchantId, settlementId, netAmount,
                        reserveAmount, currency, timestamp);
                
                // Release any held reserves if applicable
                settlementService.processReserveRelease(merchantId, settlementId, timestamp);
            }
            
            // Step 6: Handle chargebacks and adjustments
            if (chargebackFees.compareTo(BigDecimal.ZERO) > 0 || 
                adjustments.compareTo(BigDecimal.ZERO) != 0) {
                settlementService.processChargebacksAndAdjustments(settlementId, merchantId,
                        chargebackFees, adjustments, timestamp);
            }
            
            // Step 7: Generate settlement reports and statements
            if ("SETTLEMENT_COMPLETED".equals(eventType)) {
                settlementService.generateSettlementReport(settlementId, merchantId, grossAmount,
                        netAmount, processingFees, chargebackFees, monthlyFees, transactionCount,
                        settlementPeriodStart, settlementPeriodEnd, timestamp);
            }
            
            // Step 8: Update merchant tier and processing limits
            settlementService.updateMerchantProcessingHistory(merchantId, grossAmount, 
                    transactionCount, settlementType, timestamp);
            
            // Step 9: Send settlement notifications
            notificationService.sendSettlementNotification(settlementId, merchantId, eventType,
                    settlementType, grossAmount, netAmount, currency, estimatedDelivery, timestamp);
            
            // Step 10: Tax reporting and compliance
            if ("SETTLEMENT_COMPLETED".equals(eventType)) {
                settlementService.updateTaxReporting(merchantId, settlementId, grossAmount,
                        processingFees, settlementPeriodStart, settlementPeriodEnd, timestamp);
            }
            
            // Step 11: Audit logging
            auditService.auditFinancialEvent(
                    "MERCHANT_SETTLEMENT_EVENT_PROCESSED",
                    merchantId.toString(),
                    String.format("Merchant settlement event processed - Type: %s, Net Amount: %s %s, Transactions: %d", 
                            eventType, netAmount, currency, transactionCount),
                    Map.of(
                            "settlementId", settlementId.toString(),
                            "merchantId", merchantId.toString(),
                            "eventType", eventType,
                            "settlementType", settlementType,
                            "grossAmount", grossAmount.toString(),
                            "netAmount", netAmount.toString(),
                            "currency", currency,
                            "transactionCount", transactionCount.toString(),
                            "processingFees", processingFees.toString(),
                            "chargebackFees", chargebackFees.toString(),
                            "monthlyFees", monthlyFees.toString(),
                            "reserveAmount", reserveAmount.toString(),
                            "riskLevel", riskLevel,
                            "settlementStatus", settlementStatus,
                            "estimatedDelivery", estimatedDelivery != null ? estimatedDelivery.toString() : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed merchant settlement event - SettlementId: {}, EventType: {}", 
                    settlementId, eventType);
            
        } catch (Exception e) {
            log.error("Merchant settlement event processing failed - SettlementId: {}, MerchantId: {}, Error: {}", 
                    settlementId, merchantId, e.getMessage(), e);
            throw new MerchantProcessingException("Merchant settlement event processing failed", e);
        }
    }
}