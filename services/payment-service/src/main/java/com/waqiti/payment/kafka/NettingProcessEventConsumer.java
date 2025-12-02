package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.NettingService;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.service.RegulatoryReportingService;
import com.waqiti.payment.entity.NettingBatch;
import com.waqiti.payment.entity.NettingAgreement;
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
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #107: Netting Process Event Consumer
 * Processes bilateral and multilateral netting with regulatory compliance
 * Implements 12-step zero-tolerance processing for secure netting workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettingProcessEventConsumer extends BaseKafkaConsumer {

    private final NettingService nettingService;
    private final SettlementService settlementService;
    private final ComplianceService complianceService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "netting-process-events", groupId = "netting-process-group")
    @CircuitBreaker(name = "netting-process-consumer")
    @Retry(name = "netting-process-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleNettingProcessEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "netting-process-event");
        
        try {
            log.info("Step 1: Processing netting process event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String nettingId = eventData.path("nettingId").asText();
            String nettingType = eventData.path("nettingType").asText(); // BILATERAL, MULTILATERAL
            String nettingCycle = eventData.path("nettingCycle").asText(); // DAILY, WEEKLY, MONTHLY
            List<String> participantIds = objectMapper.convertValue(
                eventData.path("participantIds"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            List<String> transactionIds = objectMapper.convertValue(
                eventData.path("transactionIds"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String currency = eventData.path("currency").asText();
            String clearingHouse = eventData.path("clearingHouse").asText();
            LocalDateTime nettingDate = LocalDateTime.parse(eventData.path("nettingDate").asText());
            LocalDateTime cutoffTime = LocalDateTime.parse(eventData.path("cutoffTime").asText());
            Map<String, BigDecimal> grossAmounts = objectMapper.convertValue(
                eventData.path("grossAmounts"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, BigDecimal.class)
            );
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted netting details: nettingId={}, type={}, participants={}, transactions={}", 
                    nettingId, nettingType, participantIds.size(), transactionIds.size());
            
            // Step 3: Validate netting agreements and participant eligibility
            List<NettingAgreement> agreements = nettingService.validateNettingAgreements(
                participantIds, nettingType, currency, timestamp);
            
            log.info("Step 3: Validated netting agreements for {} participants", participantIds.size());
            
            // Step 4: Perform regulatory compliance checks (Basel III, CPMI-IOSCO)
            complianceService.performNettingComplianceCheck(
                nettingId, nettingType, participantIds, transactionIds, 
                currency, clearingHouse, timestamp);
            
            log.info("Step 4: Completed compliance checks for netting process");
            
            // Step 5: Verify transaction eligibility for netting
            List<String> eligibleTransactions = nettingService.validateTransactionEligibility(
                transactionIds, nettingDate, cutoffTime, currency, timestamp);
            
            if (eligibleTransactions.size() != transactionIds.size()) {
                log.warn("Step 5: Some transactions ineligible for netting: eligible={}, total={}", 
                        eligibleTransactions.size(), transactionIds.size());
            }
            
            // Step 6: Calculate gross settlement amounts by participant
            Map<String, BigDecimal> calculatedAmounts = nettingService.calculateGrossAmounts(
                eligibleTransactions, participantIds, currency, timestamp);
            
            // Verify calculated amounts match provided gross amounts
            nettingService.validateGrossAmounts(grossAmounts, calculatedAmounts, timestamp);
            
            log.info("Step 6: Calculated and validated gross settlement amounts");
            
            // Step 7: Execute netting algorithm (bilateral or multilateral)
            Map<String, BigDecimal> netAmounts = nettingService.executeNettingAlgorithm(
                nettingType, participantIds, calculatedAmounts, timestamp);
            
            log.info("Step 7: Executed {} netting algorithm", nettingType);
            
            // Step 8: Create netting batch and settlement instructions
            NettingBatch nettingBatch = nettingService.createNettingBatch(
                nettingId, nettingType, participantIds, eligibleTransactions, 
                grossAmounts, netAmounts, currency, nettingDate, timestamp);
            
            log.info("Step 8: Created netting batch: batchId={}, netParticipants={}", 
                    nettingBatch.getBatchId(), netAmounts.size());
            
            // Step 9: Generate settlement instructions for net amounts
            nettingService.generateSettlementInstructions(
                nettingBatch, netAmounts, clearingHouse, timestamp);
            
            log.info("Step 9: Generated settlement instructions for net amounts");
            
            // Step 10: Submit to clearing house for settlement
            settlementService.submitNettingBatchForSettlement(
                nettingBatch, clearingHouse, timestamp);
            
            log.info("Step 10: Submitted netting batch to clearing house for settlement");
            
            // Step 11: Send netting confirmations and reports to participants
            nettingService.sendNettingConfirmations(
                nettingId, participantIds, netAmounts, nettingDate, timestamp);
            
            // Generate regulatory reports for large netting cycles
            BigDecimal totalNetAmount = netAmounts.values().stream()
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            if (totalNetAmount.compareTo(new BigDecimal("10000000")) >= 0) {
                regulatoryReportingService.generateNettingReports(
                    nettingBatch, totalNetAmount, timestamp);
                
                log.info("Step 11: Generated regulatory reports for large netting cycle");
            }
            
            // Step 12: Log netting process for audit trail and update transaction statuses
            nettingService.logNettingProcessEvent(
                nettingId, nettingType, participantIds, eligibleTransactions, 
                grossAmounts, netAmounts, totalNetAmount, 
                nettingBatch.getStatus(), timestamp);
            
            nettingService.updateTransactionNettingStatus(eligibleTransactions, nettingId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed netting process event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing netting process event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("nettingId") || 
            !eventData.has("nettingType") || !eventData.has("nettingCycle") ||
            !eventData.has("participantIds") || !eventData.has("transactionIds") ||
            !eventData.has("currency") || !eventData.has("clearingHouse") ||
            !eventData.has("nettingDate") || !eventData.has("cutoffTime") ||
            !eventData.has("grossAmounts")) {
            throw new IllegalArgumentException("Invalid netting process event structure");
        }
    }
}