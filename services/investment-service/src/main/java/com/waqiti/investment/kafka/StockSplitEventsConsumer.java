package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.StockSplitProcessingService;
import com.waqiti.investment.service.CorporateActionsService;
import com.waqiti.investment.service.PortfolioAdjustmentService;
import com.waqiti.investment.service.MarketDataService;
import com.waqiti.investment.service.AuditService;
import com.waqiti.investment.entity.StockSplit;
import com.waqiti.investment.entity.CorporateAction;
import com.waqiti.investment.entity.PortfolioAdjustment;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #11: Stock Split Events Consumer
 * Processes stock splits, bonus issues, and share capital adjustments
 * Implements 12-step zero-tolerance processing for corporate actions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSplitEventsConsumer extends BaseKafkaConsumer {

    private final StockSplitProcessingService stockSplitService;
    private final CorporateActionsService corporateActionsService;
    private final PortfolioAdjustmentService portfolioAdjustmentService;
    private final MarketDataService marketDataService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(
        topics = "stock-split-events", 
        groupId = "stock-split-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "stock-split-consumer")
    @Retry(name = "stock-split-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleStockSplitEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "stock-split-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing stock split event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String symbol = eventData.path("symbol").asText();
            String companyName = eventData.path("companyName").asText();
            String splitRatio = eventData.path("splitRatio").asText(); // e.g., "2:1", "3:2"
            LocalDate exDate = LocalDate.parse(eventData.path("exDate").asText());
            LocalDate recordDate = LocalDate.parse(eventData.path("recordDate").asText());
            LocalDate payableDate = LocalDate.parse(eventData.path("payableDate").asText());
            String actionType = eventData.path("actionType").asText(); // STOCK_SPLIT, STOCK_DIVIDEND, BONUS_ISSUE
            BigDecimal adjustmentFactor = new BigDecimal(eventData.path("adjustmentFactor").asText());
            String currency = eventData.path("currency").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String exchangeCode = eventData.path("exchangeCode").asText();

            // CRITICAL SECURITY: Idempotency check - prevent duplicate stock split processing
            String idempotencyKey = "stock-split:" + symbol + ":" + exDate + ":" + eventId;
            UUID operationId = UUID.randomUUID();

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(30))) {
                log.warn("SECURITY: Duplicate stock split event ignored: symbol={}, exDate={}, eventId={}",
                        symbol, exDate, eventId);
                ack.acknowledge();
                return;
            }

            log.info("Step 2: Extracted split details: symbol={}, ratio={}, exDate={}, type={}",
                    symbol, splitRatio, exDate, actionType);
            
            // Step 3: Stock split validation and corporate action setup
            log.info("Step 3: Validating stock split parameters and setting up corporate action");
            StockSplit stockSplit = stockSplitService.createStockSplit(eventData);
            
            stockSplitService.validateSplitRatio(splitRatio);
            stockSplitService.validateDates(exDate, recordDate, payableDate);
            stockSplitService.validateSymbol(symbol, exchangeCode);
            stockSplitService.validateActionType(actionType);
            
            if (!stockSplitService.isValidAdjustmentFactor(adjustmentFactor)) {
                throw new IllegalStateException("Invalid adjustment factor: " + adjustmentFactor);
            }
            
            stockSplitService.parseRatioComponents(stockSplit, splitRatio);
            
            // Step 4: Corporate action registration and validation
            log.info("Step 4: Registering corporate action and validating compliance");
            CorporateAction corporateAction = corporateActionsService.createCorporateAction(stockSplit);
            
            corporateActionsService.validateCorporateAction(corporateAction);
            corporateActionsService.registerWithRegulators(corporateAction);
            corporateActionsService.validateExchangeCompliance(corporateAction, exchangeCode);
            corporateActionsService.checkDuplicateActions(corporateAction);
            
            corporateActionsService.updateActionStatus(corporateAction, "VALIDATED");
            corporateActionsService.notifyMarketParticipants(corporateAction);
            
            // Step 5: Market data adjustment and price recalculation
            log.info("Step 5: Adjusting market data and recalculating historical prices");
            marketDataService.adjustHistoricalPrices(symbol, adjustmentFactor, exDate);
            marketDataService.updateDividendHistory(symbol, adjustmentFactor, exDate);
            marketDataService.adjustTechnicalIndicators(symbol, adjustmentFactor, exDate);
            marketDataService.recalculateVolumes(symbol, adjustmentFactor, exDate);
            
            BigDecimal preSpitPrice = marketDataService.getPreSplitPrice(symbol, exDate);
            BigDecimal postSplitPrice = preSpitPrice.divide(adjustmentFactor, 4, RoundingMode.HALF_UP);
            
            marketDataService.updateCurrentPrice(symbol, postSplitPrice);
            marketDataService.validatePriceAdjustments(symbol, adjustmentFactor);
            
            // Step 6: Portfolio holdings identification and calculation
            log.info("Step 6: Identifying affected portfolios and calculating adjustments");
            List<String> affectedPortfolios = portfolioAdjustmentService.getAffectedPortfolios(symbol);
            
            for (String portfolioId : affectedPortfolios) {
                PortfolioAdjustment adjustment = portfolioAdjustmentService.createPortfolioAdjustment(
                    portfolioId, stockSplit);
                
                portfolioAdjustmentService.calculateShareAdjustment(adjustment, adjustmentFactor);
                portfolioAdjustmentService.calculateCostBasisAdjustment(adjustment, adjustmentFactor);
                portfolioAdjustmentService.validateAdjustmentCalculations(adjustment);
                
                portfolioAdjustmentService.processAdjustment(adjustment);
            }
            
            // Step 7: Share quantity and cost basis adjustments
            log.info("Step 7: Processing share quantity and cost basis adjustments");
            portfolioAdjustmentService.updateShareQuantities(symbol, adjustmentFactor);
            portfolioAdjustmentService.adjustCostBasis(symbol, adjustmentFactor);
            portfolioAdjustmentService.updateAverageCost(symbol, adjustmentFactor);
            
            portfolioAdjustmentService.handleFractionalShares(symbol, adjustmentFactor);
            portfolioAdjustmentService.reconcileShareCounts(symbol);
            portfolioAdjustmentService.validatePortfolioTotals(symbol);
            
            // Step 8: Tax implications and reporting adjustments
            log.info("Step 8: Processing tax implications and adjusting cost reporting");
            stockSplitService.analyzeTaxImplications(stockSplit);
            stockSplitService.adjustTaxLots(symbol, adjustmentFactor, exDate);
            stockSplitService.updateCostBasisReporting(symbol, adjustmentFactor);
            
            stockSplitService.generateTaxAdjustmentReports(stockSplit);
            stockSplitService.updateCapitalGainsCalculations(symbol, adjustmentFactor);
            stockSplitService.notifyTaxReporting(stockSplit);
            
            // Step 9: Options and derivatives adjustments
            log.info("Step 9: Adjusting options contracts and derivative instruments");
            if (stockSplitService.hasDerivativeExposure(symbol)) {
                stockSplitService.adjustOptionsContracts(symbol, adjustmentFactor);
                stockSplitService.updateStrikePrices(symbol, adjustmentFactor);
                stockSplitService.adjustContractMultipliers(symbol, adjustmentFactor);
                
                stockSplitService.notifyOptionsExchanges(stockSplit);
                stockSplitService.validateDerivativeAdjustments(symbol);
            }
            
            // Step 10: Client notification and statement updates
            log.info("Step 10: Generating client notifications and updating statements");
            stockSplitService.generateClientNotifications(stockSplit, affectedPortfolios);
            stockSplitService.updateAccountStatements(symbol, adjustmentFactor);
            stockSplitService.createCorporateActionStatements(stockSplit);
            
            portfolioAdjustmentService.updatePortfolioStatements(affectedPortfolios);
            portfolioAdjustmentService.generateAdjustmentSummaries(stockSplit);
            
            // Step 11: Regulatory reporting and compliance
            log.info("Step 11: Completing regulatory reporting and compliance requirements");
            corporateActionsService.generateRegulatoryReports(corporateAction);
            corporateActionsService.updateShareholderRecords(corporateAction);
            corporateActionsService.notifyTransferAgent(corporateAction);
            
            stockSplitService.updateCorporateRegistries(stockSplit);
            stockSplitService.completeComplianceReporting(stockSplit);
            
            // Step 12: Audit trail and reconciliation
            log.info("Step 12: Completing audit trail and performing reconciliation");
            auditService.logStockSplit(stockSplit);
            auditService.logCorporateAction(corporateAction);
            auditService.logPortfolioAdjustments(affectedPortfolios, adjustmentFactor);
            
            stockSplitService.performReconciliation(stockSplit);
            portfolioAdjustmentService.validateAllAdjustments(symbol);
            marketDataService.validateMarketDataIntegrity(symbol);
            
            auditService.generateStockSplitReport(stockSplit);
            auditService.updateRegulatoryReporting(stockSplit);

            corporateActionsService.updateActionStatus(corporateAction, "COMPLETED");

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("symbol", symbol, "splitRatio", splitRatio,
                       "exDate", exDate.toString(), "actionType", actionType,
                       "adjustmentFactor", adjustmentFactor.toString(),
                       "affectedPortfolios", String.valueOf(affectedPortfolios.size()),
                       "companyName", companyName, "exchangeCode", exchangeCode,
                       "status", "COMPLETED"), Duration.ofDays(30));

            ack.acknowledge();
            log.info("Step 12: Successfully processed stock split event: symbol={}, eventId={}, ratio={}",
                    symbol, eventId, splitRatio);
            
        } catch (Exception e) {
            log.error("SECURITY: Error processing stock split event: {}", e.getMessage(), e);
            // CRITICAL SECURITY: Mark operation as failed for retry logic
            String idempotencyKey = "stock-split:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("symbol") || 
            !eventData.has("companyName") || !eventData.has("splitRatio") ||
            !eventData.has("exDate") || !eventData.has("recordDate") ||
            !eventData.has("payableDate") || !eventData.has("actionType") ||
            !eventData.has("adjustmentFactor") || !eventData.has("currency") ||
            !eventData.has("timestamp") || !eventData.has("exchangeCode")) {
            throw new IllegalArgumentException("Invalid stock split event structure");
        }
    }
}