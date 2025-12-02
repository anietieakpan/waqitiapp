package com.waqiti.payment.international.compliance;

import com.waqiti.common.compliance.aml.AMLScreeningService;
import com.waqiti.common.compliance.sanctions.SanctionsScreeningService;
import com.waqiti.common.compliance.kyc.KYCVerificationService;
import com.waqiti.common.compliance.pep.PEPScreeningService;
import com.waqiti.payment.international.model.*;
import com.waqiti.payment.international.repository.ComplianceCheckRepository;
import com.waqiti.common.audit.Auditable;
import com.waqiti.common.security.SecurityContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * Comprehensive compliance service for international transfers
 * Handles AML, sanctions screening, KYC verification, and regulatory requirements
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternationalComplianceService implements ComplianceService {

    private final AMLScreeningService amlScreeningService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final KYCVerificationService kycVerificationService;
    private final PEPScreeningService pepScreeningService;
    private final ComplianceCheckRepository complianceRepository;
    private final ThreadPoolExecutor complianceExecutor;
    private final MeterRegistry meterRegistry;
    
    // Compliance thresholds by region/corridor
    private static final Map<String, BigDecimal> AML_REPORTING_THRESHOLDS = Map.of(
        "US", new BigDecimal("3000"),    // BSA reporting threshold
        "EU", new BigDecimal("1000"),    // EU AML threshold
        "UK", new BigDecimal("1000"),    // UK AML threshold
        "CA", new BigDecimal("1000"),    // Canada FINTRAC threshold
        "AU", new BigDecimal("2000"),    // Australia AUSTRAC threshold
        "DEFAULT", new BigDecimal("1000")
    );
    
    // High-risk countries requiring enhanced due diligence
    private static final Set<String> HIGH_RISK_COUNTRIES = Set.of(
        "AF", "AL", "BB", "BF", "KH", "KP", "HT", "IR", "JM", "JO", 
        "ML", "MM", "NI", "PK", "PA", "PH", "SN", "LK", "SY", "UG", 
        "VU", "YE", "ZW", "SO", "LY", "VE", "CU", "SD"
    );
    
    // FATF blacklisted and grey-listed countries
    private static final Set<String> FATF_BLACKLIST = Set.of("KP", "IR");
    private static final Set<String> FATF_GREYLIST = Set.of(
        "AL", "BB", "BF", "KH", "KY", "HT", "JM", "JO", "ML", "MM", 
        "NI", "PK", "PA", "PH", "SN", "LK", "SY", "TR", "UG", "VU", "YE"
    );

    private final Counter complianceChecksCounter;
    private final Counter complianceFailuresCounter;
    private final Timer complianceCheckTimer;

    public InternationalComplianceService(
            AMLScreeningService amlScreeningService,
            SanctionsScreeningService sanctionsScreeningService,
            KYCVerificationService kycVerificationService,
            PEPScreeningService pepScreeningService,
            ComplianceCheckRepository complianceRepository,
            ThreadPoolExecutor complianceExecutor,
            MeterRegistry meterRegistry) {
        
        this.amlScreeningService = amlScreeningService;
        this.sanctionsScreeningService = sanctionsScreeningService;
        this.kycVerificationService = kycVerificationService;
        this.pepScreeningService = pepScreeningService;
        this.complianceRepository = complianceRepository;
        this.complianceExecutor = complianceExecutor;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.complianceChecksCounter = Counter.builder("compliance.international.checks.total")
                .description("Total international compliance checks performed")
                .register(meterRegistry);
                
        this.complianceFailuresCounter = Counter.builder("compliance.international.failures.total")
                .description("International compliance check failures")
                .register(meterRegistry);
                
        this.complianceCheckTimer = Timer.builder("compliance.international.check.duration")
                .description("Duration of international compliance checks")
                .register(meterRegistry);
    }
    
    @Override
    @Transactional
    @Auditable(action = "INTERNATIONAL_COMPLIANCE_CHECK")
    public ComplianceCheckResult performChecks(ComplianceCheckRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String checkId = UUID.randomUUID().toString();
        
        try {
            log.info("Performing compliance checks for international transfer: {} to {}", 
                    request.getSenderCountry(), request.getRecipientCountry());
            
            complianceChecksCounter.increment();
            
            // Create compliance check record
            ComplianceCheck complianceCheck = createComplianceCheckRecord(request, checkId);
            complianceRepository.save(complianceCheck);
            
            // Determine required checks based on corridor and amount
            Set<ComplianceRequirement> requiredChecks = determineRequiredChecks(request);
            
            // Perform checks asynchronously
            List<CompletableFuture<CheckResult>> checkFutures = new ArrayList<>();
            
            if (requiredChecks.contains(ComplianceRequirement.ANTI_MONEY_LAUNDERING_CHECK)) {
                checkFutures.add(performAMLCheck(request));
            }
            
            if (requiredChecks.contains(ComplianceRequirement.OFAC_SCREENING) || 
                requiredChecks.contains(ComplianceRequirement.EU_SANCTIONS_SCREENING)) {
                checkFutures.add(performSanctionsScreening(request));
            }
            
            if (requiredChecks.contains(ComplianceRequirement.SENDER_ID_VERIFICATION)) {
                checkFutures.add(performSenderKYCCheck(request));
            }
            
            if (requiredChecks.contains(ComplianceRequirement.RECIPIENT_ID_VERIFICATION)) {
                checkFutures.add(performRecipientVerification(request));
            }
            
            if (requiredChecks.contains(ComplianceRequirement.POLITICALLY_EXPOSED_PERSON_CHECK)) {
                checkFutures.add(performPEPScreening(request));
            }
            
            if (requiredChecks.contains(ComplianceRequirement.SOURCE_OF_FUNDS)) {
                checkFutures.add(performSourceOfFundsCheck(request));
            }
            
            if (requiredChecks.contains(ComplianceRequirement.PURPOSE_OF_REMITTANCE)) {
                checkFutures.add(performPurposeValidation(request));
            }
            
            if (isHighRiskTransfer(request)) {
                checkFutures.add(performEnhancedDueDiligence(request));
            }
            
            // Wait for all checks to complete
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                    checkFutures.toArray(new CompletableFuture[0]));
            
            List<CheckResult> results = allChecks
                    .thenApply(v -> checkFutures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()))
                    .get();
            
            // Aggregate results
            ComplianceCheckResult finalResult = aggregateResults(checkId, results, requiredChecks);
            
            // Update compliance check record
            complianceCheck.updateResults(finalResult);
            complianceRepository.save(complianceCheck);
            
            // Log result
            if (finalResult.isPassed()) {
                log.info("Compliance checks passed for transfer {}", checkId);
            } else {
                log.warn("Compliance checks failed for transfer {}: {}", 
                        checkId, finalResult.getFailureReasons());
                complianceFailuresCounter.increment();
            }
            
            return finalResult;
            
        } catch (Exception e) {
            log.error("Error performing compliance checks", e);
            complianceFailuresCounter.increment();
            
            return ComplianceCheckResult.builder()
                    .checkId(checkId)
                    .passed(false)
                    .riskLevel(RiskLevel.HIGH)
                    .failureReasons(List.of("Internal compliance check error: " + e.getMessage()))
                    .checkTimestamp(Instant.now())
                    .build();
        } finally {
            sample.stop(complianceCheckTimer);
        }
    }
    
    @Override
    @Cacheable(value = "compliance_risk_scores", key = "#request.userId + '_' + #request.recipientCountry")
    public RiskAssessment assessTransferRisk(ComplianceCheckRequest request) {
        RiskAssessment.Builder riskBuilder = RiskAssessment.builder();
        
        int riskScore = 0;
        List<String> riskFactors = new ArrayList<>();
        
        // Country risk assessment
        if (FATF_BLACKLIST.contains(request.getRecipientCountry())) {
            riskScore += 50;
            riskFactors.add("FATF blacklisted destination country");
        } else if (FATF_GREYLIST.contains(request.getRecipientCountry())) {
            riskScore += 30;
            riskFactors.add("FATF grey-listed destination country");
        } else if (HIGH_RISK_COUNTRIES.contains(request.getRecipientCountry())) {
            riskScore += 20;
            riskFactors.add("High-risk destination country");
        }
        
        if (HIGH_RISK_COUNTRIES.contains(request.getSenderCountry())) {
            riskScore += 15;
            riskFactors.add("High-risk sender country");
        }
        
        // Amount-based risk
        BigDecimal amlThreshold = getAMLThreshold(request.getSenderCountry());
        if (request.getAmount().compareTo(amlThreshold) > 0) {
            riskScore += 25;
            riskFactors.add("Amount exceeds AML reporting threshold");
        }
        
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            riskScore += 15;
            riskFactors.add("High-value transaction");
        }
        
        // Currency risk
        if (isExoticCurrency(request.getCurrency())) {
            riskScore += 10;
            riskFactors.add("Exotic currency involved");
        }
        
        // Purpose risk
        if (isHighRiskPurpose(request.getPurposeCode())) {
            riskScore += 20;
            riskFactors.add("High-risk transaction purpose");
        }
        
        // Historical pattern analysis
        TransferPattern userPattern = analyzeUserTransferPattern(request.getUserId());
        if (userPattern.isUnusualPattern(request)) {
            riskScore += userPattern.getAnomalyScore();
            riskFactors.add("Unusual transfer pattern for user");
        }
        
        // Determine risk level
        RiskLevel riskLevel;
        if (riskScore >= 80) {
            riskLevel = RiskLevel.CRITICAL;
        } else if (riskScore >= 60) {
            riskLevel = RiskLevel.HIGH;
        } else if (riskScore >= 30) {
            riskLevel = RiskLevel.MEDIUM;
        } else {
            riskLevel = RiskLevel.LOW;
        }
        
        return riskBuilder
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .riskFactors(riskFactors)
                .assessmentTimestamp(Instant.now())
                .build();
    }
    
    @Override
    public boolean requiresManualReview(ComplianceCheckRequest request) {
        RiskAssessment risk = assessTransferRisk(request);
        
        // Manual review criteria
        return risk.getRiskLevel() == RiskLevel.CRITICAL ||
               risk.getRiskLevel() == RiskLevel.HIGH ||
               request.getAmount().compareTo(new BigDecimal("25000")) > 0 ||
               FATF_BLACKLIST.contains(request.getRecipientCountry()) ||
               isFirstTimeToCountry(request.getUserId(), request.getRecipientCountry()) ||
               hasRecentComplianceIssues(request.getUserId());
    }
    
    // Private helper methods
    
    private ComplianceCheck createComplianceCheckRecord(ComplianceCheckRequest request, String checkId) {
        return ComplianceCheck.builder()
                .id(checkId)
                .userId(request.getUserId())
                .transferId(request.getTransferId())
                .corridorId(request.getCorridorId())
                .senderCountry(request.getSenderCountry())
                .recipientCountry(request.getRecipientCountry())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .purposeCode(request.getPurposeCode())
                .status(ComplianceStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .createdBy(SecurityContext.getCurrentUserId())
                .build();
    }
    
    private Set<ComplianceRequirement> determineRequiredChecks(ComplianceCheckRequest request) {
        Set<ComplianceRequirement> required = new HashSet<>();
        
        // Always required for international transfers
        required.add(ComplianceRequirement.ANTI_MONEY_LAUNDERING_CHECK);
        required.add(ComplianceRequirement.SENDER_ID_VERIFICATION);
        required.add(ComplianceRequirement.PURPOSE_OF_REMITTANCE);
        
        // US-specific requirements
        if ("US".equals(request.getSenderCountry())) {
            required.add(ComplianceRequirement.OFAC_SCREENING);
            required.add(ComplianceRequirement.BSA_REPORTING);
            
            if (request.getAmount().compareTo(new BigDecimal("3000")) > 0) {
                required.add(ComplianceRequirement.SOURCE_OF_FUNDS);
            }
        }
        
        // EU-specific requirements
        if (isEUCountry(request.getSenderCountry())) {
            required.add(ComplianceRequirement.EU_SANCTIONS_SCREENING);
            required.add(ComplianceRequirement.PSD2_COMPLIANCE);
            required.add(ComplianceRequirement.GDPR_COMPLIANCE);
            
            if (request.getAmount().compareTo(new BigDecimal("1000")) > 0) {
                required.add(ComplianceRequirement.CDD_ENHANCED);
            }
        }
        
        // High-risk country requirements
        if (HIGH_RISK_COUNTRIES.contains(request.getRecipientCountry()) ||
            HIGH_RISK_COUNTRIES.contains(request.getSenderCountry())) {
            required.add(ComplianceRequirement.RECIPIENT_ID_VERIFICATION);
            required.add(ComplianceRequirement.POLITICALLY_EXPOSED_PERSON_CHECK);
            required.add(ComplianceRequirement.SOURCE_OF_FUNDS);
            required.add(ComplianceRequirement.CDD_ENHANCED);
        }
        
        // Amount-based requirements
        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            required.add(ComplianceRequirement.RECIPIENT_ID_VERIFICATION);
            required.add(ComplianceRequirement.POLITICALLY_EXPOSED_PERSON_CHECK);
            required.add(ComplianceRequirement.SOURCE_OF_FUNDS);
        }
        
        return required;
    }
    
    private CompletableFuture<CheckResult> performAMLCheck(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                AMLCheckResult amlResult = amlScreeningService.performCheck(
                        AMLCheckRequest.builder()
                                .userId(request.getUserId())
                                .amount(request.getAmount())
                                .currency(request.getCurrency())
                                .senderCountry(request.getSenderCountry())
                                .recipientCountry(request.getRecipientCountry())
                                .purposeCode(request.getPurposeCode())
                                .build()
                );
                
                return CheckResult.builder()
                        .checkType("AML_SCREENING")
                        .passed(amlResult.isPassed())
                        .riskLevel(amlResult.getRiskLevel())
                        .details(amlResult.getDetails())
                        .failureReasons(amlResult.getFailureReasons())
                        .build();
                
            } catch (Exception e) {
                log.error("AML check failed", e);
                return CheckResult.builder()
                        .checkType("AML_SCREENING")
                        .passed(false)
                        .riskLevel(RiskLevel.HIGH)
                        .failureReasons(List.of("AML check error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performSanctionsScreening(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SanctionsCheckResult sanctionsResult = sanctionsScreeningService.performCheck(
                        SanctionsCheckRequest.builder()
                                .senderName(request.getSenderName())
                                .recipientName(request.getRecipient().getFullName())
                                .senderCountry(request.getSenderCountry())
                                .recipientCountry(request.getRecipientCountry())
                                .build()
                );
                
                return CheckResult.builder()
                        .checkType("SANCTIONS_SCREENING")
                        .passed(sanctionsResult.isPassed())
                        .riskLevel(sanctionsResult.getRiskLevel())
                        .details(sanctionsResult.getMatchDetails())
                        .failureReasons(sanctionsResult.getFailureReasons())
                        .build();
                
            } catch (Exception e) {
                log.error("Sanctions screening failed", e);
                return CheckResult.builder()
                        .checkType("SANCTIONS_SCREENING")
                        .passed(false)
                        .riskLevel(RiskLevel.HIGH)
                        .failureReasons(List.of("Sanctions screening error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performSenderKYCCheck(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                KYCVerificationResult kycResult = kycVerificationService.verifyUser(request.getUserId());
                
                boolean passed = kycResult.isVerified() && 
                                kycResult.getVerificationLevel().ordinal() >= KYCLevel.ENHANCED.ordinal();
                
                return CheckResult.builder()
                        .checkType("SENDER_KYC")
                        .passed(passed)
                        .riskLevel(passed ? RiskLevel.LOW : RiskLevel.MEDIUM)
                        .details(Map.of(
                            "verification_level", kycResult.getVerificationLevel().toString(),
                            "verification_date", kycResult.getVerificationDate().toString()
                        ))
                        .failureReasons(passed ? Collections.emptyList() : 
                                      List.of("Sender KYC verification insufficient"))
                        .build();
                
            } catch (Exception e) {
                log.error("Sender KYC check failed", e);
                return CheckResult.builder()
                        .checkType("SENDER_KYC")
                        .passed(false)
                        .riskLevel(RiskLevel.HIGH)
                        .failureReasons(List.of("Sender KYC check error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performRecipientVerification(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                RecipientInformation recipient = request.getRecipient();
                
                // Basic recipient information validation
                boolean hasRequiredInfo = recipient.getFullName() != null &&
                                        !recipient.getFullName().trim().isEmpty() &&
                                        recipient.getAddress() != null;
                
                // Enhanced verification for high-risk countries
                if (HIGH_RISK_COUNTRIES.contains(request.getRecipientCountry())) {
                    hasRequiredInfo = hasRequiredInfo &&
                                    recipient.getDateOfBirth() != null &&
                                    recipient.getIdNumber() != null;
                }
                
                return CheckResult.builder()
                        .checkType("RECIPIENT_VERIFICATION")
                        .passed(hasRequiredInfo)
                        .riskLevel(hasRequiredInfo ? RiskLevel.LOW : RiskLevel.MEDIUM)
                        .details(Map.of(
                            "has_name", String.valueOf(recipient.getFullName() != null),
                            "has_address", String.valueOf(recipient.getAddress() != null),
                            "has_id", String.valueOf(recipient.getIdNumber() != null)
                        ))
                        .failureReasons(hasRequiredInfo ? Collections.emptyList() :
                                      List.of("Insufficient recipient information"))
                        .build();
                
            } catch (Exception e) {
                log.error("Recipient verification failed", e);
                return CheckResult.builder()
                        .checkType("RECIPIENT_VERIFICATION")
                        .passed(false)
                        .riskLevel(RiskLevel.MEDIUM)
                        .failureReasons(List.of("Recipient verification error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performPEPScreening(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PEPCheckResult pepResult = pepScreeningService.performCheck(
                        PEPCheckRequest.builder()
                                .senderName(request.getSenderName())
                                .recipientName(request.getRecipient().getFullName())
                                .senderCountry(request.getSenderCountry())
                                .recipientCountry(request.getRecipientCountry())
                                .build()
                );
                
                return CheckResult.builder()
                        .checkType("PEP_SCREENING")
                        .passed(pepResult.isPassed())
                        .riskLevel(pepResult.getRiskLevel())
                        .details(pepResult.getMatchDetails())
                        .failureReasons(pepResult.getFailureReasons())
                        .build();
                
            } catch (Exception e) {
                log.error("PEP screening failed", e);
                return CheckResult.builder()
                        .checkType("PEP_SCREENING")
                        .passed(false)
                        .riskLevel(RiskLevel.HIGH)
                        .failureReasons(List.of("PEP screening error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performSourceOfFundsCheck(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate source of funds declaration
                boolean hasValidSource = request.getSourceOfFunds() != null &&
                                       !request.getSourceOfFunds().trim().isEmpty() &&
                                       isValidSourceOfFunds(request.getSourceOfFunds());
                
                // For high amounts, require additional documentation
                boolean requiresDocumentation = request.getAmount().compareTo(new BigDecimal("25000")) > 0;
                boolean hasDocumentation = !requiresDocumentation || 
                                         (request.getSourceOfFundsDocuments() != null && 
                                          !request.getSourceOfFundsDocuments().isEmpty());
                
                boolean passed = hasValidSource && hasDocumentation;
                
                return CheckResult.builder()
                        .checkType("SOURCE_OF_FUNDS")
                        .passed(passed)
                        .riskLevel(passed ? RiskLevel.LOW : RiskLevel.MEDIUM)
                        .details(Map.of(
                            "source_of_funds", String.valueOf(request.getSourceOfFunds()),
                            "requires_documentation", String.valueOf(requiresDocumentation),
                            "has_documentation", String.valueOf(hasDocumentation)
                        ))
                        .failureReasons(passed ? Collections.emptyList() :
                                      List.of("Invalid or insufficient source of funds information"))
                        .build();
                
            } catch (Exception e) {
                log.error("Source of funds check failed", e);
                return CheckResult.builder()
                        .checkType("SOURCE_OF_FUNDS")
                        .passed(false)
                        .riskLevel(RiskLevel.MEDIUM)
                        .failureReasons(List.of("Source of funds check error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performPurposeValidation(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean validPurpose = isValidPurposeCode(request.getPurposeCode()) &&
                                     isPurposeConsistent(request);
                
                return CheckResult.builder()
                        .checkType("PURPOSE_VALIDATION")
                        .passed(validPurpose)
                        .riskLevel(validPurpose ? RiskLevel.LOW : RiskLevel.MEDIUM)
                        .details(Map.of(
                            "purpose_code", request.getPurposeCode(),
                            "purpose_description", String.valueOf(request.getPurposeDescription())
                        ))
                        .failureReasons(validPurpose ? Collections.emptyList() :
                                      List.of("Invalid or inconsistent purpose of remittance"))
                        .build();
                
            } catch (Exception e) {
                log.error("Purpose validation failed", e);
                return CheckResult.builder()
                        .checkType("PURPOSE_VALIDATION")
                        .passed(false)
                        .riskLevel(RiskLevel.MEDIUM)
                        .failureReasons(List.of("Purpose validation error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private CompletableFuture<CheckResult> performEnhancedDueDiligence(ComplianceCheckRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Enhanced due diligence for high-risk transfers
                List<String> eddFindings = new ArrayList<>();
                
                // Check transaction frequency
                TransferPattern pattern = analyzeUserTransferPattern(request.getUserId());
                if (pattern.isHighFrequency()) {
                    eddFindings.add("High transaction frequency detected");
                }
                
                // Check amount patterns
                if (pattern.hasUnusualAmountPattern(request.getAmount())) {
                    eddFindings.add("Unusual transaction amount pattern");
                }
                
                // Check recipient patterns
                if (pattern.hasNewRecipientPattern(request.getRecipientCountry())) {
                    eddFindings.add("New recipient country for user");
                }
                
                boolean passed = eddFindings.isEmpty();
                
                return CheckResult.builder()
                        .checkType("ENHANCED_DUE_DILIGENCE")
                        .passed(passed)
                        .riskLevel(passed ? RiskLevel.LOW : RiskLevel.HIGH)
                        .details(Map.of("findings", String.join("; ", eddFindings)))
                        .failureReasons(passed ? Collections.emptyList() : eddFindings)
                        .build();
                
            } catch (Exception e) {
                log.error("Enhanced due diligence failed", e);
                return CheckResult.builder()
                        .checkType("ENHANCED_DUE_DILIGENCE")
                        .passed(false)
                        .riskLevel(RiskLevel.HIGH)
                        .failureReasons(List.of("Enhanced due diligence error: " + e.getMessage()))
                        .build();
            }
        }, complianceExecutor);
    }
    
    private ComplianceCheckResult aggregateResults(
            String checkId, 
            List<CheckResult> results, 
            Set<ComplianceRequirement> requiredChecks) {
        
        List<String> failureReasons = new ArrayList<>();
        RiskLevel maxRiskLevel = RiskLevel.LOW;
        Map<String, Object> allDetails = new HashMap<>();
        
        boolean allPassed = true;
        
        for (CheckResult result : results) {
            if (!result.isPassed()) {
                allPassed = false;
                failureReasons.addAll(result.getFailureReasons());
            }
            
            if (result.getRiskLevel().ordinal() > maxRiskLevel.ordinal()) {
                maxRiskLevel = result.getRiskLevel();
            }
            
            if (result.getDetails() != null) {
                allDetails.putAll(result.getDetails());
            }
        }
        
        return ComplianceCheckResult.builder()
                .checkId(checkId)
                .passed(allPassed)
                .riskLevel(maxRiskLevel)
                .requiredChecks(requiredChecks)
                .completedChecks(results.stream()
                        .map(CheckResult::getCheckType)
                        .collect(Collectors.toSet()))
                .failureReasons(failureReasons)
                .details(allDetails)
                .checkTimestamp(Instant.now())
                .build();
    }
    
    // Additional helper methods
    
    private boolean isHighRiskTransfer(ComplianceCheckRequest request) {
        return HIGH_RISK_COUNTRIES.contains(request.getRecipientCountry()) ||
               HIGH_RISK_COUNTRIES.contains(request.getSenderCountry()) ||
               request.getAmount().compareTo(new BigDecimal("10000")) > 0 ||
               isHighRiskPurpose(request.getPurposeCode());
    }
    
    private BigDecimal getAMLThreshold(String country) {
        return AML_REPORTING_THRESHOLDS.getOrDefault(country, 
               AML_REPORTING_THRESHOLDS.get("DEFAULT"));
    }
    
    private boolean isEUCountry(String countryCode) {
        Set<String> euCountries = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE"
        );
        return euCountries.contains(countryCode);
    }
    
    private boolean isExoticCurrency(String currency) {
        Set<String> majorCurrencies = Set.of("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD");
        return !majorCurrencies.contains(currency);
    }
    
    private boolean isHighRiskPurpose(String purposeCode) {
        Set<String> highRiskPurposes = Set.of(
            "INVESTMENT", "LOAN_PAYMENT", "CHARITY", "GIFT", "OTHER"
        );
        return highRiskPurposes.contains(purposeCode);
    }
    
    private boolean isValidSourceOfFunds(String sourceOfFunds) {
        Set<String> validSources = Set.of(
            "SALARY", "BUSINESS_INCOME", "INVESTMENT_INCOME", "PENSION",
            "SAVINGS", "INHERITANCE", "GIFT", "LOAN", "SALE_OF_PROPERTY"
        );
        return validSources.contains(sourceOfFunds);
    }
    
    private boolean isValidPurposeCode(String purposeCode) {
        Set<String> validPurposes = Set.of(
            "FAMILY_SUPPORT", "EDUCATION", "MEDICAL", "BUSINESS",
            "INVESTMENT", "LOAN_PAYMENT", "CHARITY", "GIFT", "OTHER"
        );
        return validPurposes.contains(purposeCode);
    }
    
    private boolean isPurposeConsistent(ComplianceCheckRequest request) {
        // Check if purpose is consistent with amount and recipient relationship
        String purpose = request.getPurposeCode();
        BigDecimal amount = request.getAmount();
        
        // Business logic to validate purpose consistency
        if ("FAMILY_SUPPORT".equals(purpose) && amount.compareTo(new BigDecimal("50000")) > 0) {
            return false; // Unusually high for family support
        }
        
        if ("EDUCATION".equals(purpose) && amount.compareTo(new BigDecimal("100000")) > 0) {
            return false; // Unusually high for education
        }
        
        return true;
    }
    
    private TransferPattern analyzeUserTransferPattern(String userId) {
        // This would analyze historical transfer patterns for the user
        // For now, return a default pattern
        return new TransferPattern(userId);
    }
    
    private boolean isFirstTimeToCountry(String userId, String country) {
        // Check if this is the user's first transfer to this country
        return complianceRepository.countByUserIdAndRecipientCountry(userId, country) == 0;
    }
    
    private boolean hasRecentComplianceIssues(String userId) {
        // Check for recent compliance issues for this user
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return complianceRepository.countFailedChecksByUserIdSince(userId, thirtyDaysAgo) > 0;
    }
}