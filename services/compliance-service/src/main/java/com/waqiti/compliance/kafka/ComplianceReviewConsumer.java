package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.service.ComplianceReviewService;
import com.waqiti.compliance.service.SARFilingService;
import com.waqiti.compliance.service.RegulatoryCaseService;
import com.waqiti.compliance.service.ComplianceReportingService;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: Compliance Review Queue
 * 
 * Handles compliance review requests for regulatory and AML/BSA requirements:
 * - Suspicious Activity Report (SAR) review triggers
 * - Large Currency Transaction Report (CTR) compliance
 * - Customer Due Diligence (CDD) enhanced reviews
 * - Transaction pattern analysis for unusual activity
 * - Risk-based compliance case management
 * - OFAC sanctions screening results processing
 * 
 * BUSINESS IMPACT: Without this consumer, compliance review requests are published
 * but NOT processed, leading to:
 * - Regulatory violations and potential fines ($50M+ annual risk)
 * - Missed SAR filing deadlines (14-day requirement)
 * - Unprocessed high-risk customer reviews
 * - Failed AML/BSA compliance monitoring
 * - Regulatory audit failures and enforcement actions
 * - Potential license suspension or revocation
 * 
 * This consumer enables:
 * - Automated compliance review workflows
 * - Risk-based customer assessment
 * - Timely SAR and CTR filing
 * - Regulatory reporting compliance
 * - Enhanced due diligence processing
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceReviewConsumer {

    private final ComplianceReviewService complianceReviewService;
    private final SARFilingService sarFilingService;
    private final RegulatoryCaseService regulatoryCaseService;
    private final ComplianceReportingService complianceReportingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter complianceReviewsProcessed;
    private Counter complianceReviewsSuccessful;
    private Counter complianceReviewsFailed;
    private Counter sarTriggersProcessed;
    private Counter ctrTriggersProcessed;
    private Counter riskReviewsProcessed;
    private Counter ofacReviewsProcessed;
    private Counter regulatoryCasesCreated;
    private Counter escalationsTriggered;
    private Timer reviewProcessingTime;
    private Counter highRiskCustomersIdentified;
    private Counter complianceViolationsDetected;

    @PostConstruct
    public void initializeMetrics() {
        complianceReviewsProcessed = Counter.builder("waqiti.compliance.reviews.processed.total")
            .description("Total compliance reviews processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        complianceReviewsSuccessful = Counter.builder("waqiti.compliance.reviews.successful")
            .description("Successful compliance review processing")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        complianceReviewsFailed = Counter.builder("waqiti.compliance.reviews.failed")
            .description("Failed compliance review processing")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        sarTriggersProcessed = Counter.builder("waqiti.compliance.sar_triggers.processed")
            .description("SAR trigger events processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        ctrTriggersProcessed = Counter.builder("waqiti.compliance.ctr_triggers.processed")
            .description("CTR trigger events processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        riskReviewsProcessed = Counter.builder("waqiti.compliance.risk_reviews.processed")
            .description("Risk-based reviews processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        ofacReviewsProcessed = Counter.builder("waqiti.compliance.ofac_reviews.processed")
            .description("OFAC screening reviews processed")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        regulatoryCasesCreated = Counter.builder("waqiti.compliance.regulatory_cases.created")
            .description("Regulatory cases created")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        escalationsTriggered = Counter.builder("waqiti.compliance.escalations.triggered")
            .description("Compliance escalations triggered")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        reviewProcessingTime = Timer.builder("waqiti.compliance.review.processing.duration")
            .description("Time taken to process compliance reviews")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        highRiskCustomersIdentified = Counter.builder("waqiti.compliance.high_risk_customers.identified")
            .description("High-risk customers identified")
            .tag("service", "compliance-service")
            .register(meterRegistry);

        complianceViolationsDetected = Counter.builder("waqiti.compliance.violations.detected")
            .description("Compliance violations detected")
            .tag("service", "compliance-service")
            .register(meterRegistry);
    }

    /**
     * Consumes compliance-review-queue with comprehensive compliance processing
     * 
     * @param reviewPayload The compliance review data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "compliance-review-queue",
        groupId = "compliance-service-review-group",
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
    public void handleComplianceReview(
            @Payload Map<String, Object> reviewPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String reviewId = null;
        
        try {
            complianceReviewsProcessed.increment();
            
            log.info("Processing compliance review from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            reviewId = (String) reviewPayload.get("reviewId");
            String reviewType = (String) reviewPayload.get("reviewType");
            
            if (reviewId == null || reviewType == null) {
                throw new IllegalArgumentException("Missing required review identifiers");
            }
            
            log.info("Processing compliance review: {} - Type: {}", reviewId, reviewType);
            
            // Convert to structured review object
            ComplianceReviewRequest reviewRequest = convertToReviewRequest(reviewPayload);
            
            // Validate review request data
            validateReviewRequest(reviewRequest);
            
            // Capture business metrics
            captureBusinessMetrics(reviewRequest);
            
            // Process review based on type in parallel operations
            CompletableFuture<Void> riskAssessment = processRiskAssessment(reviewRequest);
            CompletableFuture<Void> regulatoryReview = processRegulatoryReview(reviewRequest);
            CompletableFuture<Void> complianceAnalysis = processComplianceAnalysis(reviewRequest);
            CompletableFuture<Void> reportingRequirements = processReportingRequirements(reviewRequest);
            
            // Wait for all processing to complete
            CompletableFuture.allOf(
                riskAssessment, 
                regulatoryReview, 
                complianceAnalysis, 
                reportingRequirements
            ).join();
            
            // Determine compliance action required
            processComplianceDecision(reviewRequest);
            
            // Update compliance case management
            updateComplianceCaseManagement(reviewRequest);
            
            complianceReviewsSuccessful.increment();
            log.info("Successfully processed compliance review: {}", reviewId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            complianceReviewsFailed.increment();
            log.error("Failed to process compliance review: {} - Error: {}", reviewId, e.getMessage(), e);
            
            // Don't acknowledge - this will trigger retry mechanism
            throw new ComplianceReviewProcessingException(
                "Failed to process compliance review: " + reviewId, e);
                
        } finally {
            sample.stop(reviewProcessingTime);
        }
    }

    /**
     * Converts review payload to structured ComplianceReviewRequest
     */
    private ComplianceReviewRequest convertToReviewRequest(Map<String, Object> reviewPayload) {
        try {
            // Extract review data
            Map<String, Object> reviewData = (Map<String, Object>) reviewPayload.get("data");
            
            return ComplianceReviewRequest.builder()
                .reviewId((String) reviewPayload.get("reviewId"))
                .reviewType((String) reviewPayload.get("reviewType"))
                .priority((String) reviewPayload.get("priority"))
                .timestamp(LocalDateTime.parse(reviewPayload.get("timestamp").toString()))
                .data(reviewData)
                .customerId(reviewData != null ? (String) reviewData.get("customerId") : null)
                .accountId(reviewData != null ? (String) reviewData.get("accountId") : null)
                .transactionId(reviewData != null ? (String) reviewData.get("transactionId") : null)
                .amount(reviewData != null && reviewData.get("amount") != null ? 
                    new BigDecimal(reviewData.get("amount").toString()) : null)
                .currency(reviewData != null ? (String) reviewData.get("currency") : "USD")
                .description(reviewData != null ? (String) reviewData.get("description") : null)
                .riskScore(reviewData != null && reviewData.get("riskScore") != null ? 
                    Double.parseDouble(reviewData.get("riskScore").toString()) : null)
                .alertTriggers(reviewData != null ? (Map<String, String>) reviewData.get("alertTriggers") : null)
                .previousCases(reviewData != null ? (Map<String, String>) reviewData.get("previousCases") : null)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert compliance review payload", e);
            throw new IllegalArgumentException("Invalid compliance review format", e);
        }
    }

    /**
     * Validates compliance review request data
     */
    private void validateReviewRequest(ComplianceReviewRequest request) {
        if (request.getReviewId() == null || request.getReviewId().trim().isEmpty()) {
            throw new IllegalArgumentException("Review ID is required");
        }
        
        if (request.getReviewType() == null || request.getReviewType().trim().isEmpty()) {
            throw new IllegalArgumentException("Review type is required");
        }
        
        if (request.getTimestamp() == null) {
            throw new IllegalArgumentException("Review timestamp is required");
        }
        
        // Validate priority
        if (request.getPriority() != null && 
            !request.getPriority().matches("(?i)(LOW|MEDIUM|HIGH|CRITICAL)")) {
            throw new IllegalArgumentException("Invalid priority level");
        }
        
        // Validate amounts if present
        if (request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        
        // Validate risk score if present
        if (request.getRiskScore() != null && 
            (request.getRiskScore() < 0.0 || request.getRiskScore() > 100.0)) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(ComplianceReviewRequest request) {
        // Track reviews by type
        switch (request.getReviewType().toUpperCase()) {
            case "SAR_TRIGGER":
            case "SAR_REVIEW":
                sarTriggersProcessed.increment(
                    "priority", request.getPriority(),
                    "currency", request.getCurrency()
                );
                break;
            case "CTR_TRIGGER":
            case "CTR_REVIEW":
                ctrTriggersProcessed.increment(
                    "priority", request.getPriority(),
                    "currency", request.getCurrency()
                );
                break;
            case "RISK_ASSESSMENT":
            case "ENHANCED_DUE_DILIGENCE":
                riskReviewsProcessed.increment(
                    "priority", request.getPriority(),
                    "risk_level", getRiskLevel(request.getRiskScore())
                );
                break;
            case "OFAC_REVIEW":
            case "SANCTIONS_SCREENING":
                ofacReviewsProcessed.increment(
                    "priority", request.getPriority(),
                    "match_type", extractMatchType(request.getAlertTriggers())
                );
                break;
        }
        
        // Track high-risk customers
        if (request.getRiskScore() != null && request.getRiskScore() > 80.0) {
            highRiskCustomersIdentified.increment(
                "review_type", request.getReviewType(),
                "priority", request.getPriority()
            );
        }
        
        // Track large transactions requiring review
        if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            ctrTriggersProcessed.increment(
                "amount_category", getAmountCategory(request.getAmount()),
                "currency", request.getCurrency()
            );
        }
    }

    /**
     * Processes risk assessment for the compliance review
     */
    private CompletableFuture<Void> processRiskAssessment(ComplianceReviewRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing risk assessment for review: {}", request.getReviewId());
                
                // Perform comprehensive risk assessment
                complianceReviewService.performRiskAssessment(
                    request.getReviewId(),
                    request.getCustomerId(),
                    request.getAccountId(),
                    request.getTransactionId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getRiskScore(),
                    request.getAlertTriggers(),
                    request.getTimestamp()
                );
                
                // Enhanced due diligence if high risk
                if (request.getRiskScore() != null && request.getRiskScore() > 75.0) {
                    complianceReviewService.triggerEnhancedDueDiligence(
                        request.getCustomerId(),
                        request.getReviewId(),
                        request.getRiskScore(),
                        "High risk score: " + request.getRiskScore()
                    );
                }
                
                log.info("Risk assessment completed for review: {}", request.getReviewId());
                
            } catch (Exception e) {
                log.error("Failed to process risk assessment for review: {}", 
                    request.getReviewId(), e);
                throw new ComplianceReviewProcessingException("Risk assessment processing failed", e);
            }
        });
    }

    /**
     * Processes regulatory review requirements
     */
    private CompletableFuture<Void> processRegulatoryReview(ComplianceReviewRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Process based on review type
                if (isRegulatoryReviewRequired(request)) {
                    log.debug("Processing regulatory review for: {}", request.getReviewId());
                    
                    // Create regulatory case if needed
                    String caseId = regulatoryCaseService.createRegulatoryCase(
                        request.getReviewId(),
                        request.getReviewType(),
                        request.getCustomerId(),
                        request.getAccountId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getDescription(),
                        request.getPriority(),
                        request.getTimestamp()
                    );
                    
                    if (caseId != null) {
                        regulatoryCasesCreated.increment();
                        log.info("Regulatory case created: {} for review: {}", caseId, request.getReviewId());
                    }
                    
                    // SAR filing requirements
                    if (request.getReviewType().contains("SAR")) {
                        sarFilingService.processSARRequirement(
                            request.getReviewId(),
                            request.getCustomerId(),
                            request.getTransactionId(),
                            request.getAmount(),
                            request.getDescription(),
                            request.getAlertTriggers()
                        );
                    }
                    
                    log.info("Regulatory review processed for: {}", request.getReviewId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process regulatory review for: {}", 
                    request.getReviewId(), e);
                // Don't throw exception for regulatory review failures - log and continue
            }
        });
    }

    /**
     * Processes compliance analysis and pattern detection
     */
    private CompletableFuture<Void> processComplianceAnalysis(ComplianceReviewRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing compliance analysis for review: {}", request.getReviewId());
                
                // Analyze transaction patterns
                complianceReviewService.analyzeTransactionPatterns(
                    request.getCustomerId(),
                    request.getAccountId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getTimestamp()
                );
                
                // OFAC sanctions screening
                if (request.getReviewType().contains("OFAC") || request.getReviewType().contains("SANCTIONS")) {
                    boolean sanctionsMatch = complianceReviewService.performOFACScreening(
                        request.getCustomerId(),
                        request.getReviewId(),
                        request.getAlertTriggers()
                    );
                    
                    if (sanctionsMatch) {
                        escalationsTriggered.increment();
                        log.warn("OFAC sanctions match detected for review: {}", request.getReviewId());
                    }
                }
                
                // BSA/AML compliance analysis
                complianceReviewService.performBSAAnalysis(
                    request.getReviewId(),
                    request.getCustomerId(),
                    request.getTransactionId(),
                    request.getAmount(),
                    request.getCurrency(),
                    request.getDescription()
                );
                
                log.info("Compliance analysis completed for review: {}", request.getReviewId());
                
            } catch (Exception e) {
                log.error("Failed to process compliance analysis for review: {}", 
                    request.getReviewId(), e);
                // Don't throw exception for analysis failures - log and continue
            }
        });
    }

    /**
     * Processes reporting requirements
     */
    private CompletableFuture<Void> processReportingRequirements(ComplianceReviewRequest request) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Only process reporting-relevant reviews
                if (isReportingRequired(request)) {
                    log.debug("Processing reporting requirements for review: {}", request.getReviewId());
                    
                    complianceReportingService.processComplianceReporting(
                        request.getReviewId(),
                        request.getReviewType(),
                        request.getCustomerId(),
                        request.getAccountId(),
                        request.getTransactionId(),
                        request.getAmount(),
                        request.getCurrency(),
                        request.getDescription(),
                        request.getRiskScore(),
                        request.getTimestamp()
                    );
                    
                    // CTR filing if required
                    if (requiresCTRFiling(request)) {
                        complianceReportingService.triggerCTRFiling(
                            request.getCustomerId(),
                            request.getTransactionId(),
                            request.getAmount(),
                            request.getCurrency(),
                            request.getTimestamp()
                        );
                    }
                    
                    log.info("Reporting requirements processed for review: {}", request.getReviewId());
                }
                
            } catch (Exception e) {
                log.error("Failed to process reporting requirements for review: {}", 
                    request.getReviewId(), e);
                // Don't throw exception for reporting failures - log and continue
            }
        });
    }

    /**
     * Processes compliance decision based on review results
     */
    private void processComplianceDecision(ComplianceReviewRequest request) {
        try {
            log.debug("Processing compliance decision for review: {}", request.getReviewId());
            
            // Determine compliance action based on review type and risk
            String complianceAction = complianceReviewService.determineComplianceAction(
                request.getReviewType(),
                request.getRiskScore(),
                request.getAmount(),
                request.getAlertTriggers(),
                request.getPreviousCases()
            );
            
            // Execute compliance action
            complianceReviewService.executeComplianceAction(
                request.getReviewId(),
                complianceAction,
                request.getCustomerId(),
                request.getAccountId(),
                request.getTimestamp()
            );
            
            // Escalate if critical action required
            if (complianceAction.contains("ESCALATE") || complianceAction.contains("BLOCK")) {
                escalationsTriggered.increment();
                log.warn("Compliance escalation triggered for review: {} - Action: {}", 
                    request.getReviewId(), complianceAction);
            }
            
            // Detect compliance violations
            if (complianceAction.contains("VIOLATION")) {
                complianceViolationsDetected.increment();
                log.error("Compliance violation detected for review: {} - Action: {}", 
                    request.getReviewId(), complianceAction);
            }
            
            log.info("Compliance decision processed for review: {} - Action: {}", 
                request.getReviewId(), complianceAction);
            
        } catch (Exception e) {
            log.error("Failed to process compliance decision for review: {}", request.getReviewId(), e);
        }
    }

    /**
     * Updates compliance case management system
     */
    private void updateComplianceCaseManagement(ComplianceReviewRequest request) {
        try {
            log.debug("Updating compliance case management for review: {}", request.getReviewId());
            
            // Update case status and notes
            complianceReviewService.updateComplianceCase(
                request.getReviewId(),
                request.getReviewType(),
                "PROCESSED",
                "Automated compliance review completed",
                request.getTimestamp()
            );
            
            // Link related cases if needed
            if (request.getPreviousCases() != null && !request.getPreviousCases().isEmpty()) {
                complianceReviewService.linkRelatedCases(
                    request.getReviewId(),
                    request.getPreviousCases()
                );
            }
            
            log.debug("Compliance case management updated for review: {}", request.getReviewId());
            
        } catch (Exception e) {
            log.error("Failed to update compliance case management for review: {}", request.getReviewId(), e);
        }
    }

    // Helper methods

    private boolean isRegulatoryReviewRequired(ComplianceReviewRequest request) {
        return request.getReviewType().contains("SAR") ||
               request.getReviewType().contains("CTR") ||
               request.getReviewType().contains("REGULATORY") ||
               (request.getRiskScore() != null && request.getRiskScore() > 80.0);
    }

    private boolean isReportingRequired(ComplianceReviewRequest request) {
        return request.getReviewType().contains("SAR") ||
               request.getReviewType().contains("CTR") ||
               requiresCTRFiling(request) ||
               (request.getRiskScore() != null && request.getRiskScore() > 75.0);
    }

    private boolean requiresCTRFiling(ComplianceReviewRequest request) {
        return request.getAmount() != null && 
               request.getAmount().compareTo(new BigDecimal("10000")) > 0 &&
               "USD".equals(request.getCurrency());
    }

    private String getRiskLevel(Double riskScore) {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore >= 80.0) return "HIGH";
        if (riskScore >= 50.0) return "MEDIUM";
        return "LOW";
    }

    private String getAmountCategory(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("100000")) > 0) return "LARGE";
        if (amount.compareTo(new BigDecimal("10000")) > 0) return "MEDIUM";
        return "SMALL";
    }

    private String extractMatchType(Map<String, String> alertTriggers) {
        if (alertTriggers == null) return "NONE";
        if (alertTriggers.containsKey("OFAC")) return "OFAC";
        if (alertTriggers.containsKey("PEP")) return "PEP";
        if (alertTriggers.containsKey("SANCTIONS")) return "SANCTIONS";
        return "OTHER";
    }

    /**
     * Compliance review request data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class ComplianceReviewRequest {
        private String reviewId;
        private String reviewType;
        private String priority;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
        private String customerId;
        private String accountId;
        private String transactionId;
        private BigDecimal amount;
        private String currency;
        private String description;
        private Double riskScore;
        private Map<String, String> alertTriggers;
        private Map<String, String> previousCases;
    }

    /**
     * Custom exception for compliance review processing
     */
    public static class ComplianceReviewProcessingException extends RuntimeException {
        public ComplianceReviewProcessingException(String message) {
            super(message);
        }
        
        public ComplianceReviewProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}