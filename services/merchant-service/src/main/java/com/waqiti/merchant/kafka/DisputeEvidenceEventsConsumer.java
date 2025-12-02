package com.waqiti.merchant.kafka;

import com.waqiti.common.events.DisputeEvidenceEvent;
import com.waqiti.merchant.domain.DisputeEvidence;
import com.waqiti.merchant.repository.DisputeEvidenceRepository;
import com.waqiti.merchant.service.EvidenceManagementService;
import com.waqiti.merchant.service.DocumentVerificationService;
import com.waqiti.merchant.metrics.DisputeMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

/**
 * Dispute Evidence Events Consumer
 * Processes evidence submission, validation, and management for disputes
 * Implements 12-step zero-tolerance processing for evidence lifecycle
 * 
 * Business Context:
 * - Critical for winning chargebacks and disputes
 * - Card network evidence requirements (Visa CE 3.0, Mastercard CED)
 * - Compelling evidence: delivery proof, signatures, communications
 * - Evidence deadlines: 7-30 days depending on dispute type
 * - Document format requirements: PDF, images, max file sizes
 * - Metadata validation: timestamps, authenticity, chain of custody
 * 
 * Evidence Types:
 * - Transaction receipts and invoices
 * - Delivery confirmation and tracking
 * - Customer signatures (digital/physical)
 * - Communication logs (email, chat, phone)
 * - Terms of service and refund policies
 * - IP address and device fingerprints
 * - Fraud prevention checks performed
 * - Previous transaction history
 * 
 * @author Waqiti Merchant Services Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DisputeEvidenceEventsConsumer {
    
    private final DisputeEvidenceRepository evidenceRepository;
    private final EvidenceManagementService evidenceManagementService;
    private final DocumentVerificationService documentVerificationService;
    private final DisputeMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final long MAX_FILE_SIZE_MB = 25;
    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
        "pdf", "jpg", "jpeg", "png", "doc", "docx", "txt", "csv"
    );
    
    @KafkaListener(
        topics = {"dispute-evidence-events", "chargeback-evidence-events", "evidence-submission-events"},
        groupId = "merchant-dispute-evidence-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleDisputeEvidenceEvent(
            @Payload DisputeEvidenceEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("evidence-%s-p%d-o%d", 
            event.getDisputeId(), partition, offset);
        
        log.info("Processing dispute evidence event: disputeId={}, type={}, evidenceType={}", 
            event.getDisputeId(), event.getEventType(), event.getEvidenceType());
        
        try {
            switch (event.getEventType()) {
                case EVIDENCE_REQUESTED:
                    processEvidenceRequested(event, correlationId);
                    break;
                case EVIDENCE_UPLOADED:
                    processEvidenceUploaded(event, correlationId);
                    break;
                case EVIDENCE_VALIDATED:
                    processEvidenceValidated(event, correlationId);
                    break;
                case EVIDENCE_REJECTED:
                    processEvidenceRejected(event, correlationId);
                    break;
                case EVIDENCE_ACCEPTED:
                    processEvidenceAccepted(event, correlationId);
                    break;
                case EVIDENCE_PACKAGE_COMPILED:
                    processEvidencePackageCompiled(event, correlationId);
                    break;
                case EVIDENCE_SUBMITTED_TO_NETWORK:
                    processEvidenceSubmittedToNetwork(event, correlationId);
                    break;
                case ADDITIONAL_EVIDENCE_REQUESTED:
                    processAdditionalEvidenceRequested(event, correlationId);
                    break;
                case EVIDENCE_DEADLINE_WARNING:
                    processEvidenceDeadlineWarning(event, correlationId);
                    break;
                case EVIDENCE_DEADLINE_MISSED:
                    processEvidenceDeadlineMissed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown dispute evidence event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logDisputeEvent(
                "EVIDENCE_EVENT_PROCESSED",
                event.getDisputeId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "evidenceId", event.getEvidenceId() != null ? event.getEvidenceId() : "N/A",
                    "evidenceType", event.getEvidenceType() != null ? event.getEvidenceType() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process dispute evidence event: {}", e.getMessage(), e);
            kafkaTemplate.send("dispute-evidence-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processEvidenceRequested(DisputeEvidenceEvent event, String correlationId) {
        log.info("Evidence requested: disputeId={}, evidenceTypes={}, deadline={}", 
            event.getDisputeId(), event.getRequestedEvidenceTypes(), event.getDeadline());
        
        DisputeEvidence evidence = DisputeEvidence.builder()
            .id(UUID.randomUUID().toString())
            .disputeId(event.getDisputeId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .requestedEvidenceTypes(event.getRequestedEvidenceTypes())
            .requestedAt(LocalDateTime.now())
            .deadline(event.getDeadline())
            .status("REQUESTED")
            .cardNetwork(event.getCardNetwork())
            .disputeType(event.getDisputeType())
            .correlationId(correlationId)
            .build();
        
        evidenceRepository.save(evidence);
        
        evidenceManagementService.generateEvidenceChecklist(evidence.getId());
        
        long daysUntilDeadline = java.time.Duration.between(
            LocalDateTime.now(), event.getDeadline()
        ).toDays();
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Urgent: Evidence Required for Dispute",
            String.format("Evidence is required for dispute %s. " +
                "Required documents: %s. Deadline: %s (%d days). " +
                "Failure to submit may result in automatic loss.",
                event.getDisputeId(), 
                String.join(", ", event.getRequestedEvidenceTypes()),
                event.getDeadline(), daysUntilDeadline),
            correlationId
        );
        
        metricsService.recordEvidenceRequested(event.getDisputeType());
    }
    
    private void processEvidenceUploaded(DisputeEvidenceEvent event, String correlationId) {
        log.info("Evidence uploaded: evidenceId={}, type={}, fileName={}, fileSize={}MB", 
            event.getEvidenceId(), event.getEvidenceType(), 
            event.getFileName(), event.getFileSizeMB());
        
        DisputeEvidence evidence = evidenceRepository.findById(event.getEvidenceId())
            .orElseThrow();
        
        // Validate file
        if (event.getFileSizeMB() > MAX_FILE_SIZE_MB) {
            log.error("File size exceeds limit: {}MB > {}MB", event.getFileSizeMB(), MAX_FILE_SIZE_MB);
            evidenceManagementService.rejectEvidence(evidence.getId(), 
                String.format("File size exceeds %dMB limit", MAX_FILE_SIZE_MB));
            return;
        }
        
        String fileExtension = event.getFileName().substring(event.getFileName().lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_FILE_TYPES.contains(fileExtension)) {
            log.error("File type not allowed: {}", fileExtension);
            evidenceManagementService.rejectEvidence(evidence.getId(), 
                "File type not allowed. Accepted formats: " + String.join(", ", ALLOWED_FILE_TYPES));
            return;
        }
        
        evidence.setEvidenceType(event.getEvidenceType());
        evidence.setFileName(event.getFileName());
        evidence.setFileUrl(event.getFileUrl());
        evidence.setFileSizeMB(event.getFileSizeMB());
        evidence.setFileHash(event.getFileHash());
        evidence.setUploadedAt(LocalDateTime.now());
        evidence.setStatus("UPLOADED");
        evidenceRepository.save(evidence);
        
        // Initiate validation
        documentVerificationService.validateEvidence(evidence.getId());
        
        metricsService.recordEvidenceUploaded(event.getEvidenceType());
    }
    
    private void processEvidenceValidated(DisputeEvidenceEvent event, String correlationId) {
        log.info("Evidence validated: evidenceId={}, validationResult={}, score={}", 
            event.getEvidenceId(), event.getValidationResult(), event.getValidationScore());
        
        DisputeEvidence evidence = evidenceRepository.findById(event.getEvidenceId())
            .orElseThrow();
        
        evidence.setValidated(true);
        evidence.setValidatedAt(LocalDateTime.now());
        evidence.setValidationResult(event.getValidationResult());
        evidence.setValidationScore(event.getValidationScore());
        evidence.setValidationNotes(event.getValidationNotes());
        evidenceRepository.save(evidence);
        
        if ("PASS".equals(event.getValidationResult())) {
            log.info("Evidence validation passed: score={}", event.getValidationScore());
            evidenceManagementService.acceptEvidence(evidence.getId());
        } else if ("WARNING".equals(event.getValidationResult())) {
            log.warn("Evidence validation has warnings: {}", event.getValidationNotes());
            evidenceManagementService.flagForReview(evidence.getId(), event.getValidationNotes());
        } else {
            log.error("Evidence validation failed: {}", event.getValidationNotes());
            evidenceManagementService.rejectEvidence(evidence.getId(), event.getValidationNotes());
        }
        
        metricsService.recordEvidenceValidated(event.getValidationResult());
    }
    
    private void processEvidenceRejected(DisputeEvidenceEvent event, String correlationId) {
        log.warn("Evidence rejected: evidenceId={}, reason={}", 
            event.getEvidenceId(), event.getRejectionReason());
        
        DisputeEvidence evidence = evidenceRepository.findById(event.getEvidenceId())
            .orElseThrow();
        
        evidence.setStatus("REJECTED");
        evidence.setRejectedAt(LocalDateTime.now());
        evidence.setRejectionReason(event.getRejectionReason());
        evidenceRepository.save(evidence);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Evidence Rejected - Resubmission Required",
            String.format("Evidence for dispute %s was rejected: %s. " +
                "Please upload corrected evidence as soon as possible. Deadline: %s",
                event.getDisputeId(), event.getRejectionReason(), evidence.getDeadline()),
            correlationId
        );
        
        metricsService.recordEvidenceRejected(event.getRejectionReason());
    }
    
    private void processEvidenceAccepted(DisputeEvidenceEvent event, String correlationId) {
        log.info("Evidence accepted: evidenceId={}, type={}", 
            event.getEvidenceId(), event.getEvidenceType());
        
        DisputeEvidence evidence = evidenceRepository.findById(event.getEvidenceId())
            .orElseThrow();
        
        evidence.setStatus("ACCEPTED");
        evidence.setAcceptedAt(LocalDateTime.now());
        evidenceRepository.save(evidence);
        
        // Check if all required evidence submitted
        boolean allEvidenceSubmitted = evidenceManagementService.checkCompleteness(
            evidence.getDisputeId());
        
        if (allEvidenceSubmitted) {
            log.info("All required evidence submitted for disputeId={}", evidence.getDisputeId());
            evidenceManagementService.compileEvidencePackage(evidence.getDisputeId());
        }
        
        metricsService.recordEvidenceAccepted(event.getEvidenceType());
    }
    
    private void processEvidencePackageCompiled(DisputeEvidenceEvent event, String correlationId) {
        log.info("Evidence package compiled: disputeId={}, documentCount={}, totalSizeMB={}", 
            event.getDisputeId(), event.getDocumentCount(), event.getTotalPackageSizeMB());
        
        DisputeEvidence evidence = evidenceRepository.findByDisputeId(event.getDisputeId())
            .orElseThrow();
        
        evidence.setPackageCompiled(true);
        evidence.setPackageCompiledAt(LocalDateTime.now());
        evidence.setPackageDocumentCount(event.getDocumentCount());
        evidence.setPackageTotalSizeMB(event.getTotalPackageSizeMB());
        evidence.setPackageUrl(event.getPackageUrl());
        evidence.setPackageHash(event.getPackageHash());
        evidenceRepository.save(evidence);
        
        // Final review before submission
        boolean readyForSubmission = evidenceManagementService.finalReview(evidence.getId());
        
        if (readyForSubmission) {
            evidenceManagementService.submitToCardNetwork(evidence.getId());
        } else {
            log.warn("Evidence package not ready for submission: requiresReview=true");
        }
        
        metricsService.recordEvidencePackageCompiled(event.getDocumentCount());
    }
    
    private void processEvidenceSubmittedToNetwork(DisputeEvidenceEvent event, String correlationId) {
        log.info("Evidence submitted to card network: disputeId={}, network={}, confirmationId={}", 
            event.getDisputeId(), event.getCardNetwork(), event.getNetworkConfirmationId());
        
        DisputeEvidence evidence = evidenceRepository.findByDisputeId(event.getDisputeId())
            .orElseThrow();
        
        evidence.setStatus("SUBMITTED_TO_NETWORK");
        evidence.setSubmittedToNetworkAt(LocalDateTime.now());
        evidence.setNetworkConfirmationId(event.getNetworkConfirmationId());
        evidence.setNetworkSubmissionStatus(event.getSubmissionStatus());
        evidenceRepository.save(evidence);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Evidence Successfully Submitted",
            String.format("Your evidence for dispute %s has been successfully submitted to %s. " +
                "Confirmation ID: %s. You will be notified of the decision.",
                event.getDisputeId(), event.getCardNetwork(), event.getNetworkConfirmationId()),
            correlationId
        );
        
        metricsService.recordEvidenceSubmittedToNetwork(event.getCardNetwork());
    }
    
    private void processAdditionalEvidenceRequested(DisputeEvidenceEvent event, String correlationId) {
        log.warn("Additional evidence requested: disputeId={}, requestedTypes={}, deadline={}", 
            event.getDisputeId(), event.getAdditionalEvidenceTypes(), event.getNewDeadline());
        
        DisputeEvidence evidence = evidenceRepository.findByDisputeId(event.getDisputeId())
            .orElseThrow();
        
        evidence.setAdditionalEvidenceRequested(true);
        evidence.setAdditionalEvidenceRequestedAt(LocalDateTime.now());
        evidence.setAdditionalEvidenceTypes(event.getAdditionalEvidenceTypes());
        evidence.setDeadline(event.getNewDeadline());
        evidence.setStatus("ADDITIONAL_EVIDENCE_REQUIRED");
        evidenceRepository.save(evidence);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Additional Evidence Required",
            String.format("Additional evidence is required for dispute %s: %s. " +
                "New deadline: %s. Please submit as soon as possible.",
                event.getDisputeId(), 
                String.join(", ", event.getAdditionalEvidenceTypes()),
                event.getNewDeadline()),
            correlationId
        );
        
        metricsService.recordAdditionalEvidenceRequested();
    }
    
    private void processEvidenceDeadlineWarning(DisputeEvidenceEvent event, String correlationId) {
        log.warn("Evidence deadline warning: disputeId={}, hoursRemaining={}", 
            event.getDisputeId(), event.getHoursRemaining());
        
        DisputeEvidence evidence = evidenceRepository.findByDisputeId(event.getDisputeId())
            .orElseThrow();
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "URGENT: Evidence Deadline Approaching",
            String.format("Warning: Only %d hours remaining to submit evidence for dispute %s. " +
                "Deadline: %s. Missing the deadline will result in automatic loss.",
                event.getHoursRemaining(), event.getDisputeId(), evidence.getDeadline()),
            correlationId
        );
        
        metricsService.recordEvidenceDeadlineWarning(event.getHoursRemaining());
    }
    
    private void processEvidenceDeadlineMissed(DisputeEvidenceEvent event, String correlationId) {
        log.error("Evidence deadline missed: disputeId={}, deadline={}", 
            event.getDisputeId(), event.getDeadline());
        
        DisputeEvidence evidence = evidenceRepository.findByDisputeId(event.getDisputeId())
            .orElseThrow();
        
        evidence.setStatus("DEADLINE_MISSED");
        evidence.setDeadlineMissed(true);
        evidence.setDeadlineMissedAt(LocalDateTime.now());
        evidenceRepository.save(evidence);
        
        // Auto-lose dispute
        evidenceManagementService.processAutoLoss(evidence.getDisputeId(), 
            "Evidence submission deadline missed");
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Dispute Lost - Evidence Deadline Missed",
            String.format("Dispute %s was automatically decided against you because " +
                "evidence was not submitted by the deadline (%s). " +
                "The disputed amount has been charged back.",
                event.getDisputeId(), event.getDeadline()),
            correlationId
        );
        
        metricsService.recordEvidenceDeadlineMissed();
    }
}