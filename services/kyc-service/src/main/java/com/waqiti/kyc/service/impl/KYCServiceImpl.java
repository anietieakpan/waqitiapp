package com.waqiti.kyc.service.impl;

import com.waqiti.kyc.config.KYCProperties;
import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.domain.KYCVerification.KYCStatus;
import com.waqiti.kyc.domain.KYCVerification.VerificationLevel;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.request.ReviewRequest;
import com.waqiti.kyc.dto.response.KYCStatusResponse;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.dto.response.VerificationHistoryResponse;
import com.waqiti.kyc.exception.KYCNotFoundException;
import com.waqiti.kyc.exception.KYCValidationException;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.service.DocumentService;
import com.waqiti.kyc.service.KYCProviderService;
import com.waqiti.kyc.service.KYCService;
import com.waqiti.kyc.service.PIIEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KYCServiceImpl implements KYCService {

    private final KYCVerificationRepository verificationRepository;
    private final DocumentService documentService;
    private final KYCProviderService kycProviderService;
    private final KYCProperties kycProperties;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PIIEncryptionService piiEncryptionService;

    @Override
    public KYCVerificationResponse initiateVerification(String userId, KYCVerificationRequest request) {
        log.info("Initiating KYC verification for user: {}", userId);
        
        // Check for existing active verification
        verificationRepository.findActiveVerificationByUserId(userId)
                .ifPresent(v -> {
                    throw KYCValidationException.duplicateVerification(userId);
                });

        // Create new verification
        KYCVerification verification = new KYCVerification();
        verification.setId(UUID.randomUUID().toString());
        verification.setUserId(userId);
        verification.setStatus(KYCStatus.PENDING);
        verification.setVerificationLevel(request.getVerificationLevel());
        verification.setProvider(request.getPreferredProvider() != null ? 
                request.getPreferredProvider() : kycProperties.getProviders().getDefaultProvider());
        verification.setConsentGiven(request.isConsentGiven());
        verification.setConsentText(request.getConsentText());
        verification.setConsentTimestamp(request.getConsentTimestamp());
        
        // SECURITY: Encrypt PII data for GDPR/CCPA compliance
        verification.setIpAddress(piiEncryptionService.encryptPII(request.getIpAddress()));
        verification.setUserAgent(piiEncryptionService.encryptPII(request.getUserAgent()));
        
        // Store searchable hash for IP address for fraud detection
        verification.setIpAddressHash(piiEncryptionService.hashPIIForSearch(request.getIpAddress()));
        
        // Create provider session
        String sessionId = kycProviderService.createVerificationSession(userId, request);
        verification.setProviderSessionId(sessionId);
        
        KYCVerification saved = verificationRepository.save(verification);
        
        // Publish event
        publishEvent("kyc.verification.initiated", Map.of(
                "userId", userId,
                "verificationId", saved.getId(),
                "level", request.getVerificationLevel().toString()
        ));
        
        return mapToResponse(saved);
    }

    @Override
    public KYCVerificationResponse getVerification(String verificationId) {
        KYCVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> KYCNotFoundException.verificationNotFound(verificationId));
        
        return mapToResponse(verification);
    }

    @Override
    public KYCVerificationResponse getActiveVerificationForUser(String userId) {
        KYCVerification verification = verificationRepository.findActiveVerificationByUserId(userId)
                .orElseThrow(() -> KYCNotFoundException.userVerificationNotFound(userId));
        
        return mapToResponse(verification);
    }

    @Override
    public List<KYCVerificationResponse> getUserVerifications(String userId) {
        List<KYCVerification> verifications = verificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return verifications.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public KYCVerificationResponse updateVerificationStatus(String verificationId, String status) {
        KYCVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> KYCNotFoundException.verificationNotFound(verificationId));
        
        KYCStatus newStatus = KYCStatus.valueOf(status.toUpperCase());
        validateStatusTransition(verification.getStatus(), newStatus);
        
        verification.setStatus(newStatus);
        verification.setUpdatedAt(LocalDateTime.now());
        
        if (newStatus == KYCStatus.APPROVED) {
            verification.setVerifiedAt(LocalDateTime.now());
            verification.setExpiresAt(LocalDateTime.now()
                    .plusDays(kycProperties.getVerification().getVerificationExpiryDays()));
        }
        
        KYCVerification saved = verificationRepository.save(verification);
        
        publishEvent("kyc.verification.status.updated", Map.of(
                "verificationId", verificationId,
                "oldStatus", verification.getStatus().toString(),
                "newStatus", newStatus.toString()
        ));
        
        return mapToResponse(saved);
    }

    @Override
    public KYCVerificationResponse reviewVerification(String verificationId, ReviewRequest request) {
        log.info("Reviewing verification: {} with decision: {}", verificationId, request.getDecision());
        
        KYCVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> KYCNotFoundException.verificationNotFound(verificationId));
        
        verification.setStatus(request.getDecision());
        verification.setUpdatedAt(LocalDateTime.now());
        
        if (request.getDecision() == KYCStatus.APPROVED) {
            verification.setVerifiedAt(LocalDateTime.now());
            verification.setExpiresAt(LocalDateTime.now()
                    .plusDays(kycProperties.getVerification().getVerificationExpiryDays()));
        } else if (request.getDecision() == KYCStatus.REJECTED) {
            verification.setRejectionReason(request.getRejectionReason());
        }
        
        if (request.getRiskScore() != null) {
            try {
                verification.setRiskScore(Double.parseDouble(request.getRiskScore()));
            } catch (NumberFormatException e) {
                log.warn("Invalid risk score format: {}", request.getRiskScore());
            }
        }
        
        KYCVerification saved = verificationRepository.save(verification);
        
        publishEvent("kyc.verification.reviewed", Map.of(
                "verificationId", verificationId,
                "decision", request.getDecision().toString(),
                "reviewerId", request.getReviewerId()
        ));
        
        return mapToResponse(saved);
    }

    @Override
    public KYCVerificationResponse cancelVerification(String verificationId, String reason) {
        KYCVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> KYCNotFoundException.verificationNotFound(verificationId));
        
        verification.setStatus(KYCStatus.CANCELLED);
        verification.setRejectionReason(reason);
        verification.setUpdatedAt(LocalDateTime.now());
        
        KYCVerification saved = verificationRepository.save(verification);
        
        publishEvent("kyc.verification.cancelled", Map.of(
                "verificationId", verificationId,
                "reason", reason
        ));
        
        return mapToResponse(saved);
    }

    @Override
    public void deleteVerification(String verificationId) {
        log.info("Deleting verification: {}", verificationId);
        
        if (!verificationRepository.existsById(verificationId)) {
            throw KYCNotFoundException.verificationNotFound(verificationId);
        }
        
        // Delete associated documents
        documentService.deleteVerificationDocuments(verificationId);
        
        // Delete verification
        verificationRepository.deleteById(verificationId);
        
        publishEvent("kyc.verification.deleted", Map.of(
                "verificationId", verificationId
        ));
    }

    @Override
    public Page<KYCVerificationResponse> getPendingReviews(Pageable pageable) {
        Page<KYCVerification> pendingVerifications = 
                verificationRepository.findByStatus(KYCStatus.PENDING_REVIEW, pageable);
        
        return pendingVerifications.map(this::mapToResponse);
    }

    @Override
    public KYCStatusResponse getUserKYCStatus(String userId) {
        KYCVerification activeVerification = verificationRepository
                .findActiveVerificationByUserId(userId)
                .orElse(null);
        
        KYCStatusResponse.KYCStatusResponseBuilder builder = KYCStatusResponse.builder()
                .userId(userId);
        
        if (activeVerification != null) {
            builder.currentStatus(activeVerification.getStatus())
                    .currentLevel(activeVerification.getVerificationLevel())
                    .lastVerifiedAt(activeVerification.getVerifiedAt())
                    .expiresAt(activeVerification.getExpiresAt())
                    .isActive(activeVerification.getStatus() == KYCStatus.APPROVED)
                    .canUpgrade(canUpgradeLevel(activeVerification.getVerificationLevel()));
            
            if (activeVerification.getExpiresAt() != null) {
                long daysUntilExpiry = ChronoUnit.DAYS.between(
                        LocalDateTime.now(), activeVerification.getExpiresAt());
                builder.daysUntilExpiry((int) daysUntilExpiry);
            }
        } else {
            builder.currentStatus(KYCStatus.NOT_STARTED)
                    .isActive(false)
                    .canUpgrade(true);
        }
        
        return builder.build();
    }

    @Override
    public boolean isUserVerified(String userId, String requiredLevel) {
        return verificationRepository.findActiveVerificationByUserId(userId)
                .map(v -> v.getStatus() == KYCStatus.APPROVED &&
                         v.getVerificationLevel().ordinal() >= 
                         VerificationLevel.valueOf(requiredLevel.toUpperCase()).ordinal())
                .orElse(false);
    }

    @Override
    public boolean canUserPerformAction(String userId, String action) {
        // Define action-to-level mapping
        Map<String, VerificationLevel> actionRequirements = Map.of(
                "SEND_MONEY", VerificationLevel.BASIC,
                "RECEIVE_MONEY", VerificationLevel.BASIC,
                "INTERNATIONAL_TRANSFER", VerificationLevel.INTERMEDIATE,
                "HIGH_VALUE_TRANSFER", VerificationLevel.ADVANCED,
                "CRYPTO_PURCHASE", VerificationLevel.ADVANCED
        );
        
        VerificationLevel requiredLevel = actionRequirements.getOrDefault(action, VerificationLevel.BASIC);
        return isUserVerified(userId, requiredLevel.toString());
    }

    @Override
    public VerificationHistoryResponse getUserVerificationHistory(String userId) {
        List<KYCVerification> verifications = verificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
        
        List<VerificationHistoryResponse.VerificationAttempt> attempts = verifications.stream()
                .map(v -> VerificationHistoryResponse.VerificationAttempt.builder()
                        .id(v.getId())
                        .status(v.getStatus().toString())
                        .level(v.getVerificationLevel().toString())
                        .provider(v.getProvider())
                        .attemptedAt(v.getCreatedAt())
                        .completedAt(v.getUpdatedAt())
                        .result(v.getStatus().toString())
                        .failureReason(v.getRejectionReason())
                        .durationSeconds((int) ChronoUnit.SECONDS.between(
                                v.getCreatedAt(), v.getUpdatedAt()))
                        .build())
                .collect(Collectors.toList());
        
        return VerificationHistoryResponse.builder()
                .userId(userId)
                .totalAttempts(attempts.size())
                .successfulAttempts((int) attempts.stream()
                        .filter(a -> "APPROVED".equals(a.getStatus()))
                        .count())
                .failedAttempts((int) attempts.stream()
                        .filter(a -> "REJECTED".equals(a.getStatus()))
                        .count())
                .firstAttemptAt(attempts.isEmpty() ? null : 
                        attempts.get(attempts.size() - 1).getAttemptedAt())
                .lastAttemptAt(attempts.isEmpty() ? null : 
                        attempts.get(0).getAttemptedAt())
                .attempts(attempts)
                .build();
    }

    @Override
    public Page<KYCVerificationResponse> searchVerifications(String query, Pageable pageable) {
        // Implement search logic based on query
        Page<KYCVerification> results = verificationRepository
                .searchVerifications(query, pageable);
        
        return results.map(this::mapToResponse);
    }

    @Override
    public void processProviderWebhook(String provider, String webhookData) {
        log.info("Processing webhook from provider: {}", provider);
        
        // Delegate to provider service
        kycProviderService.processWebhook(Map.of(
                "provider", provider,
                "data", webhookData
        ));
    }

    @Override
    @Transactional
    public void checkExpiredVerifications() {
        log.info("Checking for expired verifications");
        
        List<KYCVerification> expiredVerifications = verificationRepository
                .findExpiredVerifications(LocalDateTime.now(), KYCStatus.APPROVED);
        
        for (KYCVerification verification : expiredVerifications) {
            verification.setStatus(KYCStatus.EXPIRED);
            verification.setUpdatedAt(LocalDateTime.now());
        }
        
        verificationRepository.saveAll(expiredVerifications);
        
        log.info("Marked {} verifications as expired", expiredVerifications.size());
    }

    @Override
    public void syncPendingVerifications() {
        log.info("Syncing pending verifications with providers");
        
        List<KYCVerification> pendingVerifications = verificationRepository
                .findByStatus(KYCStatus.PENDING);
        
        for (KYCVerification verification : pendingVerifications) {
            try {
                Map<String, Object> status = kycProviderService
                        .getVerificationStatus(verification.getProviderSessionId());
                
                // Update verification based on provider status
                updateVerificationFromProviderStatus(verification, status);
            } catch (Exception e) {
                log.error("Error syncing verification {}: {}", 
                        verification.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void generateComplianceReports() {
        log.info("Generating compliance reports");
        // Implementation for compliance report generation
        // This would typically aggregate data and send to compliance team
    }

    private void validateStatusTransition(KYCStatus currentStatus, KYCStatus newStatus) {
        // Define valid status transitions
        boolean isValidTransition = switch (currentStatus) {
            case PENDING -> true; // Can transition to any status
            case PENDING_REVIEW -> newStatus == KYCStatus.APPROVED || 
                                  newStatus == KYCStatus.REJECTED ||
                                  newStatus == KYCStatus.REQUIRES_ADDITIONAL_INFO;
            case APPROVED -> newStatus == KYCStatus.EXPIRED;
            case REJECTED, CANCELLED, EXPIRED -> false; // Terminal states
            case REQUIRES_ADDITIONAL_INFO -> newStatus == KYCStatus.PENDING_REVIEW;
            default -> false;
        };
        
        if (!isValidTransition) {
            throw KYCValidationException.invalidStatus(
                    currentStatus.toString(), newStatus.toString());
        }
    }

    private boolean canUpgradeLevel(VerificationLevel currentLevel) {
        return currentLevel.ordinal() < VerificationLevel.ADVANCED.ordinal();
    }

    private void updateVerificationFromProviderStatus(KYCVerification verification, 
                                                     Map<String, Object> providerStatus) {
        String status = (String) providerStatus.get("status");
        if ("complete".equalsIgnoreCase(status)) {
            verification.setStatus(KYCStatus.PENDING_REVIEW);
            verification.setProviderVerificationId((String) providerStatus.get("verificationId"));
            verificationRepository.save(verification);
        }
    }

    private void publishEvent(String topic, Map<String, Object> data) {
        try {
            kafkaTemplate.send(topic, data);
        } catch (Exception e) {
            log.error("Failed to publish event to topic {}: {}", topic, e.getMessage());
        }
    }

    private KYCVerificationResponse mapToResponse(KYCVerification verification) {
        return KYCVerificationResponse.builder()
                .id(verification.getId())
                .userId(verification.getUserId())
                .status(verification.getStatus())
                .verificationLevel(verification.getVerificationLevel())
                .provider(verification.getProvider())
                .createdAt(verification.getCreatedAt())
                .updatedAt(verification.getUpdatedAt())
                .verifiedAt(verification.getVerifiedAt())
                .expiresAt(verification.getExpiresAt())
                .rejectionReason(verification.getRejectionReason())
                .attemptCount(verification.getAttemptCount())
                .canRetry(verification.getAttemptCount() < 
                         kycProperties.getVerification().getMaxAttemptsPerUser())
                .build();
    }
    
    @Override
    @Transactional
    public void syncFromLegacyService(String userId, KYCVerificationResponse legacyResponse) {
        log.info("Syncing KYC data from legacy service for user: {}", userId);
        
        // Check if verification already exists
        Optional<KYCVerification> existing = verificationRepository.findByUserId(userId);
        
        KYCVerification verification;
        if (existing.isPresent()) {
            verification = existing.get();
            log.debug("Updating existing verification for user: {}", userId);
        } else {
            verification = new KYCVerification();
            verification.setId(legacyResponse.getId() != null ? legacyResponse.getId() : UUID.randomUUID().toString());
            verification.setUserId(userId);
            log.debug("Creating new verification for user: {}", userId);
        }
        
        // Update verification data
        verification.setStatus(legacyResponse.getStatus());
        verification.setVerificationLevel(legacyResponse.getVerificationLevel());
        verification.setProvider(legacyResponse.getProvider());
        verification.setVerifiedAt(legacyResponse.getVerifiedAt());
        verification.setExpiresAt(legacyResponse.getExpiresAt());
        verification.setRejectionReason(legacyResponse.getRejectionReason());
        verification.setAttemptCount(legacyResponse.getAttemptCount() != null ? legacyResponse.getAttemptCount() : 1);
        verification.setUpdatedAt(LocalDateTime.now());
        
        verificationRepository.save(verification);
        
        log.info("Successfully synced KYC data from legacy service for user: {}", userId);
    }
    
    @Transactional(readOnly = true)
    public Map<String, Object> getVerificationStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        Map<String, Object> stats = new HashMap<>();
        
        // Total verifications
        long total = verificationRepository.count();
        stats.put("totalVerifications", total);
        
        // Status breakdown for the period
        Map<String, Long> statusCounts = new HashMap<>();
        for (KYCVerification.Status status : KYCVerification.Status.values()) {
            long count = verificationRepository.countByStatusAndCreatedAtBetween(status, startDate, endDate);
            statusCounts.put(status.toString(), count);
        }
        stats.put("statusBreakdownInPeriod", statusCounts);
        
        // Daily statistics
        stats.put("newVerificationsToday", verificationRepository.countByCreatedAtBetween(
                startDate.toLocalDate().atStartOfDay(), endDate));
        stats.put("completedToday", verificationRepository.countByCompletedAtBetween(
                startDate.toLocalDate().atStartOfDay(), endDate));
        
        // Success metrics
        long verified = statusCounts.getOrDefault("VERIFIED", 0L);
        long rejected = statusCounts.getOrDefault("REJECTED", 0L);
        long completed = verified + rejected;
        
        if (completed > 0) {
            double successRate = (double) verified / completed * 100;
            stats.put("successRate", String.format("%.2f%%", successRate));
        } else {
            stats.put("successRate", "N/A");
        }
        
        // Pending and in-progress
        long pending = verificationRepository.countByStatus(KYCVerification.Status.PENDING);
        long inProgress = verificationRepository.countByStatus(KYCVerification.Status.IN_PROGRESS);
        long manualReview = verificationRepository.countByStatus(KYCVerification.Status.MANUAL_REVIEW);
        
        stats.put("pendingVerifications", pending);
        stats.put("inProgressVerifications", inProgress);
        stats.put("requiresManualReview", manualReview);
        
        // Provider usage
        Map<String, Long> providerUsage = new HashMap<>();
        List<KYCVerification> verificationsInPeriod = verificationRepository
                .findByCreatedAtBetween(startDate, endDate);
        
        for (KYCVerification verification : verificationsInPeriod) {
            String provider = verification.getProvider();
            providerUsage.put(provider, providerUsage.getOrDefault(provider, 0L) + 1);
        }
        stats.put("providerUsage", providerUsage);
        
        // Failure reasons
        List<KYCVerification> failedVerifications = verificationRepository
                .findByStatusInAndCreatedAtBetween(
                        List.of(KYCVerification.Status.REJECTED, KYCVerification.Status.FAILED),
                        startDate, endDate
                );
        
        Map<String, Long> failureReasons = new HashMap<>();
        for (KYCVerification verification : failedVerifications) {
            String reason = verification.getRejectionReason() != null ? 
                    verification.getRejectionReason() : "Unknown";
            failureReasons.put(reason, failureReasons.getOrDefault(reason, 0L) + 1);
        }
        stats.put("topFailureReasons", failureReasons);
        
        return stats;
    }
}