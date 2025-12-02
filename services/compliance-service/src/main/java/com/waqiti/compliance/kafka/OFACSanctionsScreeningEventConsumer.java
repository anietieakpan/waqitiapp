package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.OFACSanctionsScreeningService;
import com.waqiti.compliance.service.ComplianceAuditService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.compliance.service.EnhancedMonitoringService;
import com.waqiti.compliance.entity.SanctionsScreeningResult;
import com.waqiti.compliance.entity.SanctionedEntity;
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
 * Critical Event Consumer #179: OFAC Sanctions Screening Event Consumer
 * Processes SDN list screening and sanctions enforcement
 * Implements 12-step zero-tolerance processing for secure sanctions compliance workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OFACSanctionsScreeningEventConsumer extends BaseKafkaConsumer {

    private final OFACSanctionsScreeningService ofacScreeningService;
    private final ComplianceAuditService auditService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final EnhancedMonitoringService enhancedMonitoringService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "ofac-sanctions-screening-events", groupId = "ofac-sanctions-screening-group")
    @CircuitBreaker(name = "ofac-sanctions-screening-consumer")
    @Retry(name = "ofac-sanctions-screening-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleOFACSanctionsScreeningEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "ofac-sanctions-screening-event");
        
        try {
            log.info("Step 1: Processing OFAC sanctions screening event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String screeningId = eventData.path("screeningId").asText();
            String customerId = eventData.path("customerId").asText();
            String screeningType = eventData.path("screeningType").asText(); // CUSTOMER, TRANSACTION, BENEFICIARY
            String entityName = eventData.path("entityName").asText();
            String entityType = eventData.path("entityType").asText(); // INDIVIDUAL, CORPORATION, GOVERNMENT
            List<String> aliases = objectMapper.convertValue(
                eventData.path("aliases"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String nationality = eventData.path("nationality").asText();
            String address = eventData.path("address").asText();
            String dateOfBirth = eventData.path("dateOfBirth").asText();
            String identificationNumber = eventData.path("identificationNumber").asText();
            BigDecimal transactionAmount = eventData.has("transactionAmount") ? 
                new BigDecimal(eventData.path("transactionAmount").asText()) : BigDecimal.ZERO;
            String currency = eventData.path("currency").asText();
            String transactionId = eventData.path("transactionId").asText();
            String screeningReason = eventData.path("screeningReason").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted OFAC screening details: screeningId={}, entity={}, type={}, reason={}", 
                    screeningId, entityName, screeningType, screeningReason);
            
            // Step 3: Validate OFAC screening jurisdiction and requirements
            ofacScreeningService.validateOFACScreeningRequirements(
                screeningType, entityType, nationality, transactionAmount, currency, timestamp);
            
            log.info("Step 3: Validated OFAC screening jurisdiction and requirements");
            
            // Step 4: Screen against current SDN list and consolidated sanctions list
            SanctionsScreeningResult sdnScreening = ofacScreeningService.screenAgainstSDNList(
                screeningId, entityName, aliases, nationality, address, 
                dateOfBirth, identificationNumber, entityType, timestamp);
            
            log.info("Step 4: Completed SDN list screening: matchScore={}, status={}", 
                    sdnScreening.getMatchScore(), sdnScreening.getScreeningStatus());
            
            // Step 5: Screen against sectoral sanctions and country-based sanctions
            SanctionsScreeningResult sectoralScreening = ofacScreeningService.screenSectoralSanctions(
                screeningId, entityName, nationality, entityType, transactionAmount, 
                currency, transactionId, timestamp);
            
            log.info("Step 5: Completed sectoral sanctions screening");
            
            // Step 6: Screen against Non-SDN lists (FSE, NS-ISA, etc.)
            SanctionsScreeningResult nonSdnScreening = ofacScreeningService.screenNonSDNLists(
                screeningId, entityName, aliases, nationality, entityType, timestamp);
            
            log.info("Step 6: Completed Non-SDN lists screening");
            
            // Step 7: Consolidate screening results and determine match confidence
            SanctionsScreeningResult consolidatedResult = ofacScreeningService.consolidateScreeningResults(
                screeningId, sdnScreening, sectoralScreening, nonSdnScreening, timestamp);
            
            log.info("Step 7: Consolidated screening results: overallScore={}, status={}", 
                    consolidatedResult.getMatchScore(), consolidatedResult.getScreeningStatus());
            
            // Step 8: Handle sanctions matches and blocking procedures
            if ("MATCH".equals(consolidatedResult.getScreeningStatus()) || 
                "POTENTIAL_MATCH".equals(consolidatedResult.getScreeningStatus())) {
                
                List<SanctionedEntity> matchedEntities = ofacScreeningService.handleSanctionsMatch(
                    screeningId, consolidatedResult, transactionId, transactionAmount, timestamp);
                
                log.info("Step 8: Handled sanctions match: {} entities matched", 
                        matchedEntities.size());
                
                // Block transaction if hard match
                if ("MATCH".equals(consolidatedResult.getScreeningStatus())) {
                    ofacScreeningService.blockTransaction(transactionId, screeningId, 
                        "OFAC_SANCTIONS_MATCH", timestamp);
                    
                    log.info("Step 8: Transaction blocked due to OFAC sanctions match");
                }
            } else {
                log.info("Step 8: No sanctions match detected - transaction cleared");
            }
            
            // Step 9: Generate OFAC compliance reports and notifications
            ofacScreeningService.generateOFACComplianceReport(
                screeningId, consolidatedResult, entityName, screeningType, timestamp);
            
            // Notify compliance team for potential matches
            if ("POTENTIAL_MATCH".equals(consolidatedResult.getScreeningStatus())) {
                ofacScreeningService.notifyComplianceTeam(
                    screeningId, consolidatedResult, entityName, transactionId, timestamp);
                
                log.info("Step 9: Notified compliance team for potential OFAC match");
            }
            
            // Step 10: Update customer risk profile and enhanced monitoring
            enhancedMonitoringService.updateCustomerRiskProfileFromOFAC(
                customerId, consolidatedResult, timestamp);
            
            if ("MATCH".equals(consolidatedResult.getScreeningStatus()) || 
                "POTENTIAL_MATCH".equals(consolidatedResult.getScreeningStatus())) {
                
                enhancedMonitoringService.activateOFACEnhancedMonitoring(
                    customerId, consolidatedResult.getMatchScore(), timestamp);
            }
            
            log.info("Step 10: Updated customer risk profile and enhanced monitoring");
            
            // Step 11: File regulatory notifications and maintain sanctions records
            if ("MATCH".equals(consolidatedResult.getScreeningStatus())) {
                ofacScreeningService.fileRegulatoryNotifications(
                    screeningId, consolidatedResult, transactionId, timestamp);
                
                ofacScreeningService.maintainSanctionsViolationRecord(
                    screeningId, customerId, consolidatedResult, timestamp);
                
                log.info("Step 11: Filed regulatory notifications and maintained violation records");
            }
            
            // Step 12: Log OFAC screening for audit trail and regulatory examination
            auditService.logOFACScreeningEvent(
                screeningId, customerId, entityName, screeningType, 
                consolidatedResult.getScreeningStatus(), consolidatedResult.getMatchScore(),
                transactionId, transactionAmount, currency, timestamp);
            
            regulatoryReportingService.generateOFACScreeningReports(
                consolidatedResult, screeningId, timestamp);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed OFAC sanctions screening event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing OFAC sanctions screening event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("screeningId") || 
            !eventData.has("customerId") || !eventData.has("screeningType") ||
            !eventData.has("entityName") || !eventData.has("entityType") ||
            !eventData.has("nationality") || !eventData.has("address") ||
            !eventData.has("screeningReason") || !eventData.has("currency") ||
            !eventData.has("transactionId") || !eventData.has("timestamp")) {
            throw new IllegalArgumentException("Invalid OFAC sanctions screening event structure");
        }
    }
}