package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.PortfolioManagementService;
import com.waqiti.investment.service.AssetAllocationService;
import com.waqiti.investment.service.RiskManagementService;
import com.waqiti.investment.service.PerformanceAnalyticsService;
import com.waqiti.investment.service.AuditService;
import com.waqiti.investment.entity.Portfolio;
import com.waqiti.investment.entity.AssetAllocation;
import com.waqiti.investment.entity.RiskAssessment;
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
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #10: Portfolio Events Consumer
 * Processes portfolio management, asset allocation, and investment rebalancing
 * Implements 12-step zero-tolerance processing for portfolio management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioEventsConsumer extends BaseKafkaConsumer {

    private final PortfolioManagementService portfolioService;
    private final AssetAllocationService assetAllocationService;
    private final RiskManagementService riskManagementService;
    private final PerformanceAnalyticsService performanceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(
        topics = "portfolio-events", 
        groupId = "portfolio-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "portfolio-consumer")
    @Retry(name = "portfolio-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handlePortfolioEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "portfolio-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing portfolio event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String portfolioId = eventData.path("portfolioId").asText();
            String customerId = eventData.path("customerId").asText();
            String eventType = eventData.path("eventType").asText(); // REBALANCE, ALLOCATION_CHANGE, RISK_ADJUSTMENT, PERFORMANCE_REVIEW
            BigDecimal portfolioValue = new BigDecimal(eventData.path("portfolioValue").asText());
            String riskProfile = eventData.path("riskProfile").asText(); // CONSERVATIVE, MODERATE, AGGRESSIVE
            String investmentStrategy = eventData.path("investmentStrategy").asText();
            LocalDateTime rebalanceDate = LocalDateTime.parse(eventData.path("rebalanceDate").asText());
            String currency = eventData.path("currency").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            boolean automaticRebalancing = eventData.path("automaticRebalancing").asBoolean();

            // CRITICAL SECURITY: Idempotency check - prevent duplicate portfolio event processing
            String idempotencyKey = "portfolio-event:" + portfolioId + ":" + eventType + ":" + eventId;
            UUID operationId = UUID.randomUUID();

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate portfolio event ignored: portfolioId={}, eventType={}, eventId={}",
                        portfolioId, eventType, eventId);
                ack.acknowledge();
                return;
            }

            log.info("Step 2: Extracted portfolio details: portfolioId={}, value={} {}, risk={}, type={}",
                    portfolioId, portfolioValue, currency, riskProfile, eventType);
            
            // Step 3: Portfolio validation and status verification
            log.info("Step 3: Validating portfolio status and investment constraints");
            Portfolio portfolio = portfolioService.getPortfolio(portfolioId);
            
            portfolioService.validatePortfolioStatus(portfolio);
            portfolioService.validateCustomerAccess(customerId, portfolioId);
            portfolioService.validateInvestmentConstraints(portfolio);
            
            if (!portfolioService.isValidEventType(eventType)) {
                throw new IllegalStateException("Invalid portfolio event type: " + eventType);
            }
            
            portfolioService.checkPortfolioLimits(portfolio);
            portfolioService.validateRiskProfile(riskProfile);
            
            // Step 4: Current allocation analysis and drift calculation
            log.info("Step 4: Analyzing current asset allocation and calculating drift");
            AssetAllocation currentAllocation = assetAllocationService.getCurrentAllocation(portfolioId);
            AssetAllocation targetAllocation = assetAllocationService.getTargetAllocation(portfolioId, riskProfile);
            
            assetAllocationService.calculateAllocationDrift(currentAllocation, targetAllocation);
            assetAllocationService.analyzeAssetClassPerformance(currentAllocation);
            assetAllocationService.validateAllocationConstraints(currentAllocation);
            
            BigDecimal totalDrift = assetAllocationService.calculateTotalDrift(currentAllocation, targetAllocation);
            boolean rebalanceRequired = assetAllocationService.isRebalanceRequired(totalDrift);
            
            assetAllocationService.updateAllocationMetrics(portfolioId, currentAllocation);
            
            // Step 5: Risk assessment and profile validation
            log.info("Step 5: Conducting comprehensive risk assessment and profile validation");
            RiskAssessment riskAssessment = riskManagementService.createRiskAssessment(portfolio);
            
            riskManagementService.calculatePortfolioRisk(portfolio, riskAssessment);
            riskManagementService.validateRiskLimits(portfolio, riskAssessment);
            riskManagementService.analyzeConcentrationRisk(portfolio, riskAssessment);
            riskManagementService.assessLiquidityRisk(portfolio, riskAssessment);
            
            BigDecimal currentRiskLevel = riskManagementService.calculateCurrentRiskLevel(portfolio);
            boolean riskWithinLimits = riskManagementService.isRiskWithinLimits(currentRiskLevel, riskProfile);
            
            if (!riskWithinLimits) {
                riskManagementService.escalateRiskViolation(portfolio, riskAssessment);
            }
            
            // Step 6: Performance analysis and benchmarking
            log.info("Step 6: Analyzing portfolio performance and benchmarking");
            performanceService.calculatePortfolioReturns(portfolioId);
            performanceService.analyzePerformanceMetrics(portfolioId);
            performanceService.compareToBenchmark(portfolioId, investmentStrategy);
            performanceService.calculateRiskAdjustedReturns(portfolioId);
            
            BigDecimal performanceScore = performanceService.calculatePerformanceScore(portfolioId);
            performanceService.updatePerformanceHistory(portfolioId, performanceScore);
            
            if (performanceService.underperformingBenchmark(portfolioId)) {
                performanceService.generatePerformanceAlert(portfolioId);
            }
            
            // Step 7: Rebalancing strategy and execution
            log.info("Step 7: Implementing rebalancing strategy and executing trades");
            if ("REBALANCE".equals(eventType) || rebalanceRequired) {
                assetAllocationService.calculateRebalancingTrades(portfolio, targetAllocation);
                assetAllocationService.optimizeRebalancingCosts(portfolio);
                assetAllocationService.executeRebalancingTrades(portfolio);
                
                portfolioService.updatePortfolioWeights(portfolio, targetAllocation);
                portfolioService.recordRebalancingActivity(portfolio, rebalanceDate);
                
                if (automaticRebalancing) {
                    portfolioService.scheduleNextRebalancing(portfolio);
                }
            }
            
            // Step 8: Investment strategy implementation
            log.info("Step 8: Implementing investment strategy and tactical adjustments");
            portfolioService.validateInvestmentStrategy(investmentStrategy);
            portfolioService.implementStrategyChanges(portfolio, investmentStrategy);
            portfolioService.adjustTacticalAllocations(portfolio, investmentStrategy);
            
            if ("ALLOCATION_CHANGE".equals(eventType)) {
                assetAllocationService.implementAllocationChanges(portfolio, eventData);
                assetAllocationService.validateNewAllocations(portfolio);
            }
            
            portfolioService.updateInvestmentObjectives(portfolio, investmentStrategy);
            
            // Step 9: Tax optimization and efficiency
            log.info("Step 9: Implementing tax optimization and efficiency measures");
            portfolioService.analyzeTaxImplications(portfolio);
            portfolioService.optimizeTaxEfficiency(portfolio);
            portfolioService.harvestTaxLosses(portfolio);
            
            portfolioService.calculateTaxableGains(portfolio);
            portfolioService.manageTaxableEvents(portfolio);
            portfolioService.updateTaxReporting(portfolio);
            
            if (portfolioService.hasTaxableEvents(portfolio)) {
                portfolioService.generateTaxDocuments(portfolio);
            }
            
            // Step 10: Compliance and regulatory validation
            log.info("Step 10: Ensuring compliance with regulatory requirements");
            portfolioService.validateRegulatoryCompliance(portfolio);
            portfolioService.checkInvestmentRestrictions(portfolio);
            portfolioService.validateFiduciaryRequirements(portfolio);
            
            if (portfolioService.requiresDisclosures(portfolio)) {
                portfolioService.generateDisclosureDocuments(portfolio);
            }
            
            portfolioService.updateComplianceMetrics(portfolio);
            
            // Step 11: Client reporting and communication
            log.info("Step 11: Generating client reports and communications");
            portfolioService.generatePortfolioStatement(portfolio);
            portfolioService.createPerformanceReport(portfolio);
            portfolioService.updateClientDashboard(portfolio);
            
            if ("PERFORMANCE_REVIEW".equals(eventType)) {
                performanceService.generatePerformanceReview(portfolio);
                performanceService.createInvestmentRecommendations(portfolio);
            }
            
            portfolioService.scheduleClientCommunications(portfolio);
            
            // Step 12: Audit trail and documentation
            log.info("Step 12: Completing audit trail and maintaining documentation");
            auditService.logPortfolioEvent(portfolio, eventType);
            auditService.logAssetAllocation(currentAllocation);
            auditService.logRiskAssessment(riskAssessment);
            
            portfolioService.updatePortfolioMetrics(portfolio);
            assetAllocationService.updateAllocationStatistics(currentAllocation);
            performanceService.updatePerformanceMetrics(portfolio);
            
            auditService.generatePortfolioReport(portfolio);
            auditService.updateRegulatoryReporting(portfolio);

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("portfolioId", portfolioId, "customerId", customerId,
                       "eventType", eventType, "portfolioValue", portfolioValue.toString(),
                       "riskProfile", riskProfile, "currency", currency,
                       "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed portfolio event: portfolioId={}, eventId={}, type={}",
                    portfolioId, eventId, eventType);
            
        } catch (Exception e) {
            log.error("SECURITY: Error processing portfolio event: {}", e.getMessage(), e);
            // CRITICAL SECURITY: Mark operation as failed for retry logic
            String idempotencyKey = "portfolio-event:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("portfolioId") || 
            !eventData.has("customerId") || !eventData.has("eventType") ||
            !eventData.has("portfolioValue") || !eventData.has("riskProfile") ||
            !eventData.has("investmentStrategy") || !eventData.has("rebalanceDate") ||
            !eventData.has("currency") || !eventData.has("timestamp") ||
            !eventData.has("automaticRebalancing")) {
            throw new IllegalArgumentException("Invalid portfolio event structure");
        }
    }
}