package com.waqiti.merchant.kafka;

import com.waqiti.common.events.ArbitrationEvent;
import com.waqiti.merchant.domain.ArbitrationCase;
import com.waqiti.merchant.repository.ArbitrationCaseRepository;
import com.waqiti.merchant.service.ArbitrationService;
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
 * Arbitration Events Consumer
 * Processes formal arbitration proceedings for unresolved disputes
 * Implements 12-step zero-tolerance processing for arbitration lifecycle
 * 
 * Business Context:
 * - Card network arbitration (Visa, Mastercard, Amex)
 * - Binding arbitration for high-value disputes (typically >$5,000)
 * - Third-party arbitrator involvement (AAA, JAMS)
 * - Legal compliance with arbitration agreements
 * - Final resolution mechanism after pre-arbitration fails
 * - Arbitration fees: $500-$5,000 depending on claim amount
 * - Resolution timeframe: 30-90 days
 * 
 * Arbitration Triggers:
 * - Pre-arbitration unsuccessful
 * - Dispute amount exceeds threshold
 * - Multiple dispute cycles exhausted
 * - Merchant or cardholder escalation request
 * 
 * @author Waqiti Merchant Services Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ArbitrationEventsConsumer {
    
    private final ArbitrationCaseRepository arbitrationRepository;
    private final ArbitrationService arbitrationService;
    private final DisputeResolutionService disputeResolutionService;
    private final DisputeMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal ARBITRATION_FEE_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal VISA_ARBITRATION_FEE = new BigDecimal("500");
    private static final BigDecimal MASTERCARD_ARBITRATION_FEE = new BigDecimal("600");
    private static final int ARBITRATION_RESPONSE_DAYS = 10;
    
    @KafkaListener(
        topics = {"arbitration-events", "card-network-arbitration", "binding-arbitration-events"},
        groupId = "merchant-arbitration-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 180)
    public void handleArbitrationEvent(
            @Payload ArbitrationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("arbitration-%s-p%d-o%d", 
            event.getDisputeId(), partition, offset);
        
        log.info("Processing arbitration event: disputeId={}, type={}, network={}", 
            event.getDisputeId(), event.getEventType(), event.getCardNetwork());
        
        try {
            switch (event.getEventType()) {
                case ARBITRATION_FILED:
                    processArbitrationFiled(event, correlationId);
                    break;
                case ARBITRATION_FEE_CHARGED:
                    processArbitrationFeeCharged(event, correlationId);
                    break;
                case EVIDENCE_SUBMISSION_OPENED:
                    processEvidenceSubmissionOpened(event, correlationId);
                    break;
                case MERCHANT_EVIDENCE_SUBMITTED:
                    processMerchantEvidenceSubmitted(event, correlationId);
                    break;
                case CARDHOLDER_REBUTTAL_SUBMITTED:
                    processCardholderRebuttalSubmitted(event, correlationId);
                    break;
                case EVIDENCE_REVIEW_COMPLETED:
                    processEvidenceReviewCompleted(event, correlationId);
                    break;
                case ARBITRATOR_ASSIGNED:
                    processArbitratorAssigned(event, correlationId);
                    break;
                case HEARING_SCHEDULED:
                    processHearingScheduled(event, correlationId);
                    break;
                case ARBITRATION_DECISION_ISSUED:
                    processArbitrationDecisionIssued(event, correlationId);
                    break;
                case ARBITRATION_WON:
                    processArbitrationWon(event, correlationId);
                    break;
                case ARBITRATION_LOST:
                    processArbitrationLost(event, correlationId);
                    break;
                case ARBITRATION_WITHDRAWN:
                    processArbitrationWithdrawn(event, correlationId);
                    break;
                default:
                    log.warn("Unknown arbitration event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logDisputeEvent(
                "ARBITRATION_EVENT_PROCESSED",
                event.getDisputeId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "arbitrationId", event.getArbitrationId() != null ? event.getArbitrationId() : "N/A",
                    "cardNetwork", event.getCardNetwork(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process arbitration event: {}", e.getMessage(), e);
            kafkaTemplate.send("arbitration-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processArbitrationFiled(ArbitrationEvent event, String correlationId) {
        log.warn("Arbitration filed: disputeId={}, amount={}, filedBy={}, network={}", 
            event.getDisputeId(), event.getDisputeAmount(), event.getFiledBy(), event.getCardNetwork());
        
        ArbitrationCase arbitrationCase = ArbitrationCase.builder()
            .id(UUID.randomUUID().toString())
            .disputeId(event.getDisputeId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .disputeAmount(event.getDisputeAmount())
            .cardNetwork(event.getCardNetwork())
            .filedBy(event.getFiledBy()) // MERCHANT or CARDHOLDER
            .filedAt(LocalDateTime.now())
            .arbitrationReason(event.getArbitrationReason())
            .status("FILED")
            .dueDate(LocalDateTime.now().plusDays(ARBITRATION_RESPONSE_DAYS))
            .correlationId(correlationId)
            .build();
        
        arbitrationRepository.save(arbitrationCase);
        
        // Calculate arbitration fee based on card network
        BigDecimal arbitrationFee = calculateArbitrationFee(event.getCardNetwork(), 
            event.getDisputeAmount());
        arbitrationCase.setArbitrationFee(arbitrationFee);
        arbitrationRepository.save(arbitrationCase);
        
        // Notify both parties
        String notificationMessage = String.format(
            "Arbitration has been filed for dispute %s by %s. " +
                "Amount: %s. Arbitration fee: %s. " +
                "Response deadline: %d days. This is a binding arbitration process.",
            event.getDisputeId(), event.getFiledBy(), 
            event.getDisputeAmount(), arbitrationFee, ARBITRATION_RESPONSE_DAYS
        );
        
        arbitrationService.notifyAllParties(arbitrationCase.getId(), notificationMessage);
        
        metricsService.recordArbitrationFiled(event.getCardNetwork(), event.getFiledBy());
    }
    
    private void processArbitrationFeeCharged(ArbitrationEvent event, String correlationId) {
        log.info("Arbitration fee charged: arbitrationId={}, fee={}, chargedTo={}", 
            event.getArbitrationId(), event.getArbitrationFee(), event.getFeeChargedTo());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setFeeCharged(true);
        arbitrationCase.setFeeChargedAt(LocalDateTime.now());
        arbitrationCase.setFeeChargedTo(event.getFeeChargedTo());
        arbitrationCase.setFeePaymentStatus(event.getFeePaymentStatus());
        arbitrationRepository.save(arbitrationCase);
        
        if ("FAILED".equals(event.getFeePaymentStatus())) {
            log.error("Arbitration fee payment failed: arbitrationId={}", event.getArbitrationId());
            arbitrationService.handleFailedFeePayment(arbitrationCase.getId());
        } else {
            arbitrationService.proceedWithArbitration(arbitrationCase.getId());
        }
        
        metricsService.recordArbitrationFeeCharged(event.getArbitrationFee());
    }
    
    private void processEvidenceSubmissionOpened(ArbitrationEvent event, String correlationId) {
        log.info("Evidence submission opened: arbitrationId={}", event.getArbitrationId());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setStatus("AWAITING_EVIDENCE");
        arbitrationCase.setEvidenceSubmissionOpenedAt(LocalDateTime.now());
        arbitrationCase.setEvidenceSubmissionDeadline(event.getEvidenceSubmissionDeadline());
        arbitrationRepository.save(arbitrationCase);
        
        arbitrationService.requestEvidence(arbitrationCase.getId());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Arbitration Evidence Required",
            String.format("Please submit all supporting evidence for arbitration case %s by %s. " +
                "This is your final opportunity to present your case.",
                event.getArbitrationId(), event.getEvidenceSubmissionDeadline()),
            correlationId
        );
        
        metricsService.recordEvidenceSubmissionOpened();
    }
    
    private void processMerchantEvidenceSubmitted(ArbitrationEvent event, String correlationId) {
        log.info("Merchant evidence submitted: arbitrationId={}, documentCount={}", 
            event.getArbitrationId(), event.getDocumentCount());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setMerchantEvidenceSubmitted(true);
        arbitrationCase.setMerchantEvidenceSubmittedAt(LocalDateTime.now());
        arbitrationCase.setMerchantDocumentCount(event.getDocumentCount());
        arbitrationCase.setMerchantEvidenceSummary(event.getEvidenceSummary());
        arbitrationRepository.save(arbitrationCase);
        
        arbitrationService.validateEvidence(arbitrationCase.getId(), "MERCHANT");
        metricsService.recordMerchantEvidenceSubmitted();
    }
    
    private void processCardholderRebuttalSubmitted(ArbitrationEvent event, String correlationId) {
        log.info("Cardholder rebuttal submitted: arbitrationId={}", event.getArbitrationId());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setCardholderRebuttalSubmitted(true);
        arbitrationCase.setCardholderRebuttalSubmittedAt(LocalDateTime.now());
        arbitrationCase.setCardholderRebuttalSummary(event.getRebuttalSummary());
        arbitrationRepository.save(arbitrationCase);
        
        metricsService.recordCardholderRebuttalSubmitted();
    }
    
    private void processEvidenceReviewCompleted(ArbitrationEvent event, String correlationId) {
        log.info("Evidence review completed: arbitrationId={}", event.getArbitrationId());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setStatus("UNDER_REVIEW");
        arbitrationCase.setEvidenceReviewCompletedAt(LocalDateTime.now());
        arbitrationCase.setReviewerNotes(event.getReviewerNotes());
        arbitrationRepository.save(arbitrationCase);
        
        arbitrationService.assignToArbitrator(arbitrationCase.getId());
        metricsService.recordEvidenceReviewCompleted();
    }
    
    private void processArbitratorAssigned(ArbitrationEvent event, String correlationId) {
        log.info("Arbitrator assigned: arbitrationId={}, arbitrator={}, organization={}", 
            event.getArbitrationId(), event.getArbitratorName(), event.getArbitratorOrganization());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setArbitratorAssigned(true);
        arbitrationCase.setArbitratorAssignedAt(LocalDateTime.now());
        arbitrationCase.setArbitratorName(event.getArbitratorName());
        arbitrationCase.setArbitratorOrganization(event.getArbitratorOrganization());
        arbitrationCase.setArbitratorId(event.getArbitratorId());
        arbitrationRepository.save(arbitrationCase);
        
        arbitrationService.notifyPartiesOfArbitrator(arbitrationCase.getId());
        metricsService.recordArbitratorAssigned(event.getArbitratorOrganization());
    }
    
    private void processHearingScheduled(ArbitrationEvent event, String correlationId) {
        log.info("Arbitration hearing scheduled: arbitrationId={}, hearingDate={}", 
            event.getArbitrationId(), event.getHearingDate());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setHearingScheduled(true);
        arbitrationCase.setHearingDate(event.getHearingDate());
        arbitrationCase.setHearingType(event.getHearingType()); // IN_PERSON, VIRTUAL, DOCUMENTARY
        arbitrationCase.setHearingLocation(event.getHearingLocation());
        arbitrationRepository.save(arbitrationCase);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Arbitration Hearing Scheduled",
            String.format("Your arbitration hearing for case %s is scheduled for %s. " +
                "Type: %s. Location: %s. Please prepare accordingly.",
                event.getArbitrationId(), event.getHearingDate(), 
                event.getHearingType(), event.getHearingLocation()),
            correlationId
        );
        
        metricsService.recordHearingScheduled(event.getHearingType());
    }
    
    private void processArbitrationDecisionIssued(ArbitrationEvent event, String correlationId) {
        log.info("Arbitration decision issued: arbitrationId={}, winner={}, decision={}", 
            event.getArbitrationId(), event.getWinner(), event.getDecision());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setStatus("DECIDED");
        arbitrationCase.setDecisionIssuedAt(LocalDateTime.now());
        arbitrationCase.setWinner(event.getWinner());
        arbitrationCase.setDecision(event.getDecision());
        arbitrationCase.setDecisionReasoning(event.getDecisionReasoning());
        arbitrationCase.setAwardAmount(event.getAwardAmount());
        arbitrationCase.setFeeAllocation(event.getFeeAllocation());
        arbitrationRepository.save(arbitrationCase);
        
        disputeResolutionService.executeArbitrationDecision(arbitrationCase.getId());
        
        metricsService.recordArbitrationDecisionIssued(event.getWinner());
    }
    
    private void processArbitrationWon(ArbitrationEvent event, String correlationId) {
        log.info("Arbitration won: arbitrationId={}, merchantId={}, awardAmount={}", 
            event.getArbitrationId(), event.getMerchantId(), event.getAwardAmount());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setStatus("WON");
        arbitrationCase.setClosedAt(LocalDateTime.now());
        arbitrationRepository.save(arbitrationCase);
        
        arbitrationService.processMerchantVictory(arbitrationCase.getId());
        disputeResolutionService.reverseFunds(event.getDisputeId(), event.getAwardAmount());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Arbitration Decision - You Won!",
            String.format("Congratulations! The arbitrator ruled in your favor for case %s. " +
                "Award amount: %s. Funds will be returned to your account.",
                event.getArbitrationId(), event.getAwardAmount()),
            correlationId
        );
        
        metricsService.recordArbitrationWon(event.getCardNetwork(), event.getAwardAmount());
    }
    
    private void processArbitrationLost(ArbitrationEvent event, String correlationId) {
        log.warn("Arbitration lost: arbitrationId={}, merchantId={}, liabilityAmount={}", 
            event.getArbitrationId(), event.getMerchantId(), event.getLiabilityAmount());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setStatus("LOST");
        arbitrationCase.setClosedAt(LocalDateTime.now());
        arbitrationCase.setLiabilityAmount(event.getLiabilityAmount());
        arbitrationRepository.save(arbitrationCase);
        
        arbitrationService.processMerchantDefeat(arbitrationCase.getId());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Arbitration Decision",
            String.format("The arbitrator ruled against you for case %s. " +
                "Liability: %s (includes arbitration fee). This decision is final and binding.",
                event.getArbitrationId(), event.getLiabilityAmount()),
            correlationId
        );
        
        metricsService.recordArbitrationLost(event.getCardNetwork(), event.getLiabilityAmount());
    }
    
    private void processArbitrationWithdrawn(ArbitrationEvent event, String correlationId) {
        log.info("Arbitration withdrawn: arbitrationId={}, withdrawnBy={}, reason={}", 
            event.getArbitrationId(), event.getWithdrawnBy(), event.getWithdrawalReason());
        
        ArbitrationCase arbitrationCase = arbitrationRepository.findById(event.getArbitrationId())
            .orElseThrow();
        
        arbitrationCase.setStatus("WITHDRAWN");
        arbitrationCase.setWithdrawnAt(LocalDateTime.now());
        arbitrationCase.setWithdrawnBy(event.getWithdrawnBy());
        arbitrationCase.setWithdrawalReason(event.getWithdrawalReason());
        arbitrationRepository.save(arbitrationCase);
        
        // Arbitration fee may be forfeited
        if ("MERCHANT".equals(event.getWithdrawnBy())) {
            log.warn("Merchant withdrew arbitration - fee forfeited: amount={}", 
                arbitrationCase.getArbitrationFee());
        }
        
        metricsService.recordArbitrationWithdrawn(event.getWithdrawnBy());
    }
    
    private BigDecimal calculateArbitrationFee(String cardNetwork, BigDecimal disputeAmount) {
        // Card network arbitration fee schedules
        return switch (cardNetwork) {
            case "VISA" -> disputeAmount.compareTo(ARBITRATION_FEE_THRESHOLD) > 0 ? 
                VISA_ARBITRATION_FEE.multiply(new BigDecimal("2")) : VISA_ARBITRATION_FEE;
            case "MASTERCARD" -> disputeAmount.compareTo(ARBITRATION_FEE_THRESHOLD) > 0 ?
                MASTERCARD_ARBITRATION_FEE.multiply(new BigDecimal("2")) : MASTERCARD_ARBITRATION_FEE;
            case "AMEX" -> new BigDecimal("750"); // American Express fixed fee
            case "DISCOVER" -> new BigDecimal("450"); // Discover fixed fee
            default -> new BigDecimal("500"); // Default arbitration fee
        };
    }
}