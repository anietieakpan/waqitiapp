package com.waqiti.kyc.orchestration;

import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.domain.VerificationDocument;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.exception.KYCVerificationException;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.service.*;
import com.waqiti.common.saga.SagaOrchestrator;
import com.waqiti.common.saga.SagaStep;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive KYC Verification Orchestrator
 * Manages the complete end-to-end KYC verification flow with proper error handling,
 * compliance checks, and integration with multiple verification providers
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KYCVerificationOrchestrator {

    private final KYCVerificationRepository verificationRepository;
    private final DocumentVerificationService documentVerificationService;
    private final IdentityVerificationService identityVerificationService;
    private final AddressVerificationService addressVerificationService;
    private final BiometricVerificationService biometricVerificationService;
    private final ComplianceCheckService complianceCheckService;
    private final RiskAssessmentService riskAssessmentService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SagaOrchestrator sagaOrchestrator;

    private static final String KYC_EVENTS_TOPIC = "kyc-verification-events";
    private static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final long VERIFICATION_TIMEOUT_MINUTES = 30;

    /**
     * Orchestrate complete KYC verification flow
     */
    @Transactional
    public CompletableFuture<KYCVerificationResponse> orchestrateVerification(
            String userId, KYCVerificationRequest request) {
        
        log.info("Starting KYC verification orchestration for user: {} at level: {}", 
            userId, request.getVerificationLevel());
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Step 1: Pre-verification checks
                KYCVerification verification = performPreVerificationChecks(userId, request);
                
                // Step 2: Create verification saga
                String sagaId = createVerificationSaga(verification);
                verification.setSagaId(sagaId);
                
                // Step 3: Execute verification steps based on level
                KYCVerificationResult result = executeVerificationFlow(verification, request);
                
                // Step 4: Post-verification processing
                finalizeVerification(verification, result);
                
                // Step 5: Send notifications and events
                handleVerificationCompletion(verification, result);
                
                return mapToResponse(verification, result);
                
            } catch (Exception e) {
                log.error("KYC verification orchestration failed for user: {}", userId, e);
                handleVerificationFailure(userId, request, e);
                throw new KYCVerificationException("Verification orchestration failed", e);
            }
        }).orTimeout(VERIFICATION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Perform pre-verification checks
     */
    private KYCVerification performPreVerificationChecks(String userId, KYCVerificationRequest request) {
        log.debug("Performing pre-verification checks for user: {}", userId);
        
        // Check for existing verifications
        checkExistingVerifications(userId);
        
        // Validate user eligibility
        validateUserEligibility(userId, request);
        
        // Check compliance restrictions
        checkComplianceRestrictions(userId, request);
        
        // Create verification record
        KYCVerification verification = createVerificationRecord(userId, request);
        
        // Audit the initiation
        auditService.auditKYCInitiation(verification);
        
        return verification;
    }

    /**
     * Execute verification flow based on level
     */
    private KYCVerificationResult executeVerificationFlow(
            KYCVerification verification, KYCVerificationRequest request) {
        
        KYCVerificationResult result = new KYCVerificationResult();
        result.setStartTime(LocalDateTime.now());
        
        try {
            switch (request.getVerificationLevel()) {
                case BASIC:
                    result = executeBasicVerification(verification, request);
                    break;
                    
                case INTERMEDIATE:
                    result = executeIntermediateVerification(verification, request);
                    break;
                    
                case ADVANCED:
                    result = executeAdvancedVerification(verification, request);
                    break;
                    
                case ENHANCED:
                    result = executeEnhancedVerification(verification, request);
                    break;
                    
                default:
                    throw new IllegalArgumentException("Unknown verification level: " + 
                        request.getVerificationLevel());
            }
            
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);
            
        } catch (Exception e) {
            log.error("Verification flow failed for verification: {}", verification.getId(), e);
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(false);
            result.setFailureReason(e.getMessage());
        }
        
        return result;
    }

    /**
     * Execute basic KYC verification (Level 1)
     * - Name verification
     * - Email verification
     * - Phone verification
     * - Basic compliance check
     */
    private KYCVerificationResult executeBasicVerification(
            KYCVerification verification, KYCVerificationRequest request) {
        
        log.info("Executing BASIC verification for: {}", verification.getUserId());
        
        KYCVerificationResult result = new KYCVerificationResult();
        List<VerificationStep> steps = new ArrayList<>();
        
        // Step 1: Identity verification
        VerificationStep identityStep = verifyBasicIdentity(verification, request);
        steps.add(identityStep);
        
        if (!identityStep.isSuccess()) {
            result.setSteps(steps);
            result.setOverallStatus(KYCVerification.Status.FAILED);
            return result;
        }
        
        // Step 2: Email verification
        VerificationStep emailStep = verifyEmail(verification, request.getEmail());
        steps.add(emailStep);
        
        // Step 3: Phone verification
        VerificationStep phoneStep = verifyPhone(verification, request.getPhoneNumber());
        steps.add(phoneStep);
        
        // Step 4: Basic compliance check
        VerificationStep complianceStep = performBasicComplianceCheck(verification);
        steps.add(complianceStep);
        
        // Calculate overall result
        result.setSteps(steps);
        result.setOverallStatus(calculateOverallStatus(steps));
        result.setRiskScore(calculateRiskScore(steps));
        
        return result;
    }

    /**
     * Execute intermediate KYC verification (Level 2)
     * - Everything from Basic
     * - Document verification (ID)
     * - Address verification
     * - Enhanced compliance check
     */
    private KYCVerificationResult executeIntermediateVerification(
            KYCVerification verification, KYCVerificationRequest request) {
        
        log.info("Executing INTERMEDIATE verification for: {}", verification.getUserId());
        
        // Start with basic verification
        KYCVerificationResult result = executeBasicVerification(verification, request);
        
        if (result.getOverallStatus() == KYCVerification.Status.FAILED) {
            return result;
        }
        
        List<VerificationStep> additionalSteps = new ArrayList<>();
        
        // Step 5: Document verification
        VerificationStep documentStep = verifyDocuments(verification, request.getDocuments());
        additionalSteps.add(documentStep);
        
        // Step 6: Address verification
        VerificationStep addressStep = verifyAddress(verification, request.getAddress());
        additionalSteps.add(addressStep);
        
        // Step 7: Enhanced compliance check
        VerificationStep enhancedComplianceStep = performEnhancedComplianceCheck(verification);
        additionalSteps.add(enhancedComplianceStep);
        
        // Combine results
        result.getSteps().addAll(additionalSteps);
        result.setOverallStatus(calculateOverallStatus(result.getSteps()));
        result.setRiskScore(calculateRiskScore(result.getSteps()));
        
        return result;
    }

    /**
     * Execute advanced KYC verification (Level 3)
     * - Everything from Intermediate
     * - Biometric verification
     * - Video verification
     * - Enhanced due diligence
     */
    private KYCVerificationResult executeAdvancedVerification(
            KYCVerification verification, KYCVerificationRequest request) {
        
        log.info("Executing ADVANCED verification for: {}", verification.getUserId());
        
        // Start with intermediate verification
        KYCVerificationResult result = executeIntermediateVerification(verification, request);
        
        if (result.getOverallStatus() == KYCVerification.Status.FAILED) {
            return result;
        }
        
        List<VerificationStep> additionalSteps = new ArrayList<>();
        
        // Step 8: Biometric verification
        if (request.getBiometricData() != null) {
            VerificationStep biometricStep = verifyBiometrics(verification, request.getBiometricData());
            additionalSteps.add(biometricStep);
        }
        
        // Step 9: Video verification
        if (request.getVideoVerificationRequired()) {
            VerificationStep videoStep = performVideoVerification(verification);
            additionalSteps.add(videoStep);
        }
        
        // Step 10: Enhanced due diligence
        VerificationStep eddStep = performEnhancedDueDiligence(verification);
        additionalSteps.add(eddStep);
        
        // Combine results
        result.getSteps().addAll(additionalSteps);
        result.setOverallStatus(calculateOverallStatus(result.getSteps()));
        result.setRiskScore(calculateRiskScore(result.getSteps()));
        
        return result;
    }

    /**
     * Execute enhanced KYC verification (Level 4)
     * - Everything from Advanced
     * - Source of wealth verification
     * - Politically exposed person (PEP) check
     * - Adverse media screening
     * - Manual review
     */
    private KYCVerificationResult executeEnhancedVerification(
            KYCVerification verification, KYCVerificationRequest request) {
        
        log.info("Executing ENHANCED verification for: {}", verification.getUserId());
        
        // Start with advanced verification
        KYCVerificationResult result = executeAdvancedVerification(verification, request);
        
        if (result.getOverallStatus() == KYCVerification.Status.FAILED) {
            return result;
        }
        
        List<VerificationStep> additionalSteps = new ArrayList<>();
        
        // Step 11: Source of wealth verification
        VerificationStep sowStep = verifySourceOfWealth(verification, request.getSourceOfWealth());
        additionalSteps.add(sowStep);
        
        // Step 12: PEP check
        VerificationStep pepStep = performPEPCheck(verification);
        additionalSteps.add(pepStep);
        
        // Step 13: Adverse media screening
        VerificationStep adverseMediaStep = performAdverseMediaScreening(verification);
        additionalSteps.add(adverseMediaStep);
        
        // Step 14: Manual review requirement
        VerificationStep manualReviewStep = flagForManualReview(verification, result);
        additionalSteps.add(manualReviewStep);
        
        // Combine results
        result.getSteps().addAll(additionalSteps);
        result.setOverallStatus(calculateOverallStatus(result.getSteps()));
        result.setRiskScore(calculateRiskScore(result.getSteps()));
        result.setRequiresManualReview(true);
        
        return result;
    }

    // Individual verification step implementations

    @CircuitBreaker(name = "identity-verification", fallbackMethod = "identityVerificationFallback")
    @Retry(name = "identity-verification")
    private VerificationStep verifyBasicIdentity(KYCVerification verification, KYCVerificationRequest request) {
        log.debug("Verifying basic identity for: {}", verification.getUserId());
        
        try {
            IdentityVerificationResult identityResult = identityVerificationService.verifyIdentity(
                request.getFirstName(),
                request.getLastName(),
                request.getDateOfBirth(),
                request.getNationalId()
            );
            
            return VerificationStep.builder()
                .stepName("IDENTITY_VERIFICATION")
                .status(identityResult.isVerified() ? StepStatus.PASSED : StepStatus.FAILED)
                .confidence(identityResult.getConfidenceScore())
                .details(identityResult.getDetails())
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Identity verification failed", e);
            return VerificationStep.builder()
                .stepName("IDENTITY_VERIFICATION")
                .status(StepStatus.ERROR)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    @CircuitBreaker(name = "document-verification", fallbackMethod = "documentVerificationFallback")
    @Retry(name = "document-verification")
    private VerificationStep verifyDocuments(KYCVerification verification, List<MultipartFile> documents) {
        log.debug("Verifying documents for: {}", verification.getUserId());
        
        try {
            List<DocumentVerificationResult> results = new ArrayList<>();
            
            for (MultipartFile document : documents) {
                DocumentVerificationResult docResult = documentVerificationService.verifyDocument(
                    verification.getId(),
                    document
                );
                results.add(docResult);
            }
            
            boolean allValid = results.stream().allMatch(DocumentVerificationResult::isValid);
            double avgConfidence = results.stream()
                .mapToDouble(DocumentVerificationResult::getConfidenceScore)
                .average()
                .orElse(0.0);
            
            return VerificationStep.builder()
                .stepName("DOCUMENT_VERIFICATION")
                .status(allValid ? StepStatus.PASSED : StepStatus.FAILED)
                .confidence(avgConfidence)
                .details(Map.of("documentsVerified", results.size(), "results", results))
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Document verification failed", e);
            return VerificationStep.builder()
                .stepName("DOCUMENT_VERIFICATION")
                .status(StepStatus.ERROR)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    @CircuitBreaker(name = "biometric-verification", fallbackMethod = "biometricVerificationFallback")
    @Retry(name = "biometric-verification")
    private VerificationStep verifyBiometrics(KYCVerification verification, BiometricData biometricData) {
        log.debug("Verifying biometrics for: {}", verification.getUserId());
        
        try {
            BiometricVerificationResult bioResult = biometricVerificationService.verifyBiometrics(
                verification.getId(),
                biometricData
            );
            
            return VerificationStep.builder()
                .stepName("BIOMETRIC_VERIFICATION")
                .status(bioResult.isMatch() ? StepStatus.PASSED : StepStatus.FAILED)
                .confidence(bioResult.getMatchScore())
                .details(Map.of(
                    "livenessDetected", bioResult.isLivenessDetected(),
                    "facialMatchScore", bioResult.getFacialMatchScore(),
                    "qualityScore", bioResult.getQualityScore()
                ))
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Biometric verification failed", e);
            return VerificationStep.builder()
                .stepName("BIOMETRIC_VERIFICATION")
                .status(StepStatus.ERROR)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    @CircuitBreaker(name = "compliance-check", fallbackMethod = "complianceCheckFallback")
    @Retry(name = "compliance-check")
    private VerificationStep performEnhancedComplianceCheck(KYCVerification verification) {
        log.debug("Performing enhanced compliance check for: {}", verification.getUserId());
        
        try {
            ComplianceCheckResult complianceResult = complianceCheckService.performEnhancedCheck(
                verification.getUserId(),
                verification.getId()
            );
            
            return VerificationStep.builder()
                .stepName("ENHANCED_COMPLIANCE_CHECK")
                .status(complianceResult.isPassed() ? StepStatus.PASSED : StepStatus.FAILED)
                .confidence(complianceResult.getConfidenceScore())
                .details(Map.of(
                    "sanctionsCheck", complianceResult.getSanctionsCheckResult(),
                    "pepCheck", complianceResult.getPepCheckResult(),
                    "adverseMedia", complianceResult.getAdverseMediaResult(),
                    "riskScore", complianceResult.getRiskScore()
                ))
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Compliance check failed", e);
            return VerificationStep.builder()
                .stepName("ENHANCED_COMPLIANCE_CHECK")
                .status(StepStatus.ERROR)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    private VerificationStep performPEPCheck(KYCVerification verification) {
        log.debug("Performing PEP check for: {}", verification.getUserId());
        
        try {
            PEPCheckResult pepResult = complianceCheckService.checkPoliticallyExposedPerson(
                verification.getUserId()
            );
            
            return VerificationStep.builder()
                .stepName("PEP_CHECK")
                .status(pepResult.isClean() ? StepStatus.PASSED : StepStatus.REQUIRES_REVIEW)
                .confidence(pepResult.getConfidenceScore())
                .details(Map.of(
                    "isPEP", pepResult.isPEP(),
                    "pepType", pepResult.getPepType(),
                    "positions", pepResult.getPositions(),
                    "familyPEP", pepResult.isFamilyPEP()
                ))
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("PEP check failed", e);
            return VerificationStep.builder()
                .stepName("PEP_CHECK")
                .status(StepStatus.ERROR)
                .errorMessage(e.getMessage())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }

    // Utility methods

    private void checkExistingVerifications(String userId) {
        List<KYCVerification> existingVerifications = verificationRepository
            .findByUserIdAndStatusIn(userId, Arrays.asList(
                KYCVerification.Status.PENDING,
                KYCVerification.Status.IN_PROGRESS,
                KYCVerification.Status.VERIFIED
            ));
            
        for (KYCVerification existing : existingVerifications) {
            if (existing.getStatus() == KYCVerification.Status.VERIFIED) {
                // Check if still valid
                if (existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(LocalDateTime.now())) {
                    throw new KYCVerificationException("User already has valid KYC verification until: " + 
                        existing.getExpiresAt());
                }
            } else {
                throw new KYCVerificationException("User has pending KYC verification with ID: " + 
                    existing.getId());
            }
        }
    }

    private void validateUserEligibility(String userId, KYCVerificationRequest request) {
        // Check user age
        if (request.getDateOfBirth() != null) {
            int age = calculateAge(request.getDateOfBirth());
            if (age < 18) {
                throw new KYCVerificationException("User must be at least 18 years old");
            }
        }
        
        // Check user country restrictions
        if (isRestrictedCountry(request.getCountry())) {
            throw new KYCVerificationException("KYC verification not available for country: " + 
                request.getCountry());
        }
        
        // Check verification attempts
        int recentAttempts = verificationRepository.countRecentFailedAttempts(userId, 24);
        if (recentAttempts >= MAX_VERIFICATION_ATTEMPTS) {
            throw new KYCVerificationException("Maximum verification attempts exceeded. Please try again later.");
        }
    }

    private void checkComplianceRestrictions(String userId, KYCVerificationRequest request) {
        ComplianceStatus complianceStatus = complianceCheckService.getUserComplianceStatus(userId);
        
        if (complianceStatus.isBlacklisted()) {
            throw new KYCVerificationException("User is blacklisted: " + complianceStatus.getReason());
        }
        
        if (complianceStatus.isUnderInvestigation()) {
            throw new KYCVerificationException("User account is under investigation");
        }
        
        if (complianceStatus.hasActiveRestrictions()) {
            throw new KYCVerificationException("User has active compliance restrictions");
        }
    }

    private KYCVerification createVerificationRecord(String userId, KYCVerificationRequest request) {
        KYCVerification verification = KYCVerification.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .verificationLevel(request.getVerificationLevel())
            .status(KYCVerification.Status.PENDING)
            .initiatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(365)) // 1 year validity
            .metadata(Map.of(
                "ipAddress", request.getIpAddress(),
                "userAgent", request.getUserAgent(),
                "channel", request.getChannel()
            ))
            .build();
            
        return verificationRepository.save(verification);
    }

    private String createVerificationSaga(KYCVerification verification) {
        List<SagaStep> steps = Arrays.asList(
            new SagaStep("identity-verification", this::verifyIdentityStep, this::compensateIdentityStep),
            new SagaStep("document-verification", this::verifyDocumentStep, this::compensateDocumentStep),
            new SagaStep("compliance-check", this::performComplianceStep, this::compensateComplianceStep),
            new SagaStep("risk-assessment", this::performRiskStep, this::compensateRiskStep),
            new SagaStep("final-approval", this::performApprovalStep, this::compensateApprovalStep)
        );
        
        return sagaOrchestrator.startSaga("kyc-verification", verification.getId(), steps);
    }

    private void finalizeVerification(KYCVerification verification, KYCVerificationResult result) {
        verification.setStatus(result.getOverallStatus());
        verification.setCompletedAt(LocalDateTime.now());
        verification.setRiskScore(result.getRiskScore());
        verification.setVerificationResult(result.toMap());
        
        if (result.getOverallStatus() == KYCVerification.Status.VERIFIED) {
            verification.setVerifiedAt(LocalDateTime.now());
            verification.setExpiresAt(LocalDateTime.now().plusDays(365));
        }
        
        verificationRepository.save(verification);
    }

    private void handleVerificationCompletion(KYCVerification verification, KYCVerificationResult result) {
        // Send notifications
        notificationService.sendKYCCompletionNotification(verification, result);
        
        // Publish events
        publishVerificationEvent(verification, result);
        
        // Update user permissions based on KYC level
        updateUserPermissions(verification);
        
        // Trigger downstream processes
        triggerDownstreamProcesses(verification, result);
    }

    private void publishVerificationEvent(KYCVerification verification, KYCVerificationResult result) {
        Map<String, Object> event = Map.of(
            "eventType", "KYC_VERIFICATION_COMPLETED",
            "verificationId", verification.getId(),
            "userId", verification.getUserId(),
            "status", result.getOverallStatus().toString(),
            "level", verification.getVerificationLevel().toString(),
            "riskScore", result.getRiskScore(),
            "timestamp", LocalDateTime.now()
        );
        
        kafkaTemplate.send(KYC_EVENTS_TOPIC, verification.getUserId(), event);
    }

    private KYCVerification.Status calculateOverallStatus(List<VerificationStep> steps) {
        boolean hasFailure = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.FAILED);
        boolean hasError = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.ERROR);
        boolean requiresReview = steps.stream().anyMatch(s -> s.getStatus() == StepStatus.REQUIRES_REVIEW);
        
        if (hasFailure || hasError) {
            return KYCVerification.Status.FAILED;
        } else if (requiresReview) {
            return KYCVerification.Status.PENDING_REVIEW;
        } else {
            return KYCVerification.Status.VERIFIED;
        }
    }

    private double calculateRiskScore(List<VerificationStep> steps) {
        return steps.stream()
            .mapToDouble(step -> step.getConfidence() != null ? (1.0 - step.getConfidence()) * 0.1 : 0.05)
            .sum();
    }

    // Fallback methods

    private VerificationStep identityVerificationFallback(KYCVerification verification, 
                                                         KYCVerificationRequest request, 
                                                         Exception ex) {
        log.warn("Identity verification service unavailable, using fallback");
        return VerificationStep.builder()
            .stepName("IDENTITY_VERIFICATION")
            .status(StepStatus.REQUIRES_REVIEW)
            .errorMessage("Service unavailable - manual review required")
            .completedAt(LocalDateTime.now())
            .build();
    }

    // Helper classes

    @lombok.Data
    @lombok.Builder
    public static class KYCVerificationResult {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean success;
        private String failureReason;
        private List<VerificationStep> steps;
        private KYCVerification.Status overallStatus;
        private Double riskScore;
        private boolean requiresManualReview;
        
        public Map<String, Object> toMap() {
            return Map.of(
                "success", success,
                "status", overallStatus,
                "riskScore", riskScore,
                "steps", steps.stream().map(VerificationStep::toMap).collect(Collectors.toList()),
                "requiresManualReview", requiresManualReview
            );
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class VerificationStep {
        private String stepName;
        private StepStatus status;
        private Double confidence;
        private Map<String, Object> details;
        private String errorMessage;
        private LocalDateTime completedAt;
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("stepName", stepName);
            map.put("status", status);
            if (confidence != null) map.put("confidence", confidence);
            if (details != null) map.put("details", details);
            if (errorMessage != null) map.put("errorMessage", errorMessage);
            map.put("completedAt", completedAt);
            return map;
        }
    }

    public enum StepStatus {
        PASSED, FAILED, REQUIRES_REVIEW, ERROR, SKIPPED
    }

    // Additional helper methods would go here...
}