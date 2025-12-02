package com.waqiti.user.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.EventValidator;
import com.waqiti.user.model.*;
import com.waqiti.user.repository.*;
import com.waqiti.user.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KycStatusUpdatesConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SecurityContext securityContext;
    
    private final UserRepository userRepository;
    private final KycStatusRepository kycStatusRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final KycHistoryRepository kycHistoryRepository;
    private final UserComplianceRepository userComplianceRepository;
    private final AccountLimitsRepository accountLimitsRepository;
    
    private final KycService kycService;
    private final ComplianceService complianceService;
    private final UserNotificationService userNotificationService;
    private final AccountTierService accountTierService;
    private final LimitsManagementService limitsManagementService;
    private final DocumentVerificationService documentVerificationService;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Long> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> statusUpdateCounts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "kyc-status-updates", groupId = "user-service-group")
    @CircuitBreaker(name = "kyc-status-updates-consumer", fallbackMethod = "fallbackProcessKycStatusUpdate")
    @Retry(name = "kyc-status-updates-consumer")
    @Transactional
    public void processKycStatusUpdate(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        String eventId = null;
        String kycStatus = null;
        String userId = null;

        try {
            log.info("Processing KYC status update from topic: {}, partition: {}, offset: {}", topic, partition, offset);

            JsonNode eventNode = objectMapper.readTree(eventPayload);
            eventId = eventNode.has("eventId") ? eventNode.get("eventId").asText() : UUID.randomUUID().toString();
            kycStatus = eventNode.has("kycStatus") ? eventNode.get("kycStatus").asText() : "UNKNOWN";
            userId = eventNode.has("userId") ? eventNode.get("userId").asText() : null;

            if (!eventValidator.validateEvent(eventNode, "KYC_STATUS_UPDATE_SCHEMA")) {
                throw new IllegalArgumentException("Invalid KYC status update event structure");
            }

            KycUpdateContext context = buildKycUpdateContext(eventNode, eventId, kycStatus, userId);
            
            validateKycUpdate(context);
            enrichKycContext(context);
            
            KycUpdateResult result = processKycStatusChange(context);
            
            executeAutomatedActions(context, result);
            updateKycMetrics(context, result);
            
            auditService.logUserEvent(eventId, "KYC_STATUS_UPDATE", userId, "SUCCESS", result.getProcessingDetails());
            
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordProcessingTime("kyc_status_updates_consumer", processingTime);
            metricsService.incrementCounter("kyc_status_updates_processed", "status", kycStatus);
            
            processingMetrics.put(kycStatus, processingTime);
            statusUpdateCounts.merge(kycStatus, 1, Integer::sum);

            acknowledgment.acknowledge();
            log.info("Successfully processed KYC status update: {} to status: {} in {}ms", eventId, kycStatus, processingTime);

        } catch (Exception e) {
            handleProcessingError(eventId, kycStatus, userId, eventPayload, e, acknowledgment);
        }
    }

    private KycUpdateContext buildKycUpdateContext(JsonNode eventNode, String eventId, String kycStatus, String userId) {
        return KycUpdateContext.builder()
                .eventId(eventId)
                .userId(userId)
                .newStatus(kycStatus)
                .previousStatus(eventNode.has("previousStatus") ? eventNode.get("previousStatus").asText() : null)
                .kycLevel(eventNode.has("kycLevel") ? eventNode.get("kycLevel").asText() : "LEVEL_1")
                .verificationScore(eventNode.has("verificationScore") ? 
                    BigDecimal.valueOf(eventNode.get("verificationScore").asDouble()) : null)
                .provider(eventNode.has("provider") ? eventNode.get("provider").asText() : "INTERNAL")
                .updateReason(eventNode.has("updateReason") ? eventNode.get("updateReason").asText() : null)
                .documents(parseDocuments(eventNode))
                .verificationResults(parseVerificationResults(eventNode))
                .failureReasons(parseFailureReasons(eventNode))
                .timestamp(eventNode.has("timestamp") ? 
                    Instant.ofEpochMilli(eventNode.get("timestamp").asLong()) : Instant.now())
                .sourceSystem(eventNode.has("sourceSystem") ? eventNode.get("sourceSystem").asText() : "UNKNOWN")
                .ipAddress(eventNode.has("ipAddress") ? eventNode.get("ipAddress").asText() : null)
                .sessionId(eventNode.has("sessionId") ? eventNode.get("sessionId").asText() : null)
                .build();
    }

    private List<KycDocument> parseDocuments(JsonNode eventNode) {
        List<KycDocument> documents = new ArrayList<>();
        if (eventNode.has("documents")) {
            JsonNode docsNode = eventNode.get("documents");
            if (docsNode.isArray()) {
                for (JsonNode doc : docsNode) {
                    KycDocument document = KycDocument.builder()
                            .documentType(doc.has("type") ? doc.get("type").asText() : null)
                            .documentNumber(doc.has("number") ? doc.get("number").asText() : null)
                            .documentUrl(doc.has("url") ? doc.get("url").asText() : null)
                            .verificationStatus(doc.has("status") ? doc.get("status").asText() : null)
                            .expiryDate(doc.has("expiryDate") ? 
                                LocalDateTime.parse(doc.get("expiryDate").asText()).toLocalDate() : null)
                            .build();
                    documents.add(document);
                }
            }
        }
        return documents;
    }

    private Map<String, Object> parseVerificationResults(JsonNode eventNode) {
        Map<String, Object> results = new HashMap<>();
        if (eventNode.has("verificationResults")) {
            JsonNode resultsNode = eventNode.get("verificationResults");
            resultsNode.fieldNames().forEachRemaining(fieldName -> 
                results.put(fieldName, resultsNode.get(fieldName).asText()));
        }
        return results;
    }

    private List<String> parseFailureReasons(JsonNode eventNode) {
        List<String> reasons = new ArrayList<>();
        if (eventNode.has("failureReasons")) {
            JsonNode reasonsNode = eventNode.get("failureReasons");
            if (reasonsNode.isArray()) {
                reasonsNode.forEach(reason -> reasons.add(reason.asText()));
            }
        }
        return reasons;
    }

    private void validateKycUpdate(KycUpdateContext context) {
        if (context.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required for KYC status updates");
        }

        if (!userRepository.existsById(context.getUserId())) {
            throw new IllegalStateException("User not found: " + context.getUserId());
        }

        validateKycStatus(context.getNewStatus());
        validateKycLevel(context.getKycLevel());
        validateStatusTransition(context);
        validateDocumentRequirements(context);
        validateVerificationScore(context);
    }

    private void validateKycStatus(String status) {
        Set<String> validStatuses = Set.of(
            "NOT_STARTED", "IN_PROGRESS", "PENDING_REVIEW", "DOCUMENTS_REQUIRED",
            "VERIFICATION_IN_PROGRESS", "APPROVED", "REJECTED", "EXPIRED",
            "SUSPENDED", "MANUAL_REVIEW", "PARTIALLY_VERIFIED", "RESUBMISSION_REQUIRED"
        );
        
        if (!validStatuses.contains(status)) {
            throw new IllegalArgumentException("Invalid KYC status: " + status);
        }
    }

    private void validateKycLevel(String level) {
        Set<String> validLevels = Set.of("LEVEL_0", "LEVEL_1", "LEVEL_2", "LEVEL_3", "LEVEL_4");
        if (!validLevels.contains(level)) {
            throw new IllegalArgumentException("Invalid KYC level: " + level);
        }
    }

    private void validateStatusTransition(KycUpdateContext context) {
        if (context.getPreviousStatus() != null) {
            if (!isValidTransition(context.getPreviousStatus(), context.getNewStatus())) {
                throw new IllegalStateException(
                    String.format("Invalid status transition from %s to %s", 
                        context.getPreviousStatus(), context.getNewStatus())
                );
            }
        }
    }

    private boolean isValidTransition(String fromStatus, String toStatus) {
        Map<String, Set<String>> validTransitions = Map.of(
            "NOT_STARTED", Set.of("IN_PROGRESS", "DOCUMENTS_REQUIRED"),
            "IN_PROGRESS", Set.of("PENDING_REVIEW", "DOCUMENTS_REQUIRED", "VERIFICATION_IN_PROGRESS"),
            "PENDING_REVIEW", Set.of("APPROVED", "REJECTED", "MANUAL_REVIEW", "DOCUMENTS_REQUIRED"),
            "DOCUMENTS_REQUIRED", Set.of("IN_PROGRESS", "VERIFICATION_IN_PROGRESS", "REJECTED"),
            "VERIFICATION_IN_PROGRESS", Set.of("APPROVED", "REJECTED", "PARTIALLY_VERIFIED", "MANUAL_REVIEW"),
            "PARTIALLY_VERIFIED", Set.of("APPROVED", "REJECTED", "DOCUMENTS_REQUIRED"),
            "MANUAL_REVIEW", Set.of("APPROVED", "REJECTED", "DOCUMENTS_REQUIRED"),
            "APPROVED", Set.of("EXPIRED", "SUSPENDED", "IN_PROGRESS"),
            "REJECTED", Set.of("IN_PROGRESS", "RESUBMISSION_REQUIRED"),
            "EXPIRED", Set.of("IN_PROGRESS", "DOCUMENTS_REQUIRED"),
            "SUSPENDED", Set.of("IN_PROGRESS", "APPROVED", "REJECTED")
        );
        
        return validTransitions.getOrDefault(fromStatus, Set.of()).contains(toStatus);
    }

    private void validateDocumentRequirements(KycUpdateContext context) {
        if (context.getNewStatus().equals("APPROVED")) {
            Set<String> requiredDocs = getRequiredDocumentsForLevel(context.getKycLevel());
            Set<String> providedDocs = context.getDocuments().stream()
                    .map(KycDocument::getDocumentType)
                    .collect(Collectors.toSet());
            
            if (!providedDocs.containsAll(requiredDocs)) {
                Set<String> missingDocs = new HashSet<>(requiredDocs);
                missingDocs.removeAll(providedDocs);
                throw new IllegalStateException("Missing required documents for approval: " + missingDocs);
            }
        }
    }

    private Set<String> getRequiredDocumentsForLevel(String kycLevel) {
        switch (kycLevel) {
            case "LEVEL_0":
                return Set.of();
            case "LEVEL_1":
                return Set.of("IDENTITY_DOCUMENT");
            case "LEVEL_2":
                return Set.of("IDENTITY_DOCUMENT", "ADDRESS_PROOF");
            case "LEVEL_3":
                return Set.of("IDENTITY_DOCUMENT", "ADDRESS_PROOF", "INCOME_PROOF");
            case "LEVEL_4":
                return Set.of("IDENTITY_DOCUMENT", "ADDRESS_PROOF", "INCOME_PROOF", "SOURCE_OF_FUNDS");
            default:
                return Set.of("IDENTITY_DOCUMENT");
        }
    }

    private void validateVerificationScore(KycUpdateContext context) {
        if (context.getNewStatus().equals("APPROVED") && context.getVerificationScore() != null) {
            BigDecimal minScore = getMinimumScoreForLevel(context.getKycLevel());
            if (context.getVerificationScore().compareTo(minScore) < 0) {
                throw new IllegalStateException(
                    String.format("Verification score %.2f is below minimum %.2f for %s", 
                        context.getVerificationScore(), minScore, context.getKycLevel())
                );
            }
        }
    }

    private BigDecimal getMinimumScoreForLevel(String kycLevel) {
        switch (kycLevel) {
            case "LEVEL_0":
                return BigDecimal.ZERO;
            case "LEVEL_1":
                return BigDecimal.valueOf(0.6);
            case "LEVEL_2":
                return BigDecimal.valueOf(0.7);
            case "LEVEL_3":
                return BigDecimal.valueOf(0.8);
            case "LEVEL_4":
                return BigDecimal.valueOf(0.9);
            default:
                return BigDecimal.valueOf(0.5);
        }
    }

    private void enrichKycContext(KycUpdateContext context) {
        User user = userRepository.findById(context.getUserId()).orElse(null);
        if (user != null) {
            context.setUser(user);
            context.setUserTier(user.getTier());
            context.setUserStatus(user.getStatus());
            context.setRegistrationDate(user.getCreatedAt());
        }

        KycStatus currentStatus = kycStatusRepository.findByUserId(context.getUserId()).orElse(null);
        if (currentStatus != null) {
            context.setCurrentKycStatus(currentStatus);
            context.setPreviousLevel(currentStatus.getKycLevel());
            context.setLastVerificationDate(currentStatus.getLastVerificationDate());
            context.setVerificationAttempts(currentStatus.getVerificationAttempts());
        }

        enrichWithComplianceData(context);
        enrichWithRiskProfile(context);
        enrichWithAccountLimits(context);
        enrichWithHistoricalData(context);
        enrichWithProviderData(context);
    }

    private void enrichWithComplianceData(KycUpdateContext context) {
        UserCompliance compliance = userComplianceRepository.findByUserId(context.getUserId()).orElse(null);
        if (compliance != null) {
            context.setComplianceData(compliance);
            context.setComplianceScore(compliance.getComplianceScore());
            context.setPepStatus(compliance.isPepStatus());
            context.setSanctionsStatus(compliance.getSanctionsStatus());
            context.setAdverseMediaStatus(compliance.getAdverseMediaStatus());
        }
    }

    private void enrichWithRiskProfile(KycUpdateContext context) {
        RiskProfile riskProfile = kycService.getUserRiskProfile(context.getUserId());
        if (riskProfile != null) {
            context.setRiskProfile(riskProfile);
            context.setRiskScore(riskProfile.getCurrentScore());
            context.setRiskLevel(riskProfile.getRiskLevel());
            context.setHighRiskIndicators(riskProfile.getHighRiskIndicators());
        }
    }

    private void enrichWithAccountLimits(KycUpdateContext context) {
        AccountLimits limits = accountLimitsRepository.findByUserId(context.getUserId()).orElse(null);
        if (limits != null) {
            context.setCurrentLimits(limits);
            context.setDailyTransactionLimit(limits.getDailyTransactionLimit());
            context.setMonthlyTransactionLimit(limits.getMonthlyTransactionLimit());
            context.setMaxSingleTransactionAmount(limits.getMaxSingleTransactionAmount());
        }
    }

    private void enrichWithHistoricalData(KycUpdateContext context) {
        List<KycHistory> history = kycHistoryRepository.findByUserIdOrderByTimestampDesc(context.getUserId());
        context.setKycHistory(history);
        
        if (!history.isEmpty()) {
            context.setTotalVerificationAttempts(history.size());
            context.setLastRejectionReason(
                history.stream()
                    .filter(h -> "REJECTED".equals(h.getStatus()))
                    .findFirst()
                    .map(KycHistory::getRejectionReason)
                    .orElse(null)
            );
        }
    }

    private void enrichWithProviderData(KycUpdateContext context) {
        if (context.getProvider() != null && !context.getProvider().equals("INTERNAL")) {
            ProviderVerificationData providerData = kycService.getProviderData(
                context.getUserId(), 
                context.getProvider()
            );
            
            if (providerData != null) {
                context.setProviderData(providerData);
                context.setProviderScore(providerData.getScore());
                context.setProviderRecommendation(providerData.getRecommendation());
            }
        }
    }

    private KycUpdateResult processKycStatusChange(KycUpdateContext context) {
        KycUpdateResult.Builder resultBuilder = KycUpdateResult.builder()
                .eventId(context.getEventId())
                .userId(context.getUserId())
                .newStatus(context.getNewStatus())
                .previousStatus(context.getPreviousStatus())
                .processingStartTime(Instant.now());

        try {
            KycStatus kycStatus = context.getCurrentKycStatus() != null ? 
                context.getCurrentKycStatus() : new KycStatus();
            
            kycStatus.setUserId(context.getUserId());
            kycStatus.setStatus(context.getNewStatus());
            kycStatus.setKycLevel(context.getKycLevel());
            kycStatus.setVerificationScore(context.getVerificationScore());
            kycStatus.setProvider(context.getProvider());
            kycStatus.setLastVerificationDate(context.getTimestamp());
            kycStatus.setVerificationAttempts(kycStatus.getVerificationAttempts() + 1);
            
            switch (context.getNewStatus()) {
                case "APPROVED":
                    return processKycApproval(context, kycStatus, resultBuilder);
                case "REJECTED":
                    return processKycRejection(context, kycStatus, resultBuilder);
                case "PENDING_REVIEW":
                    return processKycPendingReview(context, kycStatus, resultBuilder);
                case "DOCUMENTS_REQUIRED":
                    return processDocumentsRequired(context, kycStatus, resultBuilder);
                case "VERIFICATION_IN_PROGRESS":
                    return processVerificationInProgress(context, kycStatus, resultBuilder);
                case "MANUAL_REVIEW":
                    return processManualReview(context, kycStatus, resultBuilder);
                case "PARTIALLY_VERIFIED":
                    return processPartialVerification(context, kycStatus, resultBuilder);
                case "EXPIRED":
                    return processKycExpiration(context, kycStatus, resultBuilder);
                case "SUSPENDED":
                    return processKycSuspension(context, kycStatus, resultBuilder);
                case "RESUBMISSION_REQUIRED":
                    return processResubmissionRequired(context, kycStatus, resultBuilder);
                default:
                    return processGenericStatusUpdate(context, kycStatus, resultBuilder);
            }
        } finally {
            resultBuilder.processingEndTime(Instant.now());
        }
    }

    private KycUpdateResult processKycApproval(KycUpdateContext context, KycStatus kycStatus, 
                                              KycUpdateResult.Builder resultBuilder) {
        kycStatus.setApprovalDate(context.getTimestamp());
        kycStatus.setExpiryDate(calculateKycExpiryDate(context.getKycLevel()));
        kycStatus.setVerified(true);
        
        kycStatusRepository.save(kycStatus);
        
        saveKycDocuments(context);
        recordKycHistory(context, "APPROVED", null);
        
        AccountTier newTier = determineAccountTier(context.getKycLevel());
        User user = context.getUser();
        user.setTier(newTier.toString());
        user.setKycVerified(true);
        userRepository.save(user);
        
        AccountLimits newLimits = calculateNewLimits(context.getUserId(), newTier);
        limitsManagementService.updateAccountLimits(context.getUserId(), newLimits);
        
        List<String> unlockedFeatures = unlockFeatures(context.getUserId(), context.getKycLevel());
        
        userNotificationService.sendKycApprovalNotification(
            context.getUserId(), 
            context.getKycLevel(),
            newTier.toString(),
            unlockedFeatures
        );
        
        updateComplianceStatus(context, "COMPLIANT");
        
        kafkaTemplate.send("kyc-approved-events", Map.of(
            "userId", context.getUserId(),
            "kycLevel", context.getKycLevel(),
            "verificationScore", context.getVerificationScore(),
            "newTier", newTier.toString(),
            "timestamp", context.getTimestamp()
        ));
        
        return resultBuilder
                .success(true)
                .kycApproved(true)
                .newAccountTier(newTier.toString())
                .processingDetails(Map.of(
                    "approvalDate", kycStatus.getApprovalDate().toString(),
                    "expiryDate", kycStatus.getExpiryDate().toString(),
                    "kycLevel", context.getKycLevel(),
                    "verificationScore", context.getVerificationScore().toString(),
                    "newTier", newTier.toString(),
                    "limitsUpdated", true,
                    "featuresUnlocked", unlockedFeatures.size(),
                    "notificationSent", true
                ))
                .build();
    }

    private KycUpdateResult processKycRejection(KycUpdateContext context, KycStatus kycStatus, 
                                               KycUpdateResult.Builder resultBuilder) {
        kycStatus.setRejectionDate(context.getTimestamp());
        kycStatus.setRejectionReasons(context.getFailureReasons());
        kycStatus.setVerified(false);
        kycStatus.setNextRetryDate(calculateNextRetryDate(kycStatus.getVerificationAttempts()));
        
        kycStatusRepository.save(kycStatus);
        
        recordKycHistory(context, "REJECTED", String.join(", ", context.getFailureReasons()));
        
        List<String> missingRequirements = identifyMissingRequirements(context);
        List<String> improvementSuggestions = generateImprovementSuggestions(context);
        
        userNotificationService.sendKycRejectionNotification(
            context.getUserId(),
            context.getFailureReasons(),
            missingRequirements,
            improvementSuggestions,
            kycStatus.getNextRetryDate()
        );
        
        updateComplianceStatus(context, "NON_COMPLIANT");
        
        if (kycStatus.getVerificationAttempts() >= 3) {
            triggerManualReview(context);
        }
        
        kafkaTemplate.send("kyc-rejected-events", Map.of(
            "userId", context.getUserId(),
            "rejectionReasons", context.getFailureReasons(),
            "verificationAttempts", kycStatus.getVerificationAttempts(),
            "nextRetryDate", kycStatus.getNextRetryDate(),
            "timestamp", context.getTimestamp()
        ));
        
        return resultBuilder
                .success(true)
                .kycApproved(false)
                .failureReasons(context.getFailureReasons())
                .processingDetails(Map.of(
                    "rejectionDate", kycStatus.getRejectionDate().toString(),
                    "rejectionReasons", String.join(", ", context.getFailureReasons()),
                    "verificationAttempts", kycStatus.getVerificationAttempts(),
                    "nextRetryDate", kycStatus.getNextRetryDate().toString(),
                    "missingRequirements", missingRequirements.size(),
                    "manualReviewTriggered", kycStatus.getVerificationAttempts() >= 3
                ))
                .build();
    }

    private KycUpdateResult processKycPendingReview(KycUpdateContext context, KycStatus kycStatus, 
                                                   KycUpdateResult.Builder resultBuilder) {
        kycStatus.setReviewStartDate(context.getTimestamp());
        kycStatus.setEstimatedCompletionTime(calculateEstimatedReviewTime(context));
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "PENDING_REVIEW", "Verification submitted for review");
        
        String reviewId = UUID.randomUUID().toString();
        createReviewTask(context, reviewId);
        
        userNotificationService.sendKycPendingReviewNotification(
            context.getUserId(),
            kycStatus.getEstimatedCompletionTime()
        );
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "reviewStartDate", kycStatus.getReviewStartDate().toString(),
                    "estimatedCompletionTime", kycStatus.getEstimatedCompletionTime().toString(),
                    "reviewId", reviewId,
                    "reviewPriority", determineReviewPriority(context)
                ))
                .build();
    }

    private KycUpdateResult processDocumentsRequired(KycUpdateContext context, KycStatus kycStatus, 
                                                    KycUpdateResult.Builder resultBuilder) {
        Set<String> requiredDocs = getRequiredDocumentsForLevel(context.getKycLevel());
        Set<String> providedDocs = context.getDocuments().stream()
                .map(KycDocument::getDocumentType)
                .collect(Collectors.toSet());
        
        Set<String> missingDocs = new HashSet<>(requiredDocs);
        missingDocs.removeAll(providedDocs);
        
        kycStatus.setRequiredDocuments(new ArrayList<>(missingDocs));
        kycStatus.setDocumentDeadline(Instant.now().plusSeconds(7 * 24 * 3600)); // 7 days
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "DOCUMENTS_REQUIRED", "Missing: " + String.join(", ", missingDocs));
        
        Map<String, String> documentGuidelines = getDocumentGuidelines(missingDocs);
        
        userNotificationService.sendDocumentsRequiredNotification(
            context.getUserId(),
            missingDocs,
            documentGuidelines,
            kycStatus.getDocumentDeadline()
        );
        
        scheduleDocumentReminder(context.getUserId(), missingDocs, kycStatus.getDocumentDeadline());
        
        return resultBuilder
                .success(true)
                .documentsRequired(missingDocs)
                .processingDetails(Map.of(
                    "missingDocuments", String.join(", ", missingDocs),
                    "documentDeadline", kycStatus.getDocumentDeadline().toString(),
                    "reminderScheduled", true,
                    "guidelinesProvided", documentGuidelines.size()
                ))
                .build();
    }

    private KycUpdateResult processVerificationInProgress(KycUpdateContext context, KycStatus kycStatus, 
                                                         KycUpdateResult.Builder resultBuilder) {
        kycStatus.setVerificationStartTime(context.getTimestamp());
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "VERIFICATION_IN_PROGRESS", "Verification started");
        
        List<String> verificationSteps = getVerificationSteps(context.getKycLevel());
        Map<String, String> stepStatuses = initializeStepStatuses(verificationSteps);
        
        String verificationSessionId = UUID.randomUUID().toString();
        createVerificationSession(context, verificationSessionId, verificationSteps);
        
        userNotificationService.sendVerificationInProgressNotification(
            context.getUserId(),
            verificationSteps
        );
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "verificationStartTime", kycStatus.getVerificationStartTime().toString(),
                    "verificationSessionId", verificationSessionId,
                    "totalSteps", verificationSteps.size(),
                    "stepStatuses", stepStatuses
                ))
                .build();
    }

    private KycUpdateResult processManualReview(KycUpdateContext context, KycStatus kycStatus, 
                                               KycUpdateResult.Builder resultBuilder) {
        kycStatus.setManualReviewRequired(true);
        kycStatus.setManualReviewReason(context.getUpdateReason());
        kycStatus.setReviewAssignedTo(assignReviewer(context));
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "MANUAL_REVIEW", context.getUpdateReason());
        
        String caseId = createComplianceCase(context);
        
        notifyComplianceTeam(context, caseId);
        
        userNotificationService.sendManualReviewNotification(
            context.getUserId(),
            context.getUpdateReason(),
            calculateManualReviewSLA()
        );
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "manualReviewRequired", true,
                    "reviewReason", context.getUpdateReason(),
                    "assignedTo", kycStatus.getReviewAssignedTo(),
                    "caseId", caseId,
                    "slaHours", calculateManualReviewSLA()
                ))
                .build();
    }

    private KycUpdateResult processPartialVerification(KycUpdateContext context, KycStatus kycStatus, 
                                                      KycUpdateResult.Builder resultBuilder) {
        Map<String, Boolean> verificationComponents = analyzeVerificationComponents(context);
        
        kycStatus.setPartiallyVerified(true);
        kycStatus.setVerifiedComponents(
            verificationComponents.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
        );
        kycStatus.setUnverifiedComponents(
            verificationComponents.entrySet().stream()
                .filter(e -> !e.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
        );
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "PARTIALLY_VERIFIED", 
            "Verified: " + kycStatus.getVerifiedComponents().size() + 
            ", Pending: " + kycStatus.getUnverifiedComponents().size());
        
        AccountTier partialTier = determinePartialTier(kycStatus.getVerifiedComponents());
        if (partialTier != null) {
            applyPartialLimits(context.getUserId(), partialTier);
        }
        
        userNotificationService.sendPartialVerificationNotification(
            context.getUserId(),
            kycStatus.getVerifiedComponents(),
            kycStatus.getUnverifiedComponents()
        );
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "verifiedComponents", String.join(", ", kycStatus.getVerifiedComponents()),
                    "unverifiedComponents", String.join(", ", kycStatus.getUnverifiedComponents()),
                    "partialTier", partialTier != null ? partialTier.toString() : "NONE",
                    "partialLimitsApplied", partialTier != null
                ))
                .build();
    }

    private KycUpdateResult processKycExpiration(KycUpdateContext context, KycStatus kycStatus, 
                                                KycUpdateResult.Builder resultBuilder) {
        kycStatus.setExpired(true);
        kycStatus.setExpirationDate(context.getTimestamp());
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "EXPIRED", "KYC verification expired");
        
        User user = context.getUser();
        user.setKycVerified(false);
        userRepository.save(user);
        
        AccountTier downgradedTier = AccountTier.BASIC;
        accountTierService.downgradeAccount(context.getUserId(), downgradedTier);
        
        limitsManagementService.applyExpiredKycLimits(context.getUserId());
        
        Instant renewalDeadline = Instant.now().plusSeconds(30 * 24 * 3600); // 30 days
        
        userNotificationService.sendKycExpirationNotification(
            context.getUserId(),
            renewalDeadline,
            downgradedTier.toString()
        );
        
        scheduleExpirationReminders(context.getUserId(), renewalDeadline);
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "expirationDate", kycStatus.getExpirationDate().toString(),
                    "downgradedTo", downgradedTier.toString(),
                    "renewalDeadline", renewalDeadline.toString(),
                    "limitsReduced", true,
                    "remindersScheduled", true
                ))
                .build();
    }

    private KycUpdateResult processKycSuspension(KycUpdateContext context, KycStatus kycStatus, 
                                                KycUpdateResult.Builder resultBuilder) {
        kycStatus.setSuspended(true);
        kycStatus.setSuspensionDate(context.getTimestamp());
        kycStatus.setSuspensionReason(context.getUpdateReason());
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "SUSPENDED", context.getUpdateReason());
        
        suspendUserPrivileges(context.getUserId());
        freezeTransactions(context.getUserId());
        
        String investigationId = createInvestigation(context);
        
        userNotificationService.sendKycSuspensionNotification(
            context.getUserId(),
            context.getUpdateReason(),
            getAppealProcess()
        );
        
        notifyComplianceTeam(context, investigationId);
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "suspensionDate", kycStatus.getSuspensionDate().toString(),
                    "suspensionReason", context.getUpdateReason(),
                    "investigationId", investigationId,
                    "privilegesSuspended", true,
                    "transactionsFrozen", true,
                    "appealAvailable", true
                ))
                .build();
    }

    private KycUpdateResult processResubmissionRequired(KycUpdateContext context, KycStatus kycStatus, 
                                                       KycUpdateResult.Builder resultBuilder) {
        kycStatus.setResubmissionRequired(true);
        kycStatus.setResubmissionReasons(context.getFailureReasons());
        kycStatus.setResubmissionDeadline(Instant.now().plusSeconds(14 * 24 * 3600)); // 14 days
        
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, "RESUBMISSION_REQUIRED", String.join(", ", context.getFailureReasons()));
        
        Map<String, String> resubmissionGuidelines = generateResubmissionGuidelines(context);
        List<String> commonMistakes = identifyCommonMistakes(context);
        
        userNotificationService.sendResubmissionRequiredNotification(
            context.getUserId(),
            context.getFailureReasons(),
            resubmissionGuidelines,
            commonMistakes,
            kycStatus.getResubmissionDeadline()
        );
        
        scheduleResubmissionReminders(context.getUserId(), kycStatus.getResubmissionDeadline());
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "resubmissionRequired", true,
                    "resubmissionReasons", String.join(", ", context.getFailureReasons()),
                    "resubmissionDeadline", kycStatus.getResubmissionDeadline().toString(),
                    "guidelinesProvided", resubmissionGuidelines.size(),
                    "remindersScheduled", true
                ))
                .build();
    }

    private KycUpdateResult processGenericStatusUpdate(KycUpdateContext context, KycStatus kycStatus, 
                                                      KycUpdateResult.Builder resultBuilder) {
        kycStatusRepository.save(kycStatus);
        recordKycHistory(context, context.getNewStatus(), context.getUpdateReason());
        
        userNotificationService.sendKycStatusUpdateNotification(
            context.getUserId(),
            context.getNewStatus(),
            context.getUpdateReason()
        );
        
        return resultBuilder
                .success(true)
                .processingDetails(Map.of(
                    "statusUpdated", true,
                    "newStatus", context.getNewStatus(),
                    "updateReason", context.getUpdateReason() != null ? context.getUpdateReason() : "N/A"
                ))
                .build();
    }

    private void executeAutomatedActions(KycUpdateContext context, KycUpdateResult result) {
        try {
            if (result.isSuccess()) {
                switch (context.getNewStatus()) {
                    case "APPROVED":
                        executeApprovalActions(context, result);
                        break;
                    case "REJECTED":
                        executeRejectionActions(context, result);
                        break;
                    case "EXPIRED":
                        executeExpirationActions(context, result);
                        break;
                    case "SUSPENDED":
                        executeSuspensionActions(context, result);
                        break;
                    default:
                        executeDefaultActions(context, result);
                }
            }
            
            executeUniversalActions(context, result);
            
        } catch (Exception e) {
            log.error("Error executing automated actions for KYC update: {}", context.getEventId(), e);
            metricsService.incrementCounter("kyc_update_action_errors", "status", context.getNewStatus());
        }
    }

    private void executeApprovalActions(KycUpdateContext context, KycUpdateResult result) {
        enableUserFeatures(context.getUserId(), context.getKycLevel());
        updateTransactionLimits(context.getUserId(), result.getNewAccountTier());
        removeRestrictions(context.getUserId());
        sendWelcomePackage(context.getUserId(), context.getKycLevel());
        updateMarketingSegment(context.getUserId(), result.getNewAccountTier());
    }

    private void executeRejectionActions(KycUpdateContext context, KycUpdateResult result) {
        if (context.getVerificationAttempts() >= 5) {
            flagForManualIntervention(context.getUserId());
        }
        scheduleFollowUpContact(context.getUserId());
        updateRiskScore(context.getUserId(), "KYC_REJECTION");
    }

    private void executeExpirationActions(KycUpdateContext context, KycUpdateResult result) {
        restrictHighValueTransactions(context.getUserId());
        queueForRenewalCampaign(context.getUserId());
        updateComplianceRecord(context.getUserId(), "KYC_EXPIRED");
    }

    private void executeSuspensionActions(KycUpdateContext context, KycUpdateResult result) {
        blockAllTransactions(context.getUserId());
        notifyLegalTeam(context.getUserId(), context.getUpdateReason());
        createSuspensionRecord(context.getUserId(), context.getUpdateReason());
    }

    private void executeDefaultActions(KycUpdateContext context, KycUpdateResult result) {
        updateUserActivity(context.getUserId(), "KYC_STATUS_CHANGE", context.getNewStatus());
    }

    private void executeUniversalActions(KycUpdateContext context, KycUpdateResult result) {
        updateKycMetrics(context, result);
        recordComplianceAudit(context, result);
        syncWithExternalSystems(context, result);
        
        if (isHighRiskChange(context)) {
            triggerRiskReview(context);
        }
    }

    private void updateKycMetrics(KycUpdateContext context, KycUpdateResult result) {
        KycMetrics metrics = new KycMetrics();
        metrics.setUserId(context.getUserId());
        metrics.setStatus(context.getNewStatus());
        metrics.setKycLevel(context.getKycLevel());
        metrics.setVerificationScore(context.getVerificationScore());
        metrics.setProvider(context.getProvider());
        metrics.setProcessingTime(
            result.getProcessingEndTime().toEpochMilli() - result.getProcessingStartTime().toEpochMilli()
        );
        metrics.setTimestamp(context.getTimestamp());
        
        kycService.recordKycMetrics(metrics);
    }

    private Instant calculateKycExpiryDate(String kycLevel) {
        long daysToExpiry;
        switch (kycLevel) {
            case "LEVEL_1":
                daysToExpiry = 365; // 1 year
                break;
            case "LEVEL_2":
                daysToExpiry = 730; // 2 years
                break;
            case "LEVEL_3":
            case "LEVEL_4":
                daysToExpiry = 1095; // 3 years
                break;
            default:
                daysToExpiry = 365;
        }
        return Instant.now().plusSeconds(daysToExpiry * 24 * 3600);
    }

    private Instant calculateNextRetryDate(int attemptNumber) {
        long hoursToWait = Math.min(Math.pow(2, attemptNumber) * 24, 168); // Max 1 week
        return Instant.now().plusSeconds(hoursToWait * 3600);
    }

    private Instant calculateEstimatedReviewTime(KycUpdateContext context) {
        int baseHours = 24;
        if (context.getRiskLevel() != null && context.getRiskLevel().equals("HIGH")) {
            baseHours = 48;
        }
        if (context.isPepStatus() || context.getSanctionsStatus() != null) {
            baseHours = 72;
        }
        return Instant.now().plusSeconds(baseHours * 3600);
    }

    private AccountTier determineAccountTier(String kycLevel) {
        switch (kycLevel) {
            case "LEVEL_0":
                return AccountTier.BASIC;
            case "LEVEL_1":
                return AccountTier.STANDARD;
            case "LEVEL_2":
                return AccountTier.PREMIUM;
            case "LEVEL_3":
                return AccountTier.GOLD;
            case "LEVEL_4":
                return AccountTier.PLATINUM;
            default:
                return AccountTier.BASIC;
        }
    }

    private AccountTier determinePartialTier(List<String> verifiedComponents) {
        if (verifiedComponents.containsAll(List.of("IDENTITY", "ADDRESS"))) {
            return AccountTier.STANDARD;
        } else if (verifiedComponents.contains("IDENTITY")) {
            return AccountTier.BASIC_PLUS;
        }
        return null;
    }

    private AccountLimits calculateNewLimits(String userId, AccountTier tier) {
        return AccountLimits.builder()
                .userId(userId)
                .dailyTransactionLimit(getLimitForTier(tier, "DAILY"))
                .monthlyTransactionLimit(getLimitForTier(tier, "MONTHLY"))
                .maxSingleTransactionAmount(getLimitForTier(tier, "SINGLE"))
                .internationalTransferEnabled(tier.ordinal() >= AccountTier.PREMIUM.ordinal())
                .cryptoTradingEnabled(tier.ordinal() >= AccountTier.STANDARD.ordinal())
                .build();
    }

    private BigDecimal getLimitForTier(AccountTier tier, String limitType) {
        Map<String, BigDecimal> limits = new HashMap<>();
        switch (tier) {
            case BASIC:
                limits.put("DAILY", BigDecimal.valueOf(1000));
                limits.put("MONTHLY", BigDecimal.valueOf(5000));
                limits.put("SINGLE", BigDecimal.valueOf(500));
                break;
            case STANDARD:
                limits.put("DAILY", BigDecimal.valueOf(5000));
                limits.put("MONTHLY", BigDecimal.valueOf(25000));
                limits.put("SINGLE", BigDecimal.valueOf(2500));
                break;
            case PREMIUM:
                limits.put("DAILY", BigDecimal.valueOf(25000));
                limits.put("MONTHLY", BigDecimal.valueOf(100000));
                limits.put("SINGLE", BigDecimal.valueOf(10000));
                break;
            case GOLD:
                limits.put("DAILY", BigDecimal.valueOf(100000));
                limits.put("MONTHLY", BigDecimal.valueOf(500000));
                limits.put("SINGLE", BigDecimal.valueOf(50000));
                break;
            case PLATINUM:
                limits.put("DAILY", BigDecimal.valueOf(1000000));
                limits.put("MONTHLY", BigDecimal.valueOf(10000000));
                limits.put("SINGLE", BigDecimal.valueOf(500000));
                break;
            default:
                limits.put("DAILY", BigDecimal.valueOf(500));
                limits.put("MONTHLY", BigDecimal.valueOf(2500));
                limits.put("SINGLE", BigDecimal.valueOf(250));
        }
        return limits.get(limitType);
    }

    private List<String> unlockFeatures(String userId, String kycLevel) {
        List<String> features = new ArrayList<>();
        switch (kycLevel) {
            case "LEVEL_1":
                features.addAll(List.of("P2P_TRANSFERS", "BILL_PAYMENTS", "MOBILE_TOPUP"));
                break;
            case "LEVEL_2":
                features.addAll(List.of("INTERNATIONAL_TRANSFERS", "CRYPTO_TRADING", "INVESTMENT_PRODUCTS"));
                break;
            case "LEVEL_3":
                features.addAll(List.of("PREMIUM_CARDS", "WEALTH_MANAGEMENT", "FOREX_TRADING"));
                break;
            case "LEVEL_4":
                features.addAll(List.of("PRIVATE_BANKING", "INSTITUTIONAL_TRADING", "API_ACCESS"));
                break;
        }
        
        kycService.unlockUserFeatures(userId, features);
        return features;
    }

    private List<String> identifyMissingRequirements(KycUpdateContext context) {
        List<String> missing = new ArrayList<>();
        
        if (!hasValidIdentityDocument(context)) {
            missing.add("Valid government-issued ID");
        }
        if (!hasValidAddressProof(context)) {
            missing.add("Recent address proof (within 3 months)");
        }
        if (context.getKycLevel().equals("LEVEL_3") && !hasIncomeProof(context)) {
            missing.add("Income verification documents");
        }
        
        return missing;
    }

    private List<String> generateImprovementSuggestions(KycUpdateContext context) {
        List<String> suggestions = new ArrayList<>();
        
        for (String reason : context.getFailureReasons()) {
            if (reason.contains("QUALITY")) {
                suggestions.add("Ensure documents are clear and all text is readable");
            }
            if (reason.contains("EXPIRED")) {
                suggestions.add("Provide documents that are currently valid");
            }
            if (reason.contains("MISMATCH")) {
                suggestions.add("Ensure all information matches across documents");
            }
        }
        
        return suggestions;
    }

    private Map<String, String> getDocumentGuidelines(Set<String> documentTypes) {
        Map<String, String> guidelines = new HashMap<>();
        
        for (String docType : documentTypes) {
            switch (docType) {
                case "IDENTITY_DOCUMENT":
                    guidelines.put(docType, "Passport, driver's license, or national ID. Must be valid and clearly visible.");
                    break;
                case "ADDRESS_PROOF":
                    guidelines.put(docType, "Utility bill, bank statement, or rental agreement dated within last 3 months.");
                    break;
                case "INCOME_PROOF":
                    guidelines.put(docType, "Pay slips, tax returns, or employment letter showing income details.");
                    break;
            }
        }
        
        return guidelines;
    }

    private Map<String, String> generateResubmissionGuidelines(KycUpdateContext context) {
        Map<String, String> guidelines = new HashMap<>();
        
        guidelines.put("DOCUMENT_QUALITY", "Upload high-resolution images with all text clearly visible");
        guidelines.put("DOCUMENT_VALIDITY", "Ensure all documents are current and not expired");
        guidelines.put("INFORMATION_CONSISTENCY", "Verify that names and addresses match across all documents");
        guidelines.put("SELFIE_REQUIREMENTS", "Take selfie in good lighting with face clearly visible");
        
        return guidelines;
    }

    private List<String> identifyCommonMistakes(KycUpdateContext context) {
        return List.of(
            "Blurry or low-quality document images",
            "Expired identification documents",
            "Address proof older than 3 months",
            "Name mismatches between documents",
            "Incomplete document uploads",
            "Wrong document type submitted"
        );
    }

    private List<String> getVerificationSteps(String kycLevel) {
        List<String> steps = new ArrayList<>();
        steps.add("Identity verification");
        steps.add("Document authenticity check");
        
        if (!kycLevel.equals("LEVEL_0")) {
            steps.add("Address verification");
            steps.add("Facial recognition");
        }
        
        if (kycLevel.equals("LEVEL_2") || kycLevel.equals("LEVEL_3") || kycLevel.equals("LEVEL_4")) {
            steps.add("PEP and sanctions screening");
            steps.add("Adverse media check");
        }
        
        if (kycLevel.equals("LEVEL_3") || kycLevel.equals("LEVEL_4")) {
            steps.add("Income verification");
            steps.add("Source of funds check");
        }
        
        return steps;
    }

    private Map<String, String> initializeStepStatuses(List<String> steps) {
        Map<String, String> statuses = new HashMap<>();
        for (String step : steps) {
            statuses.put(step, "PENDING");
        }
        return statuses;
    }

    private Map<String, Boolean> analyzeVerificationComponents(KycUpdateContext context) {
        Map<String, Boolean> components = new HashMap<>();
        
        components.put("IDENTITY", context.getVerificationResults().containsKey("identityVerified") &&
            Boolean.parseBoolean(context.getVerificationResults().get("identityVerified").toString()));
        
        components.put("ADDRESS", context.getVerificationResults().containsKey("addressVerified") &&
            Boolean.parseBoolean(context.getVerificationResults().get("addressVerified").toString()));
        
        components.put("FACIAL_RECOGNITION", context.getVerificationResults().containsKey("facialMatch") &&
            Boolean.parseBoolean(context.getVerificationResults().get("facialMatch").toString()));
        
        components.put("PEP_SCREENING", !context.isPepStatus());
        
        components.put("SANCTIONS_SCREENING", context.getSanctionsStatus() == null || 
            context.getSanctionsStatus().equals("CLEAR"));
        
        return components;
    }

    private String determineReviewPriority(KycUpdateContext context) {
        if (context.getRiskScore() != null && context.getRiskScore().compareTo(BigDecimal.valueOf(0.8)) > 0) {
            return "HIGH";
        }
        if (context.isPepStatus() || context.getSanctionsStatus() != null) {
            return "HIGH";
        }
        if (context.getVerificationAttempts() > 3) {
            return "MEDIUM";
        }
        return "NORMAL";
    }

    private int calculateManualReviewSLA() {
        return 48; // hours
    }

    private String getAppealProcess() {
        return "You can appeal this decision by submitting additional documentation through our support portal.";
    }

    private boolean hasValidIdentityDocument(KycUpdateContext context) {
        return context.getDocuments().stream()
                .anyMatch(doc -> doc.getDocumentType().equals("IDENTITY_DOCUMENT") &&
                        doc.getVerificationStatus().equals("VERIFIED"));
    }

    private boolean hasValidAddressProof(KycUpdateContext context) {
        return context.getDocuments().stream()
                .anyMatch(doc -> doc.getDocumentType().equals("ADDRESS_PROOF") &&
                        doc.getVerificationStatus().equals("VERIFIED"));
    }

    private boolean hasIncomeProof(KycUpdateContext context) {
        return context.getDocuments().stream()
                .anyMatch(doc -> doc.getDocumentType().equals("INCOME_PROOF") &&
                        doc.getVerificationStatus().equals("VERIFIED"));
    }

    private boolean isHighRiskChange(KycUpdateContext context) {
        return context.getNewStatus().equals("REJECTED") && context.getVerificationAttempts() > 3 ||
               context.getNewStatus().equals("SUSPENDED") ||
               (context.isPepStatus() && context.getNewStatus().equals("APPROVED"));
    }

    private void saveKycDocuments(KycUpdateContext context) {
        for (KycDocument document : context.getDocuments()) {
            document.setUserId(context.getUserId());
            document.setUploadedAt(context.getTimestamp());
            document.setVerifiedAt(context.getTimestamp());
            kycDocumentRepository.save(document);
        }
    }

    private void recordKycHistory(KycUpdateContext context, String status, String details) {
        KycHistory history = KycHistory.builder()
                .userId(context.getUserId())
                .status(status)
                .previousStatus(context.getPreviousStatus())
                .kycLevel(context.getKycLevel())
                .provider(context.getProvider())
                .verificationScore(context.getVerificationScore())
                .details(details)
                .timestamp(context.getTimestamp())
                .build();
        
        kycHistoryRepository.save(history);
    }

    private void updateComplianceStatus(KycUpdateContext context, String status) {
        complianceService.updateUserComplianceStatus(context.getUserId(), status, "KYC_" + context.getNewStatus());
    }

    private void createReviewTask(KycUpdateContext context, String reviewId) {
        kafkaTemplate.send("review-tasks", Map.of(
            "reviewId", reviewId,
            "userId", context.getUserId(),
            "taskType", "KYC_REVIEW",
            "priority", determineReviewPriority(context),
            "deadline", calculateEstimatedReviewTime(context)
        ));
    }

    private void createVerificationSession(KycUpdateContext context, String sessionId, List<String> steps) {
        kafkaTemplate.send("verification-sessions", Map.of(
            "sessionId", sessionId,
            "userId", context.getUserId(),
            "kycLevel", context.getKycLevel(),
            "verificationSteps", steps,
            "startTime", context.getTimestamp()
        ));
    }

    private String assignReviewer(KycUpdateContext context) {
        return complianceService.assignReviewer(context.getUserId(), determineReviewPriority(context));
    }

    private String createComplianceCase(KycUpdateContext context) {
        return complianceService.createCase(
            context.getUserId(),
            "KYC_MANUAL_REVIEW",
            context.getUpdateReason(),
            determineReviewPriority(context)
        );
    }

    private String createInvestigation(KycUpdateContext context) {
        return complianceService.createInvestigation(
            context.getUserId(),
            "KYC_SUSPENSION",
            context.getUpdateReason()
        );
    }

    private void triggerManualReview(KycUpdateContext context) {
        kafkaTemplate.send("manual-review-events", Map.of(
            "userId", context.getUserId(),
            "reviewType", "KYC_FAILURE",
            "attempts", context.getVerificationAttempts(),
            "failureReasons", context.getFailureReasons()
        ));
    }

    private void scheduleDocumentReminder(String userId, Set<String> missingDocs, Instant deadline) {
        kafkaTemplate.send("reminder-events", Map.of(
            "userId", userId,
            "reminderType", "DOCUMENT_SUBMISSION",
            "missingDocuments", missingDocs,
            "deadline", deadline.toString()
        ));
    }

    private void scheduleExpirationReminders(String userId, Instant renewalDeadline) {
        kafkaTemplate.send("reminder-events", Map.of(
            "userId", userId,
            "reminderType", "KYC_RENEWAL",
            "renewalDeadline", renewalDeadline.toString()
        ));
    }

    private void scheduleResubmissionReminders(String userId, Instant deadline) {
        kafkaTemplate.send("reminder-events", Map.of(
            "userId", userId,
            "reminderType", "KYC_RESUBMISSION",
            "deadline", deadline.toString()
        ));
    }

    private void scheduleFollowUpContact(String userId) {
        kafkaTemplate.send("follow-up-events", Map.of(
            "userId", userId,
            "followUpType", "KYC_REJECTION",
            "scheduledTime", Instant.now().plusSeconds(3 * 24 * 3600).toString()
        ));
    }

    private void applyPartialLimits(String userId, AccountTier tier) {
        limitsManagementService.applyPartialLimits(userId, tier);
    }

    private void suspendUserPrivileges(String userId) {
        accountTierService.suspendPrivileges(userId);
    }

    private void freezeTransactions(String userId) {
        kafkaTemplate.send("transaction-freeze-events", Map.of(
            "userId", userId,
            "freezeReason", "KYC_SUSPENDED"
        ));
    }

    private void notifyComplianceTeam(KycUpdateContext context, String caseId) {
        complianceService.notifyTeam(
            caseId,
            context.getUserId(),
            "KYC_REVIEW_REQUIRED",
            context.getUpdateReason()
        );
    }

    private void notifyLegalTeam(String userId, String reason) {
        kafkaTemplate.send("legal-notifications", Map.of(
            "userId", userId,
            "notificationType", "KYC_SUSPENSION",
            "reason", reason
        ));
    }

    private void enableUserFeatures(String userId, String kycLevel) {
        List<String> features = unlockFeatures(userId, kycLevel);
        accountTierService.enableFeatures(userId, features);
    }

    private void updateTransactionLimits(String userId, String tier) {
        limitsManagementService.updateLimitsForTier(userId, tier);
    }

    private void removeRestrictions(String userId) {
        accountTierService.removeRestrictions(userId);
    }

    private void sendWelcomePackage(String userId, String kycLevel) {
        userNotificationService.sendWelcomePackage(userId, kycLevel);
    }

    private void updateMarketingSegment(String userId, String tier) {
        kafkaTemplate.send("marketing-segment-updates", Map.of(
            "userId", userId,
            "segment", tier
        ));
    }

    private void flagForManualIntervention(String userId) {
        complianceService.flagUser(userId, "EXCESSIVE_KYC_FAILURES");
    }

    private void updateRiskScore(String userId, String reason) {
        kafkaTemplate.send("risk-score-updates", Map.of(
            "userId", userId,
            "updateReason", reason
        ));
    }

    private void restrictHighValueTransactions(String userId) {
        limitsManagementService.restrictHighValueTransactions(userId);
    }

    private void queueForRenewalCampaign(String userId) {
        kafkaTemplate.send("marketing-campaigns", Map.of(
            "userId", userId,
            "campaignType", "KYC_RENEWAL"
        ));
    }

    private void updateComplianceRecord(String userId, String status) {
        complianceService.updateRecord(userId, status);
    }

    private void blockAllTransactions(String userId) {
        kafkaTemplate.send("transaction-block-events", Map.of(
            "userId", userId,
            "blockReason", "KYC_SUSPENDED"
        ));
    }

    private void createSuspensionRecord(String userId, String reason) {
        complianceService.createSuspensionRecord(userId, reason);
    }

    private void updateUserActivity(String userId, String activityType, String details) {
        kafkaTemplate.send("user-activity-events", Map.of(
            "userId", userId,
            "activityType", activityType,
            "details", details,
            "timestamp", Instant.now().toString()
        ));
    }

    private void recordComplianceAudit(KycUpdateContext context, KycUpdateResult result) {
        auditService.recordComplianceAudit(
            context.getUserId(),
            "KYC_STATUS_CHANGE",
            context.getNewStatus(),
            result.getProcessingDetails()
        );
    }

    private void syncWithExternalSystems(KycUpdateContext context, KycUpdateResult result) {
        kafkaTemplate.send("external-sync-events", Map.of(
            "userId", context.getUserId(),
            "syncType", "KYC_STATUS",
            "status", context.getNewStatus(),
            "kycLevel", context.getKycLevel()
        ));
    }

    private void triggerRiskReview(KycUpdateContext context) {
        kafkaTemplate.send("risk-review-events", Map.of(
            "userId", context.getUserId(),
            "reviewType", "KYC_HIGH_RISK_CHANGE",
            "kycStatus", context.getNewStatus(),
            "riskFactors", context.getHighRiskIndicators()
        ));
    }

    private void handleProcessingError(String eventId, String kycStatus, String userId, String eventPayload, 
                                     Exception e, Acknowledgment acknowledgment) {
        log.error("Error processing KYC status update: {} to status: {} for user: {}", eventId, kycStatus, userId, e);
        
        try {
            auditService.logUserEvent(eventId, "KYC_STATUS_UPDATE", userId, "ERROR", Map.of("error", e.getMessage()));
            
            metricsService.incrementCounter("kyc_status_update_errors", 
                "status", kycStatus != null ? kycStatus : "UNKNOWN",
                "error_type", e.getClass().getSimpleName());
            
            if (isRetryableError(e)) {
                sendToDlq(eventPayload, "kyc-status-updates-dlq", "RETRYABLE_ERROR", e.getMessage());
            } else {
                sendToDlq(eventPayload, "kyc-status-updates-dlq", "NON_RETRYABLE_ERROR", e.getMessage());
            }
            
        } catch (Exception dlqError) {
            log.error("Failed to send message to DLQ", dlqError);
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private boolean isRetryableError(Exception e) {
        return e instanceof org.springframework.dao.TransientDataAccessException ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof org.springframework.web.client.ResourceAccessException;
    }

    private void sendToDlq(String originalMessage, String dlqTopic, String errorType, String errorMessage) {
        Map<String, Object> dlqMessage = Map.of(
            "originalMessage", originalMessage,
            "errorType", errorType,
            "errorMessage", errorMessage,
            "timestamp", Instant.now().toString(),
            "service", "user-service"
        );
        
        kafkaTemplate.send(dlqTopic, dlqMessage);
    }

    public void fallbackProcessKycStatusUpdate(String eventPayload, String topic, int partition, long offset, 
                                              Long timestamp, Acknowledgment acknowledgment, Exception ex) {
        log.error("Circuit breaker fallback triggered for KYC status update processing", ex);
        
        metricsService.incrementCounter("kyc_status_update_circuit_breaker_fallback");
        
        sendToDlq(eventPayload, "kyc-status-updates-dlq", "CIRCUIT_BREAKER_OPEN", ex.getMessage());
        acknowledgment.acknowledge();
    }
}