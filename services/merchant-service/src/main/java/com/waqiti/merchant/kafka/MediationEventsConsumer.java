package com.waqiti.merchant.kafka;

import com.waqiti.common.events.MediationEvent;
import com.waqiti.merchant.domain.MediationCase;
import com.waqiti.merchant.repository.MediationCaseRepository;
import com.waqiti.merchant.service.MediationService;
import com.waqiti.merchant.service.DisputeResolutionService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

/**
 * Mediation Events Consumer
 * Processes voluntary dispute mediation between merchant and cardholder
 * Implements 12-step zero-tolerance processing for mediation lifecycle
 * 
 * Business Context:
 * - Alternative dispute resolution (ADR) before arbitration
 * - Voluntary, non-binding negotiation process
 * - Neutral third-party mediator facilitates resolution
 * - Lower cost than arbitration ($0-$200 vs $500-$5,000)
 * - Faster resolution (7-21 days vs 30-90 days)
 * - Higher settlement rate (~70% success rate)
 * - Preserves customer relationships
 * 
 * Mediation Process:
 * 1. Both parties agree to mediate
 * 2. Neutral mediator assigned
 * 3. Both parties present positions
 * 4. Mediator facilitates negotiation
 * 5. Settlement agreement drafted (if successful)
 * 6. Escalates to arbitration if unsuccessful
 * 
 * @author Waqiti Merchant Services Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MediationEventsConsumer {
    
    private final MediationCaseRepository mediationRepository;
    private final MediationService mediationService;
    private final DisputeResolutionService disputeResolutionService;
    private final DisputeMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MEDIATION_RESPONSE_DAYS = 5;
    private static final int MEDIATION_SESSION_DURATION_DAYS = 14;
    private static final BigDecimal MEDIATION_FEE_SMALL_CLAIMS = new BigDecimal("50");
    private static final BigDecimal MEDIATION_FEE_STANDARD = new BigDecimal("150");
    
    @KafkaListener(
        topics = {"mediation-events", "dispute-mediation-events", "adr-events"},
        groupId = "merchant-mediation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleMediationEvent(
            @Payload MediationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("mediation-%s-p%d-o%d", 
            event.getDisputeId(), partition, offset);
        
        log.info("Processing mediation event: disputeId={}, type={}, mediationId={}", 
            event.getDisputeId(), event.getEventType(), event.getMediationId());
        
        try {
            switch (event.getEventType()) {
                case MEDIATION_REQUESTED:
                    processMediationRequested(event, correlationId);
                    break;
                case MEDIATION_ACCEPTED:
                    processMediationAccepted(event, correlationId);
                    break;
                case MEDIATION_DECLINED:
                    processMediationDeclined(event, correlationId);
                    break;
                case MEDIATOR_ASSIGNED:
                    processMediatorAssigned(event, correlationId);
                    break;
                case OPENING_STATEMENTS_SUBMITTED:
                    processOpeningStatementsSubmitted(event, correlationId);
                    break;
                case MEDIATION_SESSION_SCHEDULED:
                    processMediationSessionScheduled(event, correlationId);
                    break;
                case MEDIATION_SESSION_HELD:
                    processMediationSessionHeld(event, correlationId);
                    break;
                case SETTLEMENT_OFFER_MADE:
                    processSettlementOfferMade(event, correlationId);
                    break;
                case SETTLEMENT_OFFER_ACCEPTED:
                    processSettlementOfferAccepted(event, correlationId);
                    break;
                case SETTLEMENT_OFFER_REJECTED:
                    processSettlementOfferRejected(event, correlationId);
                    break;
                case SETTLEMENT_AGREEMENT_SIGNED:
                    processSettlementAgreementSigned(event, correlationId);
                    break;
                case MEDIATION_SUCCESSFUL:
                    processMediationSuccessful(event, correlationId);
                    break;
                case MEDIATION_FAILED:
                    processMediationFailed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown mediation event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logDisputeEvent(
                "MEDIATION_EVENT_PROCESSED",
                event.getDisputeId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "mediationId", event.getMediationId() != null ? event.getMediationId() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process mediation event: {}", e.getMessage(), e);
            kafkaTemplate.send("mediation-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processMediationRequested(MediationEvent event, String correlationId) {
        log.info("Mediation requested: disputeId={}, requestedBy={}, amount={}", 
            event.getDisputeId(), event.getRequestedBy(), event.getDisputeAmount());
        
        MediationCase mediationCase = MediationCase.builder()
            .id(UUID.randomUUID().toString())
            .disputeId(event.getDisputeId())
            .merchantId(event.getMerchantId())
            .customerId(event.getCustomerId())
            .transactionId(event.getTransactionId())
            .disputeAmount(event.getDisputeAmount())
            .requestedBy(event.getRequestedBy())
            .requestedAt(LocalDateTime.now())
            .mediationReason(event.getMediationReason())
            .status("REQUESTED")
            .responseDeadline(LocalDateTime.now().plusDays(MEDIATION_RESPONSE_DAYS))
            .correlationId(correlationId)
            .build();
        
        // Calculate mediation fee
        BigDecimal mediationFee = event.getDisputeAmount().compareTo(new BigDecimal("1000")) < 0 ?
            MEDIATION_FEE_SMALL_CLAIMS : MEDIATION_FEE_STANDARD;
        mediationCase.setMediationFee(mediationFee);
        
        mediationRepository.save(mediationCase);
        
        // Request consent from other party
        String otherParty = "MERCHANT".equals(event.getRequestedBy()) ? "CARDHOLDER" : "MERCHANT";
        mediationService.requestMediationConsent(mediationCase.getId(), otherParty);
        
        String message = String.format(
            "Mediation has been requested for dispute %s. " +
                "This is a voluntary, non-binding process that could resolve the dispute faster. " +
                "Mediation fee: %s (split between parties). Please respond within %d days.",
            event.getDisputeId(), mediationFee, MEDIATION_RESPONSE_DAYS
        );
        
        if ("MERCHANT".equals(otherParty)) {
            notificationService.sendNotification(event.getMerchantId(), 
                "Mediation Request", message, correlationId);
        }
        
        metricsService.recordMediationRequested(event.getRequestedBy());
    }
    
    private void processMediationAccepted(MediationEvent event, String correlationId) {
        log.info("Mediation accepted: mediationId={}, acceptedBy={}", 
            event.getMediationId(), event.getAcceptedBy());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setStatus("ACCEPTED");
        mediationCase.setAcceptedAt(LocalDateTime.now());
        mediationCase.setAcceptedBy(event.getAcceptedBy());
        mediationRepository.save(mediationCase);
        
        // Both parties agreed - assign mediator
        mediationService.assignMediator(mediationCase.getId());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Mediation Accepted",
            String.format("Both parties have agreed to mediation for dispute %s. " +
                "A neutral mediator will be assigned shortly.", event.getDisputeId()),
            correlationId
        );
        
        metricsService.recordMediationAccepted();
    }
    
    private void processMediationDeclined(MediationEvent event, String correlationId) {
        log.warn("Mediation declined: mediationId={}, declinedBy={}, reason={}", 
            event.getMediationId(), event.getDeclinedBy(), event.getDeclineReason());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setStatus("DECLINED");
        mediationCase.setDeclinedAt(LocalDateTime.now());
        mediationCase.setDeclinedBy(event.getDeclinedBy());
        mediationCase.setDeclineReason(event.getDeclineReason());
        mediationRepository.save(mediationCase);
        
        // Escalate to pre-arbitration or arbitration
        disputeResolutionService.escalateToNextStage(event.getDisputeId(), "MEDIATION_DECLINED");
        
        metricsService.recordMediationDeclined(event.getDeclinedBy());
    }
    
    private void processMediatorAssigned(MediationEvent event, String correlationId) {
        log.info("Mediator assigned: mediationId={}, mediator={}, organization={}", 
            event.getMediationId(), event.getMediatorName(), event.getMediatorOrganization());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setStatus("MEDIATOR_ASSIGNED");
        mediationCase.setMediatorAssignedAt(LocalDateTime.now());
        mediationCase.setMediatorName(event.getMediatorName());
        mediationCase.setMediatorOrganization(event.getMediatorOrganization());
        mediationCase.setMediatorId(event.getMediatorId());
        mediationCase.setMediatorCredentials(event.getMediatorCredentials());
        mediationRepository.save(mediationCase);
        
        mediationService.requestOpeningStatements(mediationCase.getId());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Mediator Assigned",
            String.format("Your mediator %s from %s has been assigned. " +
                "Please submit your opening statement within 3 business days.",
                event.getMediatorName(), event.getMediatorOrganization()),
            correlationId
        );
        
        metricsService.recordMediatorAssigned();
    }
    
    private void processOpeningStatementsSubmitted(MediationEvent event, String correlationId) {
        log.info("Opening statements submitted: mediationId={}, submittedBy={}", 
            event.getMediationId(), event.getSubmittedBy());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        if ("MERCHANT".equals(event.getSubmittedBy())) {
            mediationCase.setMerchantStatementSubmitted(true);
            mediationCase.setMerchantStatementSubmittedAt(LocalDateTime.now());
            mediationCase.setMerchantStatement(event.getStatement());
        } else {
            mediationCase.setCardholderStatementSubmitted(true);
            mediationCase.setCardholderStatementSubmittedAt(LocalDateTime.now());
            mediationCase.setCardholderStatement(event.getStatement());
        }
        
        mediationRepository.save(mediationCase);
        
        // Check if both statements received
        if (mediationCase.isMerchantStatementSubmitted() && 
            mediationCase.isCardholderStatementSubmitted()) {
            log.info("Both opening statements received - scheduling mediation session");
            mediationService.scheduleMediationSession(mediationCase.getId());
        }
        
        metricsService.recordOpeningStatementSubmitted(event.getSubmittedBy());
    }
    
    private void processMediationSessionScheduled(MediationEvent event, String correlationId) {
        log.info("Mediation session scheduled: mediationId={}, sessionDate={}, type={}", 
            event.getMediationId(), event.getSessionDate(), event.getSessionType());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setStatus("SESSION_SCHEDULED");
        mediationCase.setSessionScheduledAt(LocalDateTime.now());
        mediationCase.setSessionDate(event.getSessionDate());
        mediationCase.setSessionType(event.getSessionType()); // VIRTUAL, PHONE, IN_PERSON
        mediationCase.setSessionLocation(event.getSessionLocation());
        mediationCase.setSessionDuration(event.getSessionDuration());
        mediationRepository.save(mediationCase);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Mediation Session Scheduled",
            String.format("Your mediation session is scheduled for %s. " +
                "Type: %s. Duration: %d minutes. Please be prepared to discuss resolution options.",
                event.getSessionDate(), event.getSessionType(), event.getSessionDuration()),
            correlationId
        );
        
        metricsService.recordMediationSessionScheduled(event.getSessionType());
    }
    
    private void processMediationSessionHeld(MediationEvent event, String correlationId) {
        log.info("Mediation session held: mediationId={}, outcome={}", 
            event.getMediationId(), event.getSessionOutcome());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setSessionHeld(true);
        mediationCase.setSessionHeldAt(LocalDateTime.now());
        mediationCase.setSessionDurationActual(event.getActualDuration());
        mediationCase.setSessionOutcome(event.getSessionOutcome());
        mediationCase.setSessionNotes(event.getSessionNotes());
        mediationRepository.save(mediationCase);
        
        if ("PROGRESS_MADE".equals(event.getSessionOutcome())) {
            log.info("Progress made during mediation - scheduling follow-up");
            mediationService.scheduleFollowUpSession(mediationCase.getId());
        } else if ("SETTLEMENT_REACHED".equals(event.getSessionOutcome())) {
            log.info("Settlement reached during mediation session");
            mediationService.draftSettlementAgreement(mediationCase.getId());
        } else if ("IMPASSE".equals(event.getSessionOutcome())) {
            log.warn("Mediation reached impasse - preparing to escalate");
        }
        
        metricsService.recordMediationSessionHeld(event.getSessionOutcome());
    }
    
    private void processSettlementOfferMade(MediationEvent event, String correlationId) {
        log.info("Settlement offer made: mediationId={}, offeredBy={}, offerAmount={}", 
            event.getMediationId(), event.getOfferedBy(), event.getOfferAmount());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setSettlementOffered(true);
        mediationCase.setSettlementOfferedAt(LocalDateTime.now());
        mediationCase.setSettlementOfferedBy(event.getOfferedBy());
        mediationCase.setSettlementOfferAmount(event.getOfferAmount());
        mediationCase.setSettlementOfferTerms(event.getOfferTerms());
        mediationCase.setOfferResponseDeadline(event.getOfferResponseDeadline());
        mediationRepository.save(mediationCase);
        
        String recipient = "MERCHANT".equals(event.getOfferedBy()) ? "CARDHOLDER" : "MERCHANT";
        mediationService.notifySettlementOffer(mediationCase.getId(), recipient);
        
        if ("MERCHANT".equals(recipient)) {
            notificationService.sendNotification(
                event.getMerchantId(),
                "Settlement Offer Received",
                String.format("A settlement offer of %s has been proposed. " +
                    "Please review and respond by %s.",
                    event.getOfferAmount(), event.getOfferResponseDeadline()),
                correlationId
            );
        }
        
        metricsService.recordSettlementOfferMade(event.getOfferAmount());
    }
    
    private void processSettlementOfferAccepted(MediationEvent event, String correlationId) {
        log.info("Settlement offer accepted: mediationId={}, acceptedBy={}", 
            event.getMediationId(), event.getAcceptedBy());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setSettlementAccepted(true);
        mediationCase.setSettlementAcceptedAt(LocalDateTime.now());
        mediationCase.setSettlementAcceptedBy(event.getAcceptedBy());
        mediationRepository.save(mediationCase);
        
        mediationService.draftSettlementAgreement(mediationCase.getId());
        
        metricsService.recordSettlementOfferAccepted();
    }
    
    private void processSettlementOfferRejected(MediationEvent event, String correlationId) {
        log.warn("Settlement offer rejected: mediationId={}, rejectedBy={}, counterOffer={}", 
            event.getMediationId(), event.getRejectedBy(), event.getCounterOfferAmount());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setSettlementRejected(true);
        mediationCase.setSettlementRejectedAt(LocalDateTime.now());
        mediationCase.setSettlementRejectedBy(event.getRejectedBy());
        mediationCase.setRejectionReason(event.getRejectionReason());
        mediationRepository.save(mediationCase);
        
        if (event.getCounterOfferAmount() != null) {
            log.info("Counter-offer proposed: amount={}", event.getCounterOfferAmount());
            mediationService.processCounterOffer(mediationCase.getId(), event.getCounterOfferAmount());
        } else {
            log.warn("No counter-offer - mediation may be failing");
            mediationService.attemptFinalSettlement(mediationCase.getId());
        }
        
        metricsService.recordSettlementOfferRejected(event.getRejectedBy());
    }
    
    private void processSettlementAgreementSigned(MediationEvent event, String correlationId) {
        log.info("Settlement agreement signed: mediationId={}, signedBy={}", 
            event.getMediationId(), event.getSignedBy());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        if ("MERCHANT".equals(event.getSignedBy())) {
            mediationCase.setMerchantSignedAgreement(true);
            mediationCase.setMerchantSignedAt(LocalDateTime.now());
        } else {
            mediationCase.setCardholderSignedAgreement(true);
            mediationCase.setCardholderSignedAt(LocalDateTime.now());
        }
        
        mediationRepository.save(mediationCase);
        
        // Check if both parties signed
        if (mediationCase.isMerchantSignedAgreement() && mediationCase.isCardholderSignedAgreement()) {
            log.info("Both parties signed settlement agreement - mediation successful");
            mediationService.finalizeSettlement(mediationCase.getId());
        }
        
        metricsService.recordSettlementAgreementSigned(event.getSignedBy());
    }
    
    private void processMediationSuccessful(MediationEvent event, String correlationId) {
        log.info("Mediation successful: mediationId={}, settlementAmount={}", 
            event.getMediationId(), event.getSettlementAmount());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setStatus("SUCCESSFUL");
        mediationCase.setResolvedAt(LocalDateTime.now());
        mediationCase.setFinalSettlementAmount(event.getSettlementAmount());
        mediationCase.setSettlementTerms(event.getSettlementTerms());
        mediationRepository.save(mediationCase);
        
        // Execute settlement
        disputeResolutionService.executeSettlement(event.getDisputeId(), 
            event.getSettlementAmount(), "MEDIATION");
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Mediation Resolved Successfully",
            String.format("Congratulations! Your dispute has been successfully resolved through mediation. " +
                "Settlement amount: %s. Thank you for your cooperation.",
                event.getSettlementAmount()),
            correlationId
        );
        
        metricsService.recordMediationSuccessful(event.getSettlementAmount());
    }
    
    private void processMediationFailed(MediationEvent event, String correlationId) {
        log.warn("Mediation failed: mediationId={}, reason={}", 
            event.getMediationId(), event.getFailureReason());
        
        MediationCase mediationCase = mediationRepository.findById(event.getMediationId())
            .orElseThrow();
        
        mediationCase.setStatus("FAILED");
        mediationCase.setFailedAt(LocalDateTime.now());
        mediationCase.setFailureReason(event.getFailureReason());
        mediationRepository.save(mediationCase);
        
        // Escalate to pre-arbitration or arbitration
        disputeResolutionService.escalateToNextStage(event.getDisputeId(), "MEDIATION_FAILED");
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Mediation Unsuccessful - Next Steps",
            "Unfortunately, mediation did not result in a resolution. " +
                "The dispute will now proceed to pre-arbitration or arbitration.",
            correlationId
        );
        
        metricsService.recordMediationFailed(event.getFailureReason());
    }
}