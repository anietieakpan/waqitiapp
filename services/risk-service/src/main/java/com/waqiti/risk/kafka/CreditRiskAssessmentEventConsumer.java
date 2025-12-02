package com.waqiti.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.risk.service.CreditRiskService;
import com.waqiti.risk.service.RiskScoringService;
import com.waqiti.risk.service.RiskReportingService;
import com.waqiti.risk.service.RiskAuditService;
import com.waqiti.risk.entity.CreditRiskAssessment;
import com.waqiti.risk.entity.RiskProfile;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #186: Credit Risk Assessment Event Consumer
 * Processes FICO scoring and credit limit determination
 * Implements 12-step zero-tolerance processing for secure credit risk workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditRiskAssessmentEventConsumer extends BaseKafkaConsumer {

    private final CreditRiskService creditRiskService;
    private final RiskScoringService riskScoringService;
    private final RiskReportingService riskReportingService;
    private final RiskAuditService riskAuditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "credit-risk-assessment-events", groupId = "credit-risk-assessment-group")
    @CircuitBreaker(name = "credit-risk-assessment-consumer")
    @Retry(name = "credit-risk-assessment-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCreditRiskAssessmentEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "credit-risk-assessment-event");
        
        try {
            log.info("Step 1: Processing credit risk assessment event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String assessmentId = eventData.path("assessmentId").asText();
            String customerId = eventData.path("customerId").asText();
            String assessmentType = eventData.path("assessmentType").asText(); // APPLICATION, REVIEW, RENEWAL
            BigDecimal requestedAmount = new BigDecimal(eventData.path("requestedAmount").asText());
            String currency = eventData.path("currency").asText();
            String productType = eventData.path("productType").asText(); // CREDIT_CARD, LOAN, LINE_OF_CREDIT
            Map<String, Object> financialData = objectMapper.convertValue(
                eventData.path("financialData"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            Map<String, Object> creditBureauData = objectMapper.convertValue(
                eventData.path("creditBureauData"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            List<String> collateralAssets = objectMapper.convertValue(
                eventData.path("collateralAssets"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String employmentStatus = eventData.path("employmentStatus").asText();
            BigDecimal annualIncome = new BigDecimal(eventData.path("annualIncome").asText());
            String riskAnalystId = eventData.path("riskAnalystId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted credit risk details: assessmentId={}, customer={}, amount={} {}, type={}", 
                    assessmentId, customerId, requestedAmount, currency, productType);
            
            // Step 3: Validate credit assessment requirements and regulatory compliance
            creditRiskService.validateCreditAssessmentRequirements(
                assessmentType, productType, requestedAmount, currency, 
                employmentStatus, timestamp);
            
            log.info("Step 3: Validated credit assessment requirements and regulatory compliance");
            
            // Step 4: Retrieve and analyze credit bureau reports
            Map<String, Object> creditBureauAnalysis = creditRiskService.analyzeCreditBureauReports(
                assessmentId, customerId, creditBureauData, timestamp);
            
            log.info("Step 4: Completed credit bureau analysis: score={}", 
                    creditBureauAnalysis.get("compositeCreditScore"));
            
            // Step 5: Calculate FICO score and alternative credit metrics
            Map<String, Object> ficoScoring = riskScoringService.calculateFICOScore(
                assessmentId, customerId, creditBureauData, financialData, timestamp);
            
            log.info("Step 5: Calculated FICO score: {}", ficoScoring.get("ficoScore"));
            
            // Step 6: Assess debt-to-income ratio and affordability analysis
            Map<String, Object> affordabilityAnalysis = creditRiskService.performAffordabilityAnalysis(
                assessmentId, annualIncome, requestedAmount, financialData, 
                productType, timestamp);
            
            log.info("Step 6: Completed affordability analysis: DTI={}", 
                    affordabilityAnalysis.get("debtToIncomeRatio"));
            
            // Step 7: Evaluate collateral and security assessment
            Map<String, Object> collateralAssessment = null;
            if (!collateralAssets.isEmpty()) {
                collateralAssessment = creditRiskService.evaluateCollateral(
                    assessmentId, collateralAssets, requestedAmount, currency, timestamp);
                
                log.info("Step 7: Completed collateral assessment: LTV={}", 
                        collateralAssessment.get("loanToValueRatio"));
            } else {
                log.info("Step 7: No collateral assessment required - unsecured credit");
            }
            
            // Step 8: Generate comprehensive credit risk score
            CreditRiskAssessment riskAssessment = creditRiskService.generateCreditRiskScore(
                assessmentId, customerId, ficoScoring, creditBureauAnalysis, 
                affordabilityAnalysis, collateralAssessment, timestamp);
            
            log.info("Step 8: Generated credit risk score: {} ({})", 
                    riskAssessment.getRiskScore(), riskAssessment.getRiskGrade());
            
            // Step 9: Determine credit decision and limits
            Map<String, Object> creditDecision = creditRiskService.makeCreditDecision(
                assessmentId, riskAssessment, requestedAmount, productType, timestamp);
            
            log.info("Step 9: Credit decision: {} with limit: {}", 
                    creditDecision.get("decision"), creditDecision.get("approvedLimit"));
            
            // Step 10: Set pricing and terms based on risk profile
            Map<String, Object> pricingTerms = creditRiskService.determinePricingTerms(
                assessmentId, riskAssessment, creditDecision, productType, timestamp);
            
            log.info("Step 10: Determined pricing terms: rate={}, term={}", 
                    pricingTerms.get("interestRate"), pricingTerms.get("termMonths"));
            
            // Step 11: Generate risk monitoring and review requirements
            creditRiskService.establishRiskMonitoring(
                customerId, assessmentId, riskAssessment, creditDecision, timestamp);
            
            // Notify risk management for high-risk approvals
            if ("APPROVED".equals(creditDecision.get("decision")) && 
                riskAssessment.getRiskScore() >= 700) {
                creditRiskService.notifyRiskManagement(
                    assessmentId, customerId, riskAssessment, creditDecision, timestamp);
                
                log.info("Step 11: Notified risk management for high-risk approval");
            }
            
            // Step 12: Log credit risk assessment for audit trail and regulatory compliance
            riskAuditService.logCreditRiskAssessmentEvent(
                assessmentId, customerId, assessmentType, productType, 
                requestedAmount, riskAssessment.getRiskScore(), 
                creditDecision.get("decision").toString(), 
                riskAnalystId, timestamp);
            
            riskReportingService.generateCreditRiskReports(
                riskAssessment, creditDecision, pricingTerms, assessmentId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed credit risk assessment event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing credit risk assessment event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("assessmentId") || 
            !eventData.has("customerId") || !eventData.has("assessmentType") ||
            !eventData.has("requestedAmount") || !eventData.has("currency") ||
            !eventData.has("productType") || !eventData.has("financialData") ||
            !eventData.has("creditBureauData") || !eventData.has("employmentStatus") ||
            !eventData.has("annualIncome") || !eventData.has("riskAnalystId") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid credit risk assessment event structure");
        }
    }
}