package com.waqiti.user.dto.security.result;

import com.waqiti.user.domain.BiometricType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive Biometric Registration Result DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of biometric registration attempt")
public class BiometricRegistrationResult {
    
    @Schema(description = "Registration success status", required = true)
    private boolean success;
    
    @Schema(description = "Unique credential identifier")
    private String credentialId;
    
    @Schema(description = "Public credential identifier for client reference")
    private String publicCredentialId;
    
    @Schema(description = "Type of biometric registered")
    private BiometricType biometricType;
    
    @Schema(description = "Registration timestamp")
    private LocalDateTime registeredAt;
    
    @Schema(description = "Biometric quality score (0.0-1.0)")
    private Double qualityScore;
    
    @Schema(description = "Quality assessment details")
    private QualityAssessment qualityAssessment;
    
    @Schema(description = "Error code if registration failed")
    private String errorCode;
    
    @Schema(description = "Human-readable error message")
    private String errorMessage;
    
    @Schema(description = "Detailed error information")
    private List<String> errorDetails;
    
    @Schema(description = "Quality issues identified")
    private List<String> qualityIssues;
    
    @Schema(description = "Security warnings")
    private List<String> securityWarnings;
    
    @Schema(description = "Registration recommendations")
    private List<String> recommendations;
    
    @Schema(description = "Processing time in milliseconds")
    private Long processingTimeMs;
    
    @Schema(description = "Template extraction metrics")
    private TemplateMetrics templateMetrics;
    
    @Schema(description = "Device trust assessment")
    private DeviceTrustAssessment deviceTrust;
    
    @Schema(description = "Additional metadata")
    private Map<String, Object> metadata;
    
    /**
     * Quality Assessment Details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QualityAssessment {
        
        @Schema(description = "Overall quality score")
        private Double overallScore;
        
        @Schema(description = "Image quality score")
        private Double imageQuality;
        
        @Schema(description = "Feature quality score")
        private Double featureQuality;
        
        @Schema(description = "Uniqueness score")
        private Double uniquenessScore;
        
        @Schema(description = "Clarity score")
        private Double clarityScore;
        
        @Schema(description = "Completeness score")
        private Double completenessScore;
        
        @Schema(description = "Quality threshold met")
        private Boolean thresholdMet;
        
        @Schema(description = "Quality assessment details")
        private Map<String, Double> detailedScores;
    }
    
    /**
     * Template Extraction Metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateMetrics {
        
        @Schema(description = "Template size in bytes")
        private Integer templateSize;
        
        @Schema(description = "Feature count extracted")
        private Integer featureCount;
        
        @Schema(description = "Template version")
        private String templateVersion;
        
        @Schema(description = "Algorithm used")
        private String algorithm;
        
        @Schema(description = "Extraction time in milliseconds")
        private Long extractionTimeMs;
        
        @Schema(description = "Template compression ratio")
        private Double compressionRatio;
        
        @Schema(description = "Extraction success rate")
        private Double successRate;
    }
    
    /**
     * Device Trust Assessment
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceTrustAssessment {
        
        @Schema(description = "Overall trust score (0.0-1.0)")
        private Double trustScore;
        
        @Schema(description = "Device integrity verified")
        private Boolean integrityVerified;
        
        @Schema(description = "Device is known/trusted")
        private Boolean isKnownDevice;
        
        @Schema(description = "Security posture assessment")
        private String securityPosture;
        
        @Schema(description = "Risk factors identified")
        private List<String> riskFactors;
        
        @Schema(description = "Trust level classification")
        private String trustLevel;
        
        @Schema(description = "Device compliance status")
        private Boolean compliant;
    }
    
    // Static factory methods for common results
    
    public static BiometricRegistrationResult success(String credentialId, String publicCredentialId, String message) {
        return BiometricRegistrationResult.builder()
            .success(true)
            .credentialId(credentialId)
            .publicCredentialId(publicCredentialId)
            .registeredAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }
    
    public static BiometricRegistrationResult failure(String errorCode, String errorMessage) {
        return BiometricRegistrationResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }
    
    public static BiometricRegistrationResult qualityFailure(String errorCode, String errorMessage, 
                                                           Double qualityScore, List<String> qualityIssues) {
        return BiometricRegistrationResult.builder()
            .success(false)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .qualityScore(qualityScore)
            .qualityIssues(qualityIssues)
            .processingTimeMs(System.currentTimeMillis())
            .build();
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
}