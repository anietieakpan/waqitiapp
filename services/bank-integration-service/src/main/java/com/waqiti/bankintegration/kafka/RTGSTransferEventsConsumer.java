package com.waqiti.bankintegration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.bankintegration.service.RTGSProcessingService;
import com.waqiti.bankintegration.service.HighValueTransferService;
import com.waqiti.bankintegration.service.LiquidityManagementService;
import com.waqiti.bankintegration.service.SettlementService;
import com.waqiti.bankintegration.service.AuditService;
import com.waqiti.bankintegration.entity.RTGSTransfer;
import com.waqiti.bankintegration.entity.LiquidityPosition;
import com.waqiti.bankintegration.entity.Settlement;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Critical Event Consumer #3: RTGS Transfer Events Consumer
 * Processes Real-Time Gross Settlement transfers, high-value payments, and central bank transactions
 * Implements 12-step zero-tolerance processing for RTGS settlement
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RTGSTransferEventsConsumer extends BaseKafkaConsumer {

    private final RTGSProcessingService rtgsProcessingService;
    private final HighValueTransferService highValueTransferService;
    private final LiquidityManagementService liquidityService;
    private final SettlementService settlementService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "rtgs-transfer-events", 
        groupId = "rtgs-transfer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "rtgs-transfer-consumer")
    @Retry(name = "rtgs-transfer-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleRTGSTransferEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "rtgs-transfer-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing RTGS transfer event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String transferId = eventData.path("transferId").asText();
            String originatingBankCode = eventData.path("originatingBankCode").asText();
            String receivingBankCode = eventData.path("receivingBankCode").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String priorityLevel = eventData.path("priorityLevel").asText(); // URGENT, HIGH, NORMAL
            LocalDateTime settlementDate = LocalDateTime.parse(eventData.path("settlementDate").asText());
            String rtgsMessageType = eventData.path("rtgsMessageType").asText(); // PACS.008, PACS.002
            String centralBankCode = eventData.path("centralBankCode").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted RTGS details: transferId={}, amount={} {}, priority={}, banks={}->{}", 
                    transferId, amount, currency, priorityLevel, originatingBankCode, receivingBankCode);
            
            // Step 3: RTGS eligibility and high-value transfer validation
            log.info("Step 3: Validating RTGS eligibility and high-value transfer requirements");
            RTGSTransfer rtgsTransfer = rtgsProcessingService.createRTGSTransfer(eventData);
            
            rtgsProcessingService.validateRTGSEligibility(rtgsTransfer);
            rtgsProcessingService.validateParticipatingBanks(originatingBankCode, receivingBankCode);
            rtgsProcessingService.validateCentralBankConnection(centralBankCode);
            
            highValueTransferService.validateHighValueThreshold(amount, currency);
            highValueTransferService.validateSettlementWindow(settlementDate);
            
            if (!rtgsProcessingService.isValidRTGSMessageType(rtgsMessageType)) {
                throw new IllegalStateException("Invalid RTGS message type: " + rtgsMessageType);
            }
            
            // Step 4: Liquidity verification and position management
            log.info("Step 4: Verifying liquidity position and managing settlement funds");
            LiquidityPosition senderPosition = liquidityService.getLiquidityPosition(originatingBankCode, currency);
            
            liquidityService.validateSufficientLiquidity(senderPosition, amount);
            liquidityService.reserveLiquidityForSettlement(senderPosition, amount);
            liquidityService.calculateIntracompanyLimits(rtgsTransfer);
            
            if (!liquidityService.hasSufficientIntracompanyLiquidity(senderPosition, amount)) {
                liquidityService.requestIntracompanyFunding(rtgsTransfer);
                liquidityService.escalateLiquidityShortfall(rtgsTransfer);
            }
            
            liquidityService.updateRealTimeLiquidityPosition(senderPosition, amount);
            
            // Step 5: Settlement timing and queue management
            log.info("Step 5: Managing settlement timing and RTGS queue processing");
            LocalTime currentTime = LocalTime.now();
            boolean withinSettlementWindow = rtgsProcessingService.isWithinSettlementWindow(currentTime);
            
            if (!withinSettlementWindow) {
                rtgsProcessingService.queueForNextSettlementWindow(rtgsTransfer);
                return;
            }
            
            if ("URGENT".equals(priorityLevel)) {
                rtgsProcessingService.prioritizeTransfer(rtgsTransfer);
            } else {
                rtgsProcessingService.addToSettlementQueue(rtgsTransfer, priorityLevel);
            }
            
            rtgsProcessingService.optimizeQueueExecution(rtgsTransfer);
            
            // Step 6: Central bank communication and authorization
            log.info("Step 6: Communicating with central bank and obtaining settlement authorization");
            String authorizationRequest = rtgsProcessingService.formatCentralBankMessage(rtgsTransfer);
            rtgsProcessingService.transmitToCentralBank(authorizationRequest, centralBankCode);
            
            String authorizationResponse = rtgsProcessingService.waitForCentralBankResponse(rtgsTransfer);
            boolean authorized = rtgsProcessingService.parseAuthorizationResponse(authorizationResponse);
            
            if (!authorized) {
                rtgsProcessingService.handleAuthorizationRejection(rtgsTransfer);
                liquidityService.releaseLiquidityReservation(senderPosition, amount);
                return;
            }
            
            rtgsProcessingService.confirmAuthorizationReceived(rtgsTransfer);
            
            // Step 7: Final irrevocable settlement execution
            log.info("Step 7: Executing final irrevocable settlement through RTGS");
            Settlement settlement = settlementService.createSettlement(rtgsTransfer);
            
            settlementService.executeIrrevocableSettlement(settlement);
            settlementService.updateCentralBankAccounts(settlement);
            settlementService.processGrossSettlement(settlement);
            
            liquidityService.debitOriginatingBank(originatingBankCode, amount, currency);
            liquidityService.creditReceivingBank(receivingBankCode, amount, currency);
            
            settlement.setStatus("SETTLED");
            settlement.setSettlementTime(LocalDateTime.now());
            
            // Step 8: Real-time position updates and reconciliation
            log.info("Step 8: Updating real-time positions and performing reconciliation");
            liquidityService.updateRealTimeNetPosition(originatingBankCode, amount.negate());
            liquidityService.updateRealTimeNetPosition(receivingBankCode, amount);
            liquidityService.reconcileLiquidityPositions(originatingBankCode, receivingBankCode);
            
            settlementService.updateSettlementStatistics(settlement);
            settlementService.validateBalanceConsistency(settlement);
            
            rtgsProcessingService.updateSystemLiquidity(amount, currency);
            
            // Step 9: Risk monitoring and exposure management
            log.info("Step 9: Monitoring settlement risk and managing counterparty exposure");
            highValueTransferService.updateCounterpartyExposure(originatingBankCode, receivingBankCode, amount);
            highValueTransferService.validateExposureLimits(originatingBankCode, receivingBankCode);
            
            rtgsProcessingService.monitorSystemicRisk(settlement);
            rtgsProcessingService.assessConcentrationRisk(receivingBankCode, amount);
            
            if (rtgsProcessingService.exceedsRiskThresholds(settlement)) {
                rtgsProcessingService.alertRiskManagement(settlement);
            }
            
            // Step 10: Operational efficiency and performance monitoring
            log.info("Step 10: Monitoring operational efficiency and system performance");
            rtgsProcessingService.measureSettlementLatency(rtgsTransfer);
            rtgsProcessingService.updateThroughputMetrics(settlement);
            rtgsProcessingService.monitorQueuePerformance();
            
            liquidityService.optimizeLiquidityUtilization();
            settlementService.analyzeDailySettlementVolume(settlement);
            
            // Step 11: Regulatory reporting and compliance
            log.info("Step 11: Generating regulatory reports and ensuring compliance");
            auditService.generateRTGSReport(settlement);
            auditService.updateCentralBankReporting(settlement);
            auditService.validateFinancialStabilityMetrics(settlement);
            
            if (amount.compareTo(new BigDecimal("1000000")) >= 0) { // Large value transactions
                auditService.generateLargeValueTransferReport(settlement);
            }
            
            auditService.maintainAuditTrail(settlement);
            
            // Step 12: Notification and audit completion
            log.info("Step 12: Completing notifications and finalizing audit records");
            rtgsProcessingService.notifyParticipatingBanks(settlement);
            rtgsProcessingService.updateTransferStatus(rtgsTransfer, "COMPLETED");
            
            auditService.logRTGSTransfer(rtgsTransfer);
            auditService.logSettlement(settlement);
            
            settlementService.archiveSettlementDocuments(settlement);
            liquidityService.updateLiquidityReports();
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed RTGS transfer: transferId={}, eventId={}, amount={} {}", 
                    transferId, eventId, amount, currency);
            
        } catch (Exception e) {
            log.error("Error processing RTGS transfer event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("transferId") || 
            !eventData.has("originatingBankCode") || !eventData.has("receivingBankCode") ||
            !eventData.has("amount") || !eventData.has("currency") ||
            !eventData.has("priorityLevel") || !eventData.has("settlementDate") ||
            !eventData.has("rtgsMessageType") || !eventData.has("centralBankCode")) {
            throw new IllegalArgumentException("Invalid RTGS transfer event structure");
        }
    }
}