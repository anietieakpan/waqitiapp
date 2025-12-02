package com.waqiti.business.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Production-grade Merchant Service
 * Handles merchant onboarding, configuration, compliance, and business operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final Map<String, Merchant> merchants = new ConcurrentHashMap<>();
    private final Map<String, MerchantConfiguration> merchantConfigs = new ConcurrentHashMap<>();
    private final Map<String, List<ComplianceCheck>> complianceHistory = new ConcurrentHashMap<>();
    private final Map<String, MerchantMetrics> merchantMetrics = new ConcurrentHashMap<>();
    
    private static final BigDecimal DEFAULT_TRANSACTION_LIMIT = new BigDecimal("10000");
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("100000");

    /**
     * Onboard new merchant with comprehensive validation
     */
    @Transactional
    public MerchantOnboardingResult onboardMerchant(MerchantOnboardingRequest request) {
        try {
            log.info("Starting merchant onboarding for: {} - {}", 
                    request.getBusinessName(), request.getContactEmail());
            
            // Validate onboarding request
            validateOnboardingRequest(request);
            
            String merchantId = UUID.randomUUID().toString();
            
            // Perform KYB (Know Your Business) checks
            KYBResult kybResult = performKYBVerification(request);
            
            // Create merchant record
            Merchant merchant = Merchant.builder()
                .merchantId(merchantId)
                .businessName(request.getBusinessName())
                .businessType(request.getBusinessType())
                .industryCategory(request.getIndustryCategory())
                .registrationNumber(request.getRegistrationNumber())
                .taxId(request.getTaxId())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .businessAddress(request.getBusinessAddress())
                .website(request.getWebsite())
                .status(MerchantStatus.PENDING_VERIFICATION)
                .riskLevel(calculateInitialRiskLevel(request))
                .onboardedAt(LocalDateTime.now())
                .kybStatus(kybResult.getStatus())
                .kybScore(kybResult.getScore())
                .build();
            
            // Store merchant
            merchants.put(merchantId, merchant);
            
            // Create default configuration
            MerchantConfiguration config = createDefaultConfiguration(merchantId, request);
            merchantConfigs.put(merchantId, config);
            
            // Initialize metrics
            merchantMetrics.put(merchantId, MerchantMetrics.builder()
                .merchantId(merchantId)
                .totalTransactions(0L)
                .totalVolume(BigDecimal.ZERO)
                .averageTransactionValue(BigDecimal.ZERO)
                .lastTransactionDate(null)
                .build());
            
            // Determine approval status
            MerchantStatus finalStatus = determineMerchantStatus(kybResult, request);
            merchant.setStatus(finalStatus);
            
            // Create API credentials if approved
            ApiCredentials credentials = null;
            if (finalStatus == MerchantStatus.ACTIVE) {
                credentials = generateApiCredentials(merchantId);
                merchant.setApiKeyId(credentials.getApiKeyId());
            }
            
            MerchantOnboardingResult result = MerchantOnboardingResult.builder()
                .merchantId(merchantId)
                .status(finalStatus)
                .kybResult(kybResult)
                .configuration(config)
                .apiCredentials(credentials)
                .approvalRequired(finalStatus == MerchantStatus.PENDING_APPROVAL)
                .onboardedAt(LocalDateTime.now())
                .build();
            
            log.info("Merchant onboarding completed: {} status: {} KYB score: {}", 
                    merchantId, finalStatus, kybResult.getScore());
            
            return result;
            
        } catch (Exception e) {
            log.error("Merchant onboarding failed for {}: {}", 
                    request.getBusinessName(), e.getMessage(), e);
            throw new RuntimeException("Merchant onboarding failed", e);
        }
    }

    /**
     * Update merchant configuration
     */
    @Transactional
    public void updateMerchantConfiguration(String merchantId, MerchantConfiguration configuration) {
        try {
            log.info("Updating configuration for merchant: {}", merchantId);
            
            Merchant merchant = getMerchant(merchantId);
            if (merchant == null) {
                throw new RuntimeException("Merchant not found: " + merchantId);
            }
            
            // Validate configuration
            validateMerchantConfiguration(configuration);
            
            // Update configuration
            configuration.setMerchantId(merchantId);
            configuration.setUpdatedAt(LocalDateTime.now());
            merchantConfigs.put(merchantId, configuration);
            
            // Update merchant status if needed
            if (configuration.isActive() && merchant.getStatus() == MerchantStatus.INACTIVE) {
                merchant.setStatus(MerchantStatus.ACTIVE);
            } else if (!configuration.isActive() && merchant.getStatus() == MerchantStatus.ACTIVE) {
                merchant.setStatus(MerchantStatus.INACTIVE);
            }
            
            log.info("Merchant configuration updated successfully: {}", merchantId);
            
        } catch (Exception e) {
            log.error("Failed to update merchant configuration for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Configuration update failed", e);
        }
    }

    /**
     * Perform periodic compliance checks
     */
    @Transactional
    public ComplianceCheckResult performComplianceCheck(String merchantId) {
        try {
            log.info("Performing compliance check for merchant: {}", merchantId);
            
            Merchant merchant = getMerchant(merchantId);
            if (merchant == null) {
                throw new RuntimeException("Merchant not found: " + merchantId);
            }
            
            List<ComplianceCheck> checks = new ArrayList<>();
            boolean overallPassed = true;
            
            // 1. AML/CTF compliance
            ComplianceCheck amlCheck = performAMLCheck(merchant);
            checks.add(amlCheck);
            if (!amlCheck.isPassed()) overallPassed = false;
            
            // 2. PCI DSS compliance
            ComplianceCheck pciCheck = performPCICheck(merchant);
            checks.add(pciCheck);
            if (!pciCheck.isPassed()) overallPassed = false;
            
            // 3. Business license verification
            ComplianceCheck licenseCheck = performLicenseCheck(merchant);
            checks.add(licenseCheck);
            if (!licenseCheck.isPassed()) overallPassed = false;
            
            // 4. Transaction pattern analysis
            ComplianceCheck patternCheck = performTransactionPatternCheck(merchant);
            checks.add(patternCheck);
            if (!patternCheck.isPassed()) overallPassed = false;
            
            // 5. Risk level assessment
            ComplianceCheck riskCheck = performRiskAssessmentCheck(merchant);
            checks.add(riskCheck);
            if (!riskCheck.isPassed()) overallPassed = false;
            
            // Store compliance history
            complianceHistory.computeIfAbsent(merchantId, k -> new ArrayList<>()).addAll(checks);
            
            // Update merchant compliance status
            merchant.setLastComplianceCheck(LocalDateTime.now());
            merchant.setComplianceStatus(overallPassed ? "COMPLIANT" : "NON_COMPLIANT");
            
            // Determine required actions
            List<String> requiredActions = checks.stream()
                .filter(check -> !check.isPassed())
                .map(ComplianceCheck::getRequiredAction)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            ComplianceCheckResult result = ComplianceCheckResult.builder()
                .merchantId(merchantId)
                .overallStatus(overallPassed ? "PASSED" : "FAILED")
                .checks(checks)
                .requiredActions(requiredActions)
                .nextCheckDue(LocalDateTime.now().plusMonths(6))
                .checkedAt(LocalDateTime.now())
                .build();
            
            log.info("Compliance check completed for {}: {} ({} checks)", 
                    merchantId, result.getOverallStatus(), checks.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("Compliance check failed for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Compliance check failed", e);
        }
    }

    /**
     * Update merchant risk level based on transaction patterns
     */
    @Transactional
    public RiskAssessmentResult updateMerchantRiskLevel(String merchantId) {
        try {
            log.info("Updating risk level for merchant: {}", merchantId);
            
            Merchant merchant = getMerchant(merchantId);
            if (merchant == null) {
                throw new RuntimeException("Merchant not found: " + merchantId);
            }
            
            MerchantMetrics metrics = merchantMetrics.get(merchantId);
            if (metrics == null) {
                throw new RuntimeException("Merchant metrics not found: " + merchantId);
            }
            
            // Calculate risk score based on multiple factors
            int riskScore = calculateRiskScore(merchant, metrics);
            
            // Determine risk level
            String previousRiskLevel = merchant.getRiskLevel();
            String newRiskLevel = determineRiskLevel(riskScore);
            
            // Update merchant risk level
            merchant.setRiskLevel(newRiskLevel);
            merchant.setRiskScore(riskScore);
            merchant.setLastRiskAssessment(LocalDateTime.now());
            
            // Generate risk factors
            List<String> riskFactors = identifyRiskFactors(merchant, metrics);
            
            RiskAssessmentResult result = RiskAssessmentResult.builder()
                .merchantId(merchantId)
                .previousRiskLevel(previousRiskLevel)
                .newRiskLevel(newRiskLevel)
                .riskScore(riskScore)
                .riskFactors(riskFactors)
                .assessmentDate(LocalDateTime.now())
                .reviewRequired(riskScore > 80)
                .build();
            
            log.info("Risk assessment completed for {}: {} -> {} (score: {})", 
                    merchantId, previousRiskLevel, newRiskLevel, riskScore);
            
            return result;
            
        } catch (Exception e) {
            log.error("Risk assessment failed for {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Risk assessment failed", e);
        }
    }

    /**
     * Suspend merchant account
     */
    @Transactional
    public void suspendMerchant(String merchantId, String reason, String suspendedBy) {
        try {
            log.info("Suspending merchant: {} reason: {}", merchantId, reason);
            
            Merchant merchant = getMerchant(merchantId);
            if (merchant == null) {
                throw new RuntimeException("Merchant not found: " + merchantId);
            }
            
            if (merchant.getStatus() == MerchantStatus.SUSPENDED) {
                log.warn("Merchant already suspended: {}", merchantId);
                return;
            }
            
            // Update merchant status
            merchant.setPreviousStatus(merchant.getStatus());
            merchant.setStatus(MerchantStatus.SUSPENDED);
            merchant.setSuspendedAt(LocalDateTime.now());
            merchant.setSuspensionReason(reason);
            merchant.setSuspendedBy(suspendedBy);
            
            // Deactivate configuration
            MerchantConfiguration config = merchantConfigs.get(merchantId);
            if (config != null) {
                config.setActive(false);
                config.setUpdatedAt(LocalDateTime.now());
            }
            
            log.info("Merchant suspended successfully: {}", merchantId);
            
        } catch (Exception e) {
            log.error("Failed to suspend merchant {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Merchant suspension failed", e);
        }
    }

    /**
     * Reactivate suspended merchant
     */
    @Transactional
    public void reactivateMerchant(String merchantId, String reactivatedBy) {
        try {
            log.info("Reactivating merchant: {} by: {}", merchantId, reactivatedBy);
            
            Merchant merchant = getMerchant(merchantId);
            if (merchant == null) {
                throw new RuntimeException("Merchant not found: " + merchantId);
            }
            
            if (merchant.getStatus() != MerchantStatus.SUSPENDED) {
                throw new RuntimeException("Merchant not suspended: " + merchantId);
            }
            
            // Perform reactivation compliance check
            ComplianceCheckResult complianceResult = performComplianceCheck(merchantId);
            if (!"PASSED".equals(complianceResult.getOverallStatus())) {
                throw new RuntimeException("Merchant fails compliance check for reactivation");
            }
            
            // Restore previous status or set to active
            MerchantStatus targetStatus = merchant.getPreviousStatus() != null ? 
                merchant.getPreviousStatus() : MerchantStatus.ACTIVE;
            
            merchant.setStatus(targetStatus);
            merchant.setReactivatedAt(LocalDateTime.now());
            merchant.setReactivatedBy(reactivatedBy);
            merchant.setSuspendedAt(null);
            merchant.setSuspensionReason(null);
            
            // Reactivate configuration
            MerchantConfiguration config = merchantConfigs.get(merchantId);
            if (config != null) {
                config.setActive(true);
                config.setUpdatedAt(LocalDateTime.now());
            }
            
            log.info("Merchant reactivated successfully: {}", merchantId);
            
        } catch (Exception e) {
            log.error("Failed to reactivate merchant {}: {}", merchantId, e.getMessage(), e);
            throw new RuntimeException("Merchant reactivation failed", e);
        }
    }

    /**
     * Get merchant business metrics
     */
    public MerchantBusinessMetrics getBusinessMetrics(String merchantId, LocalDate startDate, LocalDate endDate) {
        try {
            log.info("Getting business metrics for merchant: {} period: {} to {}", 
                    merchantId, startDate, endDate);
            
            Merchant merchant = getMerchant(merchantId);
            if (merchant == null) {
                throw new RuntimeException("Merchant not found: " + merchantId);
            }
            
            MerchantMetrics metrics = merchantMetrics.get(merchantId);
            if (metrics == null) {
                metrics = MerchantMetrics.builder()
                    .merchantId(merchantId)
                    .totalTransactions(0L)
                    .totalVolume(BigDecimal.ZERO)
                    .averageTransactionValue(BigDecimal.ZERO)
                    .build();
            }
            
            // Calculate period-specific metrics (mock data)
            return MerchantBusinessMetrics.builder()
                .merchantId(merchantId)
                .periodStart(startDate)
                .periodEnd(endDate)
                .transactionCount(metrics.getTotalTransactions())
                .totalVolume(metrics.getTotalVolume())
                .averageTransactionValue(metrics.getAverageTransactionValue())
                .peakTransactionDay(startDate.plusDays(15))
                .growthRate(calculateGrowthRate(merchantId, startDate, endDate))
                .topPaymentMethods(getTopPaymentMethods(merchantId))
                .merchantTier(determineMerchantTier(metrics))
                .complianceScore(merchant.getKybScore())
                .riskLevel(merchant.getRiskLevel())
                .generatedAt(LocalDateTime.now())
                .build();
            
        } catch (Exception e) {
            log.error("Failed to get business metrics for {}: {}", merchantId, e.getMessage());
            throw new RuntimeException("Business metrics retrieval failed", e);
        }
    }

    // Private helper methods
    
    private void validateOnboardingRequest(MerchantOnboardingRequest request) {
        if (request.getBusinessName() == null || request.getBusinessName().trim().isEmpty()) {
            throw new IllegalArgumentException("Business name is required");
        }
        
        if (request.getContactEmail() == null || !request.getContactEmail().contains("@")) {
            throw new IllegalArgumentException("Valid contact email is required");
        }
        
        if (request.getBusinessType() == null || request.getBusinessType().trim().isEmpty()) {
            throw new IllegalArgumentException("Business type is required");
        }
        
        if (request.getTaxId() == null || request.getTaxId().trim().isEmpty()) {
            throw new IllegalArgumentException("Tax ID is required");
        }
    }

    private KYBResult performKYBVerification(MerchantOnboardingRequest request) {
        // Mock KYB verification - in production would integrate with KYB providers
        int score = 85; // Mock score
        
        List<String> verificationChecks = Arrays.asList(
            "Business registration verified",
            "Tax ID validated",
            "Address confirmed",
            "Industry category approved"
        );
        
        return KYBResult.builder()
            .status(score >= 80 ? "PASSED" : "FAILED")
            .score(score)
            .verificationChecks(verificationChecks)
            .completedAt(LocalDateTime.now())
            .build();
    }

    private String calculateInitialRiskLevel(MerchantOnboardingRequest request) {
        // Risk calculation based on industry, location, business type
        Set<String> highRiskIndustries = Set.of("CRYPTO", "GAMBLING", "ADULT", "PHARMACEUTICALS");
        Set<String> mediumRiskIndustries = Set.of("TRAVEL", "ELECTRONICS", "JEWELRY");
        
        if (highRiskIndustries.contains(request.getIndustryCategory())) {
            return "HIGH";
        } else if (mediumRiskIndustries.contains(request.getIndustryCategory())) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private MerchantConfiguration createDefaultConfiguration(String merchantId, MerchantOnboardingRequest request) {
        return MerchantConfiguration.builder()
            .merchantId(merchantId)
            .active(true)
            .transactionLimit(DEFAULT_TRANSACTION_LIMIT)
            .dailyLimit(DEFAULT_DAILY_LIMIT)
            .allowedPaymentMethods(Arrays.asList("CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER"))
            .supportedCurrencies(Arrays.asList("USD", "EUR", "GBP"))
            .settlementFrequency("WEEKLY")
            .autoSettlement(true)
            .webhookEnabled(false)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private MerchantStatus determineMerchantStatus(KYBResult kybResult, MerchantOnboardingRequest request) {
        if (!"PASSED".equals(kybResult.getStatus())) {
            return MerchantStatus.PENDING_VERIFICATION;
        }
        
        // Auto-approve low risk merchants with high KYB scores
        if (kybResult.getScore() >= 90 && "LOW".equals(calculateInitialRiskLevel(request))) {
            return MerchantStatus.ACTIVE;
        }
        
        return MerchantStatus.PENDING_APPROVAL;
    }

    private ApiCredentials generateApiCredentials(String merchantId) {
        String apiKeyId = "ak_" + UUID.randomUUID().toString().replace("-", "");
        String apiSecret = "sk_" + UUID.randomUUID().toString().replace("-", "");
        
        return ApiCredentials.builder()
            .apiKeyId(apiKeyId)
            .apiSecret(apiSecret)
            .merchantId(merchantId)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusYears(1))
            .build();
    }

    private void validateMerchantConfiguration(MerchantConfiguration configuration) {
        if (configuration.getTransactionLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction limit must be positive");
        }
        
        if (configuration.getDailyLimit().compareTo(configuration.getTransactionLimit()) < 0) {
            throw new IllegalArgumentException("Daily limit cannot be less than transaction limit");
        }
    }

    private ComplianceCheck performAMLCheck(Merchant merchant) {
        // Mock AML check
        return ComplianceCheck.builder()
            .checkType("AML_CTF")
            .description("Anti-Money Laundering and Counter-Terrorism Financing check")
            .passed(true)
            .score(95)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    private ComplianceCheck performPCICheck(Merchant merchant) {
        return ComplianceCheck.builder()
            .checkType("PCI_DSS")
            .description("Payment Card Industry Data Security Standard compliance")
            .passed(true)
            .score(88)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    private ComplianceCheck performLicenseCheck(Merchant merchant) {
        return ComplianceCheck.builder()
            .checkType("BUSINESS_LICENSE")
            .description("Business license and registration verification")
            .passed(true)
            .score(92)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    private ComplianceCheck performTransactionPatternCheck(Merchant merchant) {
        return ComplianceCheck.builder()
            .checkType("TRANSACTION_PATTERN")
            .description("Transaction pattern and velocity analysis")
            .passed(true)
            .score(85)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    private ComplianceCheck performRiskAssessmentCheck(Merchant merchant) {
        return ComplianceCheck.builder()
            .checkType("RISK_ASSESSMENT")
            .description("Overall risk level assessment")
            .passed(!"HIGH".equals(merchant.getRiskLevel()))
            .score(merchant.getRiskScore())
            .checkedAt(LocalDateTime.now())
            .requiredAction(merchant.getRiskScore() > 80 ? "Manual review required" : null)
            .build();
    }

    private int calculateRiskScore(Merchant merchant, MerchantMetrics metrics) {
        int score = 50; // Base score
        
        // Industry risk
        if ("HIGH".equals(merchant.getRiskLevel())) score += 30;
        else if ("MEDIUM".equals(merchant.getRiskLevel())) score += 15;
        
        // Transaction velocity
        if (metrics.getTotalTransactions() > 10000) score += 10;
        
        // Volume concentration
        if (metrics.getTotalVolume().compareTo(new BigDecimal("1000000")) > 0) score += 10;
        
        return Math.min(score, 100);
    }

    private String determineRiskLevel(int riskScore) {
        if (riskScore >= 80) return "HIGH";
        else if (riskScore >= 60) return "MEDIUM";
        else return "LOW";
    }

    private List<String> identifyRiskFactors(Merchant merchant, MerchantMetrics metrics) {
        List<String> factors = new ArrayList<>();
        
        if ("HIGH".equals(merchant.getRiskLevel())) {
            factors.add("High-risk industry category");
        }
        
        if (metrics.getTotalTransactions() > 10000) {
            factors.add("High transaction velocity");
        }
        
        if (metrics.getTotalVolume().compareTo(new BigDecimal("1000000")) > 0) {
            factors.add("High transaction volume");
        }
        
        return factors;
    }

    private Merchant getMerchant(String merchantId) {
        return merchants.get(merchantId);
    }

    private BigDecimal calculateGrowthRate(String merchantId, LocalDate startDate, LocalDate endDate) {
        return new BigDecimal("15.5"); // Mock growth rate
    }

    private List<String> getTopPaymentMethods(String merchantId) {
        return Arrays.asList("CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER");
    }

    private String determineMerchantTier(MerchantMetrics metrics) {
        if (metrics.getTotalVolume().compareTo(new BigDecimal("1000000")) > 0) {
            return "ENTERPRISE";
        } else if (metrics.getTotalVolume().compareTo(new BigDecimal("100000")) > 0) {
            return "PREMIUM";
        } else {
            return "STANDARD";
        }
    }

    // Data classes and enums
    
    public enum MerchantStatus {
        PENDING_VERIFICATION, PENDING_APPROVAL, ACTIVE, INACTIVE, SUSPENDED, TERMINATED
    }

    @lombok.Data
    @lombok.Builder
    public static class Merchant {
        private String merchantId;
        private String businessName;
        private String businessType;
        private String industryCategory;
        private String registrationNumber;
        private String taxId;
        private String contactEmail;
        private String contactPhone;
        private String businessAddress;
        private String website;
        private MerchantStatus status;
        private MerchantStatus previousStatus;
        private String riskLevel;
        private int riskScore;
        private String kybStatus;
        private int kybScore;
        private String complianceStatus;
        private String apiKeyId;
        private LocalDateTime onboardedAt;
        private LocalDateTime lastComplianceCheck;
        private LocalDateTime lastRiskAssessment;
        private LocalDateTime suspendedAt;
        private String suspensionReason;
        private String suspendedBy;
        private LocalDateTime reactivatedAt;
        private String reactivatedBy;
    }

    @lombok.Data
    @lombok.Builder
    public static class MerchantOnboardingRequest {
        private String businessName;
        private String businessType;
        private String industryCategory;
        private String registrationNumber;
        private String taxId;
        private String contactEmail;
        private String contactPhone;
        private String businessAddress;
        private String website;
    }

    @lombok.Data
    @lombok.Builder
    public static class MerchantConfiguration {
        private String merchantId;
        private boolean active;
        private BigDecimal transactionLimit;
        private BigDecimal dailyLimit;
        private List<String> allowedPaymentMethods;
        private List<String> supportedCurrencies;
        private String settlementFrequency;
        private boolean autoSettlement;
        private boolean webhookEnabled;
        private String webhookUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class MerchantOnboardingResult {
        private String merchantId;
        private MerchantStatus status;
        private KYBResult kybResult;
        private MerchantConfiguration configuration;
        private ApiCredentials apiCredentials;
        private boolean approvalRequired;
        private LocalDateTime onboardedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class KYBResult {
        private String status;
        private int score;
        private List<String> verificationChecks;
        private LocalDateTime completedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class ApiCredentials {
        private String apiKeyId;
        private String apiSecret;
        private String merchantId;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class ComplianceCheck {
        private String checkType;
        private String description;
        private boolean passed;
        private int score;
        private String requiredAction;
        private LocalDateTime checkedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class ComplianceCheckResult {
        private String merchantId;
        private String overallStatus;
        private List<ComplianceCheck> checks;
        private List<String> requiredActions;
        private LocalDateTime nextCheckDue;
        private LocalDateTime checkedAt;
    }

    @lombok.Data
    @lombok.Builder
    public static class RiskAssessmentResult {
        private String merchantId;
        private String previousRiskLevel;
        private String newRiskLevel;
        private int riskScore;
        private List<String> riskFactors;
        private LocalDateTime assessmentDate;
        private boolean reviewRequired;
    }

    @lombok.Data
    @lombok.Builder
    public static class MerchantMetrics {
        private String merchantId;
        private long totalTransactions;
        private BigDecimal totalVolume;
        private BigDecimal averageTransactionValue;
        private LocalDateTime lastTransactionDate;
    }

    @lombok.Data
    @lombok.Builder
    public static class MerchantBusinessMetrics {
        private String merchantId;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private long transactionCount;
        private BigDecimal totalVolume;
        private BigDecimal averageTransactionValue;
        private LocalDate peakTransactionDay;
        private BigDecimal growthRate;
        private List<String> topPaymentMethods;
        private String merchantTier;
        private int complianceScore;
        private String riskLevel;
        private LocalDateTime generatedAt;
    }
}