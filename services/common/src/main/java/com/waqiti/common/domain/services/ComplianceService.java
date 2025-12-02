package com.waqiti.common.domain.services;

import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.UserId;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Compliance Domain Service
 * Encapsulates regulatory compliance business rules and validations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceService {
    
    // AML thresholds
    private static final Money AML_THRESHOLD_SINGLE = Money.ngn(1_000_000.0);
    private static final Money AML_THRESHOLD_DAILY = Money.ngn(5_000_000.0);
    private static final Money AML_THRESHOLD_MONTHLY = Money.ngn(50_000_000.0);
    
    // CTR (Currency Transaction Report) thresholds
    private static final Money CTR_THRESHOLD = Money.ngn(10_000_000.0);
    
    // Cross-border reporting thresholds
    private static final Money CROSS_BORDER_THRESHOLD = Money.usd(10_000.0);
    
    // High-risk countries (example list)
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of("AF", "IR", "KP", "SY");
    
    /**
     * Perform comprehensive compliance check
     */
    public ComplianceCheckResult performComplianceCheck(ComplianceCheckRequest request) {
        log.debug("Performing compliance check for user: {}, amount: {}", 
                request.getUserId(), request.getAmount());
        
        List<ComplianceRequirement> requirements = new ArrayList<>();
        List<String> alerts = new ArrayList<>();
        
        // AML checks
        performAmlChecks(request, requirements, alerts);
        
        // KYC requirements
        performKycChecks(request, requirements, alerts);
        
        // Sanctions screening
        performSanctionsScreening(request, requirements, alerts);
        
        // Cross-border compliance
        performCrossBorderChecks(request, requirements, alerts);
        
        // PEP screening
        performPepScreening(request, requirements, alerts);
        
        // CTR requirements
        performCtrChecks(request, requirements, alerts);
        
        ComplianceLevel level = determineComplianceLevel(requirements, alerts);
        boolean approved = level != ComplianceLevel.BLOCKED;
        
        ComplianceCheckResult result = ComplianceCheckResult.builder()
                .approved(approved)
                .complianceLevel(level)
                .requirements(requirements)
                .alerts(alerts)
                .reportingRequired(isReportingRequired(request, requirements))
                .checkedAt(Instant.now())
                .build();
        
        log.debug("Compliance check result: approved={}, level={}, requirements={}", 
                result.isApproved(), result.getComplianceLevel(), requirements.size());
        
        return result;
    }
    
    /**
     * Generate compliance report
     */
    public ComplianceReport generateComplianceReport(ComplianceReportRequest request) {
        List<ComplianceViolation> violations = new ArrayList<>();
        List<ComplianceMetric> metrics = new ArrayList<>();
        
        // Analyze transaction patterns
        analyzeTransactionPatterns(request, violations, metrics);
        
        // Check regulatory requirements
        checkRegulatoryRequirements(request, violations, metrics);
        
        return ComplianceReport.builder()
                .reportId(generateReportId())
                .period(request.getPeriod())
                .violations(violations)
                .metrics(metrics)
                .riskScore(calculateRiskScore(violations, metrics))
                .generatedAt(Instant.now())
                .build();
    }
    
    /**
     * Validate KYC status
     */
    public KycValidationResult validateKyc(KycValidationRequest request) {
        List<String> missingDocuments = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        // Check required documents
        if (!request.isIdentityDocumentVerified()) {
            missingDocuments.add("Government-issued ID verification");
        }
        
        if (!request.isAddressVerified()) {
            missingDocuments.add("Proof of address verification");
        }
        
        if (request.getAmount().isGreaterThan(Money.ngn(5_000_000.0))) {
            if (!request.isIncomeSourceVerified()) {
                missingDocuments.add("Source of income verification");
            }
            
            if (!request.isBiometricVerified()) {
                recommendations.add("Biometric verification recommended for high-value transactions");
            }
        }
        
        KycLevel level = determineKycLevel(request, missingDocuments);
        
        return KycValidationResult.builder()
                .compliant(missingDocuments.isEmpty())
                .kycLevel(level)
                .missingDocuments(missingDocuments)
                .recommendations(recommendations)
                .build();
    }
    
    // Private helper methods
    
    private void performAmlChecks(ComplianceCheckRequest request, 
                                List<ComplianceRequirement> requirements, 
                                List<String> alerts) {
        
        if (request.getAmount().isGreaterThan(AML_THRESHOLD_SINGLE)) {
            requirements.add(ComplianceRequirement.builder()
                    .type(RequirementType.AML_ENHANCED_DUE_DILIGENCE)
                    .description("Enhanced due diligence required for high-value transaction")
                    .mandatory(true)
                    .build());
        }
        
        // Pattern analysis
        if (request.getTransactionCount24h() > 50) {
            alerts.add("Unusual transaction frequency detected");
        }
        
        if (request.getDailyAmount().isGreaterThan(AML_THRESHOLD_DAILY)) {
            alerts.add("Daily AML threshold exceeded");
        }
    }
    
    private void performKycChecks(ComplianceCheckRequest request, 
                                List<ComplianceRequirement> requirements, 
                                List<String> alerts) {
        
        if (!request.isKycCompleted()) {
            requirements.add(ComplianceRequirement.builder()
                    .type(RequirementType.KYC_BASIC)
                    .description("Basic KYC verification required")
                    .mandatory(true)
                    .build());
        }
        
        if (request.getAmount().isGreaterThan(Money.ngn(2_000_000.0)) && !request.isEnhancedKycCompleted()) {
            requirements.add(ComplianceRequirement.builder()
                    .type(RequirementType.KYC_ENHANCED)
                    .description("Enhanced KYC verification required")
                    .mandatory(true)
                    .build());
        }
    }
    
    private void performSanctionsScreening(ComplianceCheckRequest request, 
                                         List<ComplianceRequirement> requirements, 
                                         List<String> alerts) {
        
        if (request.getCountryCode() != null && HIGH_RISK_COUNTRIES.contains(request.getCountryCode())) {
            requirements.add(ComplianceRequirement.builder()
                    .type(RequirementType.SANCTIONS_SCREENING)
                    .description("Sanctions screening required for high-risk country")
                    .mandatory(true)
                    .build());
            
            alerts.add("Transaction involves high-risk jurisdiction");
        }
    }
    
    private void performCrossBorderChecks(ComplianceCheckRequest request, 
                                        List<ComplianceRequirement> requirements, 
                                        List<String> alerts) {
        
        if (request.isCrossBorder()) {
            if (request.getAmount().isGreaterThan(CROSS_BORDER_THRESHOLD)) {
                requirements.add(ComplianceRequirement.builder()
                        .type(RequirementType.CROSS_BORDER_REPORTING)
                        .description("Cross-border transaction reporting required")
                        .mandatory(true)
                        .build());
            }
            
            alerts.add("Cross-border transaction detected");
        }
    }
    
    private void performPepScreening(ComplianceCheckRequest request, 
                                   List<ComplianceRequirement> requirements, 
                                   List<String> alerts) {
        
        if (request.isPepInvolved()) {
            requirements.add(ComplianceRequirement.builder()
                    .type(RequirementType.PEP_SCREENING)
                    .description("PEP enhanced due diligence required")
                    .mandatory(true)
                    .build());
            
            alerts.add("Politically Exposed Person involved in transaction");
        }
    }
    
    private void performCtrChecks(ComplianceCheckRequest request, 
                                List<ComplianceRequirement> requirements, 
                                List<String> alerts) {
        
        if (request.getAmount().isGreaterThan(CTR_THRESHOLD)) {
            requirements.add(ComplianceRequirement.builder()
                    .type(RequirementType.CTR_FILING)
                    .description("Currency Transaction Report filing required")
                    .mandatory(true)
                    .build());
        }
    }
    
    private ComplianceLevel determineComplianceLevel(List<ComplianceRequirement> requirements, List<String> alerts) {
        long mandatoryCount = requirements.stream()
                .mapToLong(req -> req.isMandatory() ? 1 : 0)
                .sum();
        
        if (mandatoryCount > 0) {
            return ComplianceLevel.REQUIRES_ACTION;
        }
        
        if (alerts.size() >= 3) {
            return ComplianceLevel.HIGH_RISK;
        }
        
        if (alerts.size() >= 1) {
            return ComplianceLevel.MEDIUM_RISK;
        }
        
        return ComplianceLevel.COMPLIANT;
    }
    
    private boolean isReportingRequired(ComplianceCheckRequest request, List<ComplianceRequirement> requirements) {
        return requirements.stream()
                .anyMatch(req -> req.getType() == RequirementType.CTR_FILING || 
                               req.getType() == RequirementType.CROSS_BORDER_REPORTING);
    }
    
    private void analyzeTransactionPatterns(ComplianceReportRequest request, 
                                          List<ComplianceViolation> violations, 
                                          List<ComplianceMetric> metrics) {
        // Implementation would analyze transaction patterns for suspicious activity
    }
    
    private void checkRegulatoryRequirements(ComplianceReportRequest request, 
                                           List<ComplianceViolation> violations, 
                                           List<ComplianceMetric> metrics) {
        // Implementation would check against regulatory requirements
    }
    
    private KycLevel determineKycLevel(KycValidationRequest request, List<String> missingDocuments) {
        if (!missingDocuments.isEmpty()) {
            return KycLevel.INCOMPLETE;
        }
        
        if (request.isBiometricVerified() && request.isIncomeSourceVerified()) {
            return KycLevel.ENHANCED;
        }
        
        return KycLevel.BASIC;
    }
    
    private String generateReportId() {
        return "rpt_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    private int calculateRiskScore(List<ComplianceViolation> violations, List<ComplianceMetric> metrics) {
        return violations.size() * 10 + metrics.size();
    }
    
    // Enums and inner classes
    
    public enum ComplianceLevel {
        COMPLIANT,
        MEDIUM_RISK,
        HIGH_RISK,
        REQUIRES_ACTION,
        BLOCKED
    }
    
    public enum RequirementType {
        KYC_BASIC,
        KYC_ENHANCED,
        AML_ENHANCED_DUE_DILIGENCE,
        SANCTIONS_SCREENING,
        PEP_SCREENING,
        CTR_FILING,
        CROSS_BORDER_REPORTING
    }
    
    public enum KycLevel {
        INCOMPLETE,
        BASIC,
        ENHANCED
    }
    
    @Data
    @Builder
    public static class ComplianceCheckRequest {
        private UserId userId;
        private Money amount;
        private String countryCode;
        private boolean crossBorder;
        private boolean kycCompleted;
        private boolean enhancedKycCompleted;
        private boolean pepInvolved;
        private int transactionCount24h;
        private Money dailyAmount;
    }
    
    @Data
    @Builder
    public static class ComplianceCheckResult {
        private boolean approved;
        private ComplianceLevel complianceLevel;
        private List<ComplianceRequirement> requirements;
        private List<String> alerts;
        private boolean reportingRequired;
        private Instant checkedAt;
    }
    
    @Data
    @Builder
    public static class ComplianceRequirement {
        private RequirementType type;
        private String description;
        private boolean mandatory;
    }
    
    @Data
    @Builder
    public static class KycValidationRequest {
        private UserId userId;
        private Money amount;
        private boolean identityDocumentVerified;
        private boolean addressVerified;
        private boolean incomeSourceVerified;
        private boolean biometricVerified;
    }
    
    @Data
    @Builder
    public static class KycValidationResult {
        private boolean compliant;
        private KycLevel kycLevel;
        private List<String> missingDocuments;
        private List<String> recommendations;
    }
    
    @Data
    @Builder
    public static class ComplianceReportRequest {
        private LocalDate startDate;
        private LocalDate endDate;
        private String period;
    }
    
    @Data
    @Builder
    public static class ComplianceReport {
        private String reportId;
        private String period;
        private List<ComplianceViolation> violations;
        private List<ComplianceMetric> metrics;
        private int riskScore;
        private Instant generatedAt;
    }
    
    @Data
    @Builder
    public static class ComplianceViolation {
        private String type;
        private String description;
        private String severity;
        private Instant occurredAt;
    }
    
    @Data
    @Builder
    public static class ComplianceMetric {
        private String name;
        private String value;
        private String description;
    }
}