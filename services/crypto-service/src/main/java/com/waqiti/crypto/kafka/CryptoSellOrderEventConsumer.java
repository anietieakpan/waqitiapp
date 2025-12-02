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
 * Critical Event Consumer #268: Crypto Sell Order Event Consumer
 * Processes crypto-to-fiat sales with capital gains reporting
 * Implements 12-step zero-tolerance processing for crypto sales
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoSellOrderEventConsumer extends BaseKafkaConsumer {

    private final CryptoTransactionService cryptoTransactionService;
    private final CryptoTradingService tradingService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crypto-sell-order-events", groupId = "crypto-sell-order-group")
    @CircuitBreaker(name = "crypto-sell-order-consumer")
    @Retry(name = "crypto-sell-order-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoSellOrderEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-sell-order-event");
        
        try {
            log.info("Step 1: Processing crypto sell order event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();

            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "crypto-sell-order:" + userId + ":" + cryptoCurrency + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto sell order ignored: userId={}, eventId={}", userId, eventId);
                ack.acknowledge();
                return;
            }
            BigDecimal cryptoAmount = new BigDecimal(eventData.path("cryptoAmount").asText());
            String fiatCurrency = eventData.path("fiatCurrency").asText();
            String orderType = eventData.path("orderType").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted sell order details: userId={}, crypto={}, amount={}", 
                    userId, cryptoCurrency, cryptoAmount);
            
            // Step 3: Sell order validation and balance check
            boolean validated = tradingService.validateSellOrder(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), cryptoAmount, timestamp);
            if (!validated) {
                log.error("Step 3: Sell order validation failed for userId={}", userId);
                tradingService.rejectSellOrder(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Sell order validation successful");
            
            // Step 4: FIFO cost basis calculation for tax reporting
            BigDecimal costBasis = complianceService.calculateFIFOCostBasis(userId, 
                    cryptoCurrency, cryptoAmount, timestamp);
            BigDecimal currentMarketValue = tradingService.getCurrentMarketPrice(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), fiatCurrency)
                    .multiply(cryptoAmount);
            BigDecimal capitalGain = currentMarketValue.subtract(costBasis);
            log.info("Step 4: Tax calculation completed - costBasis={}, marketValue={}, gain={}", 
                    costBasis, currentMarketValue, capitalGain);
            
            // Step 5: Large transaction CTR filing
            if (currentMarketValue.compareTo(new BigDecimal("10000")) >= 0) {
                boolean ctrFiled = complianceService.fileCryptoSaleCTR(userId, cryptoCurrency, 
                        cryptoAmount, currentMarketValue, timestamp);
                if (ctrFiled) {
                    log.info("Step 5: CTR filed for large crypto sale");
                } else {
                    log.warn("Step 5: CTR filing failed, proceeding with sale");
                }
            } else {
                log.info("Step 5: No CTR required for sale amount");
            }
            
            // Step 6: Anti-money laundering checks
            boolean amlCleared = complianceService.performCryptoSaleAMLCheck(userId, 
                    cryptoCurrency, cryptoAmount, currentMarketValue, timestamp);
            if (!amlCleared) {
                log.error("Step 6: AML screening failed for crypto sale");
                tradingService.holdSellOrder(eventId, "AML_REVIEW_REQUIRED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: AML screening passed");
            
            // Step 7: Market price validation
            BigDecimal marketPrice = tradingService.getCurrentMarketPrice(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), fiatCurrency);
            boolean priceAcceptable = tradingService.validateSellPrice(marketPrice, 
                    new BigDecimal(eventData.path("expectedPrice").asText()), timestamp);
            if (!priceAcceptable) {
                log.error("Step 7: Market price unfavorable for sell order");
                tradingService.rejectSellOrder(eventId, "UNFAVORABLE_PRICE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 7: Market price validated: {}", marketPrice);
            
            // Step 8: Execute sell order and crypto deduction
            TradeOrder executedOrder = tradingService.executeSellOrder(eventId, userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), cryptoAmount, 
                    marketPrice, timestamp);
            log.info("Step 8: Sell order executed: orderId={}, fiatReceived={}", 
                    executedOrder.getId(), executedOrder.getFiatAmount());
            
            // Step 9: Blockchain transaction for crypto transfer
            String blockchainTxId = cryptoTransactionService.createSaleTransaction(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), 
                    cryptoAmount, timestamp);
            log.info("Step 9: Blockchain sale transaction: txId={}", blockchainTxId);
            
            // Step 10: Capital gains tax reporting
            complianceService.recordCapitalGainsEvent(userId, cryptoCurrency, cryptoAmount, 
                    costBasis, currentMarketValue, capitalGain, timestamp);
            log.info("Step 10: Capital gains recorded for tax reporting");
            
            // Step 11: Fiat settlement processing
            tradingService.settleFiatProceeds(userId, executedOrder.getFiatAmount(),
                    fiatCurrency, executedOrder.getId(), timestamp);
            log.info("Step 11: Fiat settlement initiated");

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("userId", userId, "cryptoCurrency", cryptoCurrency, "cryptoAmount", cryptoAmount.toString(),
                       "orderId", executedOrder.getId(), "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto sell order: eventId={}, orderId={}",
                    eventId, executedOrder.getId());

        } catch (Exception e) {
            log.error("SECURITY: Error processing crypto sell order event: {}", e.getMessage(), e);
            String idempotencyKey = "crypto-sell-order:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("cryptoCurrency") || !eventData.has("cryptoAmount")) {
            throw new IllegalArgumentException("Invalid crypto sell order event structure");
        }
    }
}