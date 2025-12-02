package com.waqiti.payment.dto;

import com.waqiti.payment.entity.CheckValidationStatus;
import com.waqiti.payment.entity.FraudRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO for check validation results
 * Contains the results of OCR, MICR parsing, and fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckValidationResult {
    
    // Overall validation status
    private boolean isValid;
    private boolean requiresManualReview;
    private CheckValidationStatus validationStatus;
    
    // MICR data extraction results
    private MICRValidationResult micrResult;
    
    // Amount extraction results
    private AmountValidationResult amountResult;
    
    // Image quality assessment
    private ImageQualityResult imageQuality;
    
    // Fraud detection results
    private FraudDetectionResult fraudDetection;
    
    // Check details extraction
    private CheckDetailsResult checkDetails;
    
    // Overall confidence scores
    private BigDecimal overallConfidence; // 0-1 confidence score
    private BigDecimal riskScore; // 0-1 risk score
    
    // Validation errors and warnings
    private List<ValidationError> errors;
    private List<ValidationWarning> warnings;
    
    // Additional metadata
    private Map<String, Object> metadata;
    
    /**
     * MICR validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MICRValidationResult {
        private boolean isValid;
        private String routingNumber;
        private String accountNumber;
        private String checkNumber;
        private String rawMicrData;
        private BigDecimal confidence;
        private List<String> issues;
    }
    
    /**
     * Amount validation result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountValidationResult {
        private boolean isValid;
        private BigDecimal extractedAmount;
        private BigDecimal userProvidedAmount;
        private boolean amountsMatch;
        private BigDecimal confidence;
        private String extractionMethod; // OCR, MANUAL, etc.
        private List<String> issues;
    }
    
    /**
     * Image quality assessment result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageQualityResult {
        private boolean frontImageQualityAcceptable;
        private boolean backImageQualityAcceptable;
        private BigDecimal frontImageScore; // 0-1 quality score
        private BigDecimal backImageScore; // 0-1 quality score
        private List<String> frontImageIssues;
        private List<String> backImageIssues;
        private boolean isDuplicate;
        private String duplicateCheckId; // If duplicate found
    }
    
    /**
     * Fraud detection result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FraudDetectionResult {
        private boolean isFraudulent;
        private BigDecimal fraudScore; // 0-1 fraud score
        private List<String> fraudIndicators;
        private FraudRiskLevel riskLevel;
        private boolean requiresManualReview;
        private Map<String, Object> detectionMetadata;
    }
    
    /**
     * Check details extraction result
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckDetailsResult {
        private String payeeName;
        private String payorName;
        private LocalDate checkDate;
        private String memo;
        private String bankName;
        private String bankAddress;
        private BigDecimal extractionConfidence;
        private List<String> issues;
    }
    
    /**
     * Validation error
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String code;
        private String message;
        private String field;
        private String severity; // ERROR, WARNING, INFO
    }
    
    /**
     * Validation warning
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String code;
        private String message;
        private String field;
        private String recommendation;
    }
}