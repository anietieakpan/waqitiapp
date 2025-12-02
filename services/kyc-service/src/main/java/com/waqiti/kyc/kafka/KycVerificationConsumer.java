package com.waqiti.kyc.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.kyc.service.KycVerificationService;
import com.waqiti.kyc.service.DocumentVerificationService;
import com.waqiti.kyc.service.IdentityVerificationService;
import com.waqiti.kyc.service.KycNotificationService;
import com.waqiti.common.audit.AuditService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycVerificationConsumer {
    
    private final KycVerificationService kycVerificationService;
    private final DocumentVerificationService documentVerificationService;
    private final IdentityVerificationService identityVerificationService;
    private final KycNotificationService kycNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"kyc-verification-events", "kyc-verification"},
        groupId = "kyc-service-verification-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleKycVerification(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("KYC VERIFICATION: Processing KYC verification - Topic: {}, Partition: {}, Offset: {}", 
                topic, partition, offset);
        
        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID verificationId = null;
        UUID userId = null;
        String verificationType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            verificationId = UUID.fromString((String) event.get("verificationId"));
            userId = UUID.fromString((String) event.get("userId"));
            verificationType = (String) event.get("verificationType");
            String verificationStatus = (String) event.get("verificationStatus");
            String verificationLevel = (String) event.get("verificationLevel");
            @SuppressWarnings("unchecked")
            List<String> verificationSteps = (List<String>) event.getOrDefault("verificationSteps", List.of());
            @SuppressWarnings("unchecked")
            Map<String, Object> verificationData = (Map<String, Object>) event.getOrDefault("verificationData", Map.of());
            Integer riskScore = event.containsKey("riskScore") ? (Integer) event.get("riskScore") : 0;
            String country = (String) event.get("country");
            String idType = (String) event.get("idType");
            String idNumber = (String) event.get("idNumber");
            Boolean biometricVerified = (Boolean) event.getOrDefault("biometricVerified", false);
            Boolean addressVerified = (Boolean) event.getOrDefault("addressVerified", false);
            Boolean documentVerified = (Boolean) event.getOrDefault("documentVerified", false);
            LocalDateTime verificationTimestamp = LocalDateTime.parse((String) event.get("timestamp"));
            String rejectionReason = (String) event.get("rejectionReason");
            @SuppressWarnings("unchecked")
            List<String> flags = (List<String>) event.getOrDefault("flags", List.of());
            String verificationMethod = (String) event.get("verificationMethod");
            UUID verifierId = event.containsKey("verifierId") ? 
                    UUID.fromString((String) event.get("verifierId")) : null;
            
            log.info("KYC verification - VerificationId: {}, UserId: {}, Type: {}, Status: {}, Level: {}, Risk: {}, Biometric: {}, Document: {}, Address: {}", 
                    verificationId, userId, verificationType, verificationStatus, verificationLevel, 
                    riskScore, biometricVerified, documentVerified, addressVerified);
            
            validateKycVerification(verificationId, userId, verificationType, verificationStatus, 
                    verificationLevel);
            
            processVerificationByType(verificationId, userId, verificationType, verificationStatus, 
                    verificationLevel, verificationSteps, verificationData, riskScore, country, 
                    idType, idNumber, biometricVerified, addressVerified, documentVerified, 
                    verificationTimestamp, rejectionReason, flags, verificationMethod, verifierId);
            
            if ("VERIFIED".equals(verificationStatus)) {
                handleVerifiedKyc(verificationId, userId, verificationType, verificationLevel, 
                        riskScore, biometricVerified, addressVerified, documentVerified, 
                        verificationTimestamp);
            } else if ("REJECTED".equals(verificationStatus)) {
                handleRejectedKyc(verificationId, userId, verificationType, rejectionReason, 
                        riskScore, flags);
            } else if ("PENDING_REVIEW".equals(verificationStatus)) {
                handlePendingReviewKyc(verificationId, userId, verificationType, verificationLevel, 
                        riskScore, flags);
            } else if ("RESUBMISSION_REQUIRED".equals(verificationStatus)) {
                handleResubmissionRequired(verificationId, userId, verificationType, rejectionReason);
            }
            
            if (riskScore >= 70 || !flags.isEmpty()) {
                performEnhancedDueDiligence(verificationId, userId, riskScore, flags, country, 
                        verificationData);
            }
            
            updateUserKycStatus(userId, verificationStatus, verificationLevel, verificationTimestamp);
            
            notifyUser(userId, verificationId, verificationType, verificationStatus, 
                    verificationLevel, rejectionReason);
            
            if (verifierId != null) {
                notifyVerifier(verifierId, verificationId, userId, verificationType, 
                        verificationStatus);
            }
            
            updateKycMetrics(verificationType, verificationStatus, verificationLevel, riskScore, 
                    country, verificationMethod);
            
            auditKycVerification(verificationId, userId, verificationType, verificationStatus, 
                    verificationLevel, riskScore, processingStartTime);
            
            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            
            log.info("KYC verification processed - VerificationId: {}, Type: {}, Status: {}, ProcessingTime: {}ms", 
                    verificationId, verificationType, verificationStatus, processingTimeMs);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("CRITICAL: KYC verification processing failed - VerificationId: {}, UserId: {}, Type: {}, Error: {}", 
                    verificationId, userId, verificationType, e.getMessage(), e);
            
            if (verificationId != null && userId != null) {
                handleVerificationFailure(verificationId, userId, verificationType, e);
            }
            
            throw new RuntimeException("KYC verification processing failed", e);
        }
    }
    
    private void validateKycVerification(UUID verificationId, UUID userId, String verificationType,
                                        String verificationStatus, String verificationLevel) {
        if (verificationId == null || userId == null) {
            throw new IllegalArgumentException("Verification ID and User ID are required");
        }
        
        if (verificationType == null || verificationType.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification type is required");
        }
        
        if (verificationStatus == null || verificationStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Verification status is required");
        }
        
        List<String> validStatuses = List.of("VERIFIED", "REJECTED", "PENDING_REVIEW", 
                "RESUBMISSION_REQUIRED", "EXPIRED");
        if (!validStatuses.contains(verificationStatus)) {
            throw new IllegalArgumentException("Invalid verification status: " + verificationStatus);
        }
        
        log.debug("KYC verification validation passed - VerificationId: {}", verificationId);
    }
    
    private void processVerificationByType(UUID verificationId, UUID userId, String verificationType,
                                          String verificationStatus, String verificationLevel,
                                          List<String> verificationSteps, Map<String, Object> verificationData,
                                          Integer riskScore, String country, String idType, String idNumber,
                                          Boolean biometricVerified, Boolean addressVerified,
                                          Boolean documentVerified, LocalDateTime verificationTimestamp,
                                          String rejectionReason, List<String> flags,
                                          String verificationMethod, UUID verifierId) {
        try {
            switch (verificationType) {
                case "IDENTITY_VERIFICATION" -> processIdentityVerification(verificationId, userId, 
                        verificationStatus, verificationLevel, country, idType, idNumber, 
                        biometricVerified, documentVerified, verificationData);
                
                case "DOCUMENT_VERIFICATION" -> processDocumentVerification(verificationId, userId, 
                        verificationStatus, idType, documentVerified, verificationData);
                
                case "ADDRESS_VERIFICATION" -> processAddressVerification(verificationId, userId, 
                        verificationStatus, addressVerified, country, verificationData);
                
                case "BIOMETRIC_VERIFICATION" -> processBiometricVerification(verificationId, userId, 
                        verificationStatus, biometricVerified, verificationData);
                
                case "ENHANCED_DUE_DILIGENCE" -> processEnhancedDueDiligence(verificationId, userId, 
                        verificationStatus, riskScore, flags, verificationData);
                
                case "RE_VERIFICATION" -> processReVerification(verificationId, userId, 
                        verificationStatus, verificationLevel, verificationSteps);
                
                case "PERIODIC_REVIEW" -> processPeriodicReview(verificationId, userId, 
                        verificationStatus, verificationLevel);
                
                default -> {
                    log.warn("Unknown KYC verification type: {}", verificationType);
                    processGenericVerification(verificationId, userId, verificationType);
                }
            }
            
            log.debug("Verification type processing completed - VerificationId: {}, Type: {}", 
                    verificationId, verificationType);
            
        } catch (Exception e) {
            log.error("Failed to process verification by type - VerificationId: {}, Type: {}", 
                    verificationId, verificationType, e);
            throw new RuntimeException("Verification type processing failed", e);
        }
    }
    
    private void processIdentityVerification(UUID verificationId, UUID userId, String verificationStatus,
                                            String verificationLevel, String country, String idType,
                                            String idNumber, Boolean biometricVerified,
                                            Boolean documentVerified, Map<String, Object> verificationData) {
        log.info("Processing IDENTITY VERIFICATION - VerificationId: {}, Status: {}, Level: {}, Country: {}, IDType: {}", 
                verificationId, verificationStatus, verificationLevel, country, idType);
        
        identityVerificationService.processIdentityVerification(verificationId, userId, 
                verificationStatus, verificationLevel, country, idType, idNumber, biometricVerified, 
                documentVerified, verificationData);
    }
    
    private void processDocumentVerification(UUID verificationId, UUID userId, String verificationStatus,
                                            String idType, Boolean documentVerified,
                                            Map<String, Object> verificationData) {
        log.info("Processing DOCUMENT VERIFICATION - VerificationId: {}, Status: {}, IDType: {}, Verified: {}", 
                verificationId, verificationStatus, idType, documentVerified);
        
        documentVerificationService.processDocumentVerification(verificationId, userId, 
                verificationStatus, idType, documentVerified, verificationData);
    }
    
    private void processAddressVerification(UUID verificationId, UUID userId, String verificationStatus,
                                           Boolean addressVerified, String country,
                                           Map<String, Object> verificationData) {
        log.info("Processing ADDRESS VERIFICATION - VerificationId: {}, Status: {}, Country: {}, Verified: {}", 
                verificationId, verificationStatus, country, addressVerified);
        
        kycVerificationService.processAddressVerification(verificationId, userId, verificationStatus, 
                addressVerified, country, verificationData);
    }
    
    private void processBiometricVerification(UUID verificationId, UUID userId, String verificationStatus,
                                             Boolean biometricVerified, Map<String, Object> verificationData) {
        log.info("Processing BIOMETRIC VERIFICATION - VerificationId: {}, Status: {}, Verified: {}", 
                verificationId, verificationStatus, biometricVerified);
        
        kycVerificationService.processBiometricVerification(verificationId, userId, verificationStatus, 
                biometricVerified, verificationData);
    }
    
    private void processEnhancedDueDiligence(UUID verificationId, UUID userId, String verificationStatus,
                                            Integer riskScore, List<String> flags,
                                            Map<String, Object> verificationData) {
        log.warn("Processing ENHANCED DUE DILIGENCE - VerificationId: {}, Status: {}, RiskScore: {}, Flags: {}", 
                verificationId, verificationStatus, riskScore, flags.size());
        
        kycVerificationService.processEnhancedDueDiligence(verificationId, userId, verificationStatus, 
                riskScore, flags, verificationData);
    }
    
    private void processReVerification(UUID verificationId, UUID userId, String verificationStatus,
                                      String verificationLevel, List<String> verificationSteps) {
        log.info("Processing RE-VERIFICATION - VerificationId: {}, Status: {}, Level: {}", 
                verificationId, verificationStatus, verificationLevel);
        
        kycVerificationService.processReVerification(verificationId, userId, verificationStatus, 
                verificationLevel, verificationSteps);
    }
    
    private void processPeriodicReview(UUID verificationId, UUID userId, String verificationStatus,
                                      String verificationLevel) {
        log.info("Processing PERIODIC REVIEW - VerificationId: {}, Status: {}, Level: {}", 
                verificationId, verificationStatus, verificationLevel);
        
        kycVerificationService.processPeriodicReview(verificationId, userId, verificationStatus, 
                verificationLevel);
    }
    
    private void processGenericVerification(UUID verificationId, UUID userId, String verificationType) {
        log.info("Processing generic KYC verification - VerificationId: {}, Type: {}", 
                verificationId, verificationType);
        
        kycVerificationService.processGenericVerification(verificationId, userId, verificationType);
    }
    
    private void handleVerifiedKyc(UUID verificationId, UUID userId, String verificationType,
                                  String verificationLevel, Integer riskScore, Boolean biometricVerified,
                                  Boolean addressVerified, Boolean documentVerified,
                                  LocalDateTime verificationTimestamp) {
        try {
            log.info("Processing verified KYC - VerificationId: {}, Level: {}, Biometric: {}, Document: {}, Address: {}", 
                    verificationId, verificationLevel, biometricVerified, documentVerified, addressVerified);
            
            kycVerificationService.recordVerifiedKyc(verificationId, userId, verificationType, 
                    verificationLevel, riskScore, biometricVerified, addressVerified, 
                    documentVerified, verificationTimestamp);
            
            kycVerificationService.enableUserFeatures(userId, verificationLevel);
            
            kycVerificationService.schedulePeriodicReview(userId, verificationLevel, 
                    verificationTimestamp);
            
        } catch (Exception e) {
            log.error("Failed to handle verified KYC - VerificationId: {}", verificationId, e);
        }
    }
    
    private void handleRejectedKyc(UUID verificationId, UUID userId, String verificationType,
                                  String rejectionReason, Integer riskScore, List<String> flags) {
        try {
            log.warn("Processing rejected KYC - VerificationId: {}, Reason: {}, RiskScore: {}, Flags: {}", 
                    verificationId, rejectionReason, riskScore, flags.size());
            
            kycVerificationService.recordRejectedKyc(verificationId, userId, verificationType, 
                    rejectionReason, riskScore, flags);
            
            if (riskScore >= 80) {
                kycVerificationService.flagForInvestigation(userId, verificationId, rejectionReason, 
                        riskScore, flags);
            }
            
        } catch (Exception e) {
            log.error("Failed to handle rejected KYC - VerificationId: {}", verificationId, e);
        }
    }
    
    private void handlePendingReviewKyc(UUID verificationId, UUID userId, String verificationType,
                                       String verificationLevel, Integer riskScore, List<String> flags) {
        try {
            log.info("Processing pending review KYC - VerificationId: {}, Level: {}, RiskScore: {}, Flags: {}", 
                    verificationId, verificationLevel, riskScore, flags.size());
            
            kycVerificationService.createReviewTask(verificationId, userId, verificationType, 
                    verificationLevel, riskScore, flags);
            
            kycVerificationService.notifyComplianceTeam(verificationId, userId, verificationType, 
                    riskScore, flags);
            
        } catch (Exception e) {
            log.error("Failed to handle pending review KYC - VerificationId: {}", verificationId, e);
        }
    }
    
    private void handleResubmissionRequired(UUID verificationId, UUID userId, String verificationType,
                                           String rejectionReason) {
        try {
            log.info("Processing resubmission required - VerificationId: {}, Reason: {}", 
                    verificationId, rejectionReason);
            
            kycVerificationService.requestResubmission(verificationId, userId, verificationType, 
                    rejectionReason);
            
        } catch (Exception e) {
            log.error("Failed to handle resubmission required - VerificationId: {}", verificationId, e);
        }
    }
    
    private void performEnhancedDueDiligence(UUID verificationId, UUID userId, Integer riskScore,
                                            List<String> flags, String country,
                                            Map<String, Object> verificationData) {
        try {
            log.warn("Performing enhanced due diligence - UserId: {}, RiskScore: {}, Flags: {}", 
                    userId, riskScore, flags.size());
            
            kycVerificationService.performEnhancedDueDiligence(verificationId, userId, riskScore, 
                    flags, country, verificationData);
            
        } catch (Exception e) {
            log.error("Failed to perform enhanced due diligence - VerificationId: {}", verificationId, e);
        }
    }
    
    private void updateUserKycStatus(UUID userId, String verificationStatus, String verificationLevel,
                                    LocalDateTime verificationTimestamp) {
        try {
            if ("VERIFIED".equals(verificationStatus)) {
                kycVerificationService.updateUserKycStatus(userId, verificationStatus, 
                        verificationLevel, verificationTimestamp);
                
                log.info("User KYC status updated - UserId: {}, Status: {}, Level: {}", 
                        userId, verificationStatus, verificationLevel);
            }
            
        } catch (Exception e) {
            log.error("Failed to update user KYC status - UserId: {}", userId, e);
        }
    }
    
    private void notifyUser(UUID userId, UUID verificationId, String verificationType,
                           String verificationStatus, String verificationLevel, String rejectionReason) {
        try {
            kycNotificationService.sendVerificationNotification(userId, verificationId, verificationType, 
                    verificationStatus, verificationLevel, rejectionReason);
            
            log.info("User notified of KYC verification - UserId: {}, VerificationId: {}, Status: {}", 
                    userId, verificationId, verificationStatus);
            
        } catch (Exception e) {
            log.error("Failed to notify user - UserId: {}, VerificationId: {}", userId, verificationId, e);
        }
    }
    
    private void notifyVerifier(UUID verifierId, UUID verificationId, UUID userId,
                               String verificationType, String verificationStatus) {
        try {
            kycNotificationService.sendVerifierNotification(verifierId, verificationId, userId, 
                    verificationType, verificationStatus);
            
            log.info("Verifier notified - VerifierId: {}, VerificationId: {}", verifierId, verificationId);
            
        } catch (Exception e) {
            log.error("Failed to notify verifier - VerifierId: {}", verifierId, e);
        }
    }
    
    private void updateKycMetrics(String verificationType, String verificationStatus,
                                 String verificationLevel, Integer riskScore, String country,
                                 String verificationMethod) {
        try {
            kycVerificationService.updateVerificationMetrics(verificationType, verificationStatus, 
                    verificationLevel, riskScore, country, verificationMethod);
        } catch (Exception e) {
            log.error("Failed to update KYC metrics - Type: {}, Status: {}", verificationType, 
                    verificationStatus, e);
        }
    }
    
    private void auditKycVerification(UUID verificationId, UUID userId, String verificationType,
                                     String verificationStatus, String verificationLevel,
                                     Integer riskScore, LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditComplianceEvent(
                    "KYC_VERIFICATION_PROCESSED",
                    userId.toString(),
                    String.format("KYC verification %s - Type: %s, Level: %s, RiskScore: %d", 
                            verificationStatus, verificationType, verificationLevel, riskScore),
                    Map.of(
                            "verificationId", verificationId.toString(),
                            "userId", userId.toString(),
                            "verificationType", verificationType,
                            "verificationStatus", verificationStatus,
                            "verificationLevel", verificationLevel != null ? verificationLevel : "N/A",
                            "riskScore", riskScore,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit KYC verification - VerificationId: {}", verificationId, e);
        }
    }
    
    private void handleVerificationFailure(UUID verificationId, UUID userId, String verificationType,
                                          Exception error) {
        try {
            kycVerificationService.handleVerificationFailure(verificationId, userId, verificationType, 
                    error.getMessage());
            
            auditService.auditComplianceEvent(
                    "KYC_VERIFICATION_PROCESSING_FAILED",
                    userId.toString(),
                    "Failed to process KYC verification: " + error.getMessage(),
                    Map.of(
                            "verificationId", verificationId.toString(),
                            "userId", userId.toString(),
                            "verificationType", verificationType != null ? verificationType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle verification failure - VerificationId: {}", verificationId, e);
        }
    }
    
    @KafkaListener(
        topics = {"kyc-verification-events.DLQ", "kyc-verification.DLQ"},
        groupId = "kyc-service-verification-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: KYC verification event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID verificationId = event.containsKey("verificationId") ? 
                    UUID.fromString((String) event.get("verificationId")) : null;
            UUID userId = event.containsKey("userId") ? 
                    UUID.fromString((String) event.get("userId")) : null;
            String verificationType = (String) event.get("verificationType");
            
            log.error("DLQ: KYC verification failed permanently - VerificationId: {}, UserId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    verificationId, userId, verificationType);
            
            if (verificationId != null && userId != null) {
                kycVerificationService.markForManualReview(verificationId, userId, verificationType, 
                        "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse KYC verification DLQ event: {}", eventJson, e);
        }
    }
}