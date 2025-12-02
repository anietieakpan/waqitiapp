package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.SecuritiesSettlementService;
import com.waqiti.investment.service.CustodyService;
import com.waqiti.investment.service.ComplianceService;
import com.waqiti.investment.service.RegulatoryReportingService;
import com.waqiti.investment.entity.SecuritiesTrade;
import com.waqiti.investment.entity.SettlementInstruction;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #105: Securities Settlement Event Consumer
 * Processes T+2 settlement with Delivery versus Payment (DVP) and Receipt versus Payment (RVP)
 * Implements 12-step zero-tolerance processing for secure securities settlement workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecuritiesSettlementEventConsumer extends BaseKafkaConsumer {

    private final SecuritiesSettlementService settlementService;
    private final CustodyService custodyService;
    private final ComplianceService complianceService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "securities-settlement-events", groupId = "securities-settlement-group")
    @CircuitBreaker(name = "securities-settlement-consumer")
    @Retry(name = "securities-settlement-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSecuritiesSettlementEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "securities-settlement-event");
        
        try {
            log.info("Step 1: Processing securities settlement event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String settlementId = eventData.path("settlementId").asText();
            String tradeId = eventData.path("tradeId").asText();
            String buyerAccountId = eventData.path("buyerAccountId").asText();
            String sellerAccountId = eventData.path("sellerAccountId").asText();
            String securityId = eventData.path("securityId").asText(); // CUSIP/ISIN
            String securityType = eventData.path("securityType").asText(); // EQUITY, BOND, FUND
            BigDecimal quantity = new BigDecimal(eventData.path("quantity").asText());
            BigDecimal price = new BigDecimal(eventData.path("price").asText());
            BigDecimal settlementAmount = new BigDecimal(eventData.path("settlementAmount").asText());
            String currency = eventData.path("currency").asText();
            String settlementType = eventData.path("settlementType").asText(); // DVP, RVP, FOP
            LocalDateTime tradeDate = LocalDateTime.parse(eventData.path("tradeDate").asText());
            LocalDateTime settlementDate = LocalDateTime.parse(eventData.path("settlementDate").asText());
            String clearingAgentId = eventData.path("clearingAgentId").asText();
            List<String> settlementInstructions = objectMapper.convertValue(
                eventData.path("settlementInstructions"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());

            // CRITICAL SECURITY: Idempotency check - prevent duplicate securities settlement
            String idempotencyKey = "securities-settlement:" + settlementId + ":" + tradeId + ":" + eventId;
            UUID operationId = UUID.randomUUID();

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate securities settlement event ignored: settlementId={}, tradeId={}, eventId={}",
                        settlementId, tradeId, eventId);
                ack.acknowledge();
                return;
            }

            log.info("Step 2: Extracted settlement details: settlementId={}, security={}, quantity={}, amount={}",
                    settlementId, securityId, quantity, settlementAmount);
            
            // Step 3: Validate settlement eligibility and T+2 timing
            settlementService.validateSettlementEligibility(
                tradeId, securityId, tradeDate, settlementDate, timestamp);
            
            log.info("Step 3: Validated settlement eligibility and T+2 timing");
            
            // Step 4: Perform regulatory compliance checks (SEC, FINRA)
            complianceService.performSecuritiesSettlementComplianceCheck(
                settlementId, tradeId, buyerAccountId, sellerAccountId, 
                securityId, quantity, settlementAmount, timestamp);
            
            log.info("Step 4: Completed compliance checks for securities settlement");
            
            // Step 5: Verify security ownership and availability (seller side)
            boolean securityAvailable = custodyService.verifySecurityAvailability(
                sellerAccountId, securityId, quantity, settlementDate, timestamp);
            
            if (!securityAvailable) {
                log.warn("Step 5: Securities not available for settlement: seller={}, security={}", 
                        sellerAccountId, securityId);
                settlementService.flagSecurityUnavailability(settlementId, sellerAccountId, securityId, timestamp);
                throw new IllegalArgumentException("Securities not available for settlement");
            }
            
            // Step 6: Verify cash availability (buyer side for DVP)
            if ("DVP".equals(settlementType)) {
                boolean cashAvailable = settlementService.verifyCashAvailability(
                    buyerAccountId, settlementAmount, currency, settlementDate, timestamp);
                
                if (!cashAvailable) {
                    log.warn("Step 6: Insufficient cash for DVP settlement: buyer={}, amount={}", 
                            buyerAccountId, settlementAmount);
                    settlementService.flagInsufficientCash(settlementId, buyerAccountId, settlementAmount, timestamp);
                    throw new IllegalArgumentException("Insufficient cash for DVP settlement");
                }
            }
            
            // Step 7: Earmark securities and cash for settlement
            settlementService.earmarkSecuritiesForSettlement(
                sellerAccountId, securityId, quantity, settlementId, timestamp);
            
            if ("DVP".equals(settlementType)) {
                settlementService.earmarkCashForSettlement(
                    buyerAccountId, settlementAmount, currency, settlementId, timestamp);
            }
            
            log.info("Step 7: Earmarked securities and cash for settlement");
            
            // Step 8: Create settlement instructions and submit to clearing agent
            SettlementInstruction instruction = settlementService.createSettlementInstruction(
                settlementId, tradeId, buyerAccountId, sellerAccountId, 
                securityId, quantity, price, settlementAmount, currency, 
                settlementType, settlementDate, clearingAgentId, 
                settlementInstructions, timestamp);
            
            log.info("Step 8: Created and submitted settlement instruction to clearing agent");
            
            // Step 9: Monitor settlement status and handle affirmations
            settlementService.monitorSettlementStatus(settlementId, clearingAgentId, timestamp);
            
            // Step 10: Execute DVP/RVP settlement atomically
            SecuritiesTrade settledTrade = settlementService.executeAtomicSettlement(
                settlementId, instruction, settlementType, timestamp);
            
            log.info("Step 9-10: Executed atomic {} settlement successfully: status={}", 
                    settlementType, settledTrade.getSettlementStatus());
            
            // Step 11: Update custody records and send confirmations
            custodyService.updateCustodyRecords(
                buyerAccountId, sellerAccountId, securityId, quantity, 
                settlementAmount, currency, timestamp);
            
            settlementService.sendSettlementConfirmations(
                settlementId, buyerAccountId, sellerAccountId, settledTrade, timestamp);
            
            log.info("Step 11: Updated custody records and sent confirmations");
            
            // Step 12: Generate regulatory reports and audit trail
            regulatoryReportingService.generateSecuritiesSettlementReports(
                settlementId, settledTrade, timestamp);
            
            settlementService.logSettlementEvent(
                settlementId, tradeId, buyerAccountId, sellerAccountId,
                securityId, securityType, quantity, settlementAmount,
                settlementType, settledTrade.getSettlementStatus(), timestamp);

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("settlementId", settlementId, "tradeId", tradeId,
                       "buyerAccountId", buyerAccountId, "sellerAccountId", sellerAccountId,
                       "securityId", securityId, "quantity", quantity.toString(),
                       "settlementAmount", settlementAmount.toString(),
                       "settlementType", settlementType, "status", "SETTLED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed securities settlement event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("SECURITY: Error processing securities settlement event: {}", e.getMessage(), e);
            // CRITICAL SECURITY: Mark operation as failed for retry logic
            String idempotencyKey = "securities-settlement:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("settlementId") || 
            !eventData.has("tradeId") || !eventData.has("buyerAccountId") ||
            !eventData.has("sellerAccountId") || !eventData.has("securityId") ||
            !eventData.has("quantity") || !eventData.has("price") ||
            !eventData.has("settlementAmount") || !eventData.has("currency") ||
            !eventData.has("settlementType") || !eventData.has("tradeDate") ||
            !eventData.has("settlementDate") || !eventData.has("clearingAgentId")) {
            throw new IllegalArgumentException("Invalid securities settlement event structure");
        }
    }
}