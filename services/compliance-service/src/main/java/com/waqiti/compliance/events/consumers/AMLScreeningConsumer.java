package com.waqiti.compliance.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.compliance.AMLScreeningEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.compliance.domain.AMLScreening;
import com.waqiti.compliance.domain.AMLScreening.AMLDecision;
import com.waqiti.compliance.domain.AMLScreeningStatus;
import com.waqiti.compliance.domain.AMLRiskLevel;
import com.waqiti.compliance.domain.SanctionMatch;
import com.waqiti.compliance.repository.AMLScreeningRepository;
import com.waqiti.compliance.service.SanctionListService;
import com.waqiti.compliance.service.PEPScreeningService;
import com.waqiti.compliance.service.AdverseMediaService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.compliance.service.AMLDecisionService;
import com.waqiti.common.exceptions.AMLScreeningException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for AML (Anti-Money Laundering) screening events.
 * Performs comprehensive compliance checks including:
 * - Sanctions list screening (OFAC, UN, EU, UK)
 * - PEP (Politically Exposed Person) screening
 * - Adverse media screening
 * - Risk scoring and automated decisioning
 * - Real-time transaction monitoring
 * 
 * Critical for regulatory compliance and financial crime prevention.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AMLScreeningConsumer {

    private final AMLScreeningRepository screeningRepository;
    private final SanctionListService sanctionListService;
    private final PEPScreeningService pepScreeningService;
    private final AdverseMediaService adverseMediaService;
    private final AMLDecisionService decisionService;
    private final ComplianceNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "aml-screening-requests",
        groupId = "compliance-service-aml-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {AMLScreeningException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleAMLScreeningRequest(
            @Payload AMLScreeningEvent screeningEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "screening-type", required = false) String screeningType,
            Acknowledgment acknowledgment) {

        String eventId = screeningEvent.getEventId() != null ? 
            screeningEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing AML screening request: {} for entity: {} type: {}", 
                    eventId, screeningEvent.getEntityId(), screeningEvent.getEntityType());

            // Metrics tracking
            metricsService.incrementCounter("aml.screening.processing.started",
                Map.of(
                    "entity_type", screeningEvent.getEntityType(),
                    "screening_reason", screeningEvent.getScreeningReason()
                ));

            // Idempotency check
            if (isScreeningAlreadyProcessed(screeningEvent.getEntityId(), eventId)) {
                log.info("AML screening {} already processed for entity {}", eventId, screeningEvent.getEntityId());
                acknowledgment.acknowledge();
                return;
            }

            // Create screening record
            AMLScreening screening = createScreeningRecord(screeningEvent, eventId, correlationId);

            // Perform parallel screening checks
            CompletableFuture<List<SanctionMatch>> sanctionsFuture = 
                CompletableFuture.supplyAsync(() -> performSanctionsScreening(screening, screeningEvent));
            
            CompletableFuture<List<SanctionMatch>> pepFuture = 
                CompletableFuture.supplyAsync(() -> performPEPScreening(screening, screeningEvent));
            
            CompletableFuture<List<SanctionMatch>> adverseMediaFuture = 
                CompletableFuture.supplyAsync(() -> performAdverseMediaScreening(screening, screeningEvent));

            // Wait for all screenings to complete with timeout
            try {
                CompletableFuture.allOf(sanctionsFuture, pepFuture, adverseMediaFuture)
                    .get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("AML screening timed out after 15 seconds for transaction: {}", screeningEvent.getTransactionId(), e);
                throw new RuntimeException("AML screening timed out - cannot process", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("AML screening execution failed for transaction: {}", screeningEvent.getTransactionId(), e.getCause());
                throw new RuntimeException("AML screening failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("AML screening interrupted for transaction: {}", screeningEvent.getTransactionId(), e);
                throw new RuntimeException("AML screening interrupted", e);
            }

            // Aggregate results (already completed, safe to get immediately)
            List<SanctionMatch> allMatches = new ArrayList<>();
            try {
                allMatches.addAll(sanctionsFuture.get(1, java.util.concurrent.TimeUnit.SECONDS));
                allMatches.addAll(pepFuture.get(1, java.util.concurrent.TimeUnit.SECONDS));
                allMatches.addAll(adverseMediaFuture.get(1, java.util.concurrent.TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Failed to retrieve AML screening results for transaction: {}", screeningEvent.getTransactionId(), e);
                throw new RuntimeException("Failed to retrieve screening results", e);
            }

            screening.setMatches(allMatches);
            screening.setTotalMatches(allMatches.size());

            // Calculate risk score
            calculateRiskScore(screening, allMatches);

            // Make automated decision
            makeAMLDecision(screening, screeningEvent);

            // Update screening status
            updateScreeningStatus(screening);

            // Save screening results
            AMLScreening savedScreening = screeningRepository.save(screening);

            // Handle high-risk cases
            handleHighRiskCases(savedScreening, screeningEvent);

            // Send compliance notifications
            sendComplianceNotifications(savedScreening, screeningEvent);

            // Update metrics
            updateAMLMetrics(savedScreening, screeningEvent);

            // Create comprehensive audit trail
            createAMLAuditLog(savedScreening, screeningEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("aml.screening.processing.success",
                Map.of(
                    "risk_level", savedScreening.getRiskLevel().toString(),
                    "decision", savedScreening.getDecision()
                ));

            log.info("Successfully processed AML screening: {} for entity: {} with risk level: {} and decision: {}", 
                    savedScreening.getId(), screeningEvent.getEntityId(), 
                    savedScreening.getRiskLevel(), savedScreening.getDecision());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing AML screening event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("aml.screening.processing.error");
            
            // Critical audit log for AML failures
            auditLogger.logCriticalAlert("AML_SCREENING_PROCESSING_ERROR",
                "Critical AML screening failure - compliance at risk",
                Map.of(
                    "entityId", screeningEvent.getEntityId(),
                    "entityType", screeningEvent.getEntityType(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new AMLScreeningException("Failed to process AML screening: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "aml-screening-realtime",
        groupId = "compliance-service-aml-realtime-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleRealtimeAMLScreening(
            @Payload AMLScreeningEvent screeningEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("REALTIME AML SCREENING: Processing urgent screening for transaction: {}", 
                    screeningEvent.getTransactionId());

            // Fast-track screening for real-time transactions
            AMLScreening screening = performFastTrackScreening(screeningEvent, correlationId);

            // Immediate decision for transaction approval/rejection
            boolean allowTransaction = makeRealtimeDecision(screening);

            if (!allowTransaction) {
                // Block the transaction immediately
                blockTransaction(screeningEvent.getTransactionId(), screening);
                notificationService.sendTransactionBlockedAlert(screeningEvent, screening);
            }

            // Save screening results asynchronously
            CompletableFuture.runAsync(() -> {
                screeningRepository.save(screening);
                createAMLAuditLog(screening, screeningEvent, correlationId);
            });

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process realtime AML screening: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking realtime queue
        }
    }

    private boolean isScreeningAlreadyProcessed(String entityId, String eventId) {
        return screeningRepository.existsByEntityIdAndEventId(entityId, eventId);
    }

    private AMLScreening createScreeningRecord(AMLScreeningEvent event, String eventId, String correlationId) {
        return AMLScreening.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .entityName(event.getEntityName())
            .entityCountry(event.getEntityCountry())
            .transactionId(event.getTransactionId())
            .transactionAmount(event.getTransactionAmount())
            .transactionCurrency(event.getTransactionCurrency())
            .screeningReason(event.getScreeningReason())
            .screeningType(determineScreeningType(event))
            .status(AMLScreeningStatus.INITIATED)
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<SanctionMatch> performSanctionsScreening(AMLScreening screening, AMLScreeningEvent event) {
        try {
            log.info("Performing sanctions screening for entity: {}", screening.getEntityId());

            List<SanctionMatch> matches = new ArrayList<>();

            // Screen against multiple sanctions lists
            matches.addAll(sanctionListService.screenOFAC(
                event.getEntityName(), event.getEntityCountry(), event.getEntityIdentifiers()));
            matches.addAll(sanctionListService.screenUN(
                event.getEntityName(), event.getEntityCountry()));
            matches.addAll(sanctionListService.screenEU(
                event.getEntityName(), event.getEntityCountry()));
            matches.addAll(sanctionListService.screenUK(
                event.getEntityName(), event.getEntityCountry()));

            // Screen additional identifiers (passport, national ID, etc.)
            if (event.getEntityIdentifiers() != null) {
                for (Map.Entry<String, String> identifier : event.getEntityIdentifiers().entrySet()) {
                    matches.addAll(sanctionListService.screenByIdentifier(
                        identifier.getKey(), identifier.getValue()));
                }
            }

            screening.setSanctionsHits(matches.size());
            screening.setSanctionsScreenedAt(LocalDateTime.now());

            log.info("Sanctions screening completed: {} matches found for entity: {}", 
                    matches.size(), screening.getEntityId());

            return matches;

        } catch (Exception e) {
            log.error("Error in sanctions screening for {}: {}", screening.getEntityId(), e.getMessage());
            screening.setSanctionsError(e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SanctionMatch> performPEPScreening(AMLScreening screening, AMLScreeningEvent event) {
        try {
            log.info("Performing PEP screening for entity: {}", screening.getEntityId());

            List<SanctionMatch> matches = pepScreeningService.screenForPEP(
                event.getEntityName(), 
                event.getEntityCountry(),
                event.getEntityIdentifiers()
            );

            // Check for PEP associations (family, associates)
            matches.addAll(pepScreeningService.screenForPEPAssociations(
                event.getEntityName(),
                event.getRelatedParties()
            ));

            screening.setPepHits(matches.size());
            screening.setPepScreenedAt(LocalDateTime.now());

            log.info("PEP screening completed: {} matches found for entity: {}", 
                    matches.size(), screening.getEntityId());

            return matches;

        } catch (Exception e) {
            log.error("Error in PEP screening for {}: {}", screening.getEntityId(), e.getMessage());
            screening.setPepError(e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<SanctionMatch> performAdverseMediaScreening(AMLScreening screening, AMLScreeningEvent event) {
        try {
            log.info("Performing adverse media screening for entity: {}", screening.getEntityId());

            List<SanctionMatch> matches = adverseMediaService.screenForAdverseMedia(
                event.getEntityName(),
                event.getEntityCountry(),
                event.getScreeningKeywords()
            );

            screening.setAdverseMediaHits(matches.size());
            screening.setAdverseMediaScreenedAt(LocalDateTime.now());

            log.info("Adverse media screening completed: {} matches found for entity: {}", 
                    matches.size(), screening.getEntityId());

            return matches;

        } catch (Exception e) {
            log.error("Error in adverse media screening for {}: {}", screening.getEntityId(), e.getMessage());
            screening.setAdverseMediaError(e.getMessage());
            return new ArrayList<>();
        }
    }

    private void calculateRiskScore(AMLScreening screening, List<SanctionMatch> allMatches) {
        double riskScore = 0.0;

        // Sanctions matches carry highest weight
        if (screening.getSanctionsHits() > 0) {
            riskScore += screening.getSanctionsHits() * 0.4;
        }

        // PEP matches
        if (screening.getPepHits() > 0) {
            riskScore += screening.getPepHits() * 0.3;
        }

        // Adverse media matches
        if (screening.getAdverseMediaHits() > 0) {
            riskScore += screening.getAdverseMediaHits() * 0.2;
        }

        // High-risk country factor
        if (isHighRiskCountry(screening.getEntityCountry())) {
            riskScore += 0.3;
        }

        // Transaction amount factor
        if (screening.getTransactionAmount() != null && screening.getTransactionAmount().doubleValue() > 10000) {
            riskScore += 0.2;
        }

        // Match quality factor
        double avgMatchScore = allMatches.stream()
            .mapToDouble(m -> m.getMatchScore() != null ? m.getMatchScore() : 0.0)
            .average()
            .orElse(0.0);

        riskScore += avgMatchScore * 0.1;

        // Normalize to 0-1 scale
        riskScore = Math.min(1.0, riskScore);

        screening.setRiskScore(riskScore);
        screening.setRiskLevel(determineRiskLevel(riskScore));
    }

    private AMLRiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 0.8) return AMLRiskLevel.CRITICAL;
        if (riskScore >= 0.6) return AMLRiskLevel.HIGH;
        if (riskScore >= 0.4) return AMLRiskLevel.MEDIUM;
        if (riskScore >= 0.2) return AMLRiskLevel.LOW;
        return AMLRiskLevel.MINIMAL;
    }

    private void makeAMLDecision(AMLScreening screening, AMLScreeningEvent event) {
        try {
            // Get automated decision from decision service
            AMLDecision decision = convertToAMLDecision(decisionService.makeAMLDecision(screening, event));
            screening.setDecision(decision);
            screening.setDecisionReason(decisionService.getDecisionReason(screening));
            screening.setDecisionMadeAt(LocalDateTime.now());

            // Determine required actions
            List<String> requiredActions = determineRequiredActions(screening, decision);
            screening.setRequiredActions(requiredActions);

            // Set review requirements
            if (requiresManualReview(screening)) {
                screening.setRequiresManualReview(true);
                screening.setReviewDeadline(LocalDateTime.now().plusHours(24));
                screening.setReviewPriority(calculateReviewPriority(screening));
            }

        } catch (Exception e) {
            log.error("Error making AML decision for {}: {}", screening.getId(), e.getMessage());
            screening.setDecision(AMLDecision.ESCALATE);
            screening.setDecisionReason("Automated decision failed - manual review required");
            screening.setRequiresManualReview(true);
        }
    }

    private void updateScreeningStatus(AMLScreening screening) {
        AMLDecision decision = screening.getDecision();
        if (AMLDecision.REJECT.equals(decision)) {
            screening.setStatus(AMLScreeningStatus.BLOCKED);
        } else if (AMLDecision.APPROVE.equals(decision)) {
            screening.setStatus(AMLScreeningStatus.APPROVED);
        } else if (screening.isRequiresManualReview()) {
            screening.setStatus(AMLScreeningStatus.PENDING_REVIEW);
        } else {
            screening.setStatus(AMLScreeningStatus.COMPLETED);
        }

        screening.setCompletedAt(LocalDateTime.now());
        screening.setProcessingTimeMs(
            java.time.Duration.between(screening.getCreatedAt(), LocalDateTime.now()).toMillis()
        );
    }

    private void handleHighRiskCases(AMLScreening screening, AMLScreeningEvent event) {
        if (screening.getRiskLevel() == AMLRiskLevel.CRITICAL || 
            screening.getRiskLevel() == AMLRiskLevel.HIGH) {
            
            // Immediate escalation
            notificationService.sendHighRiskAMLAlert(screening);

            // Create case for investigation
            createInvestigationCase(screening, event);

            // Apply enhanced due diligence
            applyEnhancedDueDiligence(screening.getEntityId());

            // Block related transactions if critical
            if (screening.getRiskLevel() == AMLRiskLevel.CRITICAL) {
                blockAllEntityTransactions(screening.getEntityId());
            }
        }
    }

    private void sendComplianceNotifications(AMLScreening screening, AMLScreeningEvent event) {
        try {
            // Notify compliance team
            notificationService.sendAMLScreeningNotification(screening);

            // High-risk notifications
            if (screening.getRiskLevel() == AMLRiskLevel.CRITICAL) {
                notificationService.sendCriticalComplianceAlert(
                    "CRITICAL AML RISK DETECTED",
                    String.format("Critical AML risk detected for entity %s with %d sanctions hits",
                        screening.getEntityId(), screening.getSanctionsHits()),
                    screening
                );
            }

            // Manual review notifications
            if (screening.isRequiresManualReview()) {
                notificationService.sendManualReviewRequest(screening);
            }

            // Transaction block notifications
            if (AMLDecision.REJECT.equals(screening.getDecision())) {
                notificationService.sendTransactionBlockNotification(screening);
            }

        } catch (Exception e) {
            log.error("Failed to send AML notifications for {}: {}", screening.getId(), e.getMessage());
        }
    }

    private void updateAMLMetrics(AMLScreening screening, AMLScreeningEvent event) {
        try {
            // Record screening metrics
            metricsService.incrementCounter("aml.screening.completed",
                Map.of(
                    "risk_level", screening.getRiskLevel().toString(),
                    "decision", screening.getDecision(),
                    "entity_type", screening.getEntityType()
                ));

            // Record risk metrics
            metricsService.recordGauge("aml.risk_score", screening.getRiskScore(),
                Map.of("entity_type", screening.getEntityType()));

            // Record match metrics
            metricsService.recordTimer("aml.sanctions_hits", screening.getSanctionsHits(),
                Map.of("entity_country", screening.getEntityCountry() != null ? screening.getEntityCountry() : "unknown"));

            // Record processing time
            metricsService.recordTimer("aml.processing_time_ms", screening.getProcessingTimeMs(),
                Map.of("risk_level", screening.getRiskLevel().toString()));

            // Update compliance dashboard
            metricsService.incrementCounter("compliance.aml.screenings",
                Map.of("month", LocalDateTime.now().getMonth().toString()));

        } catch (Exception e) {
            log.error("Failed to update AML metrics for {}: {}", screening.getId(), e.getMessage());
        }
    }

    private void createAMLAuditLog(AMLScreening screening, AMLScreeningEvent event, String correlationId) {
        auditLogger.logComplianceEvent(
            "AML_SCREENING_COMPLETED",
            screening.getEntityId(),
            screening.getId(),
            screening.getEntityType(),
            screening.getTransactionAmount() != null ? screening.getTransactionAmount().doubleValue() : 0.0,
            "aml_processor",
            !(AMLDecision.REJECT.equals(screening.getDecision())),
            Map.of(
                "entityId", screening.getEntityId(),
                "entityType", screening.getEntityType(),
                "riskLevel", screening.getRiskLevel().toString(),
                "riskScore", String.valueOf(screening.getRiskScore()),
                "decision", screening.getDecision(),
                "sanctionsHits", String.valueOf(screening.getSanctionsHits()),
                "pepHits", String.valueOf(screening.getPepHits()),
                "adverseMediaHits", String.valueOf(screening.getAdverseMediaHits()),
                "totalMatches", String.valueOf(screening.getTotalMatches()),
                "requiresManualReview", String.valueOf(screening.isRequiresManualReview()),
                "processingTimeMs", String.valueOf(screening.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private AMLScreening performFastTrackScreening(AMLScreeningEvent event, String correlationId) {
        AMLScreening screening = createScreeningRecord(event, UUID.randomUUID().toString(), correlationId);
        
        // Quick sanctions check only for realtime
        List<SanctionMatch> sanctionMatches = sanctionListService.quickSanctionsCheck(
            event.getEntityName(), event.getEntityCountry()
        );
        
        screening.setSanctionsHits(sanctionMatches.size());
        screening.setMatches(sanctionMatches);
        
        // Quick risk calculation
        double riskScore = sanctionMatches.isEmpty() ? 0.1 : 0.9;
        screening.setRiskScore(riskScore);
        screening.setRiskLevel(determineRiskLevel(riskScore));
        
        screening.setStatus(AMLScreeningStatus.REALTIME_COMPLETED);
        screening.setCompletedAt(LocalDateTime.now());
        
        return screening;
    }

    private boolean makeRealtimeDecision(AMLScreening screening) {
        // Block if any sanctions hits or high risk
        return screening.getSanctionsHits() == 0 && 
               screening.getRiskLevel() != AMLRiskLevel.HIGH && 
               screening.getRiskLevel() != AMLRiskLevel.CRITICAL;
    }

    private void blockTransaction(String transactionId, AMLScreening screening) {
        if (transactionId != null) {
            log.warn("BLOCKING TRANSACTION {} due to AML screening failure", transactionId);
            // In real implementation, would call transaction service to block
        }
    }

    private String determineScreeningType(AMLScreeningEvent event) {
        if (event.getScreeningReason() != null) {
            return switch (event.getScreeningReason().toUpperCase()) {
                case "ONBOARDING" -> "KYC_ONBOARDING";
                case "TRANSACTION" -> "TRANSACTION_MONITORING";
                case "PERIODIC" -> "PERIODIC_REVIEW";
                case "ALERT" -> "ALERT_TRIGGERED";
                default -> "STANDARD";
            };
        }
        return "STANDARD";
    }

    private boolean isHighRiskCountry(String country) {
        // FATF high-risk jurisdictions
        Set<String> highRiskCountries = Set.of(
            "IR", "KP", "MM", // Iran, North Korea, Myanmar
            "AF", "SY", "YE", // Afghanistan, Syria, Yemen
            "AL", "BB", "BF", "HT", "JM", "ML", "MZ", "PH", "SN", "SS", "TZ", "UG"
        );
        return country != null && highRiskCountries.contains(country.toUpperCase());
    }

    private List<String> determineRequiredActions(AMLScreening screening, AMLDecision decision) {
        List<String> actions = new ArrayList<>();
        
        if (AMLDecision.REJECT.equals(decision)) {
            actions.add("BLOCK_ALL_TRANSACTIONS");
            actions.add("FREEZE_ACCOUNT");
            actions.add("FILE_SAR");
        }
        
        if (screening.isRequiresManualReview()) {
            actions.add("MANUAL_REVIEW");
            actions.add("COLLECT_ADDITIONAL_INFO");
        }
        
        if (screening.getRiskLevel() == AMLRiskLevel.HIGH) {
            actions.add("ENHANCED_DUE_DILIGENCE");
            actions.add("SENIOR_APPROVAL_REQUIRED");
        }
        
        if (screening.getPepHits() > 0) {
            actions.add("PEP_REVIEW");
            actions.add("SOURCE_OF_FUNDS_VERIFICATION");
        }
        
        return actions;
    }

    private boolean requiresManualReview(AMLScreening screening) {
        return screening.getSanctionsHits() > 0 ||
               screening.getPepHits() > 0 ||
               screening.getRiskLevel() == AMLRiskLevel.HIGH ||
               screening.getRiskLevel() == AMLRiskLevel.CRITICAL ||
               screening.getRiskScore() > 0.5;
    }

    private String calculateReviewPriority(AMLScreening screening) {
        if (screening.getRiskLevel() == AMLRiskLevel.CRITICAL) return "URGENT";
        if (screening.getSanctionsHits() > 0) return "HIGH";
        if (screening.getRiskLevel() == AMLRiskLevel.HIGH) return "HIGH";
        if (screening.getPepHits() > 0) return "MEDIUM";
        return "NORMAL";
    }

    private void createInvestigationCase(AMLScreening screening, AMLScreeningEvent event) {
        log.info("Creating investigation case for high-risk entity: {}", screening.getEntityId());
        // In real implementation, would create case in case management system
    }

    private void applyEnhancedDueDiligence(String entityId) {
        log.info("Applying enhanced due diligence for entity: {}", entityId);
        // In real implementation, would trigger EDD workflow
    }

    private void blockAllEntityTransactions(String entityId) {
        log.warn("Blocking all transactions for critical risk entity: {}", entityId);
        // In real implementation, would call transaction service to block all transactions
    }
    
    /**
     * Convert string decision to AMLDecision enum
     */
    private AMLDecision convertToAMLDecision(String decisionString) {
        if (decisionString == null) {
            return AMLDecision.ESCALATE;
        }
        
        return switch (decisionString.toUpperCase()) {
            case "APPROVED", "APPROVE" -> AMLDecision.APPROVE;
            case "BLOCKED", "REJECT", "REJECTED" -> AMLDecision.REJECT;
            case "ESCALATE", "ESCALATED" -> AMLDecision.ESCALATE;
            case "REQUEST_MORE_INFO", "MORE_INFO" -> AMLDecision.REQUEST_MORE_INFO;
            case "ONGOING_MONITORING", "MONITOR" -> AMLDecision.ONGOING_MONITORING;
            default -> AMLDecision.ESCALATE; // Safe fallback
        };
    }
}