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
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #272: Crypto Deposit Event Consumer
 * Processes blockchain deposit detection with confirmation requirements
 * Implements 12-step zero-tolerance processing for crypto deposits
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoDepositEventConsumer extends BaseKafkaConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crypto-deposit-events", groupId = "crypto-deposit-group")
    @CircuitBreaker(name = "crypto-deposit-consumer")
    @Retry(name = "crypto-deposit-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoDepositEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-deposit-event");
        
        try {
            log.info("Step 1: Processing crypto deposit event: partition={}, offset={}",
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);

            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String blockchainTxId = eventData.path("blockchainTxId").asText();

            // CRITICAL SECURITY: Idempotency check - prevent double-crediting of crypto deposits
            String idempotencyKey = "crypto-deposit:" + blockchainTxId + ":" + userId + ":" + eventId;
            if (!idempotencyService.startOperation(idempotencyKey, UUID.randomUUID(), Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto deposit event ignored: blockchainTxId={}, userId={}, eventId={}",
                        blockchainTxId, userId, eventId);
                ack.acknowledge();
                return;
            }
            String fromAddress = eventData.path("fromAddress").asText();
            int confirmations = eventData.path("confirmations").asInt();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted deposit details: userId={}, crypto={}, amount={}, txId={}", 
                    userId, cryptoCurrency, amount, blockchainTxId);
            
            // Step 3: Blockchain transaction verification
            boolean verified = cryptoTransactionService.verifyBlockchainTransaction(blockchainTxId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), amount, timestamp);
            if (!verified) {
                log.error("Step 3: Blockchain transaction verification failed");
                cryptoTransactionService.rejectDeposit(eventId, "INVALID_BLOCKCHAIN_TX", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Blockchain transaction verified");
            
            // Step 4: Source address risk assessment
            int riskScore = complianceService.assessSourceAddressRisk(fromAddress, cryptoCurrency, 
                    amount, timestamp);
            if (riskScore > 800) {
                log.error("Step 4: High risk source address detected: score={}", riskScore);
                cryptoTransactionService.quarantineDeposit(eventId, fromAddress, riskScore, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Source address risk assessment passed: score={}", riskScore);
            
            // Step 5: Confirmation threshold validation
            int requiredConfirmations = cryptoTransactionService.getRequiredConfirmations(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), amount);
            if (confirmations < requiredConfirmations) {
                log.info("Step 5: Insufficient confirmations: {}/{}, waiting...", 
                        confirmations, requiredConfirmations);
                cryptoTransactionService.pendDeposit(eventId, blockchainTxId, confirmations, 
                        requiredConfirmations, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Confirmation threshold met: {}/{}", confirmations, requiredConfirmations);
            
            // Step 6: Chain analysis and mixing detection
            boolean mixingDetected = complianceService.detectMixingServices(fromAddress, 
                    blockchainTxId, cryptoCurrency, timestamp);
            if (mixingDetected) {
                log.error("Step 6: Mixing service detected in transaction history");
                cryptoTransactionService.flagMixedFunds(eventId, blockchainTxId, timestamp);
            } else {
                log.info("Step 6: No mixing services detected");
            }
            
            // Step 7: Execute deposit credit
            String depositId = cryptoTransactionService.creditCryptoDeposit(eventId, userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), amount, blockchainTxId, 
                    fromAddress, timestamp);
            log.info("Step 7: Crypto deposit credited: depositId={}", depositId);
            
            // Step 8: Tax basis establishment
            BigDecimal marketValue = cryptoTransactionService.getCurrentUSDValue(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), amount, timestamp);
            complianceService.establishDepositTaxBasis(userId, cryptoCurrency, amount, 
                    marketValue, timestamp);
            log.info("Step 8: Tax basis established: marketValue={}", marketValue);
            
            // Step 9: Large deposit CTR filing
            if (marketValue.compareTo(new BigDecimal("10000")) >= 0) {
                boolean ctrFiled = complianceService.fileCryptoDepositCTR(userId, cryptoCurrency, 
                        amount, marketValue, fromAddress, timestamp);
                log.info("Step 9: CTR filed for large crypto deposit: {}", ctrFiled);
            } else {
                log.info("Step 9: No CTR required for deposit amount");
            }
            
            // Step 10: Balance reconciliation
            cryptoTransactionService.reconcileDepositBalance(userId, cryptoCurrency, amount, 
                    depositId, timestamp);
            log.info("Step 10: Balance reconciliation completed");
            
            // Step 11: Deposit notification
            cryptoTransactionService.sendDepositNotification(userId, depositId, cryptoCurrency, 
                    amount, blockchainTxId, confirmations, timestamp);
            log.info("Step 11: Deposit notification sent");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto deposit: eventId={}, depositId={}", 
                    eventId, depositId);
            
        } catch (Exception e) {
            log.error("Error processing crypto deposit event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("cryptoCurrency") || !eventData.has("amount") || !eventData.has("blockchainTxId")) {
            throw new IllegalArgumentException("Invalid crypto deposit event structure");
        }
    }
}