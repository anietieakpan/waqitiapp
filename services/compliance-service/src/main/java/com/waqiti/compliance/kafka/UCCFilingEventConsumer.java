package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.UCCFilingService;
import com.waqiti.compliance.service.CollateralManagementService;
import com.waqiti.compliance.entity.UCCFiling;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #174: UCC Filing Event Consumer
 * Processes Uniform Commercial Code secured transaction filings
 * Implements 12-step zero-tolerance processing for lien perfection and collateral tracking
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UCCFilingEventConsumer extends BaseKafkaConsumer {

    private final UCCFilingService uccFilingService;
    private final CollateralManagementService collateralManagementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ucc-filing-events", groupId = "ucc-filing-group")
    @CircuitBreaker(name = "ucc-filing-consumer")
    @Retry(name = "ucc-filing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleUCCFilingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "ucc-filing-event");
        
        try {
            log.info("Step 1: Processing UCC filing event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String filingId = eventData.path("filingId").asText();
            String loanId = eventData.path("loanId").asText();
            String debtorName = eventData.path("debtorName").asText();
            String debtorTaxId = eventData.path("debtorTaxId").asText();
            String securedParty = eventData.path("securedParty").asText();
            String collateralDescription = eventData.path("collateralDescription").asText();
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal securedAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("securedAmount").asText(),
                BigDecimal.ZERO
            );
            String filingType = eventData.path("filingType").asText();
            String jurisdiction = eventData.path("jurisdiction").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted UCC filing details: filingId={}, type={}, jurisdiction={}", 
                    filingId, filingType, jurisdiction);
            
            boolean validCollateral = collateralManagementService.validateCollateralDescription(
                    collateralDescription, timestamp);
            
            if (!validCollateral) {
                log.error("Step 3: Invalid collateral description, cannot file UCC");
                uccFilingService.rejectFiling(filingId, "INVALID_COLLATERAL", timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 3: Collateral description validated");
            
            UCCFiling filing = uccFilingService.prepareUCCForm(filingId, loanId, 
                    debtorName, debtorTaxId, securedParty, collateralDescription, 
                    securedAmount, filingType, jurisdiction, timestamp);
            
            log.info("Step 4: Prepared UCC-1 financing statement");
            
            String filingNumber = uccFilingService.submitToSecretaryOfState(
                    filing, jurisdiction, timestamp);
            
            log.info("Step 5: Submitted UCC filing to Secretary of State: {}", filingNumber);
            
            LocalDate expirationDate = uccFilingService.calculateExpirationDate(
                    timestamp.toLocalDate(), jurisdiction);
            
            log.info("Step 6: Calculated UCC expiration date: {}", expirationDate);
            
            uccFilingService.recordFilingNumber(filingId, filingNumber, expirationDate, timestamp);
            log.info("Step 7: Recorded UCC filing number and expiration");
            
            collateralManagementService.perfectSecurityInterest(loanId, filingNumber, 
                    collateralDescription, timestamp);
            log.info("Step 8: Perfected security interest");
            
            uccFilingService.scheduleRenewalReminder(filingNumber, expirationDate, timestamp);
            log.info("Step 9: Scheduled UCC renewal reminder");
            
            collateralManagementService.updateLoanCollateralStatus(loanId, 
                    "PERFECTED", filingNumber, timestamp);
            log.info("Step 10: Updated loan collateral status");
            
            uccFilingService.sendFilingConfirmation(loanId, debtorName, filingNumber, 
                    expirationDate, timestamp);
            log.info("Step 11: Sent UCC filing confirmation");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed UCC filing: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing UCC filing event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("filingId") || 
            !eventData.has("debtorName") || !eventData.has("collateralDescription")) {
            throw new IllegalArgumentException("Invalid UCC filing event structure");
        }
    }
}