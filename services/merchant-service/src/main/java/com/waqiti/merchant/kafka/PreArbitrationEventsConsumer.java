package com.waqiti.merchant.kafka;

import com.waqiti.common.events.PreArbitrationEvent;
import com.waqiti.merchant.domain.PreArbitrationCase;
import com.waqiti.merchant.repository.PreArbitrationCaseRepository;
import com.waqiti.merchant.service.PreArbitrationService;
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
 * Pre-Arbitration Events Consumer
 * Processes pre-arbitration filings before full arbitration
 * Implements 12-step zero-tolerance processing for pre-arbitration lifecycle
 * 
 * Business Context:
 * - Final negotiation step before binding arbitration
 * - Card network pre-arbitration programs (Visa Pre-Arb, MC Collaboration)
 * - Lower cost than full arbitration ($250-$350 vs $500-$5,000)
 * - Resolution timeframe: 15-30 days
 * - Success rate: ~50% (half escalate to arbitration)
 * - Pre-arb fee: Charged to losing party
 * 
 * Pre-Arbitration Process:
 * 1. Losing party files pre-arbitration
 * 2. Card network reviews case merits
 * 3. Winning party can accept liability or defend
 * 4. Network makes preliminary ruling
 * 5. Either party can escalate to full arbitration
 * 6. If neither escalates, ruling becomes final
 * 
 * Triggers:
 * - Merchant lost representment
 * - Second chargeback received
 * - Dispute amount justifies escalation
 * - Strong evidence for reversal
 * 
 * @author Waqiti Merchant Services Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PreArbitrationEventsConsumer {
    
    private final PreArbitrationCaseRepository preArbitrationRepository;
    private final PreArbitrationService preArbitrationService;
    private final DisputeResolutionService disputeResolutionService;
    private final DisputeMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal VISA_PRE_ARB_FEE = new BigDecimal("250");
    private static final BigDecimal MASTERCARD_COLLAB_FEE = new BigDecimal("300");
    private static final BigDecimal AMEX_PRE_ARB_FEE = new BigDecimal("350");
    private static final int PRE_ARB_RESPONSE_DAYS = 10;
    
    @KafkaListener(
        topics = {"pre-arbitration-events", "pre-arb-events", "collaboration-case-events"},
        groupId = "merchant-pre-arbitration-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "2"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 150)
    public void handlePreArbitrationEvent(
            @Payload PreArbitrationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("pre-arb-%s-p%d-o%d", 
            event.getDisputeId(), partition, offset);
        
        log.info("Processing pre-arbitration event: disputeId={}, type={}, network={}", 
            event.getDisputeId(), event.getEventType(), event.getCardNetwork());
        
        try {
            switch (event.getEventType()) {
                case PRE_ARBITRATION_FILED:
                    processPreArbitrationFiled(event, correlationId);
                    break;
                case PRE_ARB_FEE_CHARGED:
                    processPreArbFeeCharged(event, correlationId);
                    break;
                case CASE_REVIEW_INITIATED:
                    processCaseReviewInitiated(event, correlationId);
                    break;
                case MERCHANT_RESPONSE_REQUESTED:
                    processMerchantResponseRequested(event, correlationId);
                    break;
                case MERCHANT_ACCEPTS_LIABILITY:
                    processMerchantAcceptsLiability(event, correlationId);
                    break;
                case MERCHANT_DEFENDS_CASE:
                    processMerchantDefendsCase(event, correlationId);
                    break;
                case NETWORK_PRELIMINARY_RULING:
                    processNetworkPreliminaryRuling(event, correlationId);
                    break;
                case RULING_ACCEPTED:
                    processRulingAccepted(event, correlationId);
                    break;
                case ESCALATED_TO_ARBITRATION:
                    processEscalatedToArbitration(event, correlationId);
                    break;
                case PRE_ARB_WON:
                    processPreArbWon(event, correlationId);
                    break;
                case PRE_ARB_LOST:
                    processPreArbLost(event, correlationId);
                    break;
                case PRE_ARB_WITHDRAWN:
                    processPreArbWithdrawn(event, correlationId);
                    break;
                default:
                    log.warn("Unknown pre-arbitration event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logDisputeEvent(
                "PRE_ARBITRATION_EVENT_PROCESSED",
                event.getDisputeId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "preArbId", event.getPreArbitrationId() != null ? event.getPreArbitrationId() : "N/A",
                    "cardNetwork", event.getCardNetwork(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process pre-arbitration event: {}", e.getMessage(), e);
            kafkaTemplate.send("pre-arbitration-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processPreArbitrationFiled(PreArbitrationEvent event, String correlationId) {
        log.warn("Pre-arbitration filed: disputeId={}, filedBy={}, amount={}, network={}", 
            event.getDisputeId(), event.getFiledBy(), event.getDisputeAmount(), event.getCardNetwork());
        
        PreArbitrationCase preArbCase = PreArbitrationCase.builder()
            .id(UUID.randomUUID().toString())
            .disputeId(event.getDisputeId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .disputeAmount(event.getDisputeAmount())
            .cardNetwork(event.getCardNetwork())
            .filedBy(event.getFiledBy())
            .filedAt(LocalDateTime.now())
            .preArbReason(event.getPreArbReason())
            .previousOutcome(event.getPreviousOutcome())
            .status("FILED")
            .responseDeadline(LocalDateTime.now().plusDays(PRE_ARB_RESPONSE_DAYS))
            .correlationId(correlationId)
            .build();
        
        // Calculate pre-arbitration fee
        BigDecimal preArbFee = calculatePreArbFee(event.getCardNetwork());
        preArbCase.setPreArbFee(preArbFee);
        
        preArbitrationRepository.save(preArbCase);
        
        String message = String.format(
            "Pre-arbitration has been filed for dispute %s. " +
                "Previous outcome: %s. Amount: %s. Pre-arb fee: %s. " +
                "This is your final opportunity before binding arbitration. Response deadline: %d days.",
            event.getDisputeId(), event.getPreviousOutcome(), 
            event.getDisputeAmount(), preArbFee, PRE_ARB_RESPONSE_DAYS
        );
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Pre-Arbitration Filed - Action Required",
            message,
            correlationId
        );
        
        metricsService.recordPreArbitrationFiled(event.getCardNetwork(), event.getFiledBy());
    }
    
    private void processPreArbFeeCharged(PreArbitrationEvent event, String correlationId) {
        log.info("Pre-arbitration fee charged: preArbId={}, fee={}, chargedTo={}", 
            event.getPreArbitrationId(), event.getPreArbFee(), event.getFeeChargedTo());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setFeeCharged(true);
        preArbCase.setFeeChargedAt(LocalDateTime.now());
        preArbCase.setFeeChargedTo(event.getFeeChargedTo());
        preArbCase.setFeePaymentStatus(event.getFeePaymentStatus());
        preArbitrationRepository.save(preArbCase);
        
        if ("SUCCESS".equals(event.getFeePaymentStatus())) {
            preArbitrationService.initiateCaseReview(preArbCase.getId());
        } else {
            log.error("Pre-arb fee payment failed: preArbId={}", event.getPreArbitrationId());
            preArbitrationService.handleFailedFeePayment(preArbCase.getId());
        }
        
        metricsService.recordPreArbFeeCharged(event.getPreArbFee());
    }
    
    private void processCaseReviewInitiated(PreArbitrationEvent event, String correlationId) {
        log.info("Case review initiated: preArbId={}, reviewerId={}", 
            event.getPreArbitrationId(), event.getReviewerId());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("UNDER_REVIEW");
        preArbCase.setCaseReviewInitiatedAt(LocalDateTime.now());
        preArbCase.setReviewerId(event.getReviewerId());
        preArbCase.setReviewerName(event.getReviewerName());
        preArbitrationRepository.save(preArbCase);
        
        preArbitrationService.requestMerchantResponse(preArbCase.getId());
        
        metricsService.recordCaseReviewInitiated();
    }
    
    private void processMerchantResponseRequested(PreArbitrationEvent event, String correlationId) {
        log.info("Merchant response requested: preArbId={}, deadline={}", 
            event.getPreArbitrationId(), event.getResponseDeadline());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setMerchantResponseRequested(true);
        preArbCase.setMerchantResponseRequestedAt(LocalDateTime.now());
        preArbCase.setResponseDeadline(event.getResponseDeadline());
        preArbitrationRepository.save(preArbCase);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Pre-Arbitration Response Required",
            String.format("You must respond to pre-arbitration case %s by %s. " +
                "Options: (1) Accept liability and refund, or (2) Defend with additional evidence. " +
                "No response will result in automatic loss.",
                event.getPreArbitrationId(), event.getResponseDeadline()),
            correlationId
        );
        
        metricsService.recordMerchantResponseRequested();
    }
    
    private void processMerchantAcceptsLiability(PreArbitrationEvent event, String correlationId) {
        log.info("Merchant accepts liability: preArbId={}, refundAmount={}", 
            event.getPreArbitrationId(), event.getRefundAmount());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("LIABILITY_ACCEPTED");
        preArbCase.setMerchantAcceptedLiability(true);
        preArbCase.setLiabilityAcceptedAt(LocalDateTime.now());
        preArbCase.setRefundAmount(event.getRefundAmount());
        preArbitrationRepository.save(preArbCase);
        
        // Process refund and close case
        disputeResolutionService.processRefund(event.getDisputeId(), event.getRefundAmount());
        preArbitrationService.closeCaseAsResolved(preArbCase.getId());
        
        // Pre-arb fee charged to merchant (losing party)
        log.info("Pre-arb fee charged to merchant: amount={}", preArbCase.getPreArbFee());
        
        metricsService.recordMerchantAcceptsLiability();
    }
    
    private void processMerchantDefendsCase(PreArbitrationEvent event, String correlationId) {
        log.info("Merchant defends case: preArbId={}, evidenceCount={}", 
            event.getPreArbitrationId(), event.getDefenseEvidenceCount());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setMerchantDefendsCase(true);
        preArbCase.setMerchantDefenseSubmittedAt(LocalDateTime.now());
        preArbCase.setDefenseEvidenceCount(event.getDefenseEvidenceCount());
        preArbCase.setDefenseSummary(event.getDefenseSummary());
        preArbCase.setStatus("MERCHANT_DEFENDING");
        preArbitrationRepository.save(preArbCase);
        
        // Network will review defense and make preliminary ruling
        preArbitrationService.schedulePreliminaryRuling(preArbCase.getId());
        
        metricsService.recordMerchantDefendsCase();
    }
    
    private void processNetworkPreliminaryRuling(PreArbitrationEvent event, String correlationId) {
        log.info("Network preliminary ruling: preArbId={}, rulingInFavorOf={}", 
            event.getPreArbitrationId(), event.getRulingInFavorOf());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("PRELIMINARY_RULING_ISSUED");
        preArbCase.setPreliminaryRulingIssuedAt(LocalDateTime.now());
        preArbCase.setRulingInFavorOf(event.getRulingInFavorOf());
        preArbCase.setRulingReasoning(event.getRulingReasoning());
        preArbCase.setRulingConfidence(event.getRulingConfidence());
        preArbCase.setArbitrationEscalationDeadline(LocalDateTime.now().plusDays(10));
        preArbitrationRepository.save(preArbCase);
        
        String rulingMessage = String.format(
            "Preliminary ruling issued for pre-arbitration case %s. " +
                "Ruling in favor of: %s. Reasoning: %s. " +
                "You have 10 days to accept this ruling or escalate to full arbitration.",
            event.getPreArbitrationId(), event.getRulingInFavorOf(), event.getRulingReasoning()
        );
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Pre-Arbitration Preliminary Ruling",
            rulingMessage,
            correlationId
        );
        
        metricsService.recordNetworkPreliminaryRuling(event.getRulingInFavorOf());
    }
    
    private void processRulingAccepted(PreArbitrationEvent event, String correlationId) {
        log.info("Ruling accepted: preArbId={}, acceptedBy={}", 
            event.getPreArbitrationId(), event.getAcceptedBy());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("RULING_ACCEPTED");
        preArbCase.setRulingAcceptedAt(LocalDateTime.now());
        preArbCase.setRulingAcceptedBy(event.getAcceptedBy());
        preArbitrationRepository.save(preArbCase);
        
        // Execute ruling
        if ("MERCHANT".equals(preArbCase.getRulingInFavorOf())) {
            preArbitrationService.processMerchantVictory(preArbCase.getId());
        } else {
            preArbitrationService.processMerchantDefeat(preArbCase.getId());
        }
        
        metricsService.recordRulingAccepted(event.getAcceptedBy());
    }
    
    private void processEscalatedToArbitration(PreArbitrationEvent event, String correlationId) {
        log.warn("Escalated to arbitration: preArbId={}, escalatedBy={}", 
            event.getPreArbitrationId(), event.getEscalatedBy());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("ESCALATED_TO_ARBITRATION");
        preArbCase.setEscalatedToArbitration(true);
        preArbCase.setEscalatedAt(LocalDateTime.now());
        preArbCase.setEscalatedBy(event.getEscalatedBy());
        preArbitrationRepository.save(preArbCase);
        
        // File arbitration case
        disputeResolutionService.fileArbitration(event.getDisputeId(), 
            "PRE_ARBITRATION_ESCALATION");
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Case Escalated to Arbitration",
            String.format("Pre-arbitration case %s has been escalated to full arbitration by %s. " +
                "This will involve a binding arbitration process with higher fees.",
                event.getPreArbitrationId(), event.getEscalatedBy()),
            correlationId
        );
        
        metricsService.recordEscalatedToArbitration(event.getEscalatedBy());
    }
    
    private void processPreArbWon(PreArbitrationEvent event, String correlationId) {
        log.info("Pre-arbitration won: preArbId={}, merchantId={}", 
            event.getPreArbitrationId(), event.getMerchantId());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("WON");
        preArbCase.setClosedAt(LocalDateTime.now());
        preArbitrationRepository.save(preArbCase);
        
        // Reverse chargeback
        disputeResolutionService.reverseFunds(event.getDisputeId(), event.getDisputeAmount());
        
        // Pre-arb fee charged to cardholder/issuer (losing party)
        log.info("Pre-arb fee charged to losing party: amount={}", preArbCase.getPreArbFee());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Pre-Arbitration Won!",
            String.format("Congratulations! You won pre-arbitration case %s. " +
                "The disputed amount of %s will be returned to your account.",
                event.getPreArbitrationId(), event.getDisputeAmount()),
            correlationId
        );
        
        metricsService.recordPreArbWon(event.getCardNetwork(), event.getDisputeAmount());
    }
    
    private void processPreArbLost(PreArbitrationEvent event, String correlationId) {
        log.warn("Pre-arbitration lost: preArbId={}, merchantId={}, liabilityAmount={}", 
            event.getPreArbitrationId(), event.getMerchantId(), event.getLiabilityAmount());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("LOST");
        preArbCase.setClosedAt(LocalDateTime.now());
        preArbCase.setLiabilityAmount(event.getLiabilityAmount());
        preArbitrationRepository.save(preArbCase);
        
        // Chargeback stands + pre-arb fee
        BigDecimal totalLiability = event.getLiabilityAmount().add(preArbCase.getPreArbFee());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Pre-Arbitration Decision",
            String.format("Pre-arbitration case %s was decided against you. " +
                "Total liability: %s (includes %s pre-arb fee). " +
                "You may escalate to full arbitration within 10 days.",
                event.getPreArbitrationId(), totalLiability, preArbCase.getPreArbFee()),
            correlationId
        );
        
        metricsService.recordPreArbLost(event.getCardNetwork(), totalLiability);
    }
    
    private void processPreArbWithdrawn(PreArbitrationEvent event, String correlationId) {
        log.info("Pre-arbitration withdrawn: preArbId={}, withdrawnBy={}", 
            event.getPreArbitrationId(), event.getWithdrawnBy());
        
        PreArbitrationCase preArbCase = preArbitrationRepository.findById(event.getPreArbitrationId())
            .orElseThrow();
        
        preArbCase.setStatus("WITHDRAWN");
        preArbCase.setWithdrawnAt(LocalDateTime.now());
        preArbCase.setWithdrawnBy(event.getWithdrawnBy());
        preArbCase.setWithdrawalReason(event.getWithdrawalReason());
        preArbitrationRepository.save(preArbCase);
        
        metricsService.recordPreArbWithdrawn(event.getWithdrawnBy());
    }
    
    private BigDecimal calculatePreArbFee(String cardNetwork) {
        return switch (cardNetwork) {
            case "VISA" -> VISA_PRE_ARB_FEE;
            case "MASTERCARD" -> MASTERCARD_COLLAB_FEE;
            case "AMEX" -> AMEX_PRE_ARB_FEE;
            case "DISCOVER" -> new BigDecimal("275");
            default -> new BigDecimal("300");
        };
    }
}