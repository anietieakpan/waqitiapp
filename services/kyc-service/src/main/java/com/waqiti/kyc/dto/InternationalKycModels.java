package com.waqiti.kyc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprehensive model classes for International KYC Workflows
 * 
 * @author Waqiti KYC Team
 * @version 3.0.0
 */
public class InternationalKycModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternationalKycRequest {
        private String jurisdiction;
        private String residencyCountry;
        private String nationality;
        private String customerName;
        private LocalDate dateOfBirth;
        private BigDecimal expectedTransactionVolume;
        private String purposeOfAccount;
        private List<String> sourceOfFunds;
        private boolean isPoliticallyExposed;
        private String businessActivity;
        private List<Address> addresses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternationalKycResult {
        private boolean success;
        private String sessionId;
        private KycStatus status;
        private String reason;
        private VerificationRequirements verificationRequirements;
        private List<WorkflowStep> nextSteps;
        private LocalDateTime estimatedCompletionTime;
        private double riskScore;
        private List<String> complianceChecks;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternationalKycSession {
        private String sessionId;
        private String userId;
        private String jurisdiction;
        private String residencyCountry;
        private String nationality;
        private BigDecimal expectedTransactionVolume;
        private KycStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime expiresAt;
        private List<DocumentVerificationResult> documentResults;
        private BiometricVerificationResult biometricResult;
        private AddressVerificationResult addressResult;
        private EddResult eddResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationRequirements {
        private List<DocumentType> requiredDocuments;
        private boolean biometricRequired;
        private boolean livenessCheckRequired;
        private boolean addressVerificationRequired;
        private boolean eddRequired;
        private boolean pepScreeningRequired;
        private boolean sanctionsScreeningRequired;
        private boolean sourceOfWealthRequired;
        private BigDecimal sourceOfWealthThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentSubmissionResult {
        private boolean success;
        private String sessionId;
        private List<DocumentVerificationResult> verificationResults;
        private KycStatus status;
        private boolean isComplete;
        private LocalDateTime completedAt;
        private List<String> errors;
        private List<DocumentType> missingDocuments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentVerificationResult {
        private String documentId;
        private DocumentType documentType;
        private boolean verified;
        private double confidenceScore;
        private List<String> verificationChecks;
        private List<String> issues;
        private LocalDateTime verifiedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EddRequest {
        private String customerName;
        private LocalDate dateOfBirth;
        private String nationality;
        private List<Address> addresses;
        private List<SourceOfWealth> sourceOfWealth;
        private List<String> associates;
        private String businessActivity;
        private BigDecimal netWorth;
        private BigDecimal expectedTransactionVolume;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EddResult {
        private boolean success;
        private String userId;
        private boolean approved;
        private double riskScore;
        private RiskLevel riskLevel;
        private boolean sourceOfWealthVerified;
        private PepStatus pepStatus;
        private boolean sanctionsHit;
        private boolean adverseMediaFound;
        private String reportId;
        private List<String> recommendations;
        private boolean reviewRequired;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternationalTransactionRequest {
        private BigDecimal amount;
        private String currency;
        private String destinationCountry;
        private String beneficiaryName;
        private String beneficiaryAccount;
        private String purposeOfTransfer;
        private String sourceOfFunds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionAuthorizationResult {
        private boolean authorized;
        private String authorizationId;
        private String reason;
        private LocalDateTime validUntil;
        private double riskScore;
        private List<String> complianceChecks;
        private List<String> requiredActions;
        
        public static TransactionAuthorizationResult denied(String reason) {
            return TransactionAuthorizationResult.builder()
                    .authorized(false)
                    .reason(reason)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceReportRequest {
        private LocalDate startDate;
        private LocalDate endDate;
        private List<ComplianceReportType> reportTypes;
        private String jurisdiction;
        private List<String> currencies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceReportResult {
        private boolean success;
        private List<ComplianceReport> reports;
        private LocalDateTime generatedAt;
        private int totalReports;
        private String errorMessage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceReport {
        private String reportId;
        private ComplianceReportType reportType;
        private String jurisdiction;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private int recordCount;
        private String filePath;
        private LocalDateTime generatedAt;
    }

    // Supporting classes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String streetAddress;
        private String city;
        private String state;
        private String postalCode;
        private String country;
        private AddressType type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceOfWealth {
        private SourceType sourceType;
        private String description;
        private BigDecimal amount;
        private String documentationProvided;
        private boolean verified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowStep {
        private StepType stepType;
        private String description;
        private List<DocumentType> requiredDocuments;
        private boolean completed;
        private LocalDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreeningResult {
        private boolean blocked;
        private double riskScore;
        private String reason;
        private SanctionsResult sanctionsResult;
        private PepResult pepResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowResult {
        private KycStatus status;
        private List<WorkflowStep> nextSteps;
        private LocalDateTime estimatedCompletion;
        private List<String> complianceChecks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<DocumentType> missingDocuments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CountryRiskProfile {
        private String countryCode;
        private RiskLevel riskLevel;
        private boolean fatcaApplicable;
        private boolean crsApplicable;
        private SanctionsRisk sanctionsRisk;
        private boolean eddRequired;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JurisdictionRequirements {
        private String jurisdiction;
        private List<DocumentType> requiredDocuments;
        private boolean biometricRequired;
        private boolean livenessCheckRequired;
        private boolean addressVerificationRequired;
        private BigDecimal sourceOfWealthThreshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionsResult {
        private boolean hit;
        private double score;
        private String matchDetails;
        private List<String> matchedLists;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PepResult {
        private boolean hit;
        private double score;
        private String matchDetails;
        private PepCategory category;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PepScreeningResult {
        private PepStatus status;
        private String matchDetails;
        private PepCategory category;
        private double riskScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnhancedSanctionsResult {
        private boolean hit;
        private double riskScore;
        private List<String> matchedEntities;
        private List<String> sanctions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdverseMediaResult {
        private boolean hasAdverseNews;
        private List<String> newsItems;
        private double severity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceOfWealthResult {
        private boolean verified;
        private double confidenceScore;
        private List<String> verificationMethods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EddRiskAssessment {
        private double riskScore;
        private RiskLevel riskLevel;
        private boolean reviewRequired;
        private List<String> recommendations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EddReport {
        private String reportId;
        private String userId;
        private LocalDateTime generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternationalKycStatus {
        private boolean verified;
        private String jurisdiction;
        private LocalDateTime verifiedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionLimitsResult {
        private boolean withinLimits;
        private String exceededLimit;
        private BigDecimal availableLimit;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RealTimeScreeningResult {
        private boolean blocked;
        private double riskScore;
        private String reason;
        private List<String> complianceChecks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricVerificationResult {
        private boolean verified;
        private double confidenceScore;
        private String verificationMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressVerificationResult {
        private boolean verified;
        private String verificationMethod;
        private double confidenceScore;
    }

    // Enums

    public enum KycStatus {
        INITIATED, IN_PROGRESS, DOCUMENTS_SUBMITTED, PENDING_REVIEW, 
        VERIFIED, REJECTED, EXPIRED, ERROR, PENDING_DOCUMENTS
    }

    public enum DocumentType {
        GOVERNMENT_ID, PASSPORT, DRIVERS_LICENSE, PROOF_OF_ADDRESS,
        UTILITY_BILL, BANK_STATEMENT, SSN_VERIFICATION, BIRTH_CERTIFICATE,
        TAX_DOCUMENT, EMPLOYMENT_VERIFICATION, SOURCE_OF_WEALTH_DOCUMENT
    }

    public enum StepType {
        DOCUMENT_COLLECTION, BIOMETRIC_VERIFICATION, ADDRESS_VERIFICATION,
        ENHANCED_DUE_DILIGENCE, PEP_SCREENING, SANCTIONS_SCREENING,
        SOURCE_OF_WEALTH_VERIFICATION, MANUAL_REVIEW
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum SanctionsRisk {
        LOW, MEDIUM, HIGH
    }

    public enum PepStatus {
        NOT_PEP, PEP_DIRECT, PEP_ASSOCIATE, PEP_FAMILY, UNKNOWN
    }

    public enum PepCategory {
        HEAD_OF_STATE, GOVERNMENT_OFFICIAL, SENIOR_EXECUTIVE,
        JUDICIAL_OFFICIAL, MILITARY_OFFICIAL, PARTY_OFFICIAL,
        STATE_OWNED_ENTERPRISE, INTERNATIONAL_ORGANIZATION
    }

    public enum ComplianceReportType {
        FATCA, CRS, SAR, CTR, FBAR, BSA
    }

    public enum AddressType {
        RESIDENTIAL, BUSINESS, MAILING, TEMPORARY
    }

    public enum SourceType {
        EMPLOYMENT, BUSINESS, INVESTMENT, INHERITANCE, GIFT,
        SALE_OF_PROPERTY, LOAN, OTHER
    }
}