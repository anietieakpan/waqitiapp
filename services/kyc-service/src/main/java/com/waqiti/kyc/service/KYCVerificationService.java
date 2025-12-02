package com.waqiti.kyc.service;

import com.waqiti.kyc.domain.KYCVerification;
import com.waqiti.kyc.domain.VerificationDocument;
import com.waqiti.kyc.dto.request.KYCVerificationRequest;
import com.waqiti.kyc.dto.response.KYCVerificationResponse;
import com.waqiti.kyc.event.*;
import com.waqiti.kyc.exception.KYCVerificationException;
import com.waqiti.kyc.integration.KYCProvider;
import com.waqiti.kyc.repository.KYCVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import com.waqiti.common.cache.CacheService;
import com.waqiti.common.cache.DistributedLockService;
import com.waqiti.common.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class KYCVerificationService {

    private final KYCVerificationRepository verificationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final List<KYCProvider> kycProviders;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;
    private final DocumentProcessingService documentProcessingService;
    private final ComplianceCheckService complianceCheckService;
    private final RiskAssessmentService riskAssessmentService;
    
    @Value("${kyc.default-provider:ONFIDO}")
    private String defaultProvider;
    
    @Value("${kyc.auto-approve-low-risk:true}")
    private boolean autoApproveLowRisk;
    
    @Value("${kyc.verification-expiry-days:30}")
    private int verificationExpiryDays;
    
    @Transactional
    public CompletableFuture<KYCVerificationResponse> initiateVerification(
            UUID userId, KYCVerificationRequest request) {
        
        log.info("Initiating KYC verification for user: {} level: {}", 
                userId, request.getVerificationLevel());
        
        return CompletableFuture.supplyAsync(() -> {
            String lockKey = "kyc-initiate:" + userId;
            return lockService.executeWithLock(lockKey, Duration.ofMinutes(5), Duration.ofSeconds(30), () -> {
                
                // Check if user already has pending or valid verification
                Optional<KYCVerification> existingVerification = verificationRepository
                        .findByUserIdAndStatusIn(userId, Arrays.asList(
                                KYCVerification.Status.PENDING,
                                KYCVerification.Status.IN_PROGRESS,
                                KYCVerification.Status.VERIFIED
                        ));
                
                if (existingVerification.isPresent()) {
                    KYCVerification existing = existingVerification.get();
                    if (existing.getStatus() == KYCVerification.Status.VERIFIED) {
                        // Check if verification is still valid
                        if (existing.getCompletedAt().plusDays(verificationExpiryDays).isAfter(LocalDateTime.now())) {
                            throw new KYCVerificationException("User already has valid KYC verification");
                        }
                    } else {
                        throw new KYCVerificationException("User already has pending KYC verification");
                    }
                }
                
                // Perform compliance and risk checks
                performPreVerificationChecks(userId, request);
                
                // Select KYC provider
                KYCProvider provider = selectProvider(request.getProvider());
                
                // Create verification record
                KYCVerification verification = KYCVerification.builder()
                        .userId(userId)
                        .verificationLevel(request.getVerificationLevel())
                        .provider(provider.getProviderName())
                        .status(KYCVerification.Status.PENDING)
                        .requestData(Map.of(
                                "firstName", request.getFirstName(),
                                "lastName", request.getLastName(),
                                "email", request.getEmail(),
                                "phone", request.getPhoneNumber(),
                                "dateOfBirth", request.getDateOfBirth(),
                                "address", request.getAddress(),
                                "nationality", request.getNationality(),
                                "occupation", request.getOccupation()
                        ))
                        .metadata(new HashMap<>())
                        .expiresAt(LocalDateTime.now().plusDays(verificationExpiryDays))
                        .build();
                
                verification = verificationRepository.save(verification);
                
                // Initiate verification with provider
                try {
                    CompletableFuture<KYCVerificationResponse> providerResponse = 
                            provider.initiateVerification(request);
                    
                    KYCVerificationResponse response = providerResponse.get();
                    
                    // Update verification with provider details
                    verification.setProviderVerificationId(response.getVerificationId());
                    verification.setProviderReference(response.getProviderReference());
                    verification.setStatus(KYCVerification.Status.IN_PROGRESS);
                    verification.setSdkToken(response.getSdkToken());
                    verification.setEstimatedCompletionTime(response.getEstimatedCompletionTime());
                    
                    verificationRepository.save(verification);
                    
                    // Publish event
                    eventPublisher.publish(KYCVerificationInitiatedEvent.builder()
                            .verificationId(verification.getId())
                            .userId(userId)
                            .verificationLevel(request.getVerificationLevel())
                            .provider(provider.getProviderName())
                            .initiatedAt(LocalDateTime.now())
                            .build());
                    
                    // Map to response
                    return KYCVerificationResponse.builder()
                            .verificationId(verification.getId().toString())
                            .userId(userId)
                            .status(verification.getStatus())
                            .providerReference(verification.getProviderReference())
                            .sdkToken(verification.getSdkToken())
                            .requiredDocuments(response.getRequiredDocuments())
                            .estimatedCompletionTime(verification.getEstimatedCompletionTime())
                            .metadata(response.getMetadata())
                            .build();
                    
                } catch (Exception e) {
                    log.error("Failed to initiate verification with provider", e);
                    verification.setStatus(KYCVerification.Status.FAILED);
                    verification.setFailureReason("Provider initiation failed: " + e.getMessage());
                    verificationRepository.save(verification);
                    
                    throw new KYCVerificationException("Failed to initiate verification", e);
                }
            });
        });
    }
    
    @Transactional(readOnly = true)
    public KYCVerificationResponse getVerificationStatus(UUID userId, UUID verificationId) {
        KYCVerification verification = verificationRepository.findByIdAndUserId(verificationId, userId)
                .orElseThrow(() -> new KYCVerificationException("Verification not found"));
        
        // Check cache first
        String cacheKey = "kyc-status:" + verificationId;
        KYCVerificationResponse cached = cacheService.get(cacheKey, KYCVerificationResponse.class);
        if (cached != null && isFinalStatus(verification.getStatus())) {
            return cached;
        }
        
        // Get latest status from provider if not final
        if (!isFinalStatus(verification.getStatus())) {
            try {
                KYCProvider provider = getProvider(verification.getProvider());
                CompletableFuture<KYCVerificationResponse> providerResponse = 
                        provider.checkVerificationStatus(verification.getProviderVerificationId());
                
                KYCVerificationResponse response = providerResponse.get();
                
                // Update local status if changed
                if (response.getStatus() != verification.getStatus()) {
                    updateVerificationStatus(verificationId, response.getStatus(), 
                            response.getFailureReason(), response.getResults());
                }
                
                return response;
            } catch (Exception e) {
                log.error("Failed to check provider status", e);
                // Fall back to local status
            }
        }
        
        // Return local status
        KYCVerificationResponse response = mapToResponse(verification);
        
        // Cache if final status
        if (isFinalStatus(verification.getStatus())) {
            cacheService.set(cacheKey, response, Duration.ofDays(7));
        }
        
        return response;
    }
    
    @Transactional
    public CompletableFuture<Void> uploadDocument(UUID userId, UUID verificationId, 
                                                String documentType, MultipartFile file) {
        
        return CompletableFuture.runAsync(() -> {
            KYCVerification verification = verificationRepository.findByIdAndUserId(verificationId, userId)
                    .orElseThrow(() -> new KYCVerificationException("Verification not found"));
            
            if (!canUploadDocuments(verification.getStatus())) {
                throw new KYCVerificationException("Cannot upload documents in current verification state");
            }
            
            try {
                // Process and validate document
                VerificationDocument document = documentProcessingService.processDocument(
                        file, documentType, verification.getId());
                
                document = documentRepository.save(document);
                
                // Upload to provider
                KYCProvider provider = getProvider(verification.getProvider());
                provider.uploadDocument(verification.getProviderVerificationId(), document);
                
                // Update document status
                document.setStatus(VerificationDocument.Status.UPLOADED);
                document.setUploadedAt(LocalDateTime.now());
                documentRepository.save(document);
                
                // Publish event
                eventPublisher.publish(KYCDocumentUploadedEvent.builder()
                        .verificationId(verificationId)
                        .documentId(document.getId())
                        .documentType(documentType)
                        .uploadedAt(LocalDateTime.now())
                        .build());
                
                log.info("Document uploaded successfully: {} for verification: {}", 
                        document.getId(), verificationId);
                
            } catch (Exception e) {
                log.error("Failed to upload document", e);
                throw new KYCVerificationException("Failed to upload document", e);
            }
        });
    }
    
    @Transactional(readOnly = true)
    public Page<KYCVerificationResponse> getUserVerifications(UUID userId, Pageable pageable) {
        Page<KYCVerification> verifications = verificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        return verifications.map(this::mapToResponse);
    }
    
    @Transactional
    public void updateVerificationStatus(UUID verificationId, KYCVerification.Status status, 
                                       String reason, Map<String, Object> results) {
        
        KYCVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new KYCVerificationException("Verification not found"));
        
        KYCVerification.Status oldStatus = verification.getStatus();
        verification.setStatus(status);
        
        if (reason != null) {
            verification.setFailureReason(reason);
        }
        
        if (results != null) {
            verification.getMetadata().putAll(results);
        }
        
        switch (status) {
            case VERIFIED:
                verification.setCompletedAt(LocalDateTime.now());
                verification.setVerifiedAt(LocalDateTime.now());
                break;
            case REJECTED:
                verification.setCompletedAt(LocalDateTime.now());
                verification.setRejectedAt(LocalDateTime.now());
                break;
            case FAILED:
                verification.setFailedAt(LocalDateTime.now());
                break;
            case EXPIRED:
                verification.setExpiredAt(LocalDateTime.now());
                break;
        }
        
        verificationRepository.save(verification);
        
        // Clear cache
        cacheService.delete("kyc-status:" + verificationId);
        
        log.info("Verification status updated: {} {} -> {}", 
                verificationId, oldStatus, status);
    }
    
    @Transactional
    public void updateMetadata(UUID verificationId, Map<String, Object> metadata) {
        KYCVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new KYCVerificationException("Verification not found"));
        
        verification.getMetadata().putAll(metadata);
        verificationRepository.save(verification);
    }
    
    @Transactional(readOnly = true)
    public Optional<KYCVerification> findByProviderReference(String providerReference) {
        return verificationRepository.findByProviderVerificationId(providerReference)
                .or(() -> verificationRepository.findByProviderReference(providerReference));
    }
    
    @Transactional
    public CompletableFuture<byte[]> downloadVerificationReport(UUID userId, UUID verificationId) {
        return CompletableFuture.supplyAsync(() -> {
            KYCVerification verification = verificationRepository.findByIdAndUserId(verificationId, userId)
                    .orElseThrow(() -> new KYCVerificationException("Verification not found"));
            
            if (verification.getStatus() != KYCVerification.Status.VERIFIED &&
                verification.getStatus() != KYCVerification.Status.REJECTED) {
                throw new KYCVerificationException("Report not available for current verification status");
            }
            
            try {
                KYCProvider provider = getProvider(verification.getProvider());
                return provider.downloadVerificationReport(verification.getProviderVerificationId()).get();
            } catch (Exception e) {
                log.error("Failed to download verification report", e);
                throw new KYCVerificationException("Failed to download verification report", e);
            }
        });
    }
    
    @Transactional
    public void retryVerification(UUID userId, UUID verificationId) {
        KYCVerification verification = verificationRepository.findByIdAndUserId(verificationId, userId)
                .orElseThrow(() -> new KYCVerificationException("Verification not found"));
        
        if (!canRetry(verification.getStatus())) {
            throw new KYCVerificationException("Cannot retry verification in current state");
        }
        
        verification.setStatus(KYCVerification.Status.PENDING);
        verification.setFailureReason(null);
        verification.setRetryCount(verification.getRetryCount() + 1);
        verification.setLastRetryAt(LocalDateTime.now());
        
        if (verification.getRetryCount() > 3) {
            throw new KYCVerificationException("Maximum retry attempts exceeded");
        }
        
        verificationRepository.save(verification);
        
        // Initiate retry with provider
        try {
            KYCProvider provider = getProvider(verification.getProvider());
            // Implementation would restart the verification process
        } catch (Exception e) {
            log.error("Failed to retry verification", e);
            throw new KYCVerificationException("Failed to retry verification", e);
        }
    }
    
    // Helper methods
    
    private void performPreVerificationChecks(UUID userId, KYCVerificationRequest request) {
        // Perform compliance checks
        complianceCheckService.performChecks(userId, request);
        
        // Perform risk assessment
        riskAssessmentService.assessRisk(userId, request);
    }
    
    private KYCProvider selectProvider(String preferredProvider) {
        if (preferredProvider != null) {
            return kycProviders.stream()
                    .filter(provider -> provider.getProviderName().equals(preferredProvider))
                    .filter(KYCProvider::isAvailable)
                    .findFirst()
                    .orElseThrow(() -> new KYCVerificationException("Preferred provider not available"));
        }
        
        return kycProviders.stream()
                .filter(KYCProvider::isAvailable)
                .findFirst()
                .orElseThrow(() -> new KYCVerificationException("No KYC provider available"));
    }
    
    private KYCProvider getProvider(String providerName) {
        return kycProviders.stream()
                .filter(provider -> provider.getProviderName().equals(providerName))
                .findFirst()
                .orElseThrow(() -> new KYCVerificationException("Provider not found: " + providerName));
    }
    
    private boolean isFinalStatus(KYCVerification.Status status) {
        return status == KYCVerification.Status.VERIFIED ||
               status == KYCVerification.Status.REJECTED ||
               status == KYCVerification.Status.FAILED ||
               status == KYCVerification.Status.EXPIRED;
    }
    
    private boolean canUploadDocuments(KYCVerification.Status status) {
        return status == KYCVerification.Status.PENDING ||
               status == KYCVerification.Status.IN_PROGRESS ||
               status == KYCVerification.Status.MANUAL_REVIEW;
    }
    
    private boolean canRetry(KYCVerification.Status status) {
        return status == KYCVerification.Status.FAILED ||
               status == KYCVerification.Status.REJECTED;
    }
    
    private KYCVerificationResponse mapToResponse(KYCVerification verification) {
        return KYCVerificationResponse.builder()
                .verificationId(verification.getId().toString())
                .userId(verification.getUserId())
                .status(verification.getStatus())
                .verificationLevel(verification.getVerificationLevel())
                .provider(verification.getProvider())
                .providerReference(verification.getProviderReference())
                .initiatedAt(verification.getCreatedAt())
                .completedAt(verification.getCompletedAt())
                .estimatedCompletionTime(verification.getEstimatedCompletionTime())
                .failureReason(verification.getFailureReason())
                .results(verification.getMetadata())
                .retryCount(verification.getRetryCount())
                .lastRetryAt(verification.getLastRetryAt())
                .expiresAt(verification.getExpiresAt())
                .build();
    }
}