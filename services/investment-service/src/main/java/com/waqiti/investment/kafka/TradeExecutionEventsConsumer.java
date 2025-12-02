package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.investment.service.TradeExecutionService;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.InvestmentNotificationService;
import com.waqiti.investment.service.MarketDataService;
import com.waqiti.common.exception.InvestmentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for Trade Execution Events
 * Handles order executions, fills, settlement, and portfolio updates
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradeExecutionEventsConsumer {
    
    private final TradeExecutionService executionService;
    private final PortfolioService portfolioService;
    private final InvestmentNotificationService notificationService;
    private final MarketDataService marketDataService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;
    
    @KafkaListener(
        topics = {"trade-execution-events", "order-filled", "trade-settled", "execution-failed"},
        groupId = "investment-service-trade-execution-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleTradeExecutionEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID executionId = null;
        UUID orderId = null;
        UUID accountId = null;
        String eventType = null;
        String idempotencyKey = null;
        UUID operationId = UUID.randomUUID();

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            executionId = UUID.fromString((String) event.get("executionId"));
            orderId = UUID.fromString((String) event.get("orderId"));
            accountId = UUID.fromString((String) event.get("accountId"));
            eventType = (String) event.get("eventType");
            String symbol = (String) event.get("symbol");

            // CRITICAL SECURITY: Idempotency check - prevent duplicate trade execution processing
            idempotencyKey = String.format("trade-execution:%s:%s:%s:%s",
                executionId, orderId, eventType, event.get("timestamp"));

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate trade execution event ignored: executionId={}, orderId={}, eventType={}, idempotencyKey={}",
                        executionId, orderId, eventType, idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new trade execution with persistent idempotency: executionId={}, orderId={}, eventType={}, idempotencyKey={}",
                executionId, orderId, eventType, idempotencyKey);
            String orderSide = (String) event.get("orderSide"); // BUY, SELL
            String orderType = (String) event.get("orderType"); // MARKET, LIMIT, STOP, STOP_LIMIT
            BigDecimal quantity = new BigDecimal((String) event.get("quantity"));
            BigDecimal executedQuantity = new BigDecimal((String) event.get("executedQuantity"));
            BigDecimal executionPrice = new BigDecimal((String) event.get("executionPrice"));
            BigDecimal totalValue = executedQuantity.multiply(executionPrice);
            String currency = (String) event.get("currency");
            LocalDateTime executionTime = LocalDateTime.parse((String) event.get("executionTime"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // Execution details
            String executionVenue = (String) event.get("executionVenue"); // NYSE, NASDAQ, CBOE, etc.
            String marketMaker = (String) event.get("marketMaker");
            BigDecimal commission = new BigDecimal((String) event.get("commission"));
            BigDecimal regulatoryFees = new BigDecimal((String) event.get("regulatoryFees"));
            BigDecimal netAmount = totalValue.subtract(commission).subtract(regulatoryFees);
            String executionQuality = (String) event.get("executionQuality"); // PRICE_IMPROVEMENT, AT_MIDPOINT, WORSE_THAN_NBBO
            
            // Settlement information
            LocalDateTime settlementDate = LocalDateTime.parse((String) event.get("settlementDate"));
            String settlementStatus = (String) event.get("settlementStatus"); // PENDING, SETTLED, FAILED
            String clearingFirm = (String) event.get("clearingFirm");
            String tradeId = (String) event.get("tradeId");
            
            // Error information for failed executions
            String failureReason = (String) event.get("failureReason");
            String errorCode = (String) event.get("errorCode");
            Boolean isPartialFill = executedQuantity.compareTo(quantity) < 0;
            
            log.info("Processing trade execution event - ExecutionId: {}, OrderId: {}, Type: {}, Symbol: {}, Qty: {}", 
                    executionId, orderId, eventType, symbol, executedQuantity);
            
            // Step 1: Validate execution data
            Map<String, Object> validationResult = executionService.validateExecutionData(
                    executionId, orderId, accountId, symbol, orderSide, executedQuantity,
                    executionPrice, executionTime, settlementDate, timestamp);
            
            if ("INVALID".equals(validationResult.get("status"))) {
                executionService.rejectExecution(executionId, 
                        (String) validationResult.get("reason"), timestamp);
                log.warn("Trade execution validation failed: {}", validationResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 2: Market data validation
            Map<String, Object> marketValidation = marketDataService.validateExecutionPrice(
                    symbol, executionPrice, executionTime, executionVenue, timestamp);
            
            String priceValidation = (String) marketValidation.get("validation"); // VALID, SUSPICIOUS, INVALID
            if ("INVALID".equals(priceValidation)) {
                executionService.flagSuspiciousExecution(executionId, marketValidation, timestamp);
                log.warn("Suspicious execution price detected for symbol: {}", symbol);
            }
            
            // Step 3: Process based on event type
            switch (eventType) {
                case "ORDER_FILLED":
                    executionService.processOrderFill(executionId, orderId, accountId, symbol,
                            orderSide, orderType, executedQuantity, executionPrice, totalValue,
                            commission, regulatoryFees, executionVenue, marketMaker, 
                            executionQuality, executionTime, timestamp);
                    break;
                    
                case "TRADE_SETTLED":
                    executionService.processTradeSettlement(executionId, orderId, accountId,
                            tradeId, settlementStatus, clearingFirm, settlementDate, timestamp);
                    break;
                    
                case "EXECUTION_FAILED":
                    executionService.processExecutionFailure(executionId, orderId, accountId,
                            failureReason, errorCode, timestamp);
                    break;
                    
                case "PARTIAL_FILL":
                    executionService.processPartialFill(executionId, orderId, accountId, symbol,
                            quantity, executedQuantity, executionPrice, timestamp);
                    break;
                    
                default:
                    executionService.processGenericExecutionEvent(executionId, eventType, 
                            event, timestamp);
            }
            
            // Step 4: Portfolio updates for successful executions
            if ("ORDER_FILLED".equals(eventType) || "TRADE_SETTLED".equals(eventType)) {
                portfolioService.updatePortfolioHoldings(accountId, symbol, orderSide,
                        executedQuantity, executionPrice, commission, regulatoryFees, timestamp);
                
                // Update portfolio valuation
                portfolioService.updatePortfolioValuation(accountId, symbol, executionPrice, timestamp);
            }
            
            // Step 5: Account balance updates
            if ("TRADE_SETTLED".equals(eventType)) {
                executionService.updateAccountBalance(accountId, orderSide, netAmount,
                        currency, settlementDate, timestamp);
            }
            
            // Step 6: Best execution analysis
            Map<String, Object> bestExecutionAnalysis = executionService.analyzeBestExecution(
                    executionId, symbol, executionPrice, executionVenue, marketMaker,
                    executionQuality, executionTime, timestamp);
            
            // Step 7: Regulatory reporting
            executionService.updateRegulatoryReporting(executionId, orderId, accountId, symbol,
                    orderSide, executedQuantity, executionPrice, commission, regulatoryFees,
                    executionVenue, clearingFirm, executionTime, settlementDate, timestamp);
            
            // Step 8: Handle partial fills
            if (isPartialFill && "ORDER_FILLED".equals(eventType)) {
                BigDecimal remainingQuantity = quantity.subtract(executedQuantity);
                executionService.handlePartialFillStrategy(orderId, accountId, symbol,
                        remainingQuantity, orderType, timestamp);
            }
            
            // Step 9: Tax lot management
            if ("TRADE_SETTLED".equals(eventType)) {
                executionService.updateTaxLots(accountId, symbol, orderSide, executedQuantity,
                        executionPrice, commission, settlementDate, timestamp);
            }
            
            // Step 10: Performance tracking
            executionService.updateExecutionMetrics(executionId, symbol, executionVenue,
                    executionQuality, commission, regulatoryFees, bestExecutionAnalysis, timestamp);
            
            // Step 11: Send execution notifications
            notificationService.sendExecutionNotification(executionId, orderId, accountId,
                    eventType, symbol, orderSide, executedQuantity, executionPrice,
                    settlementStatus, timestamp);
            
            // Step 12: Market impact analysis
            if ("ORDER_FILLED".equals(eventType)) {
                executionService.analyzeMarketImpact(executionId, symbol, executedQuantity,
                        executionPrice, executionVenue, executionTime, timestamp);
            }
            
            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("executionId", executionId.toString(),
                       "orderId", orderId.toString(),
                       "accountId", accountId.toString(),
                       "eventType", eventType,
                       "symbol", symbol,
                       "orderSide", orderSide,
                       "executedQuantity", executedQuantity.toString(),
                       "executionPrice", executionPrice.toString(),
                       "settlementStatus", settlementStatus,
                       "status", "COMPLETED"), Duration.ofDays(7));

            // Step 13: Audit logging
            auditService.auditFinancialEvent(
                    "TRADE_EXECUTION_EVENT_PROCESSED",
                    accountId.toString(),
                    String.format("Trade execution event processed - Type: %s, Symbol: %s, Side: %s, Qty: %s, Price: %s",
                            eventType, symbol, orderSide, executedQuantity, executionPrice),
                    Map.of(
                            "executionId", executionId.toString(),
                            "orderId", orderId.toString(),
                            "accountId", accountId.toString(),
                            "eventType", eventType,
                            "symbol", symbol,
                            "orderSide", orderSide,
                            "orderType", orderType,
                            "quantity", quantity.toString(),
                            "executedQuantity", executedQuantity.toString(),
                            "executionPrice", executionPrice.toString(),
                            "totalValue", totalValue.toString(),
                            "commission", commission.toString(),
                            "regulatoryFees", regulatoryFees.toString(),
                            "netAmount", netAmount.toString(),
                            "executionVenue", executionVenue,
                            "marketMaker", marketMaker != null ? marketMaker : "N/A",
                            "executionQuality", executionQuality,
                            "settlementStatus", settlementStatus,
                            "isPartialFill", String.valueOf(isPartialFill),
                            "priceValidation", priceValidation,
                            "idempotencyKey", idempotencyKey
                    )
            );

            acknowledgment.acknowledge();
            log.info("Successfully processed trade execution event - ExecutionId: {}, EventType: {}, Settlement: {}",
                    executionId, eventType, settlementStatus);
            
        } catch (Exception e) {
            log.error("SECURITY: Trade execution event processing failed - ExecutionId: {}, OrderId: {}, Error: {}",
                    executionId, orderId, e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed for retry logic
            if (idempotencyKey != null) {
                idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            }

            throw new InvestmentProcessingException("Trade execution event processing failed", e);
        }
    }
}