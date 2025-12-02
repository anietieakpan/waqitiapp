package com.waqiti.common.kyc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of document verification process
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVerificationResult {
    
    private String verificationId;
    private String kycApplicationId;
    private String userId;
    private String customerId;
    private DocumentType documentType;
    private VerificationStatus verificationStatus;
    private VerificationStatus status;
    private double confidenceScore;
    private boolean approved;
    private boolean documentValid;
    private boolean faceMatch;
    private boolean watchlistClear;
    private boolean identityVerified;
    private List<String> verificationDetails;
    private boolean requiresManualReview;
    private LocalDateTime verifiedAt;
    private String failureReason;
    
    // Document analysis results
    private DocumentAnalysis documentAnalysis;
    private LivenessAnalysis livenessAnalysis;
    private FaceMatchAnalysis faceMatchAnalysis;
    
    // Provider-specific results
    private OnfidoResult onfidoResult;
    private JumioResult jumioResult;
    
    // Extracted data
    private ExtractedDocumentData extractedDocumentData;
    private Map<String, String> extractedData;
    
    // Risk assessment
    private RiskAssessment riskAssessment;
    
    // Verification metadata
    private String provider;
    private String providerCheckId;
    private LocalDateTime completedAt;
    private List<String> warnings;
    private List<String> errors;
    private Map<String, Object> rawProviderResponse;
    
    /**
     * Document analysis details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentAnalysis {
        private boolean isAuthentic;
        private boolean isExpired;
        private boolean isConsistent;
        private List<String> securityFeatures;
        private List<String> tamperingIndicators;
        private double qualityScore;
        private String extractedText;
    }
    
    /**
     * Liveness check analysis
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivenessAnalysis {
        private boolean isLive;
        private double livenessScore;
        private List<String> spoofingIndicators;
        private String imageQuality;
    }
    
    /**
     * Face matching analysis
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaceMatchAnalysis {
        private boolean isMatch;
        private double matchScore;
        private double threshold;
        private String faceComparisonResult;
    }
    
    /**
     * Onfido-specific result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnfidoResult {
        private String checkId;
        private String reportId;
        private String result;
        private String subResult;
        private Map<String, Object> breakdown;
        private List<String> properties;
    }
    
    /**
     * Jumio-specific result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JumioResult {
        private String scanReference;
        private String verificationStatus;
        private String identityVerification;
        private String similarityDecision;
        private double similarity;
    }
    
    /**
     * Extracted document data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedDocumentData {
        private String firstName;
        private String lastName;
        private String fullName;
        private String dateOfBirth;
        private String nationality;
        private String documentNumber;
        private String issuingCountry;
        private String address;
        private String gender;
        private LocalDateTime expiryDate;
        private LocalDateTime issueDate;
    }
    
    /**
     * Risk assessment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskAssessment {
        private RiskLevel riskLevel;
        private double riskScore;
        private List<String> riskFactors;
        private boolean requiresManualReview;
        private String recommendation;
    }
    
    /**
     * Risk levels
     */
    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Check if verification is approved
     */
    public boolean isApproved() {
        return approved;
    }
    
    /**
     * Check if requires manual review
     */
    public boolean isRequiresManualReview() {
        return requiresManualReview;
    }
    
    /**
     * Check if verification passed
     */
    public boolean isPassed() {
        return status == VerificationStatus.VERIFIED || status == VerificationStatus.APPROVED;
    }
    
    /**
     * Check if verification failed
     */
    public boolean isFailed() {
        return status == VerificationStatus.REJECTED || status == VerificationStatus.FAILED;
    }
    
    /**
     * Check if requires manual review
     */
    public boolean requiresManualReview() {
        return status == VerificationStatus.PENDING_REVIEW ||
               (riskAssessment != null && riskAssessment.requiresManualReview);
    }
}