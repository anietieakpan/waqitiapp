package com.waqiti.corebanking.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.corebanking.service.ClearingHouseService;
import com.waqiti.corebanking.service.SwiftIntegrationService;
import com.waqiti.corebanking.service.ComplianceIntegrationService;
import com.waqiti.corebanking.service.RegulatoryReportingService;
import com.waqiti.corebanking.entity.ClearingTransaction;
import com.waqiti.corebanking.entity.SwiftMessage;
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
 * Critical Event Consumer #102: Clearing House Event Consumer
 * Processes inter-bank clearing transactions with SWIFT integration and regulatory compliance
 * Implements 12-step zero-tolerance processing for secure inter-bank clearing workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClearingHouseEventConsumer extends BaseKafkaConsumer {

    private final ClearingHouseService clearingHouseService;
    private final SwiftIntegrationService swiftIntegrationService;
    private final ComplianceIntegrationService complianceService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "clearing-house-events", groupId = "clearing-house-group")
    @CircuitBreaker(name = "clearing-house-consumer")
    @Retry(name = "clearing-house-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleClearingHouseEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "clearing-house-event");
        
        try {
            log.info("Step 1: Processing clearing house event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String clearingId = eventData.path("clearingId").asText();
            String clearingType = eventData.path("clearingType").asText(); // ACH, WIRE, CHECK, RTP
            String originatingBankBIC = eventData.path("originatingBankBIC").asText();
            String receivingBankBIC = eventData.path("receivingBankBIC").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String clearingChannel = eventData.path("clearingChannel").asText(); // FEDWIRE, ACH, CHIPS
            String priority = eventData.path("priority").asText(); // HIGH, NORMAL, LOW
            List<String> transactionIds = objectMapper.convertValue(
                eventData.path("transactionIds"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            LocalDateTime valueDate = LocalDateTime.parse(eventData.path("valueDate").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted clearing details: clearingId={}, clearingType={}, amount={}, channel={}", 
                    clearingId, clearingType, amount, clearingChannel);
            
            // Step 3: Validate participating banks and their settlement accounts
            clearingHouseService.validateParticipatingBanks(originatingBankBIC, receivingBankBIC, currency, timestamp);
            
            log.info("Step 3: Validated participating banks: originating={}, receiving={}", 
                    originatingBankBIC, receivingBankBIC);
            
            // Step 4: Perform regulatory compliance checks (BSA/AML, OFAC, sanctions)
            complianceService.performClearingComplianceCheck(
                originatingBankBIC, receivingBankBIC, amount, currency, clearingType, timestamp);
            
            log.info("Step 4: Completed compliance checks for clearing transaction");
            
            // Step 5: Check clearing limits and risk thresholds
            boolean withinLimits = clearingHouseService.validateClearingLimits(
                originatingBankBIC, receivingBankBIC, amount, currency, clearingType, timestamp);
            
            if (!withinLimits) {
                log.warn("Step 5: Clearing transaction exceeds limits: clearingId={}", clearingId);
                clearingHouseService.flagLimitExceedance(clearingId, amount, "CLEARING_LIMIT_EXCEEDED", timestamp);
                throw new IllegalArgumentException("Clearing transaction exceeds established limits");
            }
            
            // Step 6: Reserve funds at originating bank
            clearingHouseService.reserveClearingFunds(
                clearingId, originatingBankBIC, amount, currency, valueDate, timestamp);
            
            log.info("Step 6: Reserved clearing funds at originating bank");
            
            // Step 7: Generate SWIFT messages for inter-bank communication
            SwiftMessage swiftMessage = swiftIntegrationService.generateClearingSwiftMessage(
                clearingId, clearingType, originatingBankBIC, receivingBankBIC, 
                amount, currency, clearingChannel, priority, valueDate, timestamp);
            
            log.info("Step 7: Generated SWIFT message: messageType={}, reference={}", 
                    swiftMessage.getMessageType(), swiftMessage.getReference());
            
            // Step 8: Submit to appropriate clearing network
            ClearingTransaction clearingTxn = clearingHouseService.submitToClearingNetwork(
                clearingId, clearingType, clearingChannel, swiftMessage, 
                transactionIds, priority, valueDate, timestamp);
            
            log.info("Step 8: Submitted to clearing network: clearingId={}, status={}", 
                    clearingId, clearingTxn.getStatus());
            
            // Step 9: Monitor clearing status and handle responses
            clearingHouseService.monitorClearingStatus(clearingId, clearingChannel, timestamp);
            
            // Step 10: Generate regulatory reports for large value transfers
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                regulatoryReportingService.generateClearingReports(
                    clearingId, originatingBankBIC, receivingBankBIC, amount, 
                    currency, clearingType, timestamp);
                
                log.info("Step 10: Generated regulatory reports for large value transfer");
            }
            
            // Step 11: Send clearing notifications to participating banks
            clearingHouseService.sendClearingNotifications(
                clearingId, originatingBankBIC, receivingBankBIC, 
                clearingTxn.getStatus(), valueDate, timestamp);
            
            log.info("Step 11: Sent clearing notifications to participating banks");
            
            // Step 12: Log clearing event for audit trail and update transaction statuses
            clearingHouseService.logClearingEvent(
                clearingId, clearingType, originatingBankBIC, receivingBankBIC, 
                amount, currency, clearingChannel, clearingTxn.getStatus(), timestamp);
            
            clearingHouseService.updateTransactionClearingStatus(transactionIds, clearingId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed clearing house event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing clearing house event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("clearingId") || 
            !eventData.has("clearingType") || !eventData.has("originatingBankBIC") ||
            !eventData.has("receivingBankBIC") || !eventData.has("amount") ||
            !eventData.has("currency") || !eventData.has("clearingChannel") ||
            !eventData.has("transactionIds") || !eventData.has("valueDate")) {
            throw new IllegalArgumentException("Invalid clearing house event structure");
        }
    }
}