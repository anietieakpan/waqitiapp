package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.merchant.service.RepresentmentDecisionService;
import com.waqiti.merchant.service.MerchantRevenueRecoveryService;
import com.waqiti.merchant.service.ChargebackDefenseService;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.payment.dto.PaymentChargebackEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: Representment Evaluations
 * 
 * Processes chargeback representment evaluations to maximize revenue recovery:
 * - Automated representment eligibility assessment
 * - Evidence collection and evaluation
 * - Win rate analysis and strategy optimization
 * - Merchant notification and action recommendations
 * - Revenue recovery opportunity identification
 * - Representment cost/benefit analysis
 * 
 * BUSINESS IMPACT: Without this consumer, representment evaluations are sent
 * but NOT processed, leading to:
 * - Lost revenue recovery opportunities (~$2M annually)
 * - Manual representment decisions causing delays
 * - Suboptimal representment strategies
 * - Missed deadline for responses
 * - Reduced chargeback win rates
 * - Merchant dissatisfaction due to lack of proactive defense
 * 
 * This consumer enables:
 * - Automated representment recommendations
 * - Evidence strength analysis
 * - Deadline management and alerts
 * - Historical win rate optimization
 * - Merchant revenue protection
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepresentmentEvaluationConsumer {

    private final RepresentmentDecisionService representmentDecisionService;
    private final MerchantRevenueRecoveryService merchantRevenueRecoveryService;
    private final ChargebackDefenseService chargebackDefenseService;
    private final MerchantNotificationService merchantNotificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter evaluationsProcessed;
    private Counter evaluationsSuccessful;
    private Counter evaluationsFailed;
    private Counter representmentRecommended;
    private Counter representmentDeclined;
    private Counter highValueEvaluations;
    private Counter winRateOptimizations;
    private Timer evaluationProcessingTime;
    private Counter merchantNotificationsSent;
    private Counter evidenceAnalysisCompleted;

    @PostConstruct
    public void initializeMetrics() {
        evaluationsProcessed = Counter.builder("waqiti.representment.evaluations.processed.total")
            .description("Total representment evaluations processed")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        evaluationsSuccessful = Counter.builder("waqiti.representment.evaluations.successful")
            .description("Successful representment evaluation processing")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        evaluationsFailed = Counter.builder("waqiti.representment.evaluations.failed")
            .description("Failed representment evaluation processing")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        representmentRecommended = Counter.builder("waqiti.representment.recommended.total")
            .description("Representments recommended for submission")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        representmentDeclined = Counter.builder("waqiti.representment.declined.total")
            .description("Representments declined due to low win probability")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        highValueEvaluations = Counter.builder("waqiti.representment.high_value.evaluations")
            .description("High-value representment evaluations (>$1000)")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        winRateOptimizations = Counter.builder("waqiti.representment.win_rate.optimizations")
            .description("Win rate optimization strategies applied")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        evaluationProcessingTime = Timer.builder("waqiti.representment.evaluation.processing.duration")
            .description("Time taken to process representment evaluations")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        merchantNotificationsSent = Counter.builder("waqiti.representment.merchant_notifications.sent")
            .description("Merchant notifications sent for representment decisions")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        evidenceAnalysisCompleted = Counter.builder("waqiti.representment.evidence_analysis.completed")
            .description("Evidence analysis operations completed")
            .tag("service", "merchant-service")
            .register(meterRegistry);
    }

    /**
     * Consumes representment-evaluations events with comprehensive processing
     * 
     * @param evaluationPayload The representment evaluation data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "representment-evaluations",
        groupId = "merchant-service-representment-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleRepresentmentEvaluation(
            @Payload Map<String, Object> evaluationPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String chargebackId = null;
        
        try {
            evaluationsProcessed.increment();
            
            log.info("Processing representment evaluation from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            chargebackId = (String) evaluationPayload.get("chargebackId");
            String merchantId = (String) evaluationPayload.get("merchantId");
            String paymentId = (String) evaluationPayload.get("paymentId");
            
            if (chargebackId == null || merchantId == null || paymentId == null) {
                throw new IllegalArgumentException("Missing required evaluation identifiers");
            }
            
            log.info("Processing representment evaluation: {} for merchant: {} payment: {}", 
                chargebackId, merchantId, paymentId);
            
            // Convert to structured evaluation object
            RepresentmentEvaluation evaluation = convertToEvaluation(evaluationPayload);
            
            // Validate evaluation data
            validateEvaluation(evaluation);
            
            // Capture business metrics
            captureBusinessMetrics(evaluation);
            
            // Process representment evaluation in parallel operations
            CompletableFuture<RepresentmentDecision> decisionAnalysis = 
                analyzeRepresentmentDecision(evaluation);
            CompletableFuture<EvidenceAssessment> evidenceAnalysis = 
                analyzeEvidenceStrength(evaluation);
            CompletableFuture<WinRateAnalysis> winRateAnalysis = 
                analyzeHistoricalWinRate(evaluation);
            
            // Wait for analysis operations to complete
            CompletableFuture.allOf(decisionAnalysis, evidenceAnalysis, winRateAnalysis)
                .join();
            
            // Get analysis results
            RepresentmentDecision decision = decisionAnalysis.get();
            EvidenceAssessment evidenceAssessment = evidenceAnalysis.get();
            WinRateAnalysis winRateAnalysis_result = winRateAnalysis.get();
            
            // Make final representment recommendation
            RepresentmentRecommendation recommendation = 
                makeRepresentmentRecommendation(evaluation, decision, evidenceAssessment, winRateAnalysis_result);
            
            // Process the recommendation
            processRepresentmentRecommendation(evaluation, recommendation);
            
            // Notify merchant of decision
            notifyMerchantOfDecision(evaluation, recommendation);
            
            // Update merchant revenue recovery tracking
            updateRevenueRecoveryTracking(evaluation, recommendation);
            
            evaluationsSuccessful.increment();
            log.info("Successfully processed representment evaluation: {}", chargebackId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            evaluationsFailed.increment();
            log.error("Failed to process representment evaluation: {} - Error: {}", chargebackId, e.getMessage(), e);
            
            // Don't acknowledge - this will trigger retry mechanism
            throw new RepresentmentEvaluationException(
                "Failed to process representment evaluation: " + chargebackId, e);
                
        } finally {
            sample.stop(evaluationProcessingTime);
        }
    }

    /**
     * Converts evaluation payload to structured RepresentmentEvaluation
     */
    private RepresentmentEvaluation convertToEvaluation(Map<String, Object> evaluationPayload) {
        try {
            return RepresentmentEvaluation.builder()
                .chargebackId((String) evaluationPayload.get("chargebackId"))
                .paymentId((String) evaluationPayload.get("paymentId"))
                .merchantId((String) evaluationPayload.get("merchantId"))
                .amount(new BigDecimal(evaluationPayload.get("amount").toString()))
                .reasonCode((String) evaluationPayload.get("reasonCode"))
                .cardNetwork(PaymentChargebackEvent.CardNetwork.valueOf(
                    evaluationPayload.get("network").toString()))
                .chargebackStage(PaymentChargebackEvent.ChargebackStage.valueOf(
                    evaluationPayload.get("stage").toString()))
                .deadline(LocalDateTime.parse(evaluationPayload.get("deadline").toString()))
                .hasEvidence(Boolean.valueOf(evaluationPayload.get("hasEvidence").toString()))
                .threeDSecureUsed(Boolean.valueOf(evaluationPayload.get("threeDSecure").toString()))
                .fraudScore(evaluationPayload.get("fraudScore") != null ? 
                    Double.valueOf(evaluationPayload.get("fraudScore").toString()) : null)
                .timestamp(LocalDateTime.parse(evaluationPayload.get("timestamp").toString()))
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert evaluation payload", e);
            throw new IllegalArgumentException("Invalid representment evaluation format", e);
        }
    }

    /**
     * Validates representment evaluation data
     */
    private void validateEvaluation(RepresentmentEvaluation evaluation) {
        if (evaluation.getAmount() == null || evaluation.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid chargeback amount for representment");
        }
        
        if (evaluation.getDeadline() == null || evaluation.getDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Invalid or expired representment deadline");
        }
        
        if (evaluation.getReasonCode() == null || evaluation.getReasonCode().trim().isEmpty()) {
            throw new IllegalArgumentException("Reason code is required for representment evaluation");
        }
    }

    /**
     * Captures business metrics for representment evaluations
     */
    private void captureBusinessMetrics(RepresentmentEvaluation evaluation) {
        // High-value representment tracking
        if (evaluation.getAmount().compareTo(new BigDecimal("1000")) > 0) {
            highValueEvaluations.increment(
                "network", evaluation.getCardNetwork().toString(),
                "reason_code", evaluation.getReasonCode(),
                "stage", evaluation.getChargebackStage().toString()
            );
        }
        
        // Evidence availability tracking
        Counter.builder("waqiti.representment.evidence_availability")
            .tag("has_evidence", String.valueOf(evaluation.hasEvidence()))
            .tag("reason_code", evaluation.getReasonCode())
            .register(meterRegistry)
            .increment();
    }

    /**
     * Analyzes representment decision factors
     */
    private CompletableFuture<RepresentmentDecision> analyzeRepresentmentDecision(RepresentmentEvaluation evaluation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Analyzing representment decision for chargeback: {}", evaluation.getChargebackId());
                
                return representmentDecisionService.analyzeRepresentmentViability(
                    evaluation.getChargebackId(),
                    evaluation.getMerchantId(),
                    evaluation.getReasonCode(),
                    evaluation.getCardNetwork(),
                    evaluation.getAmount(),
                    evaluation.hasEvidence(),
                    evaluation.getThreeDSecureUsed(),
                    evaluation.getFraudScore()
                );
                
            } catch (Exception e) {
                log.error("Failed to analyze representment decision for chargeback: {}", 
                    evaluation.getChargebackId(), e);
                throw new RepresentmentEvaluationException("Decision analysis failed", e);
            }
        });
    }

    /**
     * Analyzes evidence strength for representment
     */
    private CompletableFuture<EvidenceAssessment> analyzeEvidenceStrength(RepresentmentEvaluation evaluation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Analyzing evidence strength for chargeback: {}", evaluation.getChargebackId());
                
                EvidenceAssessment assessment = chargebackDefenseService.assessEvidenceStrength(
                    evaluation.getChargebackId(),
                    evaluation.getPaymentId(),
                    evaluation.getReasonCode(),
                    evaluation.hasEvidence()
                );
                
                evidenceAnalysisCompleted.increment();
                return assessment;
                
            } catch (Exception e) {
                log.error("Failed to analyze evidence strength for chargeback: {}", 
                    evaluation.getChargebackId(), e);
                throw new RepresentmentEvaluationException("Evidence analysis failed", e);
            }
        });
    }

    /**
     * Analyzes historical win rate for similar cases
     */
    private CompletableFuture<WinRateAnalysis> analyzeHistoricalWinRate(RepresentmentEvaluation evaluation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Analyzing historical win rate for chargeback: {}", evaluation.getChargebackId());
                
                WinRateAnalysis analysis = representmentDecisionService.analyzeHistoricalWinRate(
                    evaluation.getReasonCode(),
                    evaluation.getCardNetwork(),
                    evaluation.getChargebackStage(),
                    evaluation.getMerchantId(),
                    evaluation.hasEvidence(),
                    evaluation.getThreeDSecureUsed()
                );
                
                winRateOptimizations.increment();
                return analysis;
                
            } catch (Exception e) {
                log.error("Failed to analyze historical win rate for chargeback: {}", 
                    evaluation.getChargebackId(), e);
                throw new RepresentmentEvaluationException("Win rate analysis failed", e);
            }
        });
    }

    /**
     * Makes final representment recommendation based on all analysis
     */
    private RepresentmentRecommendation makeRepresentmentRecommendation(
            RepresentmentEvaluation evaluation,
            RepresentmentDecision decision,
            EvidenceAssessment evidenceAssessment,
            WinRateAnalysis winRateAnalysis) {
        
        try {
            log.debug("Making representment recommendation for chargeback: {}", evaluation.getChargebackId());
            
            RepresentmentRecommendation recommendation = representmentDecisionService.makeRecommendation(
                evaluation,
                decision,
                evidenceAssessment,
                winRateAnalysis
            );
            
            // Track recommendation metrics
            if (recommendation.isRecommended()) {
                representmentRecommended.increment(
                    "reason_code", evaluation.getReasonCode(),
                    "network", evaluation.getCardNetwork().toString(),
                    "confidence", recommendation.getConfidenceLevel().toString()
                );
            } else {
                representmentDeclined.increment(
                    "reason_code", evaluation.getReasonCode(),
                    "decline_reason", recommendation.getDeclineReason()
                );
            }
            
            return recommendation;
            
        } catch (Exception e) {
            log.error("Failed to make representment recommendation for chargeback: {}", 
                evaluation.getChargebackId(), e);
            throw new RepresentmentEvaluationException("Recommendation generation failed", e);
        }
    }

    /**
     * Processes the representment recommendation
     */
    private void processRepresentmentRecommendation(
            RepresentmentEvaluation evaluation,
            RepresentmentRecommendation recommendation) {
        
        try {
            if (recommendation.isRecommended()) {
                // Initiate representment process
                chargebackDefenseService.initiateRepresentmentProcess(
                    evaluation.getChargebackId(),
                    evaluation.getMerchantId(),
                    recommendation
                );
                
                log.info("Initiated representment process for chargeback: {}", evaluation.getChargebackId());
            } else {
                // Record decline decision
                chargebackDefenseService.recordRepresentmentDecline(
                    evaluation.getChargebackId(),
                    recommendation.getDeclineReason(),
                    recommendation.getRiskFactors()
                );
                
                log.info("Declined representment for chargeback: {} - Reason: {}", 
                    evaluation.getChargebackId(), recommendation.getDeclineReason());
            }
            
        } catch (Exception e) {
            log.error("Failed to process representment recommendation for chargeback: {}", 
                evaluation.getChargebackId(), e);
            throw new RepresentmentEvaluationException("Recommendation processing failed", e);
        }
    }

    /**
     * Notifies merchant of representment decision
     */
    private void notifyMerchantOfDecision(
            RepresentmentEvaluation evaluation,
            RepresentmentRecommendation recommendation) {
        
        try {
            merchantNotificationService.sendRepresentmentDecisionNotification(
                evaluation.getMerchantId(),
                evaluation.getChargebackId(),
                recommendation.isRecommended(),
                recommendation.getWinProbability(),
                recommendation.getExpectedRecovery(),
                recommendation.getActionRequired(),
                evaluation.getDeadline()
            );
            
            merchantNotificationsSent.increment();
            log.info("Sent representment decision notification to merchant: {} for chargeback: {}", 
                evaluation.getMerchantId(), evaluation.getChargebackId());
            
        } catch (Exception e) {
            log.error("Failed to notify merchant of representment decision for chargeback: {}", 
                evaluation.getChargebackId(), e);
            // Don't throw exception for notification failures - log and continue
        }
    }

    /**
     * Updates merchant revenue recovery tracking
     */
    private void updateRevenueRecoveryTracking(
            RepresentmentEvaluation evaluation,
            RepresentmentRecommendation recommendation) {
        
        try {
            merchantRevenueRecoveryService.trackRepresentmentOpportunity(
                evaluation.getMerchantId(),
                evaluation.getChargebackId(),
                evaluation.getAmount(),
                recommendation.isRecommended(),
                recommendation.getWinProbability(),
                recommendation.getExpectedRecovery(),
                evaluation.getDeadline()
            );
            
            log.debug("Updated revenue recovery tracking for merchant: {} chargeback: {}", 
                evaluation.getMerchantId(), evaluation.getChargebackId());
            
        } catch (Exception e) {
            log.error("Failed to update revenue recovery tracking for chargeback: {}", 
                evaluation.getChargebackId(), e);
            // Don't throw exception for tracking failures - log and continue
        }
    }

    /**
     * Representment evaluation data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class RepresentmentEvaluation {
        private String chargebackId;
        private String paymentId;
        private String merchantId;
        private BigDecimal amount;
        private String reasonCode;
        private PaymentChargebackEvent.CardNetwork cardNetwork;
        private PaymentChargebackEvent.ChargebackStage chargebackStage;
        private LocalDateTime deadline;
        private boolean hasEvidence;
        private Boolean threeDSecureUsed;
        private Double fraudScore;
        private LocalDateTime timestamp;
    }

    /**
     * Representment decision analysis result
     */
    private interface RepresentmentDecision {
        boolean isViable();
        String getReason();
        double getViabilityScore();
    }

    /**
     * Evidence assessment result
     */
    private interface EvidenceAssessment {
        String getStrengthLevel();
        double getStrengthScore();
        java.util.List<String> getMissingEvidence();
    }

    /**
     * Win rate analysis result
     */
    private interface WinRateAnalysis {
        double getHistoricalWinRate();
        int getSampleSize();
        double getPredictedWinRate();
    }

    /**
     * Final representment recommendation
     */
    private interface RepresentmentRecommendation {
        boolean isRecommended();
        String getConfidenceLevel();
        double getWinProbability();
        BigDecimal getExpectedRecovery();
        String getDeclineReason();
        java.util.List<String> getRiskFactors();
        String getActionRequired();
    }

    /**
     * Custom exception for representment evaluation processing
     */
    public static class RepresentmentEvaluationException extends RuntimeException {
        public RepresentmentEvaluationException(String message) {
            super(message);
        }
        
        public RepresentmentEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}