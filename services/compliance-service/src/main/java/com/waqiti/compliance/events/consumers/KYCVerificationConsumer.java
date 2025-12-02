package com.waqiti.compliance.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.compliance.KYCVerificationEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.compliance.domain.KYCVerification;
import com.waqiti.compliance.domain.KYCVerificationStatus;
import com.waqiti.compliance.domain.KYCLevel;
import com.waqiti.compliance.domain.DocumentVerification;
import com.waqiti.compliance.repository.KYCVerificationRepository;
import com.waqiti.compliance.service.IdentityVerificationService;
import com.waqiti.compliance.service.DocumentVerificationService;
import com.waqiti.compliance.service.BiometricVerificationService;
import com.waqiti.compliance.service.AddressVerificationService;
import com.waqiti.compliance.service.KYCDecisionService;
import com.waqiti.compliance.service.ComplianceNotificationService;
import com.waqiti.common.exceptions.KYCVerificationException;

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

/**
 * Production-grade consumer for KYC (Know Your Customer) verification events.
 * Performs comprehensive identity verification including:
 * - Document verification (passport, driver's license, national ID)
 * - Biometric verification (facial recognition, liveness detection)
 * - Address verification (proof of address, utility bills)
 * - Identity database checks
 * - Risk assessment and tiered KYC levels
 * 
 * Critical for regulatory compliance and user onboarding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KYCVerificationConsumer {

    private final KYCVerificationRepository kycRepository;
    private final IdentityVerificationService identityService;
    private final DocumentVerificationService documentService;
    private final BiometricVerificationService biometricService;
    private final AddressVerificationService addressService;
    private final KYCDecisionService decisionService;
    private final ComplianceNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    @KafkaListener(
        topics = "kyc-verification-requests",
        groupId = "compliance-service-kyc-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        include = {KYCVerificationException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleKYCVerificationRequest(
            @Payload KYCVerificationEvent kycEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "kyc-level", required = false) String requestedLevel,
            Acknowledgment acknowledgment) {

        String eventId = kycEvent.getEventId() != null ? 
            kycEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing KYC verification request: {} for user: {} level: {}", 
                    eventId, kycEvent.getUserId(), kycEvent.getKycLevel());

            // Metrics tracking
            metricsService.incrementCounter("kyc.verification.processing.started",
                Map.of(
                    "kyc_level", kycEvent.getKycLevel(),
                    "verification_type", kycEvent.getVerificationType()
                ));

            // Idempotency check
            if (isKYCAlreadyProcessed(kycEvent.getUserId(), eventId)) {
                log.info("KYC verification {} already processed for user {}", eventId, kycEvent.getUserId());
                acknowledgment.acknowledge();
                return;
            }

            // Create KYC verification record
            KYCVerification kyc = createKYCRecord(kycEvent, eventId, correlationId);

            // Determine required verifications based on KYC level
            KYCLevel kycLevel = KYCLevel.valueOf(kycEvent.getKycLevel().toUpperCase());
            List<CompletableFuture<Boolean>> verificationTasks = createVerificationTasks(kyc, kycEvent, kycLevel);

            // Execute all verifications in parallel with timeout
            CompletableFuture<Void> allVerifications = CompletableFuture.allOf(
                verificationTasks.toArray(new CompletableFuture[0])
            );

            try {
                allVerifications.get(15, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("KYC verification timed out after 15 seconds for user: {}", kycEvent.getUserId(), e);
                throw new RuntimeException("KYC verification timed out - cannot complete", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("KYC verification execution failed for user: {}", kycEvent.getUserId(), e.getCause());
                throw new RuntimeException("KYC verification failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("KYC verification interrupted for user: {}", kycEvent.getUserId(), e);
                throw new RuntimeException("KYC verification interrupted", e);
            }

            // Aggregate verification results (already completed, safe to get immediately)
            boolean allVerificationsPassed = verificationTasks.stream()
                .allMatch(future -> {
                    try {
                        return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Verification task failed: {}", e.getMessage());
                        return false;
                    }
                });

            // Calculate verification score
            calculateVerificationScore(kyc, kycEvent);

            // Make KYC decision
            makeKYCDecision(kyc, kycEvent, allVerificationsPassed);

            // Update KYC status
            updateKYCStatus(kyc);

            // Save KYC verification results
            KYCVerification savedKYC = kycRepository.save(kyc);

            // Handle verification outcomes
            handleVerificationOutcome(savedKYC, kycEvent);

            // Send compliance notifications
            sendComplianceNotifications(savedKYC, kycEvent);

            // Update metrics
            updateKYCMetrics(savedKYC, kycEvent);

            // Create comprehensive audit trail
            createKYCAuditLog(savedKYC, kycEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("kyc.verification.processing.success",
                Map.of(
                    "kyc_level", savedKYC.getKycLevel().toString(),
                    "status", savedKYC.getStatus().toString(),
                    "verification_passed", String.valueOf(savedKYC.isVerified())
                ));

            log.info("Successfully processed KYC verification: {} for user: {} with status: {} score: {}", 
                    savedKYC.getId(), kycEvent.getUserId(), savedKYC.getStatus(), savedKYC.getVerificationScore());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing KYC verification event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("kyc.verification.processing.error");
            
            // Critical audit log for KYC failures
            auditLogger.logCriticalAlert("KYC_VERIFICATION_PROCESSING_ERROR",
                "Critical KYC verification failure - onboarding blocked",
                Map.of(
                    "userId", kycEvent.getUserId(),
                    "kycLevel", kycEvent.getKycLevel(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new KYCVerificationException("Failed to process KYC verification: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "kyc-verification-express",
        groupId = "compliance-service-kyc-express-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleExpressKYCVerification(
            @Payload KYCVerificationEvent kycEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("EXPRESS KYC: Processing fast-track verification for user: {}", kycEvent.getUserId());

            // Perform lightweight KYC for basic tier
            KYCVerification kyc = performExpressKYC(kycEvent, correlationId);

            // Quick decision
            boolean approved = kyc.getVerificationScore() >= 0.7 && kyc.getDocumentVerified();

            if (approved) {
                kyc.setStatus(KYCVerificationStatus.APPROVED);
                kyc.setVerified(true);
                kyc.setApprovedAt(LocalDateTime.now());
                
                // Enable basic tier services immediately
                enableUserServices(kycEvent.getUserId(), KYCLevel.BASIC);
            } else {
                kyc.setStatus(KYCVerificationStatus.REQUIRES_REVIEW);
                kyc.setVerified(false);
            }

            // Save KYC results
            kycRepository.save(kyc);

            // Send notification
            notificationService.sendExpressKYCResult(kyc);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process express KYC verification: {}", e.getMessage(), e);
            acknowledgment.acknowledge(); // Acknowledge to prevent blocking express queue
        }
    }

    private boolean isKYCAlreadyProcessed(String userId, String eventId) {
        return kycRepository.existsByUserIdAndEventId(userId, eventId);
    }

    private KYCVerification createKYCRecord(KYCVerificationEvent event, String eventId, String correlationId) {
        return KYCVerification.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .userId(event.getUserId())
            .userType(event.getUserType())
            .kycLevel(KYCLevel.valueOf(event.getKycLevel().toUpperCase()))
            .verificationType(event.getVerificationType())
            .documentType(event.getDocumentType())
            .documentNumber(maskDocumentNumber(event.getDocumentNumber()))
            .documentCountry(event.getDocumentCountry())
            .firstName(event.getFirstName())
            .lastName(event.getLastName())
            .dateOfBirth(event.getDateOfBirth())
            .nationality(event.getNationality())
            .status(KYCVerificationStatus.INITIATED)
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<CompletableFuture<Boolean>> createVerificationTasks(
            KYCVerification kyc, KYCVerificationEvent event, KYCLevel level) {
        
        List<CompletableFuture<Boolean>> tasks = new ArrayList<>();

        // Document verification (required for all levels)
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performDocumentVerification(kyc, event)));

        // Identity verification (required for all levels)
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performIdentityVerification(kyc, event)));

        if (level == KYCLevel.ENHANCED || level == KYCLevel.FULL) {
            // Biometric verification
            tasks.add(CompletableFuture.supplyAsync(() -> 
                performBiometricVerification(kyc, event)));

            // Address verification
            tasks.add(CompletableFuture.supplyAsync(() -> 
                performAddressVerification(kyc, event)));
        }

        if (level == KYCLevel.FULL) {
            // Enhanced background checks
            tasks.add(CompletableFuture.supplyAsync(() -> 
                performEnhancedBackgroundCheck(kyc, event)));

            // Source of funds verification
            tasks.add(CompletableFuture.supplyAsync(() -> 
                performSourceOfFundsVerification(kyc, event)));
        }

        return tasks;
    }

    private boolean performDocumentVerification(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            log.info("Performing document verification for user: {}", kyc.getUserId());

            DocumentVerification result = documentService.verifyDocument(
                event.getDocumentType(),
                event.getDocumentImageFront(),
                event.getDocumentImageBack(),
                event.getDocumentCountry()
            );

            kyc.setDocumentVerified(result.isValid());
            kyc.setDocumentVerificationScore(result.getConfidenceScore());
            kyc.setDocumentExpiryDate(result.getExpiryDate());
            kyc.setDocumentVerifiedAt(LocalDateTime.now());

            // Check for document tampering
            if (result.isTampered()) {
                kyc.setDocumentTampered(true);
                kyc.setVerificationErrors(List.of("Document shows signs of tampering"));
                return false;
            }

            // Verify document data matches provided data
            boolean dataMatches = verifyDocumentDataMatch(result, event);
            kyc.setDocumentDataMatches(dataMatches);

            log.info("Document verification completed for user: {} - Valid: {}", 
                    kyc.getUserId(), result.isValid());

            return result.isValid() && dataMatches;

        } catch (Exception e) {
            log.error("Error in document verification for {}: {}", kyc.getUserId(), e.getMessage());
            kyc.setDocumentVerificationError(e.getMessage());
            return false;
        }
    }

    private boolean performIdentityVerification(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            log.info("Performing identity verification for user: {}", kyc.getUserId());

            var identityResult = identityService.verifyIdentity(
                event.getFirstName(),
                event.getLastName(),
                event.getDateOfBirth(),
                event.getDocumentNumber(),
                event.getDocumentCountry()
            );

            kyc.setIdentityVerified(identityResult.isVerified());
            kyc.setIdentityScore(identityResult.getScore());
            kyc.setIdentityVerifiedAt(LocalDateTime.now());

            // Check watchlists
            if (identityResult.isOnWatchlist()) {
                kyc.setOnWatchlist(true);
                kyc.setWatchlistMatches(identityResult.getWatchlistMatches());
            }

            log.info("Identity verification completed for user: {} - Verified: {}", 
                    kyc.getUserId(), identityResult.isVerified());

            return identityResult.isVerified() && !identityResult.isOnWatchlist();

        } catch (Exception e) {
            log.error("Error in identity verification for {}: {}", kyc.getUserId(), e.getMessage());
            kyc.setIdentityVerificationError(e.getMessage());
            return false;
        }
    }

    private boolean performBiometricVerification(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            log.info("Performing biometric verification for user: {}", kyc.getUserId());

            if (event.getSelfieImage() == null) {
                log.warn("No selfie image provided for biometric verification");
                return false;
            }

            var biometricResult = biometricService.verifyBiometric(
                event.getSelfieImage(),
                event.getDocumentImageFront(),
                event.getLivenessVideo()
            );

            kyc.setBiometricVerified(biometricResult.isMatch());
            kyc.setFaceMatchScore(biometricResult.getFaceMatchScore());
            kyc.setLivenessScore(biometricResult.getLivenessScore());
            kyc.setBiometricVerifiedAt(LocalDateTime.now());

            // Check for spoofing attempts
            if (biometricResult.isSpoofingDetected()) {
                kyc.setSpoofingDetected(true);
                kyc.addVerificationError("Potential spoofing attempt detected");
                return false;
            }

            log.info("Biometric verification completed for user: {} - Match: {} Score: {}", 
                    kyc.getUserId(), biometricResult.isMatch(), biometricResult.getFaceMatchScore());

            return biometricResult.isMatch() && biometricResult.getLivenessScore() > 0.8;

        } catch (Exception e) {
            log.error("Error in biometric verification for {}: {}", kyc.getUserId(), e.getMessage());
            kyc.setBiometricVerificationError(e.getMessage());
            return false;
        }
    }

    private boolean performAddressVerification(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            log.info("Performing address verification for user: {}", kyc.getUserId());

            var addressResult = addressService.verifyAddress(
                event.getAddressLine1(),
                event.getAddressLine2(),
                event.getCity(),
                event.getState(),
                event.getPostalCode(),
                event.getCountry(),
                event.getProofOfAddress()
            );

            kyc.setAddressVerified(addressResult.isValid());
            kyc.setAddressScore(addressResult.getConfidenceScore());
            kyc.setAddressVerifiedAt(LocalDateTime.now());

            log.info("Address verification completed for user: {} - Valid: {}", 
                    kyc.getUserId(), addressResult.isValid());

            return addressResult.isValid();

        } catch (Exception e) {
            log.error("Error in address verification for {}: {}", kyc.getUserId(), e.getMessage());
            kyc.setAddressVerificationError(e.getMessage());
            return false;
        }
    }

    private boolean performEnhancedBackgroundCheck(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            log.info("Performing enhanced background check for user: {}", kyc.getUserId());

            // Criminal background check
            var criminalCheck = identityService.performCriminalCheck(
                event.getFirstName(), event.getLastName(), event.getDateOfBirth()
            );

            // Employment verification
            var employmentCheck = identityService.verifyEmployment(
                event.getEmployerName(), event.getEmploymentStatus()
            );

            // Financial history check
            var financialCheck = identityService.checkFinancialHistory(
                event.getUserId(), event.getAnnualIncome()
            );

            kyc.setCriminalCheckPassed(!criminalCheck.hasRecords());
            kyc.setEmploymentVerified(employmentCheck.isVerified());
            kyc.setFinancialCheckPassed(financialCheck.isClean());

            return !criminalCheck.hasRecords() && employmentCheck.isVerified() && financialCheck.isClean();

        } catch (Exception e) {
            log.error("Error in enhanced background check for {}: {}", kyc.getUserId(), e.getMessage());
            return false;
        }
    }

    private boolean performSourceOfFundsVerification(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            log.info("Performing source of funds verification for user: {}", kyc.getUserId());

            var fundsResult = identityService.verifySourceOfFunds(
                event.getSourceOfFunds(),
                event.getSourceOfFundsDocuments(),
                event.getAnnualIncome(),
                event.getNetWorth()
            );

            kyc.setSourceOfFundsVerified(fundsResult.isVerified());
            kyc.setSourceOfFundsScore(fundsResult.getConfidenceScore());

            return fundsResult.isVerified();

        } catch (Exception e) {
            log.error("Error in source of funds verification for {}: {}", kyc.getUserId(), e.getMessage());
            return false;
        }
    }

    private void calculateVerificationScore(KYCVerification kyc, KYCVerificationEvent event) {
        double score = 0.0;
        double weightSum = 0.0;

        // Document verification (30% weight)
        if (kyc.getDocumentVerificationScore() != null) {
            score += kyc.getDocumentVerificationScore() * 0.3;
            weightSum += 0.3;
        }

        // Identity verification (25% weight)
        if (kyc.getIdentityScore() != null) {
            score += kyc.getIdentityScore() * 0.25;
            weightSum += 0.25;
        }

        // Biometric verification (20% weight)
        if (kyc.getFaceMatchScore() != null) {
            score += kyc.getFaceMatchScore() * 0.2;
            weightSum += 0.2;
        }

        // Address verification (15% weight)
        if (kyc.getAddressScore() != null) {
            score += kyc.getAddressScore() * 0.15;
            weightSum += 0.15;
        }

        // Liveness check (10% weight)
        if (kyc.getLivenessScore() != null) {
            score += kyc.getLivenessScore() * 0.1;
            weightSum += 0.1;
        }

        // Normalize score
        double finalScore = weightSum > 0 ? score / weightSum : 0.0;

        // Apply penalties
        if (kyc.isDocumentTampered()) finalScore *= 0.1;
        if (kyc.isSpoofingDetected()) finalScore *= 0.1;
        if (kyc.isOnWatchlist()) finalScore *= 0.5;

        kyc.setVerificationScore(Math.max(0.0, Math.min(1.0, finalScore)));
    }

    private void makeKYCDecision(KYCVerification kyc, KYCVerificationEvent event, boolean allVerificationsPassed) {
        try {
            // Get automated decision
            var decision = decisionService.makeKYCDecision(kyc, allVerificationsPassed);
            
            kyc.setDecision(decision.getDecision());
            kyc.setDecisionReason(decision.getReason());
            kyc.setDecisionMadeAt(LocalDateTime.now());

            // Determine if manual review is needed
            if (requiresManualReview(kyc)) {
                kyc.setRequiresManualReview(true);
                kyc.setReviewDeadline(calculateReviewDeadline(kyc.getKycLevel()));
                kyc.setReviewPriority(calculateReviewPriority(kyc));
            }

        } catch (Exception e) {
            log.error("Error making KYC decision for {}: {}", kyc.getId(), e.getMessage());
            kyc.setDecision("PENDING_REVIEW");
            kyc.setDecisionReason("Automated decision failed - manual review required");
            kyc.setRequiresManualReview(true);
        }
    }

    private void updateKYCStatus(KYCVerification kyc) {
        if ("APPROVED".equals(kyc.getDecision())) {
            kyc.setStatus(KYCVerificationStatus.APPROVED);
            kyc.setVerified(true);
            kyc.setApprovedAt(LocalDateTime.now());
        } else if ("REJECTED".equals(kyc.getDecision())) {
            kyc.setStatus(KYCVerificationStatus.REJECTED);
            kyc.setVerified(false);
            kyc.setRejectedAt(LocalDateTime.now());
        } else if (kyc.isRequiresManualReview()) {
            kyc.setStatus(KYCVerificationStatus.PENDING_REVIEW);
        } else {
            kyc.setStatus(KYCVerificationStatus.REQUIRES_RESUBMISSION);
        }

        kyc.setCompletedAt(LocalDateTime.now());
        kyc.setProcessingTimeMs(
            java.time.Duration.between(kyc.getCreatedAt(), LocalDateTime.now()).toMillis()
        );
    }

    private void handleVerificationOutcome(KYCVerification kyc, KYCVerificationEvent event) {
        if (kyc.getStatus() == KYCVerificationStatus.APPROVED) {
            // Enable user services based on KYC level
            enableUserServices(kyc.getUserId(), kyc.getKycLevel());
            
            // Update user limits
            updateUserLimits(kyc.getUserId(), kyc.getKycLevel());
            
        } else if (kyc.getStatus() == KYCVerificationStatus.REJECTED) {
            // Disable user services
            disableUserServices(kyc.getUserId());
            
            // Flag for compliance review if fraud detected
            if (kyc.isDocumentTampered() || kyc.isSpoofingDetected()) {
                flagForComplianceReview(kyc);
            }
            
        } else if (kyc.getStatus() == KYCVerificationStatus.REQUIRES_RESUBMISSION) {
            // Notify user to resubmit documents
            notificationService.sendKYCResubmissionRequest(kyc);
        }
    }

    private void sendComplianceNotifications(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            // Standard KYC notification
            notificationService.sendKYCVerificationNotification(kyc);

            // Manual review notifications
            if (kyc.isRequiresManualReview()) {
                notificationService.sendManualReviewRequest(kyc);
            }

            // Fraud alert notifications
            if (kyc.isDocumentTampered() || kyc.isSpoofingDetected()) {
                notificationService.sendFraudAlert(
                    "POTENTIAL_KYC_FRAUD",
                    String.format("Potential fraud detected in KYC for user %s", kyc.getUserId()),
                    kyc
                );
            }

            // Watchlist hit notifications
            if (kyc.isOnWatchlist()) {
                notificationService.sendWatchlistHitAlert(kyc);
            }

        } catch (Exception e) {
            log.error("Failed to send KYC notifications for {}: {}", kyc.getId(), e.getMessage());
        }
    }

    private void updateKYCMetrics(KYCVerification kyc, KYCVerificationEvent event) {
        try {
            // Record KYC metrics
            metricsService.incrementCounter("kyc.verification.completed",
                Map.of(
                    "kyc_level", kyc.getKycLevel().toString(),
                    "status", kyc.getStatus().toString(),
                    "verified", String.valueOf(kyc.isVerified())
                ));

            // Record verification score
            metricsService.recordGauge("kyc.verification_score", kyc.getVerificationScore(),
                Map.of("kyc_level", kyc.getKycLevel().toString()));

            // Record processing time
            metricsService.recordTimer("kyc.processing_time_ms", kyc.getProcessingTimeMs(),
                Map.of("status", kyc.getStatus().toString()));

            // Record fraud detection metrics
            if (kyc.isDocumentTampered() || kyc.isSpoofingDetected()) {
                metricsService.incrementCounter("kyc.fraud_detected",
                    Map.of(
                        "type", kyc.isDocumentTampered() ? "document_tampering" : "spoofing",
                        "user_type", kyc.getUserType()
                    ));
            }

        } catch (Exception e) {
            log.error("Failed to update KYC metrics for {}: {}", kyc.getId(), e.getMessage());
        }
    }

    private void createKYCAuditLog(KYCVerification kyc, KYCVerificationEvent event, String correlationId) {
        auditLogger.logKYCEvent(
            "KYC_VERIFICATION_COMPLETED",
            kyc.getUserId(),
            kyc.getId(),
            kyc.getKycLevel().toString(),
            kyc.getVerificationScore(),
            "kyc_processor",
            kyc.isVerified(),
            Map.of(
                "userId", kyc.getUserId(),
                "kycLevel", kyc.getKycLevel().toString(),
                "status", kyc.getStatus().toString(),
                "verificationScore", String.valueOf(kyc.getVerificationScore()),
                "documentVerified", String.valueOf(kyc.getDocumentVerified()),
                "identityVerified", String.valueOf(kyc.getIdentityVerified()),
                "biometricVerified", String.valueOf(kyc.getBiometricVerified() != null ? kyc.getBiometricVerified() : "N/A"),
                "addressVerified", String.valueOf(kyc.getAddressVerified() != null ? kyc.getAddressVerified() : "N/A"),
                "onWatchlist", String.valueOf(kyc.isOnWatchlist()),
                "fraudDetected", String.valueOf(kyc.isDocumentTampered() || kyc.isSpoofingDetected()),
                "processingTimeMs", String.valueOf(kyc.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private KYCVerification performExpressKYC(KYCVerificationEvent event, String correlationId) {
        KYCVerification kyc = createKYCRecord(event, UUID.randomUUID().toString(), correlationId);
        
        // Quick document check
        boolean documentValid = documentService.quickDocumentCheck(
            event.getDocumentType(), event.getDocumentImageFront()
        );
        kyc.setDocumentVerified(documentValid);
        kyc.setDocumentVerificationScore(documentValid ? 0.8 : 0.2);
        
        // Quick identity check
        boolean identityValid = identityService.quickIdentityCheck(
            event.getFirstName(), event.getLastName(), event.getDateOfBirth()
        );
        kyc.setIdentityVerified(identityValid);
        kyc.setIdentityScore(identityValid ? 0.75 : 0.25);
        
        // Calculate overall score
        calculateVerificationScore(kyc, event);
        
        kyc.setCompletedAt(LocalDateTime.now());
        kyc.setProcessingTimeMs(1000L); // Express processing
        
        return kyc;
    }

    private String maskDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.length() < 4) {
            return "****";
        }
        return "****" + documentNumber.substring(documentNumber.length() - 4);
    }

    private boolean verifyDocumentDataMatch(DocumentVerification result, KYCVerificationEvent event) {
        boolean nameMatches = result.getFirstName().equalsIgnoreCase(event.getFirstName()) &&
                             result.getLastName().equalsIgnoreCase(event.getLastName());
        boolean dobMatches = result.getDateOfBirth().equals(event.getDateOfBirth());
        
        return nameMatches && dobMatches;
    }

    private boolean requiresManualReview(KYCVerification kyc) {
        return kyc.isOnWatchlist() ||
               kyc.isDocumentTampered() ||
               kyc.isSpoofingDetected() ||
               kyc.getVerificationScore() < 0.7 ||
               kyc.getKycLevel() == KYCLevel.FULL;
    }

    private LocalDateTime calculateReviewDeadline(KYCLevel level) {
        return switch (level) {
            case FULL -> LocalDateTime.now().plusHours(24);
            case ENHANCED -> LocalDateTime.now().plusHours(48);
            default -> LocalDateTime.now().plusHours(72);
        };
    }

    private String calculateReviewPriority(KYCVerification kyc) {
        if (kyc.isDocumentTampered() || kyc.isSpoofingDetected()) return "URGENT";
        if (kyc.isOnWatchlist()) return "HIGH";
        if (kyc.getKycLevel() == KYCLevel.FULL) return "HIGH";
        if (kyc.getVerificationScore() < 0.5) return "MEDIUM";
        return "NORMAL";
    }

    private void enableUserServices(String userId, KYCLevel level) {
        log.info("Enabling services for user {} at KYC level {}", userId, level);
        // In real implementation, would call user service to enable features
    }

    private void updateUserLimits(String userId, KYCLevel level) {
        log.info("Updating transaction limits for user {} based on KYC level {}", userId, level);
        // In real implementation, would update user transaction limits
    }

    private void disableUserServices(String userId) {
        log.warn("Disabling services for user {} due to KYC failure", userId);
        // In real implementation, would call user service to disable features
    }

    private void flagForComplianceReview(KYCVerification kyc) {
        log.warn("Flagging user {} for compliance review due to fraud indicators", kyc.getUserId());
        // In real implementation, would create compliance case
    }
}