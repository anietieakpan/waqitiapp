package com.waqiti.common.validation.model;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enterprise Validation Model Classes
 * 
 * Comprehensive model classes for validation services following enterprise patterns:
 * - Immutable data structures
 * - Rich metadata for audit trails
 * - Standardized error handling
 * - Compliance tracking
 * - Performance metrics
 * 
 * @author Waqiti Security Team
 * @version 2.0.0
 * @since 2024-01-18
 */
public class ValidationModels {
    
    @Data
    @Builder
    @Jacksonized
    public static class GeoLocationResult {
        @NotBlank
        private final String ipAddress;
        
        private final String countryCode;
        private final String countryName;
        private final String region;
        private final String city;
        private final Double latitude;
        private final Double longitude;
        private final String timezone;
        private final String isp;
        private final String organization;
        private final String asNumber;
        
        @Min(0) @Max(100)
        private final Integer accuracyRadius;
        
        private final boolean isDatacenter;
        private final boolean isProxy;
        private final boolean isAnonymous;
        
        @NotNull
        private final LocalDateTime timestamp;
        
        @NotBlank
        private final String source;
        
        @Min(0) @Max(100)
        private final Double confidence;
        
        private final Map<String, Object> metadata;
        private final List<String> warnings;
        private final ValidationError error;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class VPNDetectionResult {
        @NotBlank
        private final String ipAddress;
        
        private final boolean isVpn;
        private final boolean isProxy;
        private final boolean isTor;
        private final boolean isRelay;
        private final boolean isHosting;
        private final boolean isDatacenter;
        
        private final String providerName;
        private final String networkType;
        
        @Min(0) @Max(100)
        private final Integer riskScore;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private final Double confidence;
        
        @NotNull
        private final LocalDateTime timestamp;
        
        @NotBlank
        private final String detectionMethod;
        
        private final List<String> detectionSources;
        private final Map<String, Object> providerDetails;
        private final ValidationError error;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class ThreatAssessmentResult {
        @NotBlank
        private final String ipAddress;
        
        @Min(0) @Max(100)
        private final Integer threatLevel;
        
        private final Set<ThreatCategory> categories;
        
        @Min(0) @Max(100)
        private final Integer abuseScore;
        
        @Min(0)
        private final Integer totalReports;
        
        private final LocalDateTime lastReportedAt;
        private final boolean isWhitelisted;
        private final boolean isBlacklisted;
        private final String reputation;
        
        @NotNull
        private final LocalDateTime assessmentTime;
        
        private final List<ThreatIndicator> indicators;
        private final Map<String, Object> intelligenceSources;
        private final ValidationError error;
        
        public enum ThreatCategory {
            FRAUD, MALWARE, PHISHING, SPAM, BOTNET, SCANNER, 
            BRUTE_FORCE, DDoS, SUSPICIOUS, COMPROMISED
        }
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class EmailValidationResult {
        @NotBlank @Email
        private final String emailAddress;
        
        @NotBlank @Email
        private final String email;
        
        private final boolean isValid;
        private final boolean isDeliverable;
        private final boolean isDisposable;
        private final boolean isRisky;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private final Double validityConfidence;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private final Double riskScore;
        
        private final String domain;
        private final boolean isDomainValid;
        private final boolean hasMxRecord;
        private final boolean hasSpfRecord;
        private final boolean hasDmarcRecord;
        
        private final DomainReputationResult domainReputation;
        private final EmailSyntaxResult syntaxValidation;
        private final DisposableCheckResult disposableCheck;
        
        @NotNull
        private final LocalDateTime validatedAt;
        
        private final List<ValidationWarning> warnings;
        private final List<ValidationError> errors;
        private final ValidationError error;

        private final Map<String, Object> metadata;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class DomainReputationResult {
        @NotBlank
        private final String domain;
        
        @Min(0) @Max(100)
        private final Integer reputationScore;
        
        private final String reputationLevel; // EXCELLENT, GOOD, NEUTRAL, POOR, DANGEROUS
        
        private final boolean isTrusted;
        private final boolean isSuspicious;
        private final boolean isMalicious;
        
        private final DomainAge domainAge;
        private final List<SecurityIndicator> securityIndicators;
        private final List<String> associatedThreats;
        
        @NotNull
        private final LocalDateTime assessedAt;
        
        private final Map<String, Object> reputationSources;
        private final ValidationError error;
    }
    
    // Supporting classes
    @Data
    @Builder
    @Jacksonized
    public static class ThreatIndicator {
        private final String type;
        private final String value;
        private final Integer severity;
        private final String source;
        private final LocalDateTime detectedAt;
        private final Map<String, Object> context;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class EmailSyntaxResult {
        private final boolean isValidSyntax;
        private final boolean isValidFormat;
        private final boolean hasValidLocalPart;
        private final boolean hasValidDomainPart;
        private final List<String> syntaxErrors;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class DisposableCheckResult {
        private final boolean isDisposable;
        private final String providerName;
        private final String providerType;
        private final Double confidence;
        private final List<String> detectionMethods;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class DomainAge {
        private final LocalDateTime registrationDate;
        private final LocalDateTime creationDate;
        private final LocalDateTime expirationDate;
        private final Integer ageInDays;
        private final boolean isNewDomain; // < 30 days
        private final boolean isRecentlyRegistered; // < 90 days
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class SecurityIndicator {
        private final String type; // SSL_CERT, DNSSEC, SPF, DMARC, etc.
        private final boolean isPresent;
        private final boolean isValid;
        private final String status;
        private final Map<String, Object> details;
    }

    @Data
    @Builder
    @Jacksonized
    public static class ValidationWarning {
        private final String field;
        private final String code;
        private final String message;
        private final String severity; // LOW, MEDIUM, HIGH
        private final String category;
        private final Map<String, Object> context;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class ValidationError {
        @NotBlank
        private final String code;
        
        @NotBlank
        private final String message;
        
        private final String field;
        private final String details;
        private final String category;
        private final boolean isRetryable;
        private final LocalDateTime occurredAt;
        private final Map<String, Object> context;
        
        // Standard error codes
        public static final String INVALID_INPUT = "INVALID_INPUT";
        public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        public static final String RATE_LIMITED = "RATE_LIMITED";
        public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
        public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }
    
    // Additional models for facade pattern
    @Data
    @Builder
    @Jacksonized
    public static class IPReputationSummary {
        @NotBlank
        private final String ipAddress;
        
        @NotBlank
        private final String assessmentId;
        
        private final long assessmentTimestamp;
        
        private final GeoLocationResult geoLocation;
        private final VPNDetectionResult vpnDetection;
        private final ThreatAssessmentResult threatAssessment;
        
        @DecimalMin("0.0") @DecimalMax("100.0")
        private final Double overallScore;
        
        private final String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
        private final List<String> riskFactors;
        private final Map<String, Object> metadata;
        private final ValidationError error;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class JurisdictionRiskResult {
        @NotBlank
        private final String countryCode;
        
        private final String jurisdiction;
        
        @DecimalMin("0.0") @DecimalMax("100.0")
        private final Double riskScore;
        
        private final List<String> riskFactors;
        private final boolean isSanctioned;
        private final boolean isHighRisk;
        private final Map<String, Object> complianceData;
        private final ValidationError error;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class RiskProfile {
        private final Set<String> highRiskCountries;
        private final Set<String> sanctionedRegions;
        private final Map<String, Double> riskWeights;
        private final String organizationPolicy;
        private final Map<String, Object> customSettings;
    }
    
    @Data
    @Builder
    @Jacksonized
    public static class ReputationScore {
        @DecimalMin("0.0") @DecimalMax("100.0")
        private final Double score;
        
        @NotBlank
        private final String riskLevel;
        
        private final Map<String, Double> factorWeights;
        private final List<String> riskIndicators;
        private final long calculatedAt;
        private final Map<String, Object> scoreDetails;
        private final ValidationError error;
    }
}