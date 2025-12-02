package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.validation.NullSafetyUtils;
import com.waqiti.compliance.service.AMLTransactionMonitoringService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.InvestigationService;
import com.waqiti.compliance.entity.SuspiciousActivity;
import com.waqiti.compliance.entity.ComplianceAlert;
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
 * Critical Event Consumer #182: Trade-Based Money Laundering Event Consumer
 * Processes TBML pattern detection and trade finance compliance
 * Implements 12-step zero-tolerance processing for secure TBML monitoring workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradeBasedMoneyLaunderingEventConsumer extends BaseKafkaConsumer {

    private final AMLTransactionMonitoringService tbmlMonitoringService;
    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final InvestigationService investigationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "trade-based-money-laundering-events", groupId = "trade-based-money-laundering-group")
    @CircuitBreaker(name = "trade-based-money-laundering-consumer")
    @Retry(name = "trade-based-money-laundering-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleTradeBasedMoneyLaunderingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "trade-based-money-laundering-event");
        
        try {
            log.info("Step 1: Processing TBML detection event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String alertId = eventData.path("alertId").asText();
            String customerId = eventData.path("customerId").asText();
            String tradeTransactionId = eventData.path("tradeTransactionId").asText();
            String tradeType = eventData.path("tradeType").asText(); // IMPORT, EXPORT, DOMESTIC
            List<String> commodityCodes = objectMapper.convertValue(
                eventData.path("commodityCodes"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal invoiceAmount = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("invoiceAmount").asText(),
                BigDecimal.ZERO
            );
            String invoiceCurrency = eventData.path("invoiceCurrency").asText();
            // SAFETY FIX: Safe parsing with validation to prevent NumberFormatException
            BigDecimal goodsValue = NullSafetyUtils.safeParseBigDecimal(
                eventData.path("goodsValue").asText(),
                BigDecimal.ZERO
            );
            String originCountry = eventData.path("originCountry").asText();
            String destinationCountry = eventData.path("destinationCountry").asText();
            String importerExporter = eventData.path("importerExporter").asText();
            String shippingMethod = eventData.path("shippingMethod").asText();
            List<String> tbmlIndicators = objectMapper.convertValue(
                eventData.path("tbmlIndicators"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            Map<String, Object> tradeDocuments = objectMapper.convertValue(
                eventData.path("tradeDocuments"), 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
            String riskLevel = eventData.path("riskLevel").asText();
            String analystId = eventData.path("analystId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted TBML details: alertId={}, trade={}, type={}, amount={} {}", 
                    alertId, tradeTransactionId, tradeType, invoiceAmount, invoiceCurrency);
            
            // Step 3: Validate trade finance regulations and TBML jurisdiction
            tbmlMonitoringService.validateTradeFinanceRegulations(
                tradeType, originCountry, destinationCountry, commodityCodes, 
                invoiceAmount, invoiceCurrency, timestamp);
            
            log.info("Step 3: Validated trade finance regulations and TBML jurisdiction");
            
            // Step 4: Analyze invoice and goods value discrepancies
            Map<String, Object> valuationAnalysis = tbmlMonitoringService.analyzeInvoiceValuationDiscrepancies(
                alertId, tradeTransactionId, invoiceAmount, goodsValue, 
                commodityCodes, originCountry, destinationCountry, timestamp);
            
            log.info("Step 4: Completed invoice valuation analysis: discrepancy={}", 
                    valuationAnalysis.get("discrepancyPercentage"));
            
            // Step 5: Detect over/under invoicing patterns
            Map<String, Object> invoicingPatterns = tbmlMonitoringService.detectInvoicingPatterns(
                customerId, tradeTransactionId, invoiceAmount, goodsValue, 
                commodityCodes, timestamp);
            
            log.info("Step 5: Completed over/under invoicing pattern detection");
            
            // Step 6: Analyze trade route and geography risk factors
            Map<String, Object> geographyRisk = tbmlMonitoringService.analyzeTradeRouteRisk(
                alertId, originCountry, destinationCountry, shippingMethod, 
                commodityCodes, timestamp);
            
            log.info("Step 6: Completed trade route and geography risk analysis");
            
            // Step 7: Verify trade documents authenticity and consistency
            Map<String, Object> documentVerification = tbmlMonitoringService.verifyTradeDocuments(
                alertId, tradeDocuments, tradeType, invoiceAmount, 
                commodityCodes, timestamp);
            
            log.info("Step 7: Completed trade documents verification");
            
            // Step 8: Calculate TBML risk score and suspicious activity assessment
            int tbmlRiskScore = tbmlMonitoringService.calculateTBMLRiskScore(
                alertId, tbmlIndicators, valuationAnalysis, invoicingPatterns, 
                geographyRisk, documentVerification, timestamp);
            
            log.info("Step 8: Calculated TBML risk score: {}", tbmlRiskScore);
            
            // Step 9: Determine investigation requirements and escalation
            if (tbmlRiskScore >= 75) {
                SuspiciousActivity investigation = investigationService.initiateTBMLInvestigation(
                    alertId, customerId, tradeTransactionId, tbmlRiskScore, 
                    tbmlIndicators, analystId, timestamp);
                
                log.info("Step 9: Initiated TBML investigation: investigationId={}", 
                        investigation.getInvestigationId());
            } else {
                log.info("Step 9: TBML risk score below investigation threshold");
            }
            
            // Step 10: Generate TBML compliance alerts and notifications
            ComplianceAlert tbmlAlert = tbmlMonitoringService.generateTBMLComplianceAlert(
                alertId, customerId, tradeTransactionId, tbmlRiskScore, 
                tbmlIndicators, riskLevel, timestamp);
            
            log.info("Step 10: Generated TBML compliance alert: severity={}", 
                    tbmlAlert.getSeverity());
            
            // Step 11: Notify trade finance compliance team and customs authorities
            tbmlMonitoringService.notifyTradeFinanceCompliance(
                alertId, tbmlAlert, tradeTransactionId, analystId, timestamp);
            
            // Report to customs if high-risk
            if (tbmlRiskScore >= 90) {
                tbmlMonitoringService.reportToCustomsAuthorities(
                    alertId, tradeTransactionId, originCountry, destinationCountry, 
                    commodityCodes, tbmlIndicators, timestamp);
                
                log.info("Step 11: Reported high-risk TBML case to customs authorities");
            }
            
            // Step 12: Log TBML detection for audit trail and regulatory examination
            auditService.logTBMLDetectionEvent(
                alertId, customerId, tradeTransactionId, tradeType, 
                tbmlRiskScore, commodityCodes, originCountry, destinationCountry, 
                invoiceAmount, invoiceCurrency, analystId, timestamp);
            
            regulatoryReportingService.generateTBMLMonitoringReports(
                tbmlAlert, valuationAnalysis, invoicingPatterns, 
                geographyRisk, alertId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed TBML detection event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing TBML detection event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("alertId") || 
            !eventData.has("customerId") || !eventData.has("tradeTransactionId") ||
            !eventData.has("tradeType") || !eventData.has("commodityCodes") ||
            !eventData.has("invoiceAmount") || !eventData.has("invoiceCurrency") ||
            !eventData.has("goodsValue") || !eventData.has("originCountry") ||
            !eventData.has("destinationCountry") || !eventData.has("tbmlIndicators") ||
            !eventData.has("analystId") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid TBML detection event structure");
        }
    }
}