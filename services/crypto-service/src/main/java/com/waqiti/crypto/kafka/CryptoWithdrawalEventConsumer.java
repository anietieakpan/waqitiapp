package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.crypto.entity.CryptoCurrency;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #271: Crypto Withdrawal Event Consumer
 * Processes blockchain withdrawals with gas fee optimization
 * Implements 12-step zero-tolerance processing for crypto withdrawals
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoWithdrawalEventConsumer extends BaseKafkaConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crypto-withdrawal-events", groupId = "crypto-withdrawal-group")
    @CircuitBreaker(name = "crypto-withdrawal-consumer")
    @Retry(name = "crypto-withdrawal-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoWithdrawalEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-withdrawal-event");
        
        try {
            log.info("Step 1: Processing crypto withdrawal event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String destinationAddress = eventData.path("destinationAddress").asText();
            String network = eventData.path("network").asText();

            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "crypto-withdrawal:" + userId + ":" + cryptoCurrency + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto withdrawal ignored: userId={}, eventId={}", userId, eventId);
                ack.acknowledge();
                return;
            }
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted withdrawal details: userId={}, crypto={}, amount={}, destination={}", 
                    userId, cryptoCurrency, amount, destinationAddress);
            
            // Step 3: Withdrawal validation and balance check
            boolean validated = cryptoTransactionService.validateWithdrawal(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), amount, destinationAddress, timestamp);
            if (!validated) {
                log.error("Step 3: Withdrawal validation failed for userId={}", userId);
                cryptoTransactionService.rejectWithdrawal(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Withdrawal validation successful");
            
            // Step 4: Destination address sanctions screening
            boolean sanctionsCleared = complianceService.screenDestinationAddress(destinationAddress, 
                    cryptoCurrency, amount, timestamp);
            if (!sanctionsCleared) {
                log.error("Step 4: Destination address failed sanctions screening");
                cryptoTransactionService.blockWithdrawal(eventId, "SANCTIONS_VIOLATION", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Destination address sanctions screening passed");
            
            // Step 5: Gas fee calculation and optimization
            BigDecimal gasFee = cryptoTransactionService.calculateOptimalGasFee(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), network, timestamp);
            BigDecimal totalCost = amount.add(gasFee);
            boolean sufficientBalance = cryptoTransactionService.checkSufficientBalanceWithFees(userId, 
                    cryptoCurrency, totalCost, timestamp);
            if (!sufficientBalance) {
                log.error("Step 5: Insufficient balance including gas fees");
                cryptoTransactionService.rejectWithdrawal(eventId, "INSUFFICIENT_BALANCE_WITH_FEES", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Gas fee calculated: {}, total cost: {}", gasFee, totalCost);
            
            // Step 6: Travel rule compliance for large transfers
            if (amount.compareTo(new BigDecimal("3000")) >= 0) {
                boolean travelRuleCompliant = complianceService.performTravelRuleCompliance(userId, 
                        destinationAddress, amount, cryptoCurrency, timestamp);
                if (!travelRuleCompliant) {
                    log.error("Step 6: Travel rule compliance failed");
                    cryptoTransactionService.holdWithdrawal(eventId, "TRAVEL_RULE_REVIEW", timestamp);
                    ack.acknowledge();
                    return;
                }
            }
            log.info("Step 6: Travel rule compliance validated");
            
            // Step 7: Execute blockchain withdrawal
            String blockchainTxId = cryptoTransactionService.executeBlockchainWithdrawal(eventId, userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), amount, destinationAddress, 
                    network, gasFee, timestamp);
            log.info("Step 7: Blockchain withdrawal executed: txId={}", blockchainTxId);
            
            // Step 8: Transaction monitoring and confirmation tracking
            cryptoTransactionService.monitorWithdrawalConfirmations(blockchainTxId, cryptoCurrency, 
                    network, timestamp);
            log.info("Step 8: Withdrawal confirmation monitoring initiated");
            
            // Step 9: Compliance reporting
            complianceService.reportCryptoWithdrawal(userId, cryptoCurrency, amount, 
                    destinationAddress, blockchainTxId, timestamp);
            log.info("Step 9: Compliance reporting completed");
            
            // Step 10: Balance reconciliation
            cryptoTransactionService.reconcilePostWithdrawalBalance(userId, cryptoCurrency, 
                    totalCost, blockchainTxId, timestamp);
            log.info("Step 10: Balance reconciliation completed");
            
            // Step 11: Customer notification
            cryptoTransactionService.sendWithdrawalConfirmation(userId, blockchainTxId, cryptoCurrency,
                    amount, destinationAddress, gasFee, timestamp);
            log.info("Step 11: Withdrawal confirmation sent");

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("userId", userId, "cryptoCurrency", cryptoCurrency, "amount", amount.toString(),
                       "destinationAddress", destinationAddress, "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto withdrawal: eventId={}, txId={}",
                    eventId, blockchainTxId);

        } catch (Exception e) {
            log.error("SECURITY: Error processing crypto withdrawal event: {}", e.getMessage(), e);
            String idempotencyKey = "crypto-withdrawal:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("cryptoCurrency") || !eventData.has("amount") || !eventData.has("destinationAddress")) {
            throw new IllegalArgumentException("Invalid crypto withdrawal event structure");
        }
    }
}