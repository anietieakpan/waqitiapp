package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.RegulatoryReportingService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.entity.Settlement;
import com.waqiti.payment.entity.SettlementBatch;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Critical Event Consumer #101: Settlement Processing Event Consumer
 * Processes payment settlements with comprehensive regulatory reporting and compliance checks
 * Implements 12-step zero-tolerance processing for secure settlement workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementProcessingEventConsumer extends BaseKafkaConsumer {

    private final SettlementService settlementService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "settlement-processing-events", groupId = "settlement-processing-group")
    @CircuitBreaker(name = "settlement-processing-consumer")
    @Retry(name = "settlement-processing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSettlementProcessingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "settlement-processing-event");
        
        try {
            log.info("Step 1: Processing settlement processing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String settlementId = eventData.path("settlementId").asText();
            String batchId = eventData.path("batchId").asText();
            String merchantId = eventData.path("merchantId").asText();
            String acquirerId = eventData.path("acquirerId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String settlementType = eventData.path("settlementType").asText(); // NET, GROSS
            String channel = eventData.path("channel").asText(); // CARD, ACH, WIRE
            List<String> transactionIds = objectMapper.convertValue(
                eventData.path("transactionIds"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            LocalDateTime settlementDate = LocalDateTime.parse(eventData.path("settlementDate").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted settlement details: settlementId={}, batchId={}, amount={}, merchantId={}", 
                    settlementId, batchId, amount, merchantId);
            
            // Step 3: Validate settlement batch exists and is ready for processing
            SettlementBatch batch = settlementService.getSettlementBatch(batchId);
            
            if (!"READY_FOR_SETTLEMENT".equals(batch.getStatus())) {
                log.error("Step 3: Settlement batch not ready for processing: {}, current status: {}", 
                        batchId, batch.getStatus());
                throw new IllegalStateException("Settlement batch cannot be processed in current status");
            }
            
            // Step 4: Perform regulatory compliance checks (BSA/AML, OFAC)
            complianceService.performSettlementComplianceCheck(merchantId, acquirerId, amount, currency, timestamp);
            
            log.info("Step 4: Completed compliance checks for settlement");
            
            // Step 5: Validate merchant settlement eligibility
            boolean merchantEligible = settlementService.validateMerchantSettlementEligibility(
                merchantId, amount, currency, channel, timestamp);
            
            if (!merchantEligible) {
                log.warn("Step 5: Merchant not eligible for settlement: merchantId={}", merchantId);
                settlementService.flagIneligibleSettlement(settlementId, merchantId, "MERCHANT_INELIGIBLE", timestamp);
                throw new IllegalArgumentException("Merchant not eligible for settlement");
            }
            
            // Step 6: Calculate settlement fees and net amount
            BigDecimal netAmount = settlementService.calculateNetSettlementAmount(
                amount, merchantId, settlementType, channel, transactionIds, timestamp);
            
            log.info("Step 6: Calculated net settlement amount: {}", netAmount);
            
            // Step 7: Verify liquidity and reserve funds
            settlementService.reserveSettlementFunds(batchId, settlementId, netAmount, currency, timestamp);
            
            // Step 8: Generate regulatory reports (if required thresholds met)
            regulatoryReportingService.generateSettlementReports(
                settlementId, merchantId, acquirerId, amount, currency, settlementType, timestamp);
            
            log.info("Step 8: Generated required regulatory reports");
            
            // Step 9: Process settlement with banking partner
            Settlement settlement = settlementService.processSettlement(
                settlementId, batchId, merchantId, acquirerId, netAmount, currency, 
                settlementType, channel, transactionIds, settlementDate, timestamp);
            
            log.info("Step 9: Processed settlement successfully: settlementId={}", settlementId);
            
            // Step 10: Update transaction statuses to SETTLED
            settlementService.updateTransactionSettlementStatus(transactionIds, settlementId, timestamp);
            
            // Step 11: Send settlement notifications and confirmations
            settlementService.sendSettlementNotifications(
                merchantId, settlementId, netAmount, currency, settlementDate, timestamp);
            
            log.info("Step 11: Sent settlement notifications");
            
            // Step 12: Log settlement for audit trail and update batch status
            settlementService.logSettlementEvent(
                settlementId, batchId, merchantId, acquirerId, amount, netAmount, 
                currency, settlementType, channel, timestamp);
            
            settlementService.updateBatchStatus(batchId, "SETTLEMENT_PROCESSED", timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed settlement event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing settlement event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("settlementId") || 
            !eventData.has("batchId") || !eventData.has("merchantId") ||
            !eventData.has("amount") || !eventData.has("currency") ||
            !eventData.has("transactionIds") || !eventData.has("settlementDate")) {
            throw new IllegalArgumentException("Invalid settlement processing event structure");
        }
    }
}