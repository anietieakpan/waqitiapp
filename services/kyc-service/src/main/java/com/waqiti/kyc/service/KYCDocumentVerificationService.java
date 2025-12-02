package com.waqiti.kyc.service;

import com.waqiti.kyc.domain.*;
import com.waqiti.kyc.dto.*;
import com.waqiti.kyc.event.*;
import com.waqiti.kyc.exception.DocumentVerificationException;
import com.waqiti.kyc.integration.ocr.OCRService;
import com.waqiti.kyc.integration.ai.DocumentAIService;
import com.waqiti.kyc.repository.DocumentVerificationRepository;
import com.waqiti.kyc.repository.VerificationDocumentRepository;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.encryption.EncryptionService;
import com.waqiti.common.events.EventPublisher;
import com.waqiti.common.fraud.FraudDetectionService;
import com.waqiti.common.metrics.MetricsCollector;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Comprehensive KYC Document Verification Service
 * 
 * Handles the complete document verification workflow including:
 * - Document upload and secure storage
 * - OCR and data extraction
 * - AI-powered document authenticity verification
 * - Cross-reference validation with submitted data
 * - Fraud detection and risk assessment
 * - Compliance checks
 * - Automated approval/rejection decisions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KYCDocumentVerificationService {

    private final DocumentStorageService storageService;
    private final DocumentVerificationRepository verificationRepository;
    private final VerificationDocumentRepository documentRepository;
    private final OCRService ocrService;
    private final DocumentAIService documentAIService;
    private final FraudDetectionService fraudDetectionService;
    private final EncryptionService encryptionService;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsCollector metricsCollector;

    @Value("${kyc.document.max-file-size:10485760}") // 10MB
    private long maxFileSize;
    
    @Value("${kyc.document.allowed-formats:image/jpeg,image/png,application/pdf}")
    private String allowedFormats;
    
    @Value("${kyc.document.min-quality-score:0.7}")
    private double minQualityScore;
    
    @Value("${kyc.document.auto-approve-score:0.95}")
    private double autoApproveScore;
    
    @Value("${kyc.document.verification-timeout-minutes:10}")
    private int verificationTimeoutMinutes;

    private static final String VERIFICATION_TOPIC = "kyc-document-verification";
    private static final String CIRCUIT_BREAKER_NAME = "document-verification";

    /**
     * Complete document verification workflow
     */
    @Transactional
    public CompletableFuture<DocumentVerificationResponse> verifyDocument(
            UUID userId, 
            String documentType, 
            MultipartFile file,
            Map<String, String> expectedData) {
            
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.info("Starting document verification for user: {} type: {}", userId, documentType);
            
            // Step 1: Validate document
            validateDocument(file, documentType);
            
            // Step 2: Create verification record
            DocumentVerification verification = createVerificationRecord(userId, documentType);
            
            // Step 3: Store document securely
            String documentKey = storeDocumentSecurely(userId, documentType, file);
            verification.setDocumentKey(documentKey);
            
            // Step 4: Process document asynchronously
            return processDocumentAsync(verification, file.getBytes(), expectedData)
                .whenComplete((result, error) -> {
                    sample.stop(Timer.builder("kyc.document.verification.duration")
                        .tag("document_type", documentType)
                        .tag("success", error == null ? "true" : "false")
                        .register(meterRegistry));
                        
                    if (error != null) {
                        log.error("Document verification failed for user: {}", userId, error);
                        recordVerificationFailure(verification, error);
                    }
                });
                
        } catch (Exception e) {
            log.error("Error in document verification for user: {}", userId, e);
            throw new DocumentVerificationException("Document verification failed", e);
        }
    }

    /**
     * Process document asynchronously with all verification steps
     */
    @Async
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "handleVerificationFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    protected CompletableFuture<DocumentVerificationResponse> processDocumentAsync(
            DocumentVerification verification,
            byte[] documentBytes,
            Map<String, String> expectedData) {
            
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing document verification: {}", verification.getId());
                
                // Step 1: Image Quality Check
                QualityCheckResult qualityResult = performQualityCheck(documentBytes);
                verification.setQualityScore(qualityResult.getScore());
                
                if (qualityResult.getScore() < minQualityScore) {
                    throw new DocumentVerificationException("Document quality too low: " + qualityResult.getIssues());
                }
                
                // Step 2: OCR Data Extraction
                OCRResult ocrResult = performOCR(documentBytes, verification.getDocumentType());
                verification.setExtractedData(encryptSensitiveData(ocrResult.getExtractedData()));
                
                // Step 3: AI Document Authenticity Check
                AuthenticityResult authenticityResult = checkDocumentAuthenticity(
                    documentBytes, 
                    verification.getDocumentType()
                );
                verification.setAuthenticityScore(authenticityResult.getScore());
                
                // Step 4: Data Validation
                ValidationResult validationResult = validateExtractedData(
                    ocrResult.getExtractedData(), 
                    expectedData
                );
                verification.setDataMatchScore(validationResult.getMatchScore());
                
                // Step 5: Fraud Detection
                FraudCheckResult fraudResult = performFraudCheck(
                    verification.getUserId(),
                    documentBytes,
                    ocrResult.getExtractedData()
                );
                verification.setFraudScore(fraudResult.getRiskScore());
                
                // Step 6: Calculate Final Score
                double finalScore = calculateFinalScore(
                    qualityResult,
                    authenticityResult,
                    validationResult,
                    fraudResult
                );
                verification.setFinalScore(finalScore);
                
                // Step 7: Make Decision
                VerificationDecision decision = makeVerificationDecision(finalScore, fraudResult);
                verification.setStatus(decision.getStatus());
                verification.setDecisionReason(decision.getReason());
                verification.setCompletedAt(LocalDateTime.now());
                
                // Step 8: Save results
                verification = verificationRepository.save(verification);
                
                // Step 9: Publish events
                publishVerificationResult(verification, decision);
                
                // Step 10: Audit trail
                auditVerification(verification, decision);
                
                return buildVerificationResponse(verification, decision);
                
            } catch (Exception e) {
                log.error("Error processing document verification: {}", verification.getId(), e);
                verification.setStatus(DocumentVerification.Status.FAILED);
                verification.setDecisionReason("Processing error: " + e.getMessage());
                verificationRepository.save(verification);
                throw new DocumentVerificationException("Document processing failed", e);
            }
        }).orTimeout(verificationTimeoutMinutes, TimeUnit.MINUTES);
    }

    /**
     * Perform quality check on document image
     */
    private QualityCheckResult performQualityCheck(byte[] documentBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(documentBytes));
            
            if (image == null) {
                return QualityCheckResult.builder()
                    .score(0.0)
                    .issues(List.of("Unable to read image"))
                    .build();
            }
            
            List<String> issues = new ArrayList<>();
            double score = 1.0;
            
            // Check resolution
            if (image.getWidth() < 800 || image.getHeight() < 600) {
                issues.add("Low resolution");
                score -= 0.3;
            }
            
            // Check image clarity (simplified - in production use computer vision)
            if (isBlurry(image)) {
                issues.add("Image is blurry");
                score -= 0.4;
            }
            
            // Check lighting
            if (isPoorlyLit(image)) {
                issues.add("Poor lighting");
                score -= 0.2;
            }
            
            return QualityCheckResult.builder()
                .score(Math.max(0, score))
                .issues(issues)
                .passed(score >= minQualityScore)
                .build();
                
        } catch (IOException e) {
            log.error("Error performing quality check", e);
            return QualityCheckResult.builder()
                .score(0.0)
                .issues(List.of("Quality check failed"))
                .build();
        }
    }

    /**
     * Perform OCR on document
     */
    private OCRResult performOCR(byte[] documentBytes, DocumentType documentType) {
        return ocrService.extractText(documentBytes, documentType);
    }

    /**
     * Check document authenticity using AI
     */
    private AuthenticityResult checkDocumentAuthenticity(byte[] documentBytes, DocumentType documentType) {
        return documentAIService.verifyAuthenticity(documentBytes, documentType);
    }

    /**
     * Validate extracted data against expected data
     */
    private ValidationResult validateExtractedData(Map<String, String> extractedData, 
                                                  Map<String, String> expectedData) {
        if (expectedData == null || expectedData.isEmpty()) {
            return ValidationResult.builder()
                .matchScore(1.0)
                .matchedFields(extractedData.keySet())
                .mismatchedFields(Set.of())
                .build();
        }
        
        Set<String> matchedFields = new HashSet<>();
        Set<String> mismatchedFields = new HashSet<>();
        
        for (Map.Entry<String, String> expected : expectedData.entrySet()) {
            String extractedValue = extractedData.get(expected.getKey());
            
            if (extractedValue != null && 
                normalizeValue(extractedValue).equals(normalizeValue(expected.getValue()))) {
                matchedFields.add(expected.getKey());
            } else {
                mismatchedFields.add(expected.getKey());
            }
        }
        
        double matchScore = expectedData.isEmpty() ? 1.0 : 
            (double) matchedFields.size() / expectedData.size();
            
        return ValidationResult.builder()
            .matchScore(matchScore)
            .matchedFields(matchedFields)
            .mismatchedFields(mismatchedFields)
            .build();
    }

    /**
     * Perform fraud check
     */
    private FraudCheckResult performFraudCheck(UUID userId, byte[] documentBytes, 
                                              Map<String, String> extractedData) {
        // Check for document tampering
        boolean isTampered = documentAIService.detectTampering(documentBytes);
        
        // Check against fraud database
        boolean isInFraudDatabase = fraudDetectionService.checkDocumentFraud(
            extractedData.get("documentNumber")
        );
        
        // Calculate risk score
        double riskScore = 0.0;
        List<String> riskFactors = new ArrayList<>();
        
        if (isTampered) {
            riskScore += 0.8;
            riskFactors.add("Document appears tampered");
        }
        
        if (isInFraudDatabase) {
            riskScore += 0.9;
            riskFactors.add("Document flagged in fraud database");
        }
        
        // Check velocity (multiple verifications in short time)
        int recentAttempts = verificationRepository.countByUserIdAndCreatedAtAfter(
            userId, LocalDateTime.now().minusHours(24)
        );
        
        if (recentAttempts > 3) {
            riskScore += 0.3;
            riskFactors.add("Multiple verification attempts");
        }
        
        return FraudCheckResult.builder()
            .riskScore(Math.min(1.0, riskScore))
            .riskFactors(riskFactors)
            .requiresManualReview(riskScore > 0.5)
            .build();
    }

    /**
     * Calculate final verification score
     */
    private double calculateFinalScore(QualityCheckResult quality,
                                      AuthenticityResult authenticity,
                                      ValidationResult validation,
                                      FraudCheckResult fraud) {
        // Weighted scoring
        double qualityWeight = 0.2;
        double authenticityWeight = 0.3;
        double validationWeight = 0.3;
        double fraudWeight = 0.2;
        
        double score = (quality.getScore() * qualityWeight) +
                      (authenticity.getScore() * authenticityWeight) +
                      (validation.getMatchScore() * validationWeight) +
                      ((1.0 - fraud.getRiskScore()) * fraudWeight);
                      
        return Math.max(0, Math.min(1.0, score));
    }

    /**
     * Make verification decision based on scores
     */
    private VerificationDecision makeVerificationDecision(double finalScore, FraudCheckResult fraudResult) {
        if (fraudResult.getRiskScore() > 0.8) {
            return VerificationDecision.builder()
                .status(DocumentVerification.Status.REJECTED)
                .reason("High fraud risk detected")
                .requiresManualReview(false)
                .build();
        }
        
        if (finalScore >= autoApproveScore && !fraudResult.isRequiresManualReview()) {
            return VerificationDecision.builder()
                .status(DocumentVerification.Status.VERIFIED)
                .reason("Automated approval - high confidence")
                .requiresManualReview(false)
                .build();
        }
        
        if (finalScore < 0.5) {
            return VerificationDecision.builder()
                .status(DocumentVerification.Status.REJECTED)
                .reason("Verification criteria not met")
                .requiresManualReview(finalScore > 0.3)
                .build();
        }
        
        return VerificationDecision.builder()
            .status(DocumentVerification.Status.PENDING_REVIEW)
            .reason("Manual review required")
            .requiresManualReview(true)
            .build();
    }

    /**
     * Validate document before processing
     */
    private void validateDocument(MultipartFile file, String documentType) {
        if (file.isEmpty()) {
            throw new DocumentVerificationException("Document file is empty");
        }
        
        if (file.getSize() > maxFileSize) {
            throw new DocumentVerificationException("Document file size exceeds limit");
        }
        
        String contentType = file.getContentType();
        if (!allowedFormats.contains(contentType)) {
            throw new DocumentVerificationException("Document format not supported: " + contentType);
        }
        
        // Validate document type
        try {
            DocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DocumentVerificationException("Invalid document type: " + documentType);
        }
    }

    /**
     * Create verification record
     */
    private DocumentVerification createVerificationRecord(UUID userId, String documentType) {
        DocumentVerification verification = DocumentVerification.builder()
            .userId(userId)
            .documentType(DocumentType.valueOf(documentType.toUpperCase()))
            .status(DocumentVerification.Status.PROCESSING)
            .createdAt(LocalDateTime.now())
            .metadata(new HashMap<>())
            .build();
            
        return verificationRepository.save(verification);
    }

    /**
     * Store document securely
     */
    private String storeDocumentSecurely(UUID userId, String documentType, MultipartFile file) {
        // Store in S3 with encryption
        String documentKey = storageService.storeDocument(
            userId.toString(), 
            documentType, 
            file
        );
        
        // Record document metadata
        VerificationDocument document = VerificationDocument.builder()
            .userId(userId)
            .documentType(DocumentType.valueOf(documentType.toUpperCase()))
            .documentKey(documentKey)
            .fileName(file.getOriginalFilename())
            .fileSize(file.getSize())
            .contentType(file.getContentType())
            .uploadedAt(LocalDateTime.now())
            .build();
            
        documentRepository.save(document);
        
        return documentKey;
    }

    /**
     * Encrypt sensitive data
     */
    private Map<String, String> encryptSensitiveData(Map<String, String> data) {
        Map<String, String> encrypted = new HashMap<>();
        Set<String> sensitiveFields = Set.of("ssn", "documentNumber", "dateOfBirth");
        
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (sensitiveFields.contains(entry.getKey().toLowerCase())) {
                encrypted.put(entry.getKey(), encryptionService.encrypt(entry.getValue()));
            } else {
                encrypted.put(entry.getKey(), entry.getValue());
            }
        }
        
        return encrypted;
    }

    /**
     * Normalize value for comparison
     */
    private String normalizeValue(String value) {
        if (value == null) return "";
        return value.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * Check if image is blurry (simplified implementation)
     */
    private boolean isBlurry(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        double totalVariance = 0.0;
        int sampleCount = 0;
        int step = 10;
        
        for (int y = 1; y < height - 1; y += step) {
            for (int x = 1; x < width - 1; x += step) {
                int centerPixel = getGrayValue(image.getRGB(x, y));
                int rightPixel = getGrayValue(image.getRGB(x + 1, y));
                int bottomPixel = getGrayValue(image.getRGB(x, y + 1));
                
                int dx = Math.abs(rightPixel - centerPixel);
                int dy = Math.abs(bottomPixel - centerPixel);
                
                totalVariance += (dx * dx + dy * dy);
                sampleCount++;
            }
        }
        
        double variance = sampleCount > 0 ? totalVariance / sampleCount : 0;
        double blurThreshold = 100.0;
        
        return variance < blurThreshold;
    }
    
    private int getGrayValue(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r + g + b) / 3;
    }

    /**
     * Check if image is poorly lit using brightness analysis
     */
    private boolean isPoorlyLit(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        long totalBrightness = 0;
        int pixelCount = 0;
        int step = 10;
        
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int brightness = getGrayValue(rgb);
                totalBrightness += brightness;
                pixelCount++;
            }
        }
        
        double averageBrightness = pixelCount > 0 ? (double) totalBrightness / pixelCount : 0;
        
        double minBrightness = 40.0;
        double maxBrightness = 240.0;
        
        return averageBrightness < minBrightness || averageBrightness > maxBrightness;
    }

    /**
     * Publish verification result
     */
    private void publishVerificationResult(DocumentVerification verification, VerificationDecision decision) {
        DocumentVerificationEvent event = DocumentVerificationEvent.builder()
            .verificationId(verification.getId())
            .userId(verification.getUserId())
            .documentType(verification.getDocumentType())
            .status(verification.getStatus())
            .finalScore(verification.getFinalScore())
            .decision(decision)
            .timestamp(LocalDateTime.now())
            .build();
            
        eventPublisher.publish(event);
        kafkaTemplate.send(VERIFICATION_TOPIC, event);
        
        Counter.builder("kyc.document.verification")
            .tag("status", verification.getStatus().name())
            .tag("document_type", verification.getDocumentType().name())
            .register(meterRegistry)
            .increment();
    }

    /**
     * Audit verification
     */
    private void auditVerification(DocumentVerification verification, VerificationDecision decision) {
        auditService.auditAction(
            "DOCUMENT_VERIFICATION",
            verification.getUserId().toString(),
            Map.of(
                "verificationId", verification.getId(),
                "documentType", verification.getDocumentType(),
                "status", verification.getStatus(),
                "finalScore", verification.getFinalScore(),
                "decision", decision
            )
        );
    }

    /**
     * Record verification failure
     */
    private void recordVerificationFailure(DocumentVerification verification, Throwable error) {
        verification.setStatus(DocumentVerification.Status.FAILED);
        verification.setDecisionReason("Processing error: " + error.getMessage());
        verification.setCompletedAt(LocalDateTime.now());
        verificationRepository.save(verification);
        
        Counter.builder("kyc.document.verification.error")
            .tag("document_type", verification.getDocumentType().name())
            .tag("error", error.getClass().getSimpleName())
            .register(meterRegistry)
            .increment();
    }

    /**
     * Build verification response
     */
    private DocumentVerificationResponse buildVerificationResponse(
            DocumentVerification verification, 
            VerificationDecision decision) {
        return DocumentVerificationResponse.builder()
            .verificationId(verification.getId())
            .userId(verification.getUserId())
            .documentType(verification.getDocumentType())
            .status(verification.getStatus())
            .finalScore(verification.getFinalScore())
            .qualityScore(verification.getQualityScore())
            .authenticityScore(verification.getAuthenticityScore())
            .dataMatchScore(verification.getDataMatchScore())
            .fraudScore(verification.getFraudScore())
            .decision(decision)
            .completedAt(verification.getCompletedAt())
            .build();
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompletableFuture<DocumentVerificationResponse> handleVerificationFallback(
            DocumentVerification verification,
            byte[] documentBytes,
            Map<String, String> expectedData,
            Exception ex) {
        
        log.error("Document verification circuit breaker triggered for verification: {}", 
            verification.getId(), ex);
            
        verification.setStatus(DocumentVerification.Status.PENDING_REVIEW);
        verification.setDecisionReason("Automated verification unavailable - manual review required");
        verificationRepository.save(verification);
        
        return CompletableFuture.completedFuture(
            DocumentVerificationResponse.builder()
                .verificationId(verification.getId())
                .status(DocumentVerification.Status.PENDING_REVIEW)
                .decision(VerificationDecision.builder()
                    .status(DocumentVerification.Status.PENDING_REVIEW)
                    .reason("System temporarily unavailable - queued for manual review")
                    .requiresManualReview(true)
                    .build())
                .build()
        );
    }

    // DTOs and domain models
    @lombok.Data
    @lombok.Builder
    public static class QualityCheckResult {
        private double score;
        private List<String> issues;
        private boolean passed;
    }

    @lombok.Data
    @lombok.Builder
    public static class OCRResult {
        private Map<String, String> extractedData;
        private double confidence;
        private List<String> warnings;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuthenticityResult {
        private double score;
        private List<String> securityFeatures;
        private List<String> anomalies;
    }

    @lombok.Data
    @lombok.Builder
    public static class ValidationResult {
        private double matchScore;
        private Set<String> matchedFields;
        private Set<String> mismatchedFields;
    }

    @lombok.Data
    @lombok.Builder
    public static class FraudCheckResult {
        private double riskScore;
        private List<String> riskFactors;
        private boolean requiresManualReview;
    }

    @lombok.Data
    @lombok.Builder
    public static class VerificationDecision {
        private DocumentVerification.Status status;
        private String reason;
        private boolean requiresManualReview;
    }

    @lombok.Data
    @lombok.Builder
    public static class DocumentVerificationResponse {
        private UUID verificationId;
        private UUID userId;
        private DocumentType documentType;
        private DocumentVerification.Status status;
        private Double finalScore;
        private Double qualityScore;
        private Double authenticityScore;
        private Double dataMatchScore;
        private Double fraudScore;
        private VerificationDecision decision;
        private LocalDateTime completedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class DocumentVerificationEvent {
        private UUID verificationId;
        private UUID userId;
        private DocumentType documentType;
        private DocumentVerification.Status status;
        private Double finalScore;
        private VerificationDecision decision;
        private LocalDateTime timestamp;
    }

    public enum DocumentType {
        PASSPORT,
        DRIVERS_LICENSE,
        NATIONAL_ID,
        PROOF_OF_ADDRESS,
        BANK_STATEMENT,
        UTILITY_BILL,
        TAX_DOCUMENT,
        EMPLOYMENT_LETTER
    }
}