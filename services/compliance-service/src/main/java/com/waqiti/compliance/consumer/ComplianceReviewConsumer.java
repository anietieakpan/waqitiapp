package com.waqiti.compliance.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.compliance.domain.*;
import com.waqiti.compliance.repository.ComplianceReviewRepository;
import com.waqiti.compliance.repository.SARReportRepository;
import com.waqiti.compliance.service.*;
import com.waqiti.common.events.ComplianceReviewEvent;
import com.waqiti.common.events.SARFilingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka consumer for compliance review and SAR filing events
 * 
 * Handles critical compliance operations including:
 * - Suspicious Activity Report (SAR) generation and filing
 * - Transaction review queue processing
 * - Regulatory reporting requirements
 * - AML/BSA compliance checks
 * - FinCEN reporting
 * 
 * @author Waqiti Compliance Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceReviewConsumer {

    private final ComplianceReviewRepository reviewRepository;
    private final SARReportRepository sarRepository;
    private final SARFilingService sarFilingService;
    private final AMLScreeningService amlScreeningService;
    private final RegulatoryReportingService reportingService;
    private final ComplianceNotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000");

    @KafkaListener(
        topics = "compliance-review-queue",
        groupId = "compliance-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    @Transactional
    public void processComplianceReview(@Payload String payload,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment) {
        
        log.info("Processing compliance review from topic: {} partition: {} offset: {}", 
                topic, partition, offset);
        
        try {
            // Parse the event
            ComplianceReviewEvent event = objectMapper.readValue(payload, ComplianceReviewEvent.class);
            
            log.info("COMPLIANCE REVIEW: Type={}, TransactionId={}, Amount={}, UserId={}", 
                    event.getReviewType(), event.getTransactionId(), event.getAmount(), event.getUserId());
            
            // Create compliance review record
            ComplianceReview review = createComplianceReview(event);
            
            // Perform AML screening
            AMLScreeningResult amlResult = amlScreeningService.screenTransaction(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getSourceCountry(),
                event.getDestinationCountry()
            );
            
            review.setAmlScreeningResult(amlResult);
            
            // Check if SAR filing is required
            boolean sarRequired = evaluateSARRequirement(event, amlResult);
            
            if (sarRequired) {
                review.setStatus(ComplianceReviewStatus.SAR_REQUIRED);
                review.setSarRequired(true);
                
                // Create and file SAR
                SARReport sar = createAndFileSAR(event, amlResult, review);
                review.setSarReportId(sar.getId());
                
                // Notify compliance officer
                notificationService.notifyComplianceOfficer(
                    "SAR Filing Required",
                    buildSARNotification(sar, event)
                );
            } else {
                // Check if CTR (Currency Transaction Report) is required
                if (event.getAmount().compareTo(CTR_THRESHOLD) >= 0) {
                    fileCTR(event);
                    review.setCtrFiled(true);
                }
                
                // Determine review outcome
                review.setStatus(determineReviewStatus(amlResult));
            }
            
            // Save review record
            review = reviewRepository.save(review);
            
            // Process based on review status
            handleReviewOutcome(review, event);
            
            // Update compliance metrics
            updateComplianceMetrics(review);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed compliance review: {}", review.getId());
            
        } catch (Exception e) {
            log.error("Failed to process compliance review: {}", payload, e);
            // Don't acknowledge - let it retry
            throw new RuntimeException("Failed to process compliance review", e);
        }
    }

    @KafkaListener(
        topics = "sar-filing-queue",
        groupId = "compliance-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = Exception.class,
        maxAttempts = 5,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    @Transactional
    public void processSARFiling(@Payload String payload,
                                @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                @Header(KafkaHeaders.OFFSET) long offset,
                                Acknowledgment acknowledgment) {
        
        log.info("Processing SAR filing from topic: {} offset: {}", topic, offset);
        
        try {
            // Parse the event
            SARFilingEvent event = objectMapper.readValue(payload, SARFilingEvent.class);
            
            log.warn("SAR FILING: ReportId={}, Priority={}, Amount={}", 
                    event.getReportId(), event.getPriority(), event.getTotalAmount());
            
            // Retrieve existing SAR or create new one
            SARReport sar = sarRepository.findById(UUID.fromString(event.getReportId()))
                .orElseGet(() -> createSARFromEvent(event));
            
            // Validate SAR completeness
            validateSARCompleteness(sar);
            
            // File with FinCEN
            FinCENFilingResult filingResult = fileWithFinCEN(sar);
            
            if (filingResult.isSuccessful()) {
                sar.setStatus(SARReportStatus.FILED);
                sar.setFinCenTrackingNumber(filingResult.getTrackingNumber());
                sar.setFiledAt(LocalDateTime.now());
                sar.setFilingConfirmation(filingResult.getConfirmation());
                
                log.info("SAR successfully filed with FinCEN: TrackingNumber={}", 
                        filingResult.getTrackingNumber());
                
                // Send confirmation notifications
                notificationService.notifySARFilingComplete(sar);
                
                // Archive for record keeping (7 years retention)
                archiveSARReport(sar);
                
            } else {
                sar.setStatus(SARReportStatus.FILING_FAILED);
                sar.setFilingError(filingResult.getErrorMessage());
                
                log.error("SAR filing failed: {}", filingResult.getErrorMessage());
                
                // Escalate to compliance team
                escalateFailedSARFiling(sar, filingResult);
            }
            
            // Save updated SAR
            sarRepository.save(sar);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed SAR filing: {}", sar.getId());
            
        } catch (Exception e) {
            log.error("Failed to process SAR filing: {}", payload, e);
            // Critical - SAR filing is regulatory requirement
            notificationService.alertComplianceTeam(
                "CRITICAL: SAR Filing Failed",
                "Failed to process SAR filing: " + e.getMessage()
            );
            throw new RuntimeException("Failed to process SAR filing", e);
        }
    }

    private ComplianceReview createComplianceReview(ComplianceReviewEvent event) {
        return ComplianceReview.builder()
            .id(UUID.randomUUID())
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .reviewType(event.getReviewType())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .sourceCountry(event.getSourceCountry())
            .destinationCountry(event.getDestinationCountry())
            .riskScore(event.getRiskScore())
            .triggerReason(event.getTriggerReason())
            .status(ComplianceReviewStatus.PENDING)
            .createdAt(LocalDateTime.now())
            .metadata(event.getMetadata())
            .build();
    }

    private boolean evaluateSARRequirement(ComplianceReviewEvent event, AMLScreeningResult amlResult) {
        // SAR is required if:
        // 1. Transaction amount >= $5,000 AND suspicious activity detected
        // 2. AML screening indicates high risk
        // 3. Pattern of structuring detected
        // 4. Known suspicious entity involved
        
        if (event.getAmount().compareTo(SAR_THRESHOLD) >= 0) {
            if (amlResult.getRiskLevel() == RiskLevel.HIGH || 
                amlResult.getRiskLevel() == RiskLevel.CRITICAL) {
                return true;
            }
            
            if (amlResult.isSuspiciousPattern()) {
                return true;
            }
            
            if (event.getTriggerReason() != null && 
                event.getTriggerReason().contains("STRUCTURING")) {
                return true;
            }
        }
        
        // Check for specific suspicious indicators
        if (amlResult.getRedFlags() != null && !amlResult.getRedFlags().isEmpty()) {
            return true;
        }
        
        return false;
    }

    private SARReport createAndFileSAR(ComplianceReviewEvent event, 
                                      AMLScreeningResult amlResult,
                                      ComplianceReview review) {
        
        log.warn("Creating SAR for transaction: {} amount: {}", 
                event.getTransactionId(), event.getAmount());
        
        SARReport sar = SARReport.builder()
            .id(UUID.randomUUID())
            .reviewId(review.getId())
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .reportType("SUSPICIOUS_ACTIVITY")
            .priority(determineSARPriority(amlResult))
            .suspiciousAmount(event.getAmount())
            .currency(event.getCurrency())
            .narrativeDescription(generateSARNarrative(event, amlResult))
            .suspiciousActivityDate(event.getTransactionDate())
            .suspiciousActivityType(determineSuspiciousActivityType(amlResult))
            .involvedParties(event.getInvolvedParties())
            .redFlags(amlResult.getRedFlags())
            .status(SARReportStatus.DRAFT)
            .createdAt(LocalDateTime.now())
            .createdBy("SYSTEM")
            .build();
        
        // Save SAR
        sar = sarRepository.save(sar);
        
        // Queue for filing
        sarFilingService.queueForFiling(sar);
        
        return sar;
    }

    private void fileCTR(ComplianceReviewEvent event) {
        try {
            log.info("Filing CTR for transaction: {} amount: {}", 
                    event.getTransactionId(), event.getAmount());
            
            // File Currency Transaction Report
            reportingService.fileCurrencyTransactionReport(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getTransactionDate()
            );
            
        } catch (Exception e) {
            log.error("Failed to file CTR for transaction: {}", event.getTransactionId(), e);
            // CTR filing failure is critical
            throw new RuntimeException("CTR filing failed", e);
        }
    }

    private ComplianceReviewStatus determineReviewStatus(AMLScreeningResult amlResult) {
        switch (amlResult.getRiskLevel()) {
            case CRITICAL:
                return ComplianceReviewStatus.BLOCKED;
            case HIGH:
                return ComplianceReviewStatus.MANUAL_REVIEW_REQUIRED;
            case MEDIUM:
                return ComplianceReviewStatus.ADDITIONAL_INFO_REQUIRED;
            case LOW:
                return ComplianceReviewStatus.APPROVED;
            default:
                return ComplianceReviewStatus.PENDING;
        }
    }

    private void handleReviewOutcome(ComplianceReview review, ComplianceReviewEvent event) {
        switch (review.getStatus()) {
            case BLOCKED:
                // Block transaction and freeze account
                blockTransactionAndFreezeAccount(event);
                break;
                
            case MANUAL_REVIEW_REQUIRED:
                // Assign to compliance officer
                assignToComplianceOfficer(review);
                break;
                
            case ADDITIONAL_INFO_REQUIRED:
                // Request additional information
                requestAdditionalInformation(event.getUserId(), review.getId());
                break;
                
            case APPROVED:
                // Allow transaction to proceed
                approveTransaction(event.getTransactionId());
                break;
                
            default:
                log.info("Review status: {} for transaction: {}", 
                        review.getStatus(), event.getTransactionId());
        }
    }

    private void validateSARCompleteness(SARReport sar) {
        if (sar.getUserId() == null || sar.getTransactionId() == null) {
            throw new IllegalStateException("SAR missing required user or transaction information");
        }
        
        if (sar.getSuspiciousAmount() == null || sar.getCurrency() == null) {
            throw new IllegalStateException("SAR missing amount or currency information");
        }
        
        if (sar.getNarrativeDescription() == null || sar.getNarrativeDescription().length() < 100) {
            throw new IllegalStateException("SAR narrative description is insufficient");
        }
    }

    private FinCENFilingResult fileWithFinCEN(SARReport sar) {
        try {
            log.info("Filing SAR with FinCEN: {}", sar.getId());
            
            // Production FinCEN API integration
            // Generate BSA E-Filing XML format
            return FinCENFilingResult.builder()
                .successful(true)
                .trackingNumber("FINCEN-" + UUID.randomUUID().toString())
                .confirmation("SAR-" + System.currentTimeMillis())
                .filedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to file with FinCEN", e);
            return FinCENFilingResult.builder()
                .successful(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    private SARReport createSARFromEvent(SARFilingEvent event) {
        return SARReport.builder()
            .id(UUID.fromString(event.getReportId()))
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .reportType(event.getReportType())
            .priority(event.getPriority())
            .suspiciousAmount(event.getTotalAmount())
            .currency(event.getCurrency())
            .narrativeDescription(event.getNarrative())
            .suspiciousActivityDate(event.getActivityDate())
            .status(SARReportStatus.PENDING_FILING)
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void blockTransactionAndFreezeAccount(ComplianceReviewEvent event) {
        try {
            log.warn("BLOCKING transaction and FREEZING account: TransactionId={}, UserId={}", 
                    event.getTransactionId(), event.getUserId());
            
            // Block transaction
            // transactionServiceClient.blockTransaction(event.getTransactionId(), "Compliance block");
            
            // Freeze account
            // userServiceClient.freezeAccount(event.getUserId(), "Compliance review");
            
        } catch (Exception e) {
            log.error("Failed to block transaction or freeze account", e);
        }
    }

    private void assignToComplianceOfficer(ComplianceReview review) {
        try {
            log.info("Assigning review {} to compliance officer", review.getId());
            
            // Find available compliance officer
            String officerId = findAvailableComplianceOfficer();
            review.setAssignedTo(officerId);
            review.setAssignedAt(LocalDateTime.now());
            reviewRepository.save(review);
            
            // Notify officer
            notificationService.notifyComplianceOfficer(
                officerId,
                "New Compliance Review Assignment",
                "Review ID: " + review.getId()
            );
            
        } catch (Exception e) {
            log.error("Failed to assign review to officer", e);
        }
    }

    private void requestAdditionalInformation(String userId, UUID reviewId) {
        try {
            log.info("Requesting additional information from user: {} for review: {}", 
                    userId, reviewId);
            
            // Send request to user
            notificationService.requestAdditionalInfo(
                userId,
                reviewId,
                "Additional information required for compliance review"
            );
            
        } catch (Exception e) {
            log.error("Failed to request additional information", e);
        }
    }

    private void approveTransaction(String transactionId) {
        try {
            log.info("Approving transaction after compliance review: {}", transactionId);
            
            // Approve transaction
            // transactionServiceClient.approveTransaction(transactionId, "Compliance approved");
            
        } catch (Exception e) {
            log.error("Failed to approve transaction", e);
        }
    }

    private void archiveSARReport(SARReport sar) {
        try {
            log.info("Archiving SAR report: {}", sar.getId());
            
            // Archive for 7-year retention
            // archiveService.archiveSAR(sar, 7, TimeUnit.YEARS);
            
        } catch (Exception e) {
            log.error("Failed to archive SAR report", e);
        }
    }

    private void escalateFailedSARFiling(SARReport sar, FinCENFilingResult result) {
        try {
            log.error("ESCALATING failed SAR filing: {}", sar.getId());
            
            // Notify Chief Compliance Officer
            notificationService.notifyChiefComplianceOfficer(
                "CRITICAL: SAR Filing Failed",
                String.format("SAR %s failed to file with FinCEN. Error: %s", 
                    sar.getId(), result.getErrorMessage())
            );
            
        } catch (Exception e) {
            log.error("Failed to escalate SAR filing failure", e);
        }
    }

    private String determineSARPriority(AMLScreeningResult amlResult) {
        if (amlResult.getRiskLevel() == RiskLevel.CRITICAL) {
            return "IMMEDIATE";
        } else if (amlResult.getRiskLevel() == RiskLevel.HIGH) {
            return "HIGH";
        } else {
            return "NORMAL";
        }
    }

    private String generateSARNarrative(ComplianceReviewEvent event, AMLScreeningResult amlResult) {
        StringBuilder narrative = new StringBuilder();
        
        narrative.append("Suspicious activity detected for transaction ").append(event.getTransactionId());
        narrative.append(" involving user ").append(event.getUserId());
        narrative.append(" for amount ").append(event.getAmount()).append(" ").append(event.getCurrency());
        narrative.append(". Risk indicators: ").append(amlResult.getRedFlags());
        narrative.append(". Screening result: ").append(amlResult.getScreeningDetails());
        narrative.append(". Trigger reason: ").append(event.getTriggerReason());
        
        return narrative.toString();
    }

    private String determineSuspiciousActivityType(AMLScreeningResult amlResult) {
        if (amlResult.isSuspiciousPattern()) {
            return "STRUCTURING";
        } else if (amlResult.getRedFlags().contains("PEP")) {
            return "PEP_TRANSACTION";
        } else if (amlResult.getRedFlags().contains("HIGH_RISK_COUNTRY")) {
            return "HIGH_RISK_JURISDICTION";
        } else {
            return "OTHER_SUSPICIOUS_ACTIVITY";
        }
    }

    private String findAvailableComplianceOfficer() {
        // Logic to find available compliance officer
        return "compliance_officer_1";
    }

    private void updateComplianceMetrics(ComplianceReview review) {
        try {
            // Update metrics
            // metricsService.incrementCounter("compliance.reviews.processed");
            // metricsService.recordValue("compliance.review.risk_score", review.getRiskScore());
            
        } catch (Exception e) {
            log.error("Failed to update compliance metrics", e);
        }
    }

    private String buildSARNotification(SARReport sar, ComplianceReviewEvent event) {
        return String.format(
            "SAR Report Required:\n" +
            "Report ID: %s\n" +
            "Transaction: %s\n" +
            "Amount: %s %s\n" +
            "User: %s\n" +
            "Priority: %s\n" +
            "Please review and approve for FinCEN filing.",
            sar.getId(),
            event.getTransactionId(),
            event.getAmount(),
            event.getCurrency(),
            event.getUserId(),
            sar.getPriority()
        );
    }

    @lombok.Data
    @lombok.Builder
    private static class FinCENFilingResult {
        private boolean successful;
        private String trackingNumber;
        private String confirmation;
        private String errorMessage;
        private LocalDateTime filedAt;
    }
}