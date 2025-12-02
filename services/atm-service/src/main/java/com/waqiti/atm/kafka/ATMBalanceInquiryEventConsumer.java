package com.waqiti.atm.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.util.DataMaskingUtil;
import com.waqiti.atm.service.ATMInquiryService;
import com.waqiti.atm.service.MiniStatementService;
import com.waqiti.atm.entity.ATMInquiry;
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
 * Critical Event Consumer #310: ATM Balance Inquiry Event Consumer
 * Processes balance check and mini-statement with regulatory compliance
 * Implements 12-step zero-tolerance processing for ATM inquiries
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ATMBalanceInquiryEventConsumer extends BaseKafkaConsumer {

    private final ATMInquiryService atmInquiryService;
    private final MiniStatementService miniStatementService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "atm-balance-inquiry-events", groupId = "atm-balance-inquiry-group")
    @CircuitBreaker(name = "atm-balance-inquiry-consumer")
    @Retry(name = "atm-balance-inquiry-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleATMBalanceInquiryEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "atm-balance-inquiry-event");
        
        try {
            log.info("Step 1: Processing ATM balance inquiry event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String inquiryId = eventData.path("inquiryId").asText();
            String atmId = eventData.path("atmId").asText();
            String cardNumber = eventData.path("cardNumber").asText();
            String accountId = eventData.path("accountId").asText();
            String inquiryType = eventData.path("inquiryType").asText(); // BALANCE, MINI_STATEMENT
            String pinHash = eventData.path("pinHash").asText();
            boolean receiptRequested = eventData.path("receiptRequested").asBoolean();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted inquiry details: inquiryId={}, type={}, atmId={}", 
                    inquiryId, inquiryType, atmId);
            
            // Step 3: Validate PIN
            boolean pinValid = atmInquiryService.validatePIN(cardNumber, pinHash, timestamp);
            
            if (!pinValid) {
                // PCI DSS COMPLIANCE: Mask card number in logs per PCI DSS v4.0 Requirement 3.3
                log.error("Step 3: PIN validation failed for cardNumber={}", DataMaskingUtil.maskCardNumber(cardNumber));
                atmInquiryService.rejectInquiry(inquiryId, "INVALID_PIN", timestamp);
                atmInquiryService.incrementFailedAttempts(cardNumber, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: PIN validated");
            
            // Step 4: Validate card status
            boolean cardActive = atmInquiryService.validateCardStatus(cardNumber, timestamp);
            
            if (!cardActive) {
                // PCI DSS COMPLIANCE: Mask card number in logs per PCI DSS v4.0 Requirement 3.3
                log.error("Step 4: Card not active: cardNumber={}", DataMaskingUtil.maskCardNumber(cardNumber));
                atmInquiryService.rejectInquiry(inquiryId, "CARD_INACTIVE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Card status validated");
            
            // Step 5: Validate account access
            boolean accountAccessible = atmInquiryService.validateAccountAccess(
                    cardNumber, accountId, timestamp);
            
            if (!accountAccessible) {
                log.error("Step 5: Account access denied: accountId={}", accountId);
                atmInquiryService.rejectInquiry(inquiryId, "ACCOUNT_ACCESS_DENIED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Account access validated");
            
            // Step 6: Check inquiry limits
            boolean withinInquiryLimits = atmInquiryService.validateInquiryLimits(
                    cardNumber, inquiryType, timestamp);
            
            if (!withinInquiryLimits) {
                // PCI DSS COMPLIANCE: Mask card number in logs per PCI DSS v4.0 Requirement 3.3
                log.error("Step 6: Inquiry limits exceeded: cardNumber={}", DataMaskingUtil.maskCardNumber(cardNumber));
                atmInquiryService.rejectInquiry(inquiryId, "INQUIRY_LIMIT_EXCEEDED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: Inquiry limits validated");
            
            // Step 7: Create inquiry record
            ATMInquiry inquiry = atmInquiryService.createInquiry(
                    inquiryId, atmId, cardNumber, accountId, inquiryType, 
                    receiptRequested, timestamp);
            log.info("Step 7: Created ATM inquiry record");
            
            // Step 8: Process based on inquiry type
            if ("BALANCE".equals(inquiryType)) {
                BigDecimal availableBalance = atmInquiryService.getAvailableBalance(accountId, timestamp);
                BigDecimal currentBalance = atmInquiryService.getCurrentBalance(accountId, timestamp);
                atmInquiryService.setBalanceInformation(inquiryId, availableBalance, currentBalance, timestamp);
                log.info("Step 8: Retrieved balance information: available={}, current={}", 
                        availableBalance, currentBalance);
                
            } else if ("MINI_STATEMENT".equals(inquiryType)) {
                List<String> recentTransactions = miniStatementService.getRecentTransactions(
                        accountId, 10, timestamp);
                atmInquiryService.setMiniStatementData(inquiryId, recentTransactions, timestamp);
                log.info("Step 8: Retrieved mini-statement with {} transactions", recentTransactions.size());
            }
            
            // Step 9: Generate response data
            String responseData = atmInquiryService.generateResponseData(inquiryId, timestamp);
            log.info("Step 9: Generated response data");
            
            // Step 10: Print receipt if requested
            if (receiptRequested) {
                String receiptNumber = atmInquiryService.printReceipt(inquiryId, atmId, timestamp);
                log.info("Step 10: Printed receipt: {}", receiptNumber);
            } else {
                log.info("Step 10: No receipt requested");
            }
            
            // Step 11: Update inquiry counters
            atmInquiryService.updateInquiryCounters(cardNumber, inquiryType, timestamp);
            atmInquiryService.updateATMUsageStats(atmId, inquiryType, timestamp);
            log.info("Step 11: Updated inquiry counters");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed ATM balance inquiry: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing ATM balance inquiry event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("inquiryId") || 
            !eventData.has("atmId") || !eventData.has("inquiryType")) {
            throw new IllegalArgumentException("Invalid ATM balance inquiry event structure");
        }
    }
}