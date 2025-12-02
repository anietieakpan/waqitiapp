package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletComplianceService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Critical Event Consumer #265: Wallet Statement Event Consumer
 * Processes transaction history and reporting with GDPR compliance
 * Implements 12-step zero-tolerance processing for statements
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletStatementEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-statement-events", groupId = "wallet-statement-group")
    @CircuitBreaker(name = "wallet-statement-consumer")
    @Retry(name = "wallet-statement-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletStatementEvent(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-statement-event");

        try {
            log.info("Step 1: Processing wallet statement event: partition={}, offset={}",
                    partition, offset);
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String walletId = eventData.path("walletId").asText();
            String statementType = eventData.path("statementType").asText();
            LocalDate startDate = LocalDate.parse(eventData.path("startDate").asText());
            LocalDate endDate = LocalDate.parse(eventData.path("endDate").asText());
            String requestedBy = eventData.path("requestedBy").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted statement details: walletId={}, type={}, period={} to {}", 
                    walletId, statementType, startDate, endDate);
            
            // Step 3: Statement request authorization
            boolean authorized = walletService.validateStatementRequest(walletId, statementType, 
                    requestedBy, timestamp);
            if (!authorized) {
                log.error("Step 3: Statement request authorization failed for walletId={}", walletId);
                walletService.rejectStatementRequest(eventId, "UNAUTHORIZED_REQUEST", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Statement request authorized");
            
            // Step 4: GDPR data access validation
            boolean gdprCompliant = complianceService.validateGDPRDataAccess(walletId, requestedBy, 
                    statementType, timestamp);
            if (!gdprCompliant) {
                log.error("Step 4: GDPR compliance validation failed");
                walletService.escalateDataRequest(eventId, "GDPR_REVIEW_REQUIRED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: GDPR compliance validated");
            
            // Step 5: Data retention policy check
            boolean withinRetention = complianceService.validateDataRetentionPeriod(walletId, 
                    startDate, endDate, timestamp);
            if (!withinRetention) {
                log.warn("Step 5: Requested data outside retention period");
                walletService.limitStatementScope(eventId, walletId, timestamp);
            } else {
                log.info("Step 5: Data within retention period");
            }
            
            // Step 6: Transaction data aggregation
            BigDecimal totalInflow = walletService.calculateTotalInflow(walletId, startDate, endDate);
            BigDecimal totalOutflow = walletService.calculateTotalOutflow(walletId, startDate, endDate);
            int transactionCount = walletService.getTransactionCount(walletId, startDate, endDate);
            log.info("Step 6: Data aggregated - inflow={}, outflow={}, count={}", 
                    totalInflow, totalOutflow, transactionCount);
            
            // Step 7: PII data masking for external requests
            String maskedData;
            if ("EXTERNAL".equals(requestedBy)) {
                maskedData = complianceService.maskSensitiveData(walletId, startDate, endDate, timestamp);
                log.info("Step 7: PII data masked for external request");
            } else {
                maskedData = walletService.getFullTransactionData(walletId, startDate, endDate);
                log.info("Step 7: Full data prepared for internal request");
            }
            
            // Step 8: Statement generation with encryption
            String statementId = walletService.generateEncryptedStatement(eventId, walletId, 
                    statementType, maskedData, startDate, endDate, timestamp);
            log.info("Step 8: Encrypted statement generated: statementId={}", statementId);
            
            // Step 9: Digital signature application
            String digitalSignature = complianceService.applyDigitalSignature(statementId, 
                    walletId, timestamp);
            log.info("Step 9: Digital signature applied");
            
            // Step 10: Secure delivery preparation
            String deliveryMethod = eventData.path("deliveryMethod").asText("EMAIL");
            String deliveryReference = walletService.prepareSecureDelivery(statementId, walletId, 
                    deliveryMethod, timestamp);
            log.info("Step 10: Secure delivery prepared: method={}, reference={}", 
                    deliveryMethod, deliveryReference);
            
            // Step 11: Access logging and audit trail
            complianceService.logStatementAccess(eventId, walletId, statementType, requestedBy, 
                    startDate, endDate, timestamp);
            log.info("Step 11: Statement access logged");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet statement: eventId={}, statementId={}", 
                    eventId, statementId);
            
        } catch (Exception e) {
            log.error("Error processing wallet event: topic={}, partition={}, offset={}, error={}",
                    topic, partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet event - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet event processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("walletId") || 
            !eventData.has("statementType") || !eventData.has("startDate") || !eventData.has("endDate")) {
            throw new IllegalArgumentException("Invalid wallet statement event structure");
        }
    }
}