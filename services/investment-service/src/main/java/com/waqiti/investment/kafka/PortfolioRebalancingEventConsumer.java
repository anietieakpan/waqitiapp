package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.PortfolioRebalancingService;
import com.waqiti.investment.service.TaxLotOptimizationService;
import com.waqiti.investment.entity.RebalancingOrder;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #170: Portfolio Rebalancing Event Consumer
 * Processes automated portfolio rebalancing with tax-loss harvesting
 * Implements 12-step zero-tolerance processing for asset allocation optimization
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioRebalancingEventConsumer extends BaseKafkaConsumer {

    private final PortfolioRebalancingService portfolioRebalancingService;
    private final TaxLotOptimizationService taxLotOptimizationService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "portfolio-rebalancing-events", groupId = "portfolio-rebalancing-group")
    @CircuitBreaker(name = "portfolio-rebalancing-consumer")
    @Retry(name = "portfolio-rebalancing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePortfolioRebalancingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "portfolio-rebalancing-event");
        
        try {
            log.info("Step 1: Processing portfolio rebalancing event: partition={}, offset={}",
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);

            String eventId = eventData.path("eventId").asText();
            String portfolioId = eventData.path("portfolioId").asText();
            String accountId = eventData.path("accountId").asText();

            // CRITICAL SECURITY: Idempotency check - prevent duplicate portfolio rebalancing
            String idempotencyKey = "portfolio-rebalancing:" + portfolioId + ":" + eventId;
            UUID operationId = UUID.randomUUID();

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate portfolio rebalancing event ignored: portfolioId={}, eventId={}",
                        portfolioId, eventId);
                ack.acknowledge();
                return;
            }
            String rebalancingStrategy = eventData.path("rebalancingStrategy").asText();
            BigDecimal driftThreshold = new BigDecimal(eventData.path("driftThreshold").asText());
            boolean taxLossHarvestingEnabled = eventData.path("taxLossHarvestingEnabled").asBoolean();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted rebalancing details: portfolioId={}, strategy={}", 
                    portfolioId, rebalancingStrategy);
            
            Map<String, BigDecimal> currentAllocations = portfolioRebalancingService.getCurrentAllocations(
                    portfolioId, timestamp);
            
            log.info("Step 3: Retrieved current portfolio allocations");
            
            Map<String, BigDecimal> targetAllocations = portfolioRebalancingService.getTargetAllocations(
                    portfolioId, rebalancingStrategy, timestamp);
            
            log.info("Step 4: Retrieved target allocations for strategy: {}", rebalancingStrategy);
            
            BigDecimal maxDrift = portfolioRebalancingService.calculateAllocationDrift(
                    currentAllocations, targetAllocations);
            
            if (maxDrift.compareTo(driftThreshold) < 0) {
                log.info("Step 5: Drift below threshold, no rebalancing needed: drift={}", maxDrift);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 5: Drift exceeds threshold, rebalancing required: drift={}", maxDrift);
            
            List<RebalancingOrder> orders = portfolioRebalancingService.generateRebalancingOrders(
                    portfolioId, currentAllocations, targetAllocations, timestamp);
            
            log.info("Step 6: Generated {} rebalancing orders", orders.size());
            
            if (taxLossHarvestingEnabled) {
                orders = taxLotOptimizationService.optimizeForTaxLossHarvesting(
                        portfolioId, orders, timestamp);
                log.info("Step 7: Applied tax-loss harvesting optimization");
            } else {
                log.info("Step 7: Tax-loss harvesting not enabled");
            }
            
            portfolioRebalancingService.validateOrderCompliance(orders, accountId, timestamp);
            log.info("Step 8: Validated rebalancing order compliance");
            
            portfolioRebalancingService.executeRebalancingOrders(portfolioId, orders, timestamp);
            log.info("Step 9: Executed rebalancing orders");
            
            portfolioRebalancingService.updatePortfolioMetrics(portfolioId, 
                    targetAllocations, timestamp);
            log.info("Step 10: Updated portfolio performance metrics");
            
            portfolioRebalancingService.sendRebalancingNotification(accountId, portfolioId,
                    orders.size(), maxDrift, timestamp);
            log.info("Step 11: Sent rebalancing notification");

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("portfolioId", portfolioId, "ordersGenerated", orders.size(),
                       "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed portfolio rebalancing: eventId={}", eventId);

        } catch (Exception e) {
            log.error("SECURITY: Error processing portfolio rebalancing event: {}", e.getMessage(), e);
            // CRITICAL SECURITY: Mark operation as failed for retry logic
            String idempotencyKey = "portfolio-rebalancing:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("portfolioId") || 
            !eventData.has("accountId") || !eventData.has("rebalancingStrategy")) {
            throw new IllegalArgumentException("Invalid portfolio rebalancing event structure");
        }
    }
}