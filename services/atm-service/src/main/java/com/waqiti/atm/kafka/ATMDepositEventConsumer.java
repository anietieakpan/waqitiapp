package com.waqiti.atm.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.common.util.DataMaskingUtil;
import com.waqiti.atm.service.ATMDepositService;
import com.waqiti.atm.service.CheckImagingService;
import com.waqiti.atm.entity.ATMDeposit;
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
 * Critical Event Consumer #309: ATM Deposit Event Consumer
 * Processes cash/check deposit with imaging and regulatory compliance
 * Implements 12-step zero-tolerance processing for ATM deposits
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ATMDepositEventConsumer extends BaseKafkaConsumer {

    private final ATMDepositService atmDepositService;
    private final CheckImagingService checkImagingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "atm-deposit-events", groupId = "atm-deposit-group")
    @CircuitBreaker(name = "atm-deposit-consumer")
    @Retry(name = "atm-deposit-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleATMDepositEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "atm-deposit-event");
        
        try {
            log.info("Step 1: Processing ATM deposit event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String depositId = eventData.path("depositId").asText();
            String atmId = eventData.path("atmId").asText();
            String cardNumber = eventData.path("cardNumber").asText();
            String accountId = eventData.path("accountId").asText();
            String depositType = eventData.path("depositType").asText(); // CASH, CHECK, MIXED
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal cashAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("cashAmount").asText(),
                BigDecimal.ZERO
            );
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal checkAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("checkAmount").asText(),
                BigDecimal.ZERO
            );
            List<String> checkImages = objectMapper.convertValue(
                    eventData.path("checkImages"), List.class);
            Integer numberOfChecks = eventData.path("numberOfChecks").asInt();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted deposit details: depositId={}, type={}, cash={}, checks={}", 
                    depositId, depositType, cashAmount, checkAmount);
            
            // Step 3: Validate card and account
            boolean cardValid = atmDepositService.validateCardForDeposit(cardNumber, accountId, timestamp);
            
            if (!cardValid) {
                // PCI DSS COMPLIANCE: Mask card number in logs per PCI DSS v4.0 Requirement 3.3
                log.error("Step 3: Card validation failed for deposit: cardNumber={}", DataMaskingUtil.maskCardNumber(cardNumber));
                atmDepositService.rejectDeposit(depositId, "INVALID_CARD", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Card and account validated");
            
            // Step 4: Validate deposit limits
            BigDecimal totalAmount = cashAmount.add(checkAmount);
            boolean withinLimits = atmDepositService.validateDepositLimits(
                    cardNumber, accountId, totalAmount, depositType, timestamp);
            
            if (!withinLimits) {
                log.error("Step 4: Deposit exceeds limits: amount={}", totalAmount);
                atmDepositService.rejectDeposit(depositId, "LIMIT_EXCEEDED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Deposit limits validated");
            
            // Step 5: Process check imaging if applicable
            boolean checkImagingSuccess = true;
            if (checkAmount.compareTo(BigDecimal.ZERO) > 0) {
                checkImagingSuccess = checkImagingService.processCheckImages(
                        depositId, checkImages, numberOfChecks, timestamp);
                
                if (!checkImagingSuccess) {
                    log.error("Step 5: Check imaging failed for depositId={}", depositId);
                    atmDepositService.rejectDeposit(depositId, "CHECK_IMAGING_FAILED", timestamp);
                    ack.acknowledge();
                    return;
                }
                log.info("Step 5: Check imaging processed successfully");
            } else {
                log.info("Step 5: No checks to process");
            }
            
            // Step 6: Validate check amounts if applicable
            if (checkAmount.compareTo(BigDecimal.ZERO) > 0) {
                boolean amountsValid = checkImagingService.validateCheckAmounts(
                        depositId, checkAmount, numberOfChecks, timestamp);
                
                if (!amountsValid) {
                    log.error("Step 6: Check amounts validation failed for depositId={}", depositId);
                    atmDepositService.holdDeposit(depositId, "CHECK_AMOUNT_MISMATCH", timestamp);
                    ack.acknowledge();
                    return;
                }
                log.info("Step 6: Check amounts validated");
            } else {
                log.info("Step 6: No check amounts to validate");
            }
            
            // Step 7: Create deposit record
            ATMDeposit deposit = atmDepositService.createDeposit(
                    depositId, atmId, cardNumber, accountId, depositType,
                    cashAmount, checkAmount, numberOfChecks, timestamp);
            log.info("Step 7: Created ATM deposit record");
            
            // Step 8: Process cash portion immediately
            if (cashAmount.compareTo(BigDecimal.ZERO) > 0) {
                atmDepositService.processCashDeposit(depositId, cashAmount, timestamp);
                atmDepositService.updateAccountBalance(accountId, cashAmount, timestamp);
                log.info("Step 8: Processed cash deposit: {}", cashAmount);
            } else {
                log.info("Step 8: No cash to process");
            }
            
            // Step 9: Handle check portion with holds
            if (checkAmount.compareTo(BigDecimal.ZERO) > 0) {
                atmDepositService.applyCheckHolds(depositId, checkAmount, timestamp);
                atmDepositService.scheduleCheckProcessing(depositId, timestamp);
                log.info("Step 9: Applied check holds and scheduled processing");
            } else {
                log.info("Step 9: No checks to hold");
            }
            
            // Step 10: Update ATM counters
            atmDepositService.updateATMCounters(atmId, cashAmount, numberOfChecks, timestamp);
            log.info("Step 10: Updated ATM counters");
            
            // Step 11: Generate deposit receipt
            String receiptNumber = atmDepositService.generateDepositReceipt(depositId, timestamp);
            log.info("Step 11: Generated deposit receipt: {}", receiptNumber);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed ATM deposit: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing ATM deposit event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("depositId") || 
            !eventData.has("atmId") || !eventData.has("depositType")) {
            throw new IllegalArgumentException("Invalid ATM deposit event structure");
        }
    }
}