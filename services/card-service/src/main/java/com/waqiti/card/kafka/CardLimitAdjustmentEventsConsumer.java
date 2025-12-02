package com.waqiti.card.kafka;

import com.waqiti.common.events.CardLimitAdjustmentEvent;
import com.waqiti.card.domain.Card;
import com.waqiti.card.domain.LimitAdjustmentRequest;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.LimitAdjustmentRequestRepository;
import com.waqiti.card.service.LimitManagementService;
import com.waqiti.card.service.CreditAssessmentService;
import com.waqiti.card.service.RiskEvaluationService;
import com.waqiti.card.metrics.CardMetricsService;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class CardLimitAdjustmentEventsConsumer {
    
    private final CardRepository cardRepository;
    private final LimitAdjustmentRequestRepository adjustmentRequestRepository;
    private final LimitManagementService limitManagementService;
    private final CreditAssessmentService creditAssessmentService;
    private final RiskEvaluationService riskEvaluationService;
    private final CardMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal MAX_AUTO_INCREASE_PERCENTAGE = new BigDecimal("0.50");
    private static final BigDecimal SIGNIFICANT_INCREASE_THRESHOLD = new BigDecimal("5000");
    private static final int LIMIT_REVIEW_FREQUENCY_MONTHS = 6;
    
    @KafkaListener(
        topics = {"card-limit-adjustment-events", "spending-limit-change-events", "credit-limit-events"},
        groupId = "card-limit-adjustment-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 120)
    public void handleCardLimitAdjustmentEvent(
            @Payload CardLimitAdjustmentEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("limit-adjustment-%s-p%d-o%d", 
            event.getCardId(), partition, offset);
        
        log.info("Processing card limit adjustment event: cardId={}, type={}, limitType={}", 
            event.getCardId(), event.getEventType(), event.getLimitType());
        
        try {
            switch (event.getEventType()) {
                case LIMIT_INCREASE_REQUESTED:
                    processLimitIncreaseRequested(event, correlationId);
                    break;
                case LIMIT_DECREASE_REQUESTED:
                    processLimitDecreaseRequested(event, correlationId);
                    break;
                case CREDIT_ASSESSMENT_INITIATED:
                    processCreditAssessmentInitiated(event, correlationId);
                    break;
                case CREDIT_ASSESSMENT_COMPLETED:
                    processCreditAssessmentCompleted(event, correlationId);
                    break;
                case RISK_EVALUATION_COMPLETED:
                    processRiskEvaluationCompleted(event, correlationId);
                    break;
                case LIMIT_INCREASE_APPROVED:
                    processLimitIncreaseApproved(event, correlationId);
                    break;
                case LIMIT_INCREASE_DECLINED:
                    processLimitIncreaseDeclined(event, correlationId);
                    break;
                case LIMIT_DECREASE_COMPLETED:
                    processLimitDecreaseCompleted(event, correlationId);
                    break;
                case TEMPORARY_LIMIT_INCREASE_GRANTED:
                    processTemporaryLimitIncreaseGranted(event, correlationId);
                    break;
                case TEMPORARY_LIMIT_EXPIRED:
                    processTemporaryLimitExpired(event, correlationId);
                    break;
                case AUTO_LIMIT_INCREASE_OFFERED:
                    processAutoLimitIncreaseOffered(event, correlationId);
                    break;
                case LIMIT_RESTORED:
                    processLimitRestored(event, correlationId);
                    break;
                default:
                    log.warn("Unknown card limit adjustment event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logCardEvent(
                "CARD_LIMIT_ADJUSTMENT_EVENT_PROCESSED",
                event.getCardId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "userId", event.getUserId(),
                    "limitType", event.getLimitType(),
                    "oldLimit", event.getOldLimit() != null ? event.getOldLimit().toString() : "N/A",
                    "newLimit", event.getNewLimit() != null ? event.getNewLimit().toString() : "N/A",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process card limit adjustment event: {}", e.getMessage(), e);
            kafkaTemplate.send("card-limit-adjustment-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processLimitIncreaseRequested(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Limit increase requested: cardId={}, limitType={}, currentLimit={}, requestedLimit={}", 
            event.getCardId(), event.getLimitType(), event.getOldLimit(), event.getRequestedLimit());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        BigDecimal increaseAmount = event.getRequestedLimit().subtract(event.getOldLimit());
        BigDecimal increasePercentage = increaseAmount.divide(event.getOldLimit(), 4, 
            java.math.RoundingMode.HALF_UP);
        
        LimitAdjustmentRequest request = LimitAdjustmentRequest.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .limitType(event.getLimitType())
            .currentLimit(event.getOldLimit())
            .requestedLimit(event.getRequestedLimit())
            .increaseAmount(increaseAmount)
            .increasePercentage(increasePercentage)
            .requestReason(event.getRequestReason())
            .requestedAt(LocalDateTime.now())
            .status("REQUESTED")
            .requiresApproval(increaseAmount.compareTo(SIGNIFICANT_INCREASE_THRESHOLD) > 0)
            .correlationId(correlationId)
            .build();
        
        adjustmentRequestRepository.save(request);
        
        if (request.isRequiresApproval()) {
            log.info("Significant increase requires assessment: requestId={}, amount={}", 
                request.getId(), increaseAmount);
            creditAssessmentService.initiateCreditAssessment(request.getId());
        } else {
            log.info("Auto-approving small increase: requestId={}, amount={}", 
                request.getId(), increaseAmount);
            limitManagementService.autoApproveIncrease(request.getId());
        }
        
        metricsService.recordLimitIncreaseRequested(event.getLimitType(), increaseAmount);
    }
    
    private void processLimitDecreaseRequested(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Limit decrease requested: cardId={}, limitType={}, currentLimit={}, requestedLimit={}", 
            event.getCardId(), event.getLimitType(), event.getOldLimit(), event.getRequestedLimit());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        BigDecimal decreaseAmount = event.getOldLimit().subtract(event.getRequestedLimit());
        
        LimitAdjustmentRequest request = LimitAdjustmentRequest.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .limitType(event.getLimitType())
            .currentLimit(event.getOldLimit())
            .requestedLimit(event.getRequestedLimit())
            .decreaseAmount(decreaseAmount)
            .requestReason(event.getRequestReason())
            .requestedAt(LocalDateTime.now())
            .status("REQUESTED")
            .isDecrease(true)
            .correlationId(correlationId)
            .build();
        
        adjustmentRequestRepository.save(request);
        
        limitManagementService.processLimitDecrease(request.getId());
        
        metricsService.recordLimitDecreaseRequested(event.getLimitType(), decreaseAmount);
    }
    
    private void processCreditAssessmentInitiated(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Credit assessment initiated: requestId={}, userId={}", 
            event.getRequestId(), event.getUserId());
        
        LimitAdjustmentRequest request = adjustmentRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setCreditAssessmentInitiatedAt(LocalDateTime.now());
        request.setStatus("CREDIT_ASSESSMENT");
        adjustmentRequestRepository.save(request);
        
        creditAssessmentService.performCreditCheck(
            request.getId(), 
            event.getUserId(), 
            request.getRequestedLimit()
        );
        
        metricsService.recordCreditAssessmentInitiated();
    }
    
    private void processCreditAssessmentCompleted(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Credit assessment completed: requestId={}, creditScore={}, recommendation={}", 
            event.getRequestId(), event.getCreditScore(), event.getRecommendation());
        
        LimitAdjustmentRequest request = adjustmentRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setCreditAssessmentCompletedAt(LocalDateTime.now());
        request.setCreditScore(event.getCreditScore());
        request.setCreditAssessmentResult(event.getRecommendation());
        adjustmentRequestRepository.save(request);
        
        riskEvaluationService.evaluateRisk(request.getId());
        
        metricsService.recordCreditAssessmentCompleted(event.getRecommendation());
    }
    
    private void processRiskEvaluationCompleted(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Risk evaluation completed: requestId={}, riskLevel={}, approved={}", 
            event.getRequestId(), event.getRiskLevel(), event.isApproved());
        
        LimitAdjustmentRequest request = adjustmentRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setRiskEvaluationCompletedAt(LocalDateTime.now());
        request.setRiskLevel(event.getRiskLevel());
        request.setRiskFactors(event.getRiskFactors());
        adjustmentRequestRepository.save(request);
        
        if (event.isApproved()) {
            limitManagementService.approveLimitIncrease(request.getId(), event.getApprovedLimit());
        } else {
            limitManagementService.declineLimitIncrease(request.getId(), event.getDeclineReason());
        }
        
        metricsService.recordRiskEvaluationCompleted(event.getRiskLevel(), event.isApproved());
    }
    
    private void processLimitIncreaseApproved(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Limit increase approved: requestId={}, cardId={}, newLimit={}", 
            event.getRequestId(), event.getCardId(), event.getNewLimit());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        switch (event.getLimitType()) {
            case "DAILY_SPENDING":
                card.setDailySpendingLimit(event.getNewLimit());
                break;
            case "MONTHLY_SPENDING":
                card.setMonthlySpendingLimit(event.getNewLimit());
                break;
            case "SINGLE_TRANSACTION":
                card.setSingleTransactionLimit(event.getNewLimit());
                break;
            case "DAILY_ATM":
                card.setDailyAtmLimit(event.getNewLimit());
                break;
        }
        
        card.setLastLimitAdjustment(LocalDateTime.now());
        cardRepository.save(card);
        
        LimitAdjustmentRequest request = adjustmentRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setApprovedAt(LocalDateTime.now());
        request.setApprovedLimit(event.getNewLimit());
        request.setApprovedBy(event.getApprovedBy());
        request.setStatus("APPROVED");
        adjustmentRequestRepository.save(request);
        
        BigDecimal increaseAmount = event.getNewLimit().subtract(event.getOldLimit());
        
        notificationService.sendNotification(
            event.getUserId(),
            "Limit Increase Approved",
            String.format("Great news! Your %s limit has been increased from %s to %s. " +
                "The new limit is effective immediately.",
                event.getLimitType().replace("_", " "), 
                event.getOldLimit(), event.getNewLimit()),
            correlationId
        );
        
        metricsService.recordLimitIncreaseApproved(event.getLimitType(), increaseAmount);
    }
    
    private void processLimitIncreaseDeclined(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Limit increase declined: requestId={}, reason={}", 
            event.getRequestId(), event.getDeclineReason());
        
        LimitAdjustmentRequest request = adjustmentRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setDeclinedAt(LocalDateTime.now());
        request.setDeclineReason(event.getDeclineReason());
        request.setStatus("DECLINED");
        adjustmentRequestRepository.save(request);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Limit Increase Request",
            String.format("We're unable to approve your limit increase request at this time. %s " +
                "You can reapply in %d months or contact us to discuss your options.",
                event.getDeclineReason(), LIMIT_REVIEW_FREQUENCY_MONTHS),
            correlationId
        );
        
        metricsService.recordLimitIncreaseDeclined(event.getDeclineReason());
    }
    
    private void processLimitDecreaseCompleted(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Limit decrease completed: requestId={}, cardId={}, newLimit={}", 
            event.getRequestId(), event.getCardId(), event.getNewLimit());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        switch (event.getLimitType()) {
            case "DAILY_SPENDING":
                card.setDailySpendingLimit(event.getNewLimit());
                break;
            case "MONTHLY_SPENDING":
                card.setMonthlySpendingLimit(event.getNewLimit());
                break;
            case "SINGLE_TRANSACTION":
                card.setSingleTransactionLimit(event.getNewLimit());
                break;
            case "DAILY_ATM":
                card.setDailyAtmLimit(event.getNewLimit());
                break;
        }
        
        card.setLastLimitAdjustment(LocalDateTime.now());
        cardRepository.save(card);
        
        LimitAdjustmentRequest request = adjustmentRequestRepository.findById(event.getRequestId())
            .orElseThrow();
        
        request.setCompletedAt(LocalDateTime.now());
        request.setStatus("COMPLETED");
        adjustmentRequestRepository.save(request);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Limit Decreased",
            String.format("Your %s limit has been decreased to %s as requested. " +
                "The new limit is effective immediately.",
                event.getLimitType().replace("_", " "), event.getNewLimit()),
            correlationId
        );
        
        metricsService.recordLimitDecreaseCompleted(event.getLimitType());
    }
    
    private void processTemporaryLimitIncreaseGranted(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Temporary limit increase granted: cardId={}, tempLimit={}, duration={} days", 
            event.getCardId(), event.getTemporaryLimit(), event.getTempLimitDurationDays());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setTemporaryLimitActive(true);
        card.setTemporaryLimit(event.getTemporaryLimit());
        card.setTemporaryLimitStartDate(LocalDateTime.now());
        card.setTemporaryLimitEndDate(LocalDateTime.now().plusDays(event.getTempLimitDurationDays()));
        card.setTemporaryLimitReason(event.getTempLimitReason());
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Temporary Limit Increase",
            String.format("A temporary %s limit of %s has been granted for %d days. Reason: %s. " +
                "Your regular limit will be restored on %s.",
                event.getLimitType().replace("_", " "), 
                event.getTemporaryLimit(), 
                event.getTempLimitDurationDays(),
                event.getTempLimitReason(),
                card.getTemporaryLimitEndDate().toLocalDate()),
            correlationId
        );
        
        metricsService.recordTemporaryLimitIncreaseGranted(event.getTempLimitReason(), event.getTempLimitDurationDays());
    }
    
    private void processTemporaryLimitExpired(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Temporary limit expired: cardId={}, restoredLimit={}", 
            event.getCardId(), event.getRestoredLimit());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        card.setTemporaryLimitActive(false);
        card.setTemporaryLimit(null);
        card.setTemporaryLimitEndDate(null);
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Temporary Limit Expired",
            String.format("Your temporary limit has expired. Your %s limit has been restored to %s.",
                event.getLimitType().replace("_", " "), event.getRestoredLimit()),
            correlationId
        );
        
        metricsService.recordTemporaryLimitExpired();
    }
    
    private void processAutoLimitIncreaseOffered(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Auto limit increase offered: userId={}, cardId={}, currentLimit={}, offeredLimit={}", 
            event.getUserId(), event.getCardId(), event.getOldLimit(), event.getOfferedLimit());
        
        LimitAdjustmentRequest offer = LimitAdjustmentRequest.builder()
            .id(UUID.randomUUID().toString())
            .cardId(event.getCardId())
            .userId(event.getUserId())
            .limitType(event.getLimitType())
            .currentLimit(event.getOldLimit())
            .requestedLimit(event.getOfferedLimit())
            .increaseAmount(event.getOfferedLimit().subtract(event.getOldLimit()))
            .requestReason("AUTO_OFFER_BASED_ON_USAGE")
            .requestedAt(LocalDateTime.now())
            .status("OFFERED")
            .isAutoOffer(true)
            .offerExpiresAt(LocalDateTime.now().plusDays(30))
            .correlationId(correlationId)
            .build();
        
        adjustmentRequestRepository.save(offer);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Limit Increase Offer",
            String.format("Based on your excellent payment history and usage, we'd like to offer you " +
                "a %s limit increase from %s to %s. " +
                "Accept this offer in the app within 30 days. No credit check required!",
                event.getLimitType().replace("_", " "), 
                event.getOldLimit(), event.getOfferedLimit()),
            correlationId
        );
        
        metricsService.recordAutoLimitIncreaseOffered(event.getLimitType(), event.getOfferedLimit());
    }
    
    private void processLimitRestored(CardLimitAdjustmentEvent event, String correlationId) {
        log.info("Limit restored: cardId={}, restoredLimit={}, reason={}", 
            event.getCardId(), event.getRestoredLimit(), event.getRestoreReason());
        
        Card card = cardRepository.findById(event.getCardId())
            .orElseThrow();
        
        switch (event.getLimitType()) {
            case "DAILY_SPENDING":
                card.setDailySpendingLimit(event.getRestoredLimit());
                break;
            case "MONTHLY_SPENDING":
                card.setMonthlySpendingLimit(event.getRestoredLimit());
                break;
            case "SINGLE_TRANSACTION":
                card.setSingleTransactionLimit(event.getRestoredLimit());
                break;
            case "DAILY_ATM":
                card.setDailyAtmLimit(event.getRestoredLimit());
                break;
        }
        
        card.setLastLimitAdjustment(LocalDateTime.now());
        cardRepository.save(card);
        
        notificationService.sendNotification(
            event.getUserId(),
            "Limit Restored",
            String.format("Your %s limit has been restored to %s. Reason: %s",
                event.getLimitType().replace("_", " "), 
                event.getRestoredLimit(), event.getRestoreReason()),
            correlationId
        );
        
        metricsService.recordLimitRestored(event.getRestoreReason());
    }
}