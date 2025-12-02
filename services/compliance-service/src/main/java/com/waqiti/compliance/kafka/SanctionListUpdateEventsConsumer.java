package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.compliance.service.SanctionsListManagementService;
import com.waqiti.compliance.service.OFACUpdateService;
import com.waqiti.compliance.service.GlobalSanctionsService;
import com.waqiti.compliance.service.ComplianceRescreeningService;
import com.waqiti.compliance.service.AuditService;
import com.waqiti.compliance.entity.SanctionsList;
import com.waqiti.compliance.entity.SanctionsUpdate;
import com.waqiti.compliance.entity.RescreeningJob;
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

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #7: Sanction List Update Events Consumer
 * Processes sanctions list updates, OFAC changes, and global sanctions management
 * Implements 12-step zero-tolerance processing for sanctions compliance
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SanctionListUpdateEventsConsumer extends BaseKafkaConsumer {

    private final SanctionsListManagementService sanctionsListService;
    private final OFACUpdateService ofacUpdateService;
    private final GlobalSanctionsService globalSanctionsService;
    private final ComplianceRescreeningService rescreeningService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "sanction-list-update-events", 
        groupId = "sanction-list-update-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "sanction-list-update-consumer")
    @Retry(name = "sanction-list-update-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSanctionListUpdateEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "sanction-list-update-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing sanction list update event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String listSource = eventData.path("listSource").asText(); // OFAC, EU, UN, HMT, DFAT
            String listType = eventData.path("listType").asText(); // SDN, NON_SDN, SECTORAL, CONSOLIDATED
            String updateType = eventData.path("updateType").asText(); // ADD, DELETE, MODIFY
            String updateVersion = eventData.path("updateVersion").asText();
            LocalDateTime updateTimestamp = LocalDateTime.parse(eventData.path("updateTimestamp").asText());
            int recordCount = eventData.path("recordCount").asInt();
            String dataChecksum = eventData.path("dataChecksum").asText();
            boolean emergencyUpdate = eventData.path("emergencyUpdate").asBoolean();
            String regulatoryJurisdiction = eventData.path("regulatoryJurisdiction").asText();
            
            log.info("Step 2: Extracted update details: source={}, type={}, operation={}, records={}, emergency={}", 
                    listSource, listType, updateType, recordCount, emergencyUpdate);
            
            // Step 3: Sanctions list validation and integrity verification
            log.info("Step 3: Validating sanctions list update and verifying data integrity");
            SanctionsUpdate update = sanctionsListService.createSanctionsUpdate(eventData);
            
            sanctionsListService.validateUpdateSource(listSource);
            sanctionsListService.validateUpdateType(updateType);
            sanctionsListService.verifyDataIntegrity(update, dataChecksum);
            sanctionsListService.validateUpdateVersion(listSource, updateVersion);
            
            if (!sanctionsListService.isValidJurisdiction(regulatoryJurisdiction)) {
                throw new IllegalStateException("Invalid regulatory jurisdiction: " + regulatoryJurisdiction);
            }
            
            sanctionsListService.checkUpdateSequence(listSource, updateVersion);
            
            // Step 4: OFAC-specific processing and validation
            log.info("Step 4: Processing OFAC-specific updates and regulatory compliance");
            if ("OFAC".equals(listSource)) {
                ofacUpdateService.processOFACUpdate(update);
                ofacUpdateService.validateOFACFormat(update);
                ofacUpdateService.parseSDNEntries(update);
                ofacUpdateService.processNonSDNEntries(update);
                
                if ("SDN".equals(listType)) {
                    ofacUpdateService.updateSDNList(update);
                    ofacUpdateService.processAliasUpdates(update);
                    ofacUpdateService.updateAddressInformation(update);
                }
                
                ofacUpdateService.validateOFACCompliance(update);
            }
            
            // Step 5: Global sanctions list synchronization
            log.info("Step 5: Synchronizing global sanctions lists and cross-referencing");
            globalSanctionsService.synchronizeGlobalLists(update);
            globalSanctionsService.crossReferenceEntries(update);
            globalSanctionsService.consolidateMultiJurisdictionEntries(update);
            
            globalSanctionsService.updateJurisdictionMappings(update);
            globalSanctionsService.resolveConflictingEntries(update);
            globalSanctionsService.validateGlobalConsistency(update);
            
            if (globalSanctionsService.hasConflictingEntries(update)) {
                globalSanctionsService.escalateConflictResolution(update);
            }
            
            // Step 6: Database update and version management
            log.info("Step 6: Updating sanctions database and managing version control");
            SanctionsList currentList = sanctionsListService.getCurrentList(listSource, listType);
            
            if ("ADD".equals(updateType)) {
                sanctionsListService.addEntries(currentList, update);
            } else if ("DELETE".equals(updateType)) {
                sanctionsListService.removeEntries(currentList, update);
            } else if ("MODIFY".equals(updateType)) {
                sanctionsListService.modifyEntries(currentList, update);
            }
            
            sanctionsListService.updateListVersion(currentList, updateVersion);
            sanctionsListService.createBackupVersion(currentList);
            sanctionsListService.validateDatabaseConsistency(currentList);
            
            // Step 7: Emergency update processing and prioritization
            log.info("Step 7: Processing emergency updates and implementing priority screening");
            if (emergencyUpdate) {
                sanctionsListService.processEmergencyUpdate(update);
                sanctionsListService.prioritizeRescreening(update);
                sanctionsListService.notifyComplianceTeam(update);
                
                rescreeningService.initiateEmergencyRescreening(update);
                rescreeningService.escalateHighPriorityMatches(update);
            }
            
            sanctionsListService.updateEmergencyProtocols(update);
            
            // Step 8: Comprehensive rescreening initiation
            log.info("Step 8: Initiating comprehensive rescreening of existing entities");
            RescreeningJob rescreeningJob = rescreeningService.createRescreeningJob(update);
            
            if ("ADD".equals(updateType) || "MODIFY".equals(updateType)) {
                rescreeningService.scheduleCustomerRescreening(rescreeningJob);
                rescreeningService.scheduleMerchantRescreening(rescreeningJob);
                rescreeningService.scheduleTransactionRescreening(rescreeningJob);
                rescreeningService.scheduleEmployeeRescreening(rescreeningJob);
            }
            
            rescreeningService.prioritizeRescreeningQueue(rescreeningJob, emergencyUpdate);
            rescreeningService.updateRescreeningStatus(rescreeningJob);
            
            // Step 9: Match identification and alert generation
            log.info("Step 9: Identifying potential matches and generating compliance alerts");
            List<String> potentialMatches = rescreeningService.identifyPotentialMatches(update);
            
            for (String entityId : potentialMatches) {
                rescreeningService.generateComplianceAlert(entityId, update);
                rescreeningService.requireManualReview(entityId, update);
                rescreeningService.updateEntityRiskScore(entityId, update);
            }
            
            rescreeningService.escalateHighRiskMatches(potentialMatches, update);
            rescreeningService.updateMatchStatistics(update, potentialMatches.size());
            
            // Step 10: Regulatory notification and reporting
            log.info("Step 10: Generating regulatory notifications and compliance reports");
            sanctionsListService.generateUpdateReport(update);
            sanctionsListService.notifyRegulatoryBodies(update);
            sanctionsListService.updateComplianceMetrics(update);
            
            if (sanctionsListService.requiresImmediateReporting(update)) {
                sanctionsListService.generateImmediateReport(update);
                sanctionsListService.notifyBoardOfDirectors(update);
            }
            
            sanctionsListService.updateRegulatoryCompliance(update);
            
            // Step 11: System integration and API updates
            log.info("Step 11: Updating system integrations and external API endpoints");
            sanctionsListService.updateAPIEndpoints(update);
            sanctionsListService.refreshScreeningCaches(update);
            sanctionsListService.notifyIntegratedSystems(update);
            
            sanctionsListService.validateSystemConsistency(update);
            sanctionsListService.updateScreeningAlgorithms(update);
            
            if (sanctionsListService.requiresSystemRestart(update)) {
                sanctionsListService.scheduleSystemMaintenance(update);
            }
            
            // Step 12: Audit trail and compliance documentation
            log.info("Step 12: Completing audit trail and compliance documentation");
            auditService.logSanctionsUpdate(update);
            auditService.logRescreeningJob(rescreeningJob);
            auditService.documentComplianceActions(update);
            
            sanctionsListService.updateSanctionsMetrics(update);
            rescreeningService.updateRescreeningStatistics(rescreeningJob);
            
            auditService.generateSanctionsReport(update);
            auditService.updateRegulatoryReporting(update);
            auditService.archiveUpdateDocuments(update);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed sanction list update: source={}, eventId={}, records={}", 
                    listSource, eventId, recordCount);
            
        } catch (Exception e) {
            log.error("Error processing sanction list update event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("listSource") || 
            !eventData.has("listType") || !eventData.has("updateType") ||
            !eventData.has("updateVersion") || !eventData.has("updateTimestamp") ||
            !eventData.has("recordCount") || !eventData.has("dataChecksum") ||
            !eventData.has("emergencyUpdate") || !eventData.has("regulatoryJurisdiction")) {
            throw new IllegalArgumentException("Invalid sanction list update event structure");
        }
    }
}