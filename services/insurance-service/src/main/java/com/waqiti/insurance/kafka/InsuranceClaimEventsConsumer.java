package com.waqiti.insurance.kafka;

import com.waqiti.common.events.InsuranceClaimEvent;
import com.waqiti.insurance.domain.InsuranceClaim;
import com.waqiti.insurance.domain.ClaimDocument;
import com.waqiti.insurance.repository.InsuranceClaimRepository;
import com.waqiti.insurance.repository.ClaimDocumentRepository;
import com.waqiti.insurance.service.ClaimProcessingService;
import com.waqiti.insurance.service.ClaimAdjudicationService;
import com.waqiti.insurance.metrics.InsuranceMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class InsuranceClaimEventsConsumer {
    
    private final InsuranceClaimRepository claimRepository;
    private final ClaimDocumentRepository documentRepository;
    private final ClaimProcessingService claimService;
    private final ClaimAdjudicationService adjudicationService;
    private final InsuranceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"insurance-claim-events", "claim-processing-events", "claim-approval-events"},
        groupId = "insurance-claim-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInsuranceClaimEvent(
            @Payload InsuranceClaimEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("claim-%s-p%d-o%d", 
            event.getClaimId(), partition, offset);
        
        log.info("Processing insurance claim event: claimId={}, type={}", 
            event.getClaimId(), event.getEventType());
        
        try {
            switch (event.getEventType()) {
                case CLAIM_SUBMITTED:
                    processClaimSubmitted(event, correlationId);
                    break;
                case DOCUMENT_UPLOADED:
                    processDocumentUploaded(event, correlationId);
                    break;
                case CLAIM_UNDER_REVIEW:
                    processClaimUnderReview(event, correlationId);
                    break;
                case CLAIM_APPROVED:
                    processClaimApproved(event, correlationId);
                    break;
                case CLAIM_DENIED:
                    processClaimDenied(event, correlationId);
                    break;
                case PAYOUT_INITIATED:
                    processPayoutInitiated(event, correlationId);
                    break;
                case PAYOUT_COMPLETED:
                    processPayoutCompleted(event, correlationId);
                    break;
                default:
                    log.warn("Unknown insurance claim event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logInsuranceEvent("CLAIM_EVENT_PROCESSED", event.getClaimId(),
                Map.of("eventType", event.getEventType(), "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process claim event: {}", e.getMessage(), e);
            kafkaTemplate.send("insurance-claim-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processClaimSubmitted(InsuranceClaimEvent event, String correlationId) {
        log.info("Claim submitted: claimId={}, policyNumber={}, claimAmount={}", 
            event.getClaimId(), event.getPolicyNumber(), event.getClaimAmount());
        
        InsuranceClaim claim = InsuranceClaim.builder()
            .id(event.getClaimId())
            .userId(event.getUserId())
            .policyNumber(event.getPolicyNumber())
            .claimType(event.getClaimType())
            .claimAmount(event.getClaimAmount())
            .incidentDate(event.getIncidentDate())
            .description(event.getDescription())
            .status("SUBMITTED")
            .submittedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        claimRepository.save(claim);
        claimService.initiateReview(event.getClaimId());
        
        notificationService.sendNotification(event.getUserId(), "Claim Submitted",
            String.format("Your insurance claim of %.2f has been submitted. Reference: %s", 
                event.getClaimAmount(), event.getClaimId()), correlationId);
        
        metricsService.recordClaimSubmitted(event.getClaimType());
    }
    
    private void processDocumentUploaded(InsuranceClaimEvent event, String correlationId) {
        log.info("Document uploaded: claimId={}, documentType={}", 
            event.getClaimId(), event.getDocumentType());
        
        ClaimDocument document = ClaimDocument.builder()
            .id(UUID.randomUUID().toString())
            .claimId(event.getClaimId())
            .documentType(event.getDocumentType())
            .documentUrl(event.getDocumentUrl())
            .uploadedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        documentRepository.save(document);
        metricsService.recordDocumentUploaded(event.getDocumentType());
    }
    
    private void processClaimUnderReview(InsuranceClaimEvent event, String correlationId) {
        log.info("Claim under review: claimId={}", event.getClaimId());
        
        InsuranceClaim claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setStatus("UNDER_REVIEW");
        claim.setReviewStartedAt(LocalDateTime.now());
        claimRepository.save(claim);
        
        notificationService.sendNotification(event.getUserId(), "Claim Under Review",
            "Your insurance claim is being reviewed by our team.", correlationId);
        
        metricsService.recordClaimUnderReview();
    }
    
    private void processClaimApproved(InsuranceClaimEvent event, String correlationId) {
        log.info("Claim approved: claimId={}, approvedAmount={}", 
            event.getClaimId(), event.getApprovedAmount());
        
        InsuranceClaim claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setStatus("APPROVED");
        claim.setApprovedAt(LocalDateTime.now());
        claim.setApprovedAmount(event.getApprovedAmount());
        claimRepository.save(claim);
        
        notificationService.sendNotification(event.getUserId(), "Claim Approved",
            String.format("Your claim has been approved! Amount: %.2f", event.getApprovedAmount()), correlationId);
        
        metricsService.recordClaimApproved(event.getApprovedAmount());
    }
    
    private void processClaimDenied(InsuranceClaimEvent event, String correlationId) {
        log.warn("Claim denied: claimId={}, reason={}", event.getClaimId(), event.getDenialReason());
        
        InsuranceClaim claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setStatus("DENIED");
        claim.setDeniedAt(LocalDateTime.now());
        claim.setDenialReason(event.getDenialReason());
        claimRepository.save(claim);
        
        notificationService.sendNotification(event.getUserId(), "Claim Denied",
            String.format("Your claim has been denied: %s", event.getDenialReason()), correlationId);
        
        metricsService.recordClaimDenied(event.getDenialReason());
    }
    
    private void processPayoutInitiated(InsuranceClaimEvent event, String correlationId) {
        log.info("Payout initiated: claimId={}, amount={}", event.getClaimId(), event.getPayoutAmount());
        
        InsuranceClaim claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setPayoutInitiated(true);
        claim.setPayoutInitiatedAt(LocalDateTime.now());
        claim.setPayoutAmount(event.getPayoutAmount());
        claimRepository.save(claim);
        
        metricsService.recordPayoutInitiated(event.getPayoutAmount());
    }
    
    private void processPayoutCompleted(InsuranceClaimEvent event, String correlationId) {
        log.info("Payout completed: claimId={}, amount={}", event.getClaimId(), event.getPayoutAmount());
        
        InsuranceClaim claim = claimRepository.findById(event.getClaimId()).orElseThrow();
        claim.setStatus("PAID");
        claim.setPayoutCompletedAt(LocalDateTime.now());
        claim.setPaymentReference(event.getPaymentReference());
        claimRepository.save(claim);
        
        notificationService.sendNotification(event.getUserId(), "Payout Complete",
            String.format("Your claim payout of %.2f has been completed.", event.getPayoutAmount()), correlationId);
        
        metricsService.recordPayoutCompleted(event.getPayoutAmount());
    }
}