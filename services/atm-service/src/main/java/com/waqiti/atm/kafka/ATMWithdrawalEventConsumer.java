package com.waqiti.atm.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.common.util.DataMaskingUtil;
import com.waqiti.atm.service.ATMTransactionService;
import com.waqiti.atm.service.EMVValidationService;
import com.waqiti.atm.entity.ATMWithdrawal;
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
 * Critical Event Consumer #308: ATM Withdrawal Event Consumer
 * Processes cash withdrawal with EMV validation and regulatory compliance
 * Implements 12-step zero-tolerance processing for ATM withdrawals
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ATMWithdrawalEventConsumer extends BaseKafkaConsumer {

    private final ATMTransactionService atmTransactionService;
    private final EMVValidationService emvValidationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "atm-withdrawal-events", groupId = "atm-withdrawal-group")
    @CircuitBreaker(name = "atm-withdrawal-consumer")
    @Retry(name = "atm-withdrawal-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleATMWithdrawalEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "atm-withdrawal-event");
        
        try {
            log.info("Step 1: Processing ATM withdrawal event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String withdrawalId = eventData.path("withdrawalId").asText();
            String atmId = eventData.path("atmId").asText();
            String cardNumber = eventData.path("cardNumber").asText();
            String accountId = eventData.path("accountId").asText();
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal withdrawalAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("withdrawalAmount").asText(),
                BigDecimal.ZERO
            );
            String pinHash = eventData.path("pinHash").asText();
            String emvData = eventData.path("emvData").asText();
            String authorizationCode = eventData.path("authorizationCode").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted withdrawal details: withdrawalId={}, atmId={}, amount={}", 
                    withdrawalId, atmId, withdrawalAmount);
            
            // Step 3: Validate EMV chip data
            boolean emvValid = emvValidationService.validateEMVData(
                    cardNumber, emvData, authorizationCode, timestamp);
            
            if (!emvValid) {
                // PCI DSS COMPLIANCE: Mask card number in logs per PCI DSS v4.0 Requirement 3.3
                log.error("Step 3: EMV validation failed for cardNumber={}", DataMaskingUtil.maskCardNumber(cardNumber));
                atmTransactionService.declineWithdrawal(withdrawalId, "EMV_VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: EMV data validated");
            
            // Step 4: Validate PIN
            boolean pinValid = atmTransactionService.validatePIN(cardNumber, pinHash, timestamp);
            
            if (!pinValid) {
                // PCI DSS COMPLIANCE: Mask card number in logs per PCI DSS v4.0 Requirement 3.3
                log.error("Step 4: PIN validation failed for cardNumber={}", DataMaskingUtil.maskCardNumber(cardNumber));
                atmTransactionService.declineWithdrawal(withdrawalId, "INVALID_PIN", timestamp);
                atmTransactionService.incrementFailedAttempts(cardNumber, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: PIN validated");
            
            // Step 5: Check account status
            boolean accountActive = atmTransactionService.validateAccountStatus(accountId, timestamp);
            
            if (!accountActive) {
                log.error("Step 5: Account not active: accountId={}", accountId);
                atmTransactionService.declineWithdrawal(withdrawalId, "ACCOUNT_INACTIVE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Account status validated");
            
            // Step 6: Validate withdrawal limits
            boolean withinLimits = atmTransactionService.validateWithdrawalLimits(
                    cardNumber, accountId, withdrawalAmount, timestamp);
            
            if (!withinLimits) {
                log.error("Step 6: Withdrawal exceeds limits: amount={}", withdrawalAmount);
                atmTransactionService.declineWithdrawal(withdrawalId, "LIMIT_EXCEEDED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: Withdrawal limits validated");
            
            // Step 7: Check sufficient funds
            boolean sufficientFunds = atmTransactionService.validateSufficientFunds(
                    accountId, withdrawalAmount, timestamp);
            
            if (!sufficientFunds) {
                log.error("Step 7: Insufficient funds: accountId={}, amount={}", accountId, withdrawalAmount);
                atmTransactionService.declineWithdrawal(withdrawalId, "INSUFFICIENT_FUNDS", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 7: Sufficient funds validated");
            
            // Step 8: Check ATM cash availability
            boolean cashAvailable = atmTransactionService.validateATMCashAvailability(
                    atmId, withdrawalAmount, timestamp);
            
            if (!cashAvailable) {
                log.error("Step 8: ATM cash not available: atmId={}, amount={}", atmId, withdrawalAmount);
                atmTransactionService.declineWithdrawal(withdrawalId, "ATM_CASH_UNAVAILABLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 8: ATM cash availability validated");
            
            // Step 9: Process withdrawal
            ATMWithdrawal withdrawal = atmTransactionService.processWithdrawal(
                    withdrawalId, atmId, cardNumber, accountId, withdrawalAmount, 
                    authorizationCode, timestamp);
            log.info("Step 9: Processed ATM withdrawal");
            
            // Step 10: Dispense cash
            boolean cashDispensed = atmTransactionService.dispenseCash(atmId, withdrawalAmount, timestamp);
            
            if (!cashDispensed) {
                log.error("Step 10: Cash dispensing failed: withdrawalId={}", withdrawalId);
                atmTransactionService.reverseWithdrawal(withdrawalId, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 10: Cash dispensed successfully");
            
            // Step 11: Update balances and limits
            atmTransactionService.updateAccountBalance(accountId, withdrawalAmount, timestamp);
            atmTransactionService.updateDailyLimits(cardNumber, withdrawalAmount, timestamp);
            atmTransactionService.updateATMCashBalance(atmId, withdrawalAmount, timestamp);
            log.info("Step 11: Updated balances and limits");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed ATM withdrawal: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing ATM withdrawal event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("withdrawalId") || 
            !eventData.has("atmId") || !eventData.has("withdrawalAmount")) {
            throw new IllegalArgumentException("Invalid ATM withdrawal event structure");
        }
    }
}