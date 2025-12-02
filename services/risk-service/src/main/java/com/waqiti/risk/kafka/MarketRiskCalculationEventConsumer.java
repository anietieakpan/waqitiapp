package com.waqiti.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.risk.service.MarketRiskService;
import com.waqiti.risk.service.RiskReportingService;
import com.waqiti.risk.entity.MarketRiskAssessment;
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
 * Critical Event Consumer #119: Market Risk Calculation Event Consumer
 * Processes market risk calculations with VaR, CVaR, and stress testing
 * Implements 12-step zero-tolerance processing for Basel III market risk compliance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketRiskCalculationEventConsumer extends BaseKafkaConsumer {

    private final MarketRiskService marketRiskService;
    private final RiskReportingService riskReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "market-risk-calculation-events", groupId = "market-risk-group")
    @CircuitBreaker(name = "market-risk-consumer")
    @Retry(name = "market-risk-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMarketRiskCalculationEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "market-risk-calculation-event");
        
        try {
            log.info("Step 1: Processing market risk calculation event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String portfolioId = eventData.path("portfolioId").asText();
            BigDecimal portfolioValue = new BigDecimal(eventData.path("portfolioValue").asText());
            String currency = eventData.path("currency").asText();
            int confidenceLevel = eventData.path("confidenceLevel").asInt();
            int timeHorizon = eventData.path("timeHorizon").asInt();
            String calculationMethod = eventData.path("calculationMethod").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted market risk details: portfolioId={}, value={}, method={}", 
                    portfolioId, portfolioValue, calculationMethod);
            
            // Step 3: Retrieve portfolio positions and market data
            marketRiskService.loadPortfolioPositions(portfolioId, timestamp);
            marketRiskService.loadMarketData(portfolioId, currency, timestamp);
            
            log.info("Step 3: Loaded portfolio positions and market data");
            
            // Step 4: Calculate Value at Risk (VaR) - Basel III requirement
            BigDecimal varAmount = marketRiskService.calculateVaR(
                    portfolioId, portfolioValue, confidenceLevel, timeHorizon, 
                    calculationMethod, timestamp);
            
            log.info("Step 4: Calculated VaR: amount={}, confidence={}%, horizon={} days", 
                    varAmount, confidenceLevel, timeHorizon);
            
            // Step 5: Calculate Conditional VaR (CVaR/Expected Shortfall)
            BigDecimal cvarAmount = marketRiskService.calculateCVaR(
                    portfolioId, portfolioValue, confidenceLevel, timeHorizon, timestamp);
            
            log.info("Step 5: Calculated CVaR: amount={}", cvarAmount);
            
            // Step 6: Perform historical simulation
            BigDecimal historicalVar = marketRiskService.calculateHistoricalVaR(
                    portfolioId, confidenceLevel, timeHorizon, timestamp);
            
            log.info("Step 6: Historical simulation VaR: amount={}", historicalVar);
            
            // Step 7: Execute stress testing scenarios (Basel III FRTB)
            marketRiskService.executeStressTests(portfolioId, portfolioValue, timestamp);
            
            log.info("Step 7: Executed stress testing scenarios");
            
            // Step 8: Calculate Greeks for derivatives positions
            if (marketRiskService.hasDerivatives(portfolioId)) {
                marketRiskService.calculateGreeks(portfolioId, timestamp);
                log.info("Step 8: Calculated Greeks for derivatives");
            }
            
            // Step 9: Assess risk limit breaches
            boolean limitBreached = marketRiskService.assessRiskLimits(
                    portfolioId, varAmount, cvarAmount, timestamp);
            
            if (limitBreached) {
                log.error("Step 9: CRITICAL - Risk limit breach detected: portfolioId={}", portfolioId);
                marketRiskService.triggerRiskLimitAlert(portfolioId, varAmount, timestamp);
            }
            
            // Step 10: Calculate regulatory capital requirement (Basel III)
            BigDecimal capitalRequirement = marketRiskService.calculateMarketRiskCapital(
                    varAmount, cvarAmount, timestamp);
            
            log.info("Step 10: Calculated regulatory capital requirement: amount={}", capitalRequirement);
            
            // Step 11: Generate risk reports and dashboards
            MarketRiskAssessment assessment = riskReportingService.generateMarketRiskReport(
                    portfolioId, varAmount, cvarAmount, historicalVar, capitalRequirement, timestamp);
            
            log.info("Step 11: Generated market risk assessment report");
            
            // Step 12: Archive risk calculations for backtesting
            marketRiskService.archiveRiskCalculation(eventId, assessment, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed market risk calculation event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing market risk calculation event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("portfolioId") || 
            !eventData.has("portfolioValue") || !eventData.has("confidenceLevel")) {
            throw new IllegalArgumentException("Invalid market risk calculation event structure");
        }
    }
}