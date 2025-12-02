package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.CTRFilingService;
import com.waqiti.compliance.service.FINCENReportingService;
import com.waqiti.compliance.entity.CurrencyTransactionReport;
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
import java.util.UUID;

/**
 * Critical Event Consumer #113: CTR Filing Event Consumer
 * Processes Currency Transaction Reports for transactions $10,000+ with FinCEN compliance
 * Implements 12-step zero-tolerance processing for BSA/AML regulatory reporting
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CTRFilingEventConsumer extends BaseKafkaConsumer {

    private final CTRFilingService ctrFilingService;
    private final FINCENReportingService fincenReportingService;
    private final UniversalDLQHandler universalDLQHandler;
    private final ObjectMapper objectMapper;
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000.00");

    @KafkaListener(topics = "ctr-filing-events", groupId = "ctr-filing-group")
    @CircuitBreaker(name = "ctr-filing-consumer")
    @Retry(name = "ctr-filing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCTRFilingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "ctr-filing-event");
        MDC.put("regulatoryReport", "CTR");
        
        try {
            log.info("Step 1: Processing CTR filing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String transactionId = eventData.path("transactionId").asText();
            String customerId = eventData.path("customerId").asText();
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal amount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("amount").asText(),
                BigDecimal.ZERO
            );
            String currency = eventData.path("currency").asText();
            String transactionType = eventData.path("transactionType").asText();
            String accountNumber = eventData.path("accountNumber").asText();
            String tinSSN = eventData.path("tinSSN").asText();
            String customerName = eventData.path("customerName").asText();
            String address = eventData.path("address").asText();
            LocalDateTime transactionDate = LocalDateTime.parse(eventData.path("transactionDate").asText());
            
            log.info("Step 2: Extracted CTR details: transactionId={}, amount={}, type={}", 
                    transactionId, amount, transactionType);
            
            // Step 3: Validate amount meets CTR threshold
            if (amount.compareTo(CTR_THRESHOLD) < 0) {
                log.warn("Step 3: Transaction below CTR threshold: amount={}, threshold={}", 
                        amount, CTR_THRESHOLD);
                ack.acknowledge();
                return;
            }
            
            // Step 4: Check for existing CTR to avoid duplicates
            boolean existingCTR = ctrFilingService.checkExistingCTR(transactionId, transactionDate);
            
            if (existingCTR) {
                log.info("Step 4: CTR already filed for transaction: {}", transactionId);
                ack.acknowledge();
                return;
            }
            
            // Step 5: Aggregate daily cash transactions for customer
            BigDecimal dailyTotal = ctrFilingService.aggregateDailyCashTransactions(
                    customerId, transactionDate.toLocalDate());
            
            log.info("Step 5: Aggregated daily cash transactions: total={}", dailyTotal);
            
            // Step 6: Verify customer identity information (BSA 326 requirement)
            boolean identityVerified = ctrFilingService.verifyCustomerIdentity(
                    customerId, tinSSN, customerName, address);
            
            if (!identityVerified) {
                log.error("Step 6: CRITICAL - Customer identity verification failed: {}", customerId);
                ctrFilingService.flagForComplianceReview(transactionId, "IDENTITY_VERIFICATION_FAILED");
            }
            
            // Step 7: Detect potential structuring (smurfing)
            boolean potentialStructuring = ctrFilingService.detectStructuring(
                    customerId, amount, transactionDate, dailyTotal);
            
            if (potentialStructuring) {
                log.warn("Step 7: Potential structuring detected: customerId={}, amount={}", 
                        customerId, amount);
                ctrFilingService.escalateForInvestigation(transactionId, "STRUCTURING_SUSPECTED");
            }
            
            // Step 8: Create CTR (FinCEN Form 112)
            CurrencyTransactionReport ctr = ctrFilingService.createCTR(
                    transactionId, customerId, amount, currency, transactionType, 
                    accountNumber, tinSSN, customerName, address, transactionDate);
            
            log.info("Step 8: Created CTR: ctrId={}, BSA ID={}", ctr.getCtrId(), ctr.getBsaIdentifier());
            
            // Step 9: Validate CTR completeness (FinCEN requirements)
            ctrFilingService.validateCTRCompleteness(ctr);
            
            log.info("Step 9: Validated CTR completeness");
            
            // Step 10: Submit to FinCEN BSA E-Filing System
            String filingConfirmation = fincenReportingService.submitCTR(ctr);
            
            log.info("Step 10: Submitted CTR to FinCEN: confirmationNumber={}", filingConfirmation);
            
            // Step 11: Update customer risk profile
            ctrFilingService.updateCustomerRiskProfile(customerId, amount, transactionType, transactionDate);
            
            // Step 12: Archive CTR with 5-year retention (BSA requirement)
            ctrFilingService.archiveCTR(ctr.getCtrId(), filingConfirmation, transactionDate);
            
            log.info("Step 12: Archived CTR with 5-year retention: ctrId={}", ctr.getCtrId());
            
            ack.acknowledge();
            log.info("Successfully processed CTR filing event: eventId={}, ctrId={}", 
                    eventId, ctr.getCtrId());
            
        } catch (Exception e) {
            log.error("Error processing CTR filing event: {}", e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send CTR filing event to DLQ", dlqEx);
            }

            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("transactionId") || 
            !eventData.has("customerId") || !eventData.has("amount") || 
            !eventData.has("tinSSN")) {
            throw new IllegalArgumentException("Invalid CTR filing event structure");
        }
    }
}