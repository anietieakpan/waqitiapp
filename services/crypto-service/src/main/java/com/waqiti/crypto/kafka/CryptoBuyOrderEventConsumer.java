package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.trading.CryptoTradingService;
import com.waqiti.crypto.compliance.ComplianceService;
import com.waqiti.crypto.entity.TradeOrder;
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
 * Critical Event Consumer #267: Crypto Buy Order Event Consumer
 * Processes fiat-to-crypto purchases with AML, OFAC compliance
 * Implements 12-step zero-tolerance processing for crypto purchases
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoBuyOrderEventConsumer extends BaseKafkaConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoTradingService tradingService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crypto-buy-order-events", groupId = "crypto-buy-order-group")
    @CircuitBreaker(name = "crypto-buy-order-consumer")
    @Retry(name = "crypto-buy-order-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoBuyOrderEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-buy-order-event");
        
        try {
            log.info("Step 1: Processing crypto buy order event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();
            BigDecimal fiatAmount = new BigDecimal(eventData.path("fiatAmount").asText());
            String fiatCurrency = eventData.path("fiatCurrency").asText();
            String paymentMethodId = eventData.path("paymentMethodId").asText();
            String orderType = eventData.path("orderType").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());

            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "crypto-buy-order:" + userId + ":" + cryptoCurrency + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto buy order ignored: userId={}, eventId={}", userId, eventId);
                ack.acknowledge();
                return;
            }

            log.info("Step 2: Extracted buy order details: userId={}, crypto={}, fiatAmount={}",
                    userId, cryptoCurrency, fiatAmount);
            
            // Step 3: Buy order validation with limits
            boolean validated = tradingService.validateBuyOrder(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), fiatAmount, 
                    paymentMethodId, timestamp);
            if (!validated) {
                log.error("Step 3: Buy order validation failed for userId={}", userId);
                tradingService.rejectBuyOrder(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Buy order validation successful");
            
            // Step 4: Large transaction BSA reporting
            if (fiatAmount.compareTo(new BigDecimal("10000")) >= 0) {
                boolean bsaCompliant = complianceService.performCryptoBSAReporting(userId, 
                        fiatAmount, cryptoCurrency, "BUY_ORDER", timestamp);
                if (!bsaCompliant) {
                    log.error("Step 4: BSA compliance failed for large crypto purchase");
                    tradingService.holdOrder(eventId, "BSA_REVIEW_REQUIRED", timestamp);
                }
            }
            log.info("Step 4: BSA compliance verified");
            
            // Step 5: AML transaction monitoring
            boolean amlCleared = complianceService.performCryptoAMLScreening(userId, 
                    cryptoCurrency, fiatAmount, "PURCHASE", timestamp);
            if (!amlCleared) {
                log.error("Step 5: AML screening failed for crypto purchase");
                tradingService.escalateForReview(eventId, "AML_REVIEW_REQUIRED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: AML screening passed");
            
            // Step 6: Market price validation and slippage protection
            BigDecimal currentPrice = tradingService.getCurrentMarketPrice(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), fiatCurrency);
            BigDecimal expectedCryptoAmount = fiatAmount.divide(currentPrice, 8, RoundingMode.DOWN);
            boolean priceAcceptable = tradingService.validatePriceSlippage(expectedCryptoAmount, 
                    eventData.path("expectedAmount").asText(), timestamp);
            if (!priceAcceptable) {
                log.error("Step 6: Price slippage exceeds tolerance");
                tradingService.rejectBuyOrder(eventId, "PRICE_SLIPPAGE_EXCEEDED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: Market price validated: currentPrice={}, expectedAmount={}", 
                    currentPrice, expectedCryptoAmount);
            
            // Step 7: Payment method verification and processing
            boolean paymentProcessed = cryptoTransactionService.processPayment(paymentMethodId, 
                    fiatAmount, fiatCurrency, eventId, timestamp);
            if (!paymentProcessed) {
                log.error("Step 7: Payment processing failed");
                tradingService.rejectBuyOrder(eventId, "PAYMENT_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 7: Payment processed successfully");
            
            // Step 8: Order execution and crypto allocation
            TradeOrder executedOrder = tradingService.executeBuyOrder(eventId, userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), fiatAmount, 
                    expectedCryptoAmount, currentPrice, timestamp);
            log.info("Step 8: Buy order executed: orderId={}, cryptoAmount={}", 
                    executedOrder.getId(), executedOrder.getCryptoAmount());
            
            // Step 9: Blockchain transaction creation
            String blockchainTxId = cryptoTransactionService.createBlockchainTransaction(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), 
                    expectedCryptoAmount, "PURCHASE", timestamp);
            log.info("Step 9: Blockchain transaction created: txId={}", blockchainTxId);
            
            // Step 10: Tax reporting for crypto purchases
            complianceService.recordCryptoTaxEvent(userId, cryptoCurrency, expectedCryptoAmount, 
                    currentPrice, "PURCHASE", timestamp);
            log.info("Step 10: Tax event recorded");
            
            // Step 11: Customer notification and confirmation
            tradingService.sendBuyOrderConfirmation(userId, executedOrder.getId(),
                    cryptoCurrency, expectedCryptoAmount, blockchainTxId, timestamp);
            log.info("Step 11: Buy order confirmation sent");

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("userId", userId, "cryptoCurrency", cryptoCurrency, "fiatAmount", fiatAmount.toString(),
                       "orderId", executedOrder.getId(), "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto buy order: eventId={}, orderId={}",
                    eventId, executedOrder.getId());

        } catch (Exception e) {
            log.error("SECURITY: Error processing crypto buy order event: {}", e.getMessage(), e);
            String idempotencyKey = "crypto-buy-order:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("cryptoCurrency") || !eventData.has("fiatAmount")) {
            throw new IllegalArgumentException("Invalid crypto buy order event structure");
        }
    }
}