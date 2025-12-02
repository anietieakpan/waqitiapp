package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.trading.CryptoTradingService;
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
 * Critical Event Consumer #269: Crypto Swap Event Consumer
 * Processes crypto-to-crypto exchanges with regulatory compliance
 * Implements 12-step zero-tolerance processing for crypto swaps
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoSwapEventConsumer extends BaseKafkaConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoTradingService tradingService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crypto-swap-events", groupId = "crypto-swap-group")
    @CircuitBreaker(name = "crypto-swap-consumer")
    @Retry(name = "crypto-swap-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoSwapEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-swap-event");
        
        try {
            log.info("Step 1: Processing crypto swap event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String fromCrypto = eventData.path("fromCrypto").asText();
            String toCrypto = eventData.path("toCrypto").asText();
            BigDecimal fromAmount = new BigDecimal(eventData.path("fromAmount").asText());
            BigDecimal expectedToAmount = new BigDecimal(eventData.path("expectedToAmount").asText());
            String swapType = eventData.path("swapType").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());

            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "crypto-swap:" + userId + ":" + fromCrypto + ":" + toCrypto + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto swap ignored: userId={}, eventId={}", userId, eventId);
                ack.acknowledge();
                return;
            }

            log.info("Step 2: Extracted swap details: userId={}, from={}:{}, to={}:{}",
                    userId, fromCrypto, fromAmount, toCrypto, expectedToAmount);
            
            // Step 3: Swap validation and balance verification
            boolean validated = tradingService.validateSwapOrder(userId, 
                    CryptoCurrency.valueOf(fromCrypto.toUpperCase()), 
                    CryptoCurrency.valueOf(toCrypto.toUpperCase()), fromAmount, timestamp);
            if (!validated) {
                log.error("Step 3: Swap validation failed for userId={}", userId);
                tradingService.rejectSwap(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Swap validation successful");
            
            // Step 4: DEX liquidity and slippage checks
            BigDecimal currentExchangeRate = tradingService.getSwapExchangeRate(
                    CryptoCurrency.valueOf(fromCrypto.toUpperCase()), 
                    CryptoCurrency.valueOf(toCrypto.toUpperCase()));
            BigDecimal actualToAmount = fromAmount.multiply(currentExchangeRate);
            BigDecimal slippage = expectedToAmount.subtract(actualToAmount)
                    .divide(expectedToAmount, 4, RoundingMode.HALF_UP);
            if (slippage.abs().compareTo(new BigDecimal("0.05")) > 0) {
                log.error("Step 4: Slippage exceeds 5% tolerance: {}", slippage);
                tradingService.rejectSwap(eventId, "EXCESSIVE_SLIPPAGE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Slippage acceptable: rate={}, slippage={}", currentExchangeRate, slippage);
            
            // Step 5: Cross-chain compliance validation
            boolean crossChainCompliant = complianceService.validateCrossChainCompliance(
                    fromCrypto, toCrypto, fromAmount, userId, timestamp);
            if (!crossChainCompliant) {
                log.error("Step 5: Cross-chain compliance failed");
                tradingService.escalateSwap(eventId, "CROSS_CHAIN_REVIEW", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Cross-chain compliance validated");
            
            // Step 6: Tax event calculation for swap
            BigDecimal fromCostBasis = complianceService.calculateSwapCostBasis(userId, 
                    fromCrypto, fromAmount, timestamp);
            BigDecimal fromMarketValue = tradingService.getCurrentUSDValue(
                    CryptoCurrency.valueOf(fromCrypto.toUpperCase()), fromAmount);
            BigDecimal swapGainLoss = fromMarketValue.subtract(fromCostBasis);
            log.info("Step 6: Swap tax calculation - basis={}, marketValue={}, gainLoss={}", 
                    fromCostBasis, fromMarketValue, swapGainLoss);
            
            // Step 7: Execute atomic swap transaction
            String swapTransactionId = tradingService.executeAtomicSwap(eventId, userId, 
                    CryptoCurrency.valueOf(fromCrypto.toUpperCase()), 
                    CryptoCurrency.valueOf(toCrypto.toUpperCase()), 
                    fromAmount, actualToAmount, timestamp);
            log.info("Step 7: Atomic swap executed: transactionId={}", swapTransactionId);
            
            // Step 8: Blockchain confirmations monitoring
            cryptoTransactionService.monitorSwapConfirmations(swapTransactionId, 
                    fromCrypto, toCrypto, timestamp);
            log.info("Step 8: Blockchain confirmation monitoring initiated");
            
            // Step 9: MEV protection validation
            boolean mevProtected = tradingService.validateMEVProtection(swapTransactionId, 
                    fromCrypto, toCrypto, currentExchangeRate, timestamp);
            if (!mevProtected) {
                log.warn("Step 9: MEV attack detected, transaction flagged");
                tradingService.flagMEVIncident(swapTransactionId, timestamp);
            } else {
                log.info("Step 9: MEV protection validated");
            }
            
            // Step 10: Cost basis updates for new holdings
            complianceService.updateCostBasisForSwap(userId, toCrypto, actualToAmount, 
                    fromMarketValue, timestamp);
            log.info("Step 10: Cost basis updated for new crypto holdings");
            
            // Step 11: Swap confirmation and receipt
            tradingService.sendSwapConfirmation(userId, swapTransactionId, fromCrypto,
                    toCrypto, fromAmount, actualToAmount, timestamp);
            log.info("Step 11: Swap confirmation sent");

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("userId", userId, "fromCrypto", fromCrypto, "toCrypto", toCrypto,
                       "fromAmount", fromAmount.toString(), "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto swap: eventId={}, transactionId={}",
                    eventId, swapTransactionId);

        } catch (Exception e) {
            log.error("SECURITY: Error processing crypto swap event: {}", e.getMessage(), e);
            String idempotencyKey = "crypto-swap:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("fromCrypto") || !eventData.has("toCrypto") || !eventData.has("fromAmount")) {
            throw new IllegalArgumentException("Invalid crypto swap event structure");
        }
    }
}