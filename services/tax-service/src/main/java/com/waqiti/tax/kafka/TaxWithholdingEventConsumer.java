package com.waqiti.tax.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.tax.service.TaxWithholdingService;
import com.waqiti.tax.service.IRSReportingService;
import com.waqiti.tax.entity.TaxWithholding;
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
 * Critical Event Consumer #172: Tax Withholding Event Consumer
 * Processes federal and state tax withholding with IRS compliance
 * Implements 12-step zero-tolerance processing for backup withholding and W-9 validation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxWithholdingEventConsumer extends BaseKafkaConsumer {

    private final TaxWithholdingService taxWithholdingService;
    private final IRSReportingService irsReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "tax-withholding-events", groupId = "tax-withholding-group")
    @CircuitBreaker(name = "tax-withholding-consumer")
    @Retry(name = "tax-withholding-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleTaxWithholdingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "tax-withholding-event");
        
        try {
            log.info("Step 1: Processing tax withholding event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String withholdingId = eventData.path("withholdingId").asText();
            String accountId = eventData.path("accountId").asText();
            String taxIdNumber = eventData.path("taxIdNumber").asText();
            BigDecimal paymentAmount = new BigDecimal(eventData.path("paymentAmount").asText());
            String incomeType = eventData.path("incomeType").asText();
            String withholdingType = eventData.path("withholdingType").asText();
            boolean foreignAccount = eventData.path("foreignAccount").asBoolean();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted withholding details: accountId={}, amount={}, type={}", 
                    accountId, paymentAmount, withholdingType);
            
            boolean tinValidated = taxWithholdingService.validateTIN(taxIdNumber, timestamp);
            
            if (!tinValidated) {
                log.warn("Step 3: TIN validation failed, applying backup withholding");
                withholdingType = "BACKUP_WITHHOLDING";
            } else {
                log.info("Step 3: TIN validated successfully");
            }
            
            BigDecimal withholdingRate = taxWithholdingService.determineWithholdingRate(
                    withholdingType, incomeType, foreignAccount, timestamp);
            
            log.info("Step 4: Determined withholding rate: {}%", withholdingRate);

            BigDecimal withholdingAmount = paymentAmount.multiply(withholdingRate)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.CEILING); // Tax withholding rounds up

            log.info("Step 5: Calculated withholding amount: {}", withholdingAmount);
            
            TaxWithholding withholding = taxWithholdingService.createWithholdingRecord(
                    withholdingId, accountId, taxIdNumber, paymentAmount, 
                    withholdingAmount, withholdingType, incomeType, timestamp);
            
            log.info("Step 6: Created tax withholding record");
            
            if (foreignAccount) {
                boolean fbarRequired = taxWithholdingService.checkFBARRequirement(
                        accountId, timestamp);
                log.info("Step 7: Foreign account FBAR requirement: {}", fbarRequired);
            } else {
                log.info("Step 7: Domestic account, no FBAR required");
            }
            
            taxWithholdingService.withholdTaxFromPayment(accountId, withholdingAmount, 
                    withholdingId, timestamp);
            log.info("Step 8: Withheld tax from payment");
            
            taxWithholdingService.remitToTreasury(withholdingAmount, incomeType, timestamp);
            log.info("Step 9: Scheduled remittance to US Treasury");
            
            irsReportingService.record1099Entry(accountId, taxIdNumber, incomeType, 
                    paymentAmount, withholdingAmount, timestamp);
            log.info("Step 10: Recorded 1099 tax form entry");
            
            taxWithholdingService.sendWithholdingNotification(accountId, withholdingAmount, 
                    withholdingType, timestamp);
            log.info("Step 11: Sent tax withholding notification");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed tax withholding: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing tax withholding event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("withholdingId") || 
            !eventData.has("accountId") || !eventData.has("paymentAmount")) {
            throw new IllegalArgumentException("Invalid tax withholding event structure");
        }
    }
}