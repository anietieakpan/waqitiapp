package com.waqiti.legal.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.legal.service.BankruptcyProcessingService;
import com.waqiti.legal.service.AutomaticStayService;
import com.waqiti.legal.domain.BankruptcyCase;
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
 * Critical Event Consumer #175: Bankruptcy Notification Event Consumer
 * Processes bankruptcy notifications with automatic stay enforcement
 * Implements 12-step zero-tolerance processing for bankruptcy code compliance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankruptcyNotificationEventConsumer extends BaseKafkaConsumer {

    private final BankruptcyProcessingService bankruptcyProcessingService;
    private final AutomaticStayService automaticStayService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "bankruptcy-notification-events", groupId = "bankruptcy-notification-group")
    @CircuitBreaker(name = "bankruptcy-notification-consumer")
    @Retry(name = "bankruptcy-notification-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleBankruptcyNotificationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "bankruptcy-notification-event");
        
        try {
            log.info("Step 1: Processing bankruptcy notification event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String bankruptcyId = eventData.path("bankruptcyId").asText();
            String customerId = eventData.path("customerId").asText();
            String caseNumber = eventData.path("caseNumber").asText();
            String bankruptcyChapter = eventData.path("bankruptcyChapter").asText();
            LocalDate filingDate = LocalDate.parse(eventData.path("filingDate").asText());
            String courtDistrict = eventData.path("courtDistrict").asText();
            String trusteeInfo = eventData.path("trusteeInfo").asText();
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal totalDebtAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("totalDebtAmount").asText(),
                BigDecimal.ZERO
            );
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted bankruptcy details: caseNumber={}, chapter={}, customer={}", 
                    caseNumber, bankruptcyChapter, customerId);
            
            BankruptcyCase bankruptcyCase = bankruptcyProcessingService.createBankruptcyRecord(
                    bankruptcyId, customerId, caseNumber, bankruptcyChapter, 
                    filingDate, courtDistrict, trusteeInfo, totalDebtAmount, timestamp);
            
            log.info("Step 3: Created bankruptcy case record");
            
            automaticStayService.enforceAutomaticStay(customerId, caseNumber, 
                    filingDate, timestamp);
            log.info("Step 4: ENFORCED AUTOMATIC STAY - All collection activities halted");
            
            bankruptcyProcessingService.freezeAllAccounts(customerId, bankruptcyId, timestamp);
            log.info("Step 5: Froze all customer accounts");
            
            bankruptcyProcessingService.cancelPendingTransactions(customerId, timestamp);
            log.info("Step 6: Cancelled all pending transactions");
            
            BigDecimal claimAmount = bankruptcyProcessingService.calculateCreditorClaim(
                    customerId, timestamp);
            
            log.info("Step 7: Calculated creditor claim amount: {}", claimAmount);
            
            bankruptcyProcessingService.fileProofOfClaim(caseNumber, claimAmount, 
                    courtDistrict, timestamp);
            log.info("Step 8: Filed proof of claim with bankruptcy court");
            
            if ("CHAPTER_13".equals(bankruptcyChapter)) {
                bankruptcyProcessingService.prepareRepaymentPlan(customerId, 
                        claimAmount, timestamp);
                log.info("Step 9: Prepared Chapter 13 repayment plan");
            } else if ("CHAPTER_7".equals(bankruptcyChapter)) {
                bankruptcyProcessingService.identifyExemptAssets(customerId, timestamp);
                log.info("Step 9: Identified exempt assets for Chapter 7");
            } else {
                log.info("Step 9: Processing {} bankruptcy type", bankruptcyChapter);
            }
            
            automaticStayService.notifyAllDepartments(customerId, caseNumber, timestamp);
            log.info("Step 10: Notified all departments of automatic stay");
            
            bankruptcyProcessingService.flagCreditReporting(customerId, bankruptcyChapter, 
                    filingDate, timestamp);
            log.info("Step 11: Flagged credit bureaus of bankruptcy filing");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed bankruptcy notification: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing bankruptcy notification event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("bankruptcyId") || 
            !eventData.has("customerId") || !eventData.has("caseNumber")) {
            throw new IllegalArgumentException("Invalid bankruptcy notification event structure");
        }
    }
}