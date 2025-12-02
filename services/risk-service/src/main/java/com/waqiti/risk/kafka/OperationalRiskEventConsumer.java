package com.waqiti.risk.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.risk.service.OperationalRiskService;
import com.waqiti.risk.service.RiskAuditService;
import com.waqiti.risk.service.RiskReportingService;
import com.waqiti.risk.entity.OperationalRiskEvent;
import com.waqiti.risk.entity.RiskLoss;
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
 * Critical Event Consumer #187: Operational Risk Event Consumer
 * Processes operational loss tracking and Basel II compliance
 * Implements 12-step zero-tolerance processing for secure operational risk workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationalRiskEventConsumer extends BaseKafkaConsumer {

    private final OperationalRiskService operationalRiskService;
    private final RiskAuditService riskAuditService;
    private final RiskReportingService riskReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "operational-risk-events", groupId = "operational-risk-group")
    @CircuitBreaker(name = "operational-risk-consumer")
    @Retry(name = "operational-risk-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleOperationalRiskEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "operational-risk-event");
        
        try {
            log.info("Step 1: Processing operational risk event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String riskEventId = eventData.path("riskEventId").asText();
            String eventType = eventData.path("eventType").asText(); // INTERNAL_FRAUD, EXTERNAL_FRAUD, SYSTEM_FAILURE
            String businessLine = eventData.path("businessLine").asText();
            String eventCategory = eventData.path("eventCategory").asText(); // BASEL_II_CATEGORY
            BigDecimal lossAmount = new BigDecimal(eventData.path("lossAmount").asText());
            String currency = eventData.path("currency").asText();
            LocalDateTime eventDate = LocalDateTime.parse(eventData.path("eventDate").asText());
            LocalDateTime discoveryDate = LocalDateTime.parse(eventData.path("discoveryDate").asText());
            String severity = eventData.path("severity").asText(); // LOW, MEDIUM, HIGH, CRITICAL
            List<String> affectedSystems = objectMapper.convertValue(
                eventData.path("affectedSystems"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            Map<String, Object> rootCauseAnalysis = objectMapper.convertValue(
                eventData.path("rootCauseAnalysis"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            List<String> controlFailures = objectMapper.convertValue(
                eventData.path("controlFailures"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String reporterId = eventData.path("reporterId").asText();
            String riskManagerId = eventData.path("riskManagerId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted operational risk details: eventId={}, type={}, loss={} {}, severity={}", 
                    riskEventId, eventType, lossAmount, currency, severity);
            
            // Step 3: Validate operational risk classification and Basel II compliance
            operationalRiskService.validateOperationalRiskClassification(
                eventType, eventCategory, businessLine, lossAmount, currency, timestamp);
            
            log.info("Step 3: Validated operational risk classification and Basel II compliance");
            
            // Step 4: Conduct immediate impact assessment and containment
            Map<String, Object> impactAssessment = operationalRiskService.conductImpactAssessment(
                riskEventId, eventType, affectedSystems, lossAmount, businessLine, timestamp);
            
            log.info("Step 4: Completed impact assessment: businessImpact={}", 
                    impactAssessment.get("businessImpactLevel"));
            
            // Step 5: Analyze root cause and control effectiveness
            Map<String, Object> rootCauseAnalysisResult = operationalRiskService.analyzeRootCause(
                riskEventId, rootCauseAnalysis, controlFailures, eventType, timestamp);
            
            log.info("Step 5: Completed root cause analysis");
            
            // Step 6: Calculate operational risk capital requirements
            Map<String, Object> capitalCalculation = operationalRiskService.calculateOperationalRiskCapital(
                riskEventId, lossAmount, eventCategory, businessLine, 
                impactAssessment, timestamp);
            
            log.info("Step 6: Calculated operational risk capital: requirement={}", 
                    capitalCalculation.get("capitalRequirement"));
            
            // Step 7: Update operational risk database and loss data collection
            OperationalRiskEvent operationalRiskEvent = operationalRiskService.recordOperationalRiskEvent(
                riskEventId, eventType, eventCategory, businessLine, 
                lossAmount, currency, eventDate, discoveryDate, 
                impactAssessment, rootCauseAnalysisResult, timestamp);
            
            log.info("Step 7: Recorded operational risk event in database");
            
            // Step 8: Generate corrective action plans and control improvements
            List<String> correctiveActions = operationalRiskService.generateCorrectiveActionPlans(
                riskEventId, rootCauseAnalysisResult, controlFailures, 
                severity, timestamp);
            
            log.info("Step 8: Generated {} corrective action plans", correctiveActions.size());
            
            // Step 9: Update risk indicators and early warning systems
            operationalRiskService.updateRiskIndicators(
                businessLine, eventType, operationalRiskEvent, timestamp);
            
            log.info("Step 9: Updated risk indicators and early warning systems");
            
            // Step 10: Implement immediate remediation and process improvements
            operationalRiskService.implementImmediateRemediation(
                riskEventId, affectedSystems, correctiveActions, severity, timestamp);
            
            log.info("Step 10: Implemented immediate remediation measures");
            
            // Step 11: Notify stakeholders and regulatory bodies
            operationalRiskService.notifyStakeholders(
                riskEventId, operationalRiskEvent, impactAssessment, 
                riskManagerId, timestamp);
            
            // Report significant losses to regulators
            if (lossAmount.compareTo(new BigDecimal("100000")) >= 0) {
                operationalRiskService.reportToRegulatoryBodies(
                    riskEventId, operationalRiskEvent, capitalCalculation, timestamp);
                
                log.info("Step 11: Reported significant operational loss to regulatory bodies");
            }
            
            // Step 12: Log operational risk for audit trail and capital adequacy reporting
            riskAuditService.logOperationalRiskEvent(
                riskEventId, eventType, eventCategory, businessLine, 
                lossAmount, currency, severity, reporterId, 
                riskManagerId, timestamp);
            
            riskReportingService.generateOperationalRiskReports(
                operationalRiskEvent, impactAssessment, capitalCalculation, 
                correctiveActions, riskEventId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed operational risk event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing operational risk event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("riskEventId") || 
            !eventData.has("eventType") || !eventData.has("businessLine") ||
            !eventData.has("eventCategory") || !eventData.has("lossAmount") ||
            !eventData.has("currency") || !eventData.has("eventDate") ||
            !eventData.has("discoveryDate") || !eventData.has("severity") ||
            !eventData.has("reporterId") || !eventData.has("riskManagerId") ||
            !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid operational risk event structure");
        }
    }
}