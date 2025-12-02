package com.waqiti.compliance.events;

import com.waqiti.common.events.SarFilingRequestEvent;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.RegulatoryReportingService;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Critical Event Consumer for SAR Filing Requests
 * 
 * REGULATORY IMPACT: Ensures compliance with mandatory SAR filing requirements
 * LEGAL IMPACT: Prevents regulatory fines and legal action for non-compliance
 * 
 * This consumer was identified as MISSING in the forensic audit, causing:
 * - Failed SAR filing deadlines
 * - Regulatory compliance violations
 * - Potential criminal liability
 * - Missing law enforcement notifications
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SarFilingRequestEventConsumer {
    
    private final SarFilingService sarFilingService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ComprehensiveAuditService auditService;
    
    /**
     * CRITICAL: Process SAR filing requests to maintain regulatory compliance
     * 
     * This consumer handles SAR filing requests from detection systems
     * and ensures timely regulatory reporting
     */
    @KafkaListener(
        topics = "sar-filing-requests",
        groupId = "compliance-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleSarFilingRequest(
            @Payload SarFilingRequestEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.error("REGULATORY: Processing SAR filing request for user {} with priority {} from partition {}, offset {}", 
            event.getUserId(), event.getPriority(), partition, offset);
        
        try {
            // Audit the SAR request reception
            auditService.auditCriticalComplianceEvent(
                "SAR_FILING_REQUEST_RECEIVED",
                event.getUserId().toString(),
                "SAR filing request received for user: " + event.getUserId(),
                Map.of(
                    "category", event.getCategory(),
                    "priority", event.getPriority(),
                    "caseId", event.getCaseId(),
                    "totalAmount", event.getTotalSuspiciousAmount(),
                    "deadline", event.getFilingDeadline()
                )
            );
            
            // Process SAR filing based on priority
            processSarFiling(event);
            
            // Handle regulatory notifications
            handleRegulatoryNotifications(event);
            
            // Process law enforcement notifications if required
            if (event.requiresLawEnforcementNotification()) {
                notifyLawEnforcement(event);
            }
            
            // Schedule follow-up actions
            scheduleFollowUpActions(event);
            
            // Log successful processing
            log.error("REGULATORY: Successfully processed SAR filing for user {} - Category: {}", 
                event.getUserId(), event.getCategory());
            
            // Acknowledge the message after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process SAR filing request for user {}", event.getUserId(), e);
            
            // Audit the failure - SAR filing failure is critical
            auditService.auditCriticalComplianceEvent(
                "SAR_FILING_FAILED",
                event.getUserId().toString(),
                "CRITICAL: Failed to file SAR: " + e.getMessage(),
                Map.of(
                    "error", e.getMessage(),
                    "category", event.getCategory()
                )
            );
            
            // Don't acknowledge - let the message be retried or sent to DLQ
            throw new RuntimeException("SAR filing processing failed", e);
        }
    }
    
    /**
     * Process SAR filing based on priority and category
     */
    private void processSarFiling(SarFilingRequestEvent event) {
        switch (event.getPriority()) {
            case IMMEDIATE:
                handleImmediateSarFiling(event);
                break;
            case URGENT:
                handleUrgentSarFiling(event);
                break;
            case HIGH:
                handleHighPrioritySarFiling(event);
                break;
            case STANDARD:
                handleStandardSarFiling(event);
                break;
            case LOW:
                handleLowPrioritySarFiling(event);
                break;
            default:
                log.warn("Unknown SAR priority: {}", event.getPriority());
                handleStandardSarFiling(event); // Default to standard
        }
    }
    
    /**
     * Handle immediate SAR filing - file within 24 hours
     */
    private void handleImmediateSarFiling(SarFilingRequestEvent event) {
        log.error("CRITICAL SAR: Processing IMMEDIATE SAR filing for user {} - {}", 
            event.getUserId(), event.getCategory());
        
        try {
            // Generate SAR report immediately
            String sarId = sarFilingService.generateSarReport(
                event,
                SarFilingRequestEvent.SarPriority.IMMEDIATE
            );
            
            // File with all required regulatory bodies immediately
            for (String regulatoryBody : event.getRegulatoryBodies()) {
                sarFilingService.fileWithRegulatoryBody(
                    sarId,
                    regulatoryBody,
                    event,
                    true // expedited filing
                );
            }
            
            // Send executive notification
            sarFilingService.sendExecutiveSarNotification(event, sarId);
            
            // Create high-priority compliance case
            sarFilingService.createHighPriorityComplianceCase(
                event.getUserId(),
                sarId,
                event.getCategory().toString(),
                event.getCaseId()
            );
            
            log.error("CRITICAL SAR: Immediate SAR filed. SAR ID: {}", sarId);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to file immediate SAR for user {}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * Handle urgent SAR filing - file within 48 hours
     */
    private void handleUrgentSarFiling(SarFilingRequestEvent event) {
        log.error("URGENT SAR: Processing urgent SAR filing for user {} - {}", 
            event.getUserId(), event.getCategory());
        
        try {
            // Generate SAR report with urgent priority
            String sarId = sarFilingService.generateSarReport(
                event,
                SarFilingRequestEvent.SarPriority.URGENT
            );
            
            // Schedule filing with regulatory bodies
            sarFilingService.scheduleRegulatoryFiling(
                sarId,
                event,
                LocalDateTime.now().plusHours(24) // File within 24 hours for urgent
            );
            
            // Notify compliance team
            sarFilingService.notifyComplianceTeam(event, sarId);
            
            // Create urgent compliance case
            sarFilingService.createUrgentComplianceCase(
                event.getUserId(),
                sarId,
                event.getCategory().toString(),
                event.getCaseId()
            );
            
            log.error("URGENT SAR: Urgent SAR scheduled for filing. SAR ID: {}", sarId);
            
        } catch (Exception e) {
            log.error("Failed to process urgent SAR for user {}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * Handle high priority SAR filing - file within 5 days
     */
    private void handleHighPrioritySarFiling(SarFilingRequestEvent event) {
        log.warn("HIGH PRIORITY SAR: Processing high priority SAR for user {} - {}", 
            event.getUserId(), event.getCategory());
        
        try {
            // Generate SAR report
            String sarId = sarFilingService.generateSarReport(
                event,
                SarFilingRequestEvent.SarPriority.HIGH
            );
            
            // Schedule filing with regulatory bodies
            sarFilingService.scheduleRegulatoryFiling(
                sarId,
                event,
                LocalDateTime.now().plusDays(3) // File within 3 days for high priority
            );
            
            // Create compliance case for review
            sarFilingService.createComplianceCase(
                event.getUserId(),
                sarId,
                event.getCategory().toString(),
                event.getCaseId(),
                "HIGH_PRIORITY"
            );
            
            log.warn("HIGH PRIORITY SAR: SAR scheduled for filing. SAR ID: {}", sarId);
            
        } catch (Exception e) {
            log.error("Failed to process high priority SAR for user {}", event.getUserId(), e);
            throw e;
        }
    }
    
    /**
     * Handle standard SAR filing - file within 30 days
     */
    private void handleStandardSarFiling(SarFilingRequestEvent event) {
        log.info("STANDARD SAR: Processing standard SAR filing for user {} - {}", 
            event.getUserId(), event.getCategory());
        
        try {
            // Generate SAR report
            String sarId = sarFilingService.generateSarReport(
                event,
                SarFilingRequestEvent.SarPriority.STANDARD
            );
            
            // Schedule filing with regulatory bodies
            sarFilingService.scheduleRegulatoryFiling(
                sarId,
                event,
                event.getFilingDeadline() != null ? event.getFilingDeadline() : LocalDateTime.now().plusDays(15)
            );
            
            // Create standard compliance case
            sarFilingService.createComplianceCase(
                event.getUserId(),
                sarId,
                event.getCategory().toString(),
                event.getCaseId(),
                "STANDARD"
            );
            
            log.info("STANDARD SAR: SAR scheduled for filing. SAR ID: {}", sarId);
            
        } catch (Exception e) {
            log.error("Failed to process standard SAR for user {}", event.getUserId(), e);
            // Don't rethrow for standard priority
        }
    }
    
    /**
     * Handle low priority SAR filing - file within 60 days
     */
    private void handleLowPrioritySarFiling(SarFilingRequestEvent event) {
        log.info("LOW PRIORITY SAR: Processing low priority SAR for user {} - {}", 
            event.getUserId(), event.getCategory());
        
        try {
            // Generate SAR report
            String sarId = sarFilingService.generateSarReport(
                event,
                SarFilingRequestEvent.SarPriority.LOW
            );
            
            // Schedule filing with regulatory bodies
            sarFilingService.scheduleRegulatoryFiling(
                sarId,
                event,
                LocalDateTime.now().plusDays(30)
            );
            
            log.info("LOW PRIORITY SAR: SAR scheduled for filing. SAR ID: {}", sarId);
            
        } catch (Exception e) {
            log.error("Failed to process low priority SAR for user {}", event.getUserId(), e);
            // Don't rethrow for low priority
        }
    }
    
    /**
     * Handle regulatory notifications
     */
    private void handleRegulatoryNotifications(SarFilingRequestEvent event) {
        try {
            // Notify each regulatory body specified
            if (event.getRegulatoryBodies() != null && !event.getRegulatoryBodies().isEmpty()) {
                for (String regulatoryBody : event.getRegulatoryBodies()) {
                    regulatoryReportingService.notifyRegulatoryBody(
                        regulatoryBody,
                        event.getUserId(),
                        event.getCategory().toString(),
                        event.getCaseId(),
                        event.getTotalSuspiciousAmount()
                    );
                }
            }
            
            // Special handling for specific categories
            if (event.getCategory() == SarFilingRequestEvent.SarCategory.TERRORIST_FINANCING) {
                regulatoryReportingService.notifyCounterTerrorismUnit(event);
            }
            
            if (event.getCategory() == SarFilingRequestEvent.SarCategory.SANCTIONS_VIOLATION) {
                regulatoryReportingService.notifyOfacCompliance(event);
            }
            
        } catch (Exception e) {
            log.error("Failed to send regulatory notifications for SAR filing", e);
            // Don't rethrow - notifications shouldn't block SAR filing
        }
    }
    
    /**
     * Notify law enforcement when required
     */
    private void notifyLawEnforcement(SarFilingRequestEvent event) {
        log.error("LAW ENFORCEMENT: Notifying law enforcement for SAR - User: {}, Category: {}", 
            event.getUserId(), event.getCategory());
        
        try {
            regulatoryReportingService.notifyLawEnforcement(
                event.getUserId(),
                event.getCategory().toString(),
                event.getSuspiciousActivity(),
                event.getTotalSuspiciousAmount(),
                event.getCaseId()
            );
            
            // Audit law enforcement notification
            auditService.auditCriticalComplianceEvent(
                "LAW_ENFORCEMENT_NOTIFIED_SAR",
                event.getUserId().toString(),
                "Law enforcement notified of suspicious activity",
                Map.of(
                    "category", event.getCategory(),
                    "amount", event.getTotalSuspiciousAmount(),
                    "caseId", event.getCaseId()
                )
            );
            
            log.error("LAW ENFORCEMENT: Notification sent for SAR category: {}", event.getCategory());
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to notify law enforcement for SAR", e);
            // This is critical but don't block SAR filing
        }
    }
    
    /**
     * Schedule follow-up actions for SAR
     */
    private void scheduleFollowUpActions(SarFilingRequestEvent event) {
        try {
            // Schedule account review
            sarFilingService.scheduleAccountReview(
                event.getUserId(),
                event.getCaseId(),
                LocalDateTime.now().plusDays(90) // 90-day review
            );
            
            // Schedule enhanced monitoring
            sarFilingService.enableEnhancedMonitoring(
                event.getUserId(),
                event.getCategory().toString(),
                LocalDateTime.now().plusMonths(6) // 6-month monitoring
            );
            
            // Schedule relationship review for related parties
            if (event.getRelatedParties() != null && !event.getRelatedParties().isEmpty()) {
                for (SarFilingRequestEvent.RelatedParty party : event.getRelatedParties()) {
                    if (party.isPEP() || party.isSanctioned()) {
                        sarFilingService.scheduleRelationshipReview(
                            party.getPartyId(),
                            event.getCaseId(),
                            "HIGH_RISK_PARTY"
                        );
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to schedule follow-up actions for SAR", e);
            // Don't rethrow - follow-up scheduling shouldn't block SAR filing
        }
    }
}