package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.AMLTransactionMonitoringService;
import com.waqiti.compliance.service.TransactionPatternAnalyzer;
import com.waqiti.compliance.service.SARProcessingService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.entity.AMLAlert;
import com.waqiti.compliance.entity.TransactionRiskProfile;
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
 * Critical Event Consumer #111: AML Transaction Monitoring Event Consumer
 * Processes continuous transaction monitoring with AML compliance and suspicious activity detection
 * Implements 12-step zero-tolerance processing for comprehensive AML monitoring workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AMLTransactionMonitoringEventConsumer extends BaseKafkaConsumer {

    private final AMLTransactionMonitoringService amlMonitoringService;
    private final TransactionPatternAnalyzer patternAnalyzer;
    private final SARProcessingService sarProcessingService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final UniversalDLQHandler universalDLQHandler;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "aml-transaction-monitoring-events", groupId = "aml-transaction-monitoring-group")
    @CircuitBreaker(name = "aml-transaction-monitoring-consumer")
    @Retry(name = "aml-transaction-monitoring-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleAMLTransactionMonitoringEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "aml-transaction-monitoring-event");
        
        try {
            log.info("Step 1: Processing AML transaction monitoring event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String transactionId = eventData.path("transactionId").asText();
            String customerId = eventData.path("customerId").asText();
            String counterpartyId = eventData.path("counterpartyId").asText();
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal amount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("amount").asText(),
                BigDecimal.ZERO
            );
            String currency = eventData.path("currency").asText();
            String transactionType = eventData.path("transactionType").asText();
            String originCountry = eventData.path("originCountry").asText();
            String destinationCountry = eventData.path("destinationCountry").asText();
            String purposeCode = eventData.path("purposeCode").asText();
            String channel = eventData.path("channel").asText(); // ONLINE, MOBILE, BRANCH, ATM
            List<String> involvedAccounts = objectMapper.convertValue(
                eventData.path("involvedAccounts"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            Map<String, Object> transactionMetadata = objectMapper.convertValue(
                eventData.path("transactionMetadata"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            LocalDateTime transactionDateTime = LocalDateTime.parse(eventData.path("transactionDateTime").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted AML monitoring details: transactionId={}, amount={} {}, customer={}, type={}", 
                    transactionId, amount, currency, customerId, transactionType);
            
            // Step 3: Retrieve customer risk profile and transaction history
            TransactionRiskProfile riskProfile = amlMonitoringService.getCustomerRiskProfile(
                customerId, timestamp);
            
            List<String> recentTransactions = amlMonitoringService.getRecentTransactionHistory(
                customerId, 30, timestamp); // Last 30 days
            
            log.info("Step 3: Retrieved customer risk profile: riskLevel={}, recentTransactions={}", 
                    riskProfile.getRiskLevel(), recentTransactions.size());
            
            // Step 4: Execute real-time AML screening rules
            List<String> triggeredRules = amlMonitoringService.executeAMLScreeningRules(
                transactionId, customerId, amount, currency, transactionType, 
                originCountry, destinationCountry, riskProfile, timestamp);
            
            log.info("Step 4: AML screening completed: triggeredRules={}", triggeredRules.size());
            
            // Step 5: Analyze transaction patterns and behavioral anomalies
            List<String> detectedPatterns = patternAnalyzer.analyzeTransactionPatterns(
                customerId, transactionId, amount, currency, transactionType, 
                channel, recentTransactions, transactionMetadata, timestamp);
            
            if (!detectedPatterns.isEmpty()) {
                log.warn("Step 5: Suspicious patterns detected: patterns={}", detectedPatterns);
            }
            
            // Step 6: Perform velocity and threshold checks
            boolean velocityAlert = amlMonitoringService.checkVelocityLimits(
                customerId, amount, currency, transactionType, timestamp);
            
            boolean thresholdAlert = amlMonitoringService.checkAMLThresholds(
                amount, currency, originCountry, destinationCountry, timestamp);
            
            if (velocityAlert || thresholdAlert) {
                log.warn("Step 6: Velocity/threshold alerts triggered: velocity={}, threshold={}", 
                        velocityAlert, thresholdAlert);
            }
            
            // Step 7: Calculate composite risk score
            BigDecimal riskScore = amlMonitoringService.calculateCompositeRiskScore(
                riskProfile, triggeredRules, detectedPatterns, velocityAlert, 
                thresholdAlert, transactionMetadata, timestamp);
            
            log.info("Step 7: Calculated composite risk score: {}", riskScore);
            
            // Step 8: Generate AML alerts based on risk score and rules
            if (riskScore.compareTo(new BigDecimal("70")) >= 0 || !triggeredRules.isEmpty()) {
                AMLAlert alert = amlMonitoringService.generateAMLAlert(
                    transactionId, customerId, counterpartyId, riskScore, 
                    triggeredRules, detectedPatterns, amount, currency, timestamp);
                
                log.warn("Step 8: Generated AML alert: alertId={}, riskScore={}", 
                        alert.getAlertId(), riskScore);
                
                // Step 9: Initiate enhanced due diligence if high risk
                if (riskScore.compareTo(new BigDecimal("85")) >= 0) {
                    amlMonitoringService.initiateEnhancedDueDiligence(
                        customerId, alert.getAlertId(), timestamp);
                    
                    log.warn("Step 9: Initiated enhanced due diligence for high-risk transaction");
                }
            }
            
            // Step 10: Check if SAR filing is required
            boolean sarRequired = sarProcessingService.evaluateSARRequirement(
                transactionId, customerId, amount, currency, riskScore, 
                triggeredRules, detectedPatterns, timestamp);
            
            if (sarRequired) {
                sarProcessingService.initiateSARFiling(
                    transactionId, customerId, riskScore, triggeredRules, timestamp);
                
                log.warn("Step 10: Initiated SAR filing for suspicious transaction");
            }
            
            // Step 11: Update customer risk profile and monitoring parameters
            amlMonitoringService.updateCustomerRiskProfile(
                customerId, riskScore, triggeredRules, detectedPatterns, timestamp);
            
            amlMonitoringService.adjustMonitoringParameters(
                customerId, riskScore, timestamp);
            
            log.info("Step 11: Updated customer risk profile and monitoring parameters");
            
            // Step 12: Log AML monitoring event and generate reports
            amlMonitoringService.logAMLMonitoringEvent(
                transactionId, customerId, counterpartyId, amount, currency, 
                transactionType, riskScore, triggeredRules, detectedPatterns, 
                velocityAlert, thresholdAlert, timestamp);
            
            // Generate regulatory reports for high-risk transactions
            if (riskScore.compareTo(new BigDecimal("80")) >= 0) {
                regulatoryReportingService.generateAMLMonitoringReports(
                    transactionId, customerId, riskScore, timestamp);
                
                log.info("Step 12: Generated regulatory reports for high-risk AML monitoring");
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed AML transaction monitoring event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing AML transaction monitoring event: {}", e.getMessage(), e);

            // Send to DLQ for retry/parking
            try {
                universalDLQHandler.handleFailedMessage(record, e);
            } catch (Exception dlqEx) {
                log.error("CRITICAL: Failed to send AML transaction monitoring event to DLQ", dlqEx);
            }

            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("transactionId") || 
            !eventData.has("customerId") || !eventData.has("amount") ||
            !eventData.has("currency") || !eventData.has("transactionType") ||
            !eventData.has("originCountry") || !eventData.has("destinationCountry") ||
            !eventData.has("purposeCode") || !eventData.has("channel") ||
            !eventData.has("involvedAccounts") || !eventData.has("transactionDateTime")) {
            throw new IllegalArgumentException("Invalid AML transaction monitoring event structure");
        }
    }
}