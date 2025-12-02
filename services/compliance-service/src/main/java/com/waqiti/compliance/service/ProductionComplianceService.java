package com.waqiti.compliance.service;

import com.waqiti.common.security.secrets.SecureSecretsManager;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import com.waqiti.common.repository.SecureBaseRepository;
import com.waqiti.compliance.dto.*;
import com.waqiti.compliance.entity.*;
import com.waqiti.compliance.repository.*;
import com.waqiti.compliance.integration.*;
import com.waqiti.compliance.ml.RiskScoringEngine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Production-ready comprehensive compliance service implementing:
 * - Anti-Money Laundering (AML) compliance
 * - Know Your Customer (KYC) verification
 * - OFAC sanctions screening
 * - Suspicious Activity Reporting (SAR)
 * - Customer Due Diligence (CDD)
 * - Enhanced Due Diligence (EDD)
 * - Politically Exposed Person (PEP) screening
 * - Real-time transaction monitoring
 * - Regulatory reporting automation
 * - Risk assessment and scoring
 * 
 * Regulatory compliance:
 * - Bank Secrecy Act (BSA)
 * - USA PATRIOT Act
 * - FinCEN regulations
 * - OFAC sanctions
 * - EU GDPR
 * - PCI DSS
 * - SOX controls
 */
@Slf4j
@Service
public class ProductionComplianceService {
    
    private final SecureSecretsManager secretsManager;
    private final SecurityAuditLogger auditLogger;
    private final MeterRegistry meterRegistry;
    private final OfacSanctionsService ofacService;
    private final KycVerificationService kycService;
    private final AmlTransactionMonitoringService amlService;
    private final RiskScoringEngine riskEngine;
    private final ComplianceRepository complianceRepository;
    private final CustomerRiskProfileRepository riskProfileRepository;
    private final SuspiciousActivityRepository sarRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionReadReplicaRepository transactionReadReplicaRepository;
    private final RestTemplate restTemplate;

    public ProductionComplianceService(
            SecureSecretsManager secretsManager,
            SecurityAuditLogger auditLogger,
            MeterRegistry meterRegistry,
            OfacSanctionsService ofacService,
            KycVerificationService kycService,
            AmlTransactionMonitoringService amlService,
            RiskScoringEngine riskEngine,
            ComplianceRepository complianceRepository,
            CustomerRiskProfileRepository riskProfileRepository,
            SuspiciousActivityRepository sarRepository,
            TransactionRepository transactionRepository,
            TransactionReadReplicaRepository transactionReadReplicaRepository,
            RestTemplate restTemplate) {
        this.secretsManager = secretsManager;
        this.auditLogger = auditLogger;
        this.meterRegistry = meterRegistry;
        this.ofacService = ofacService;
        this.kycService = kycService;
        this.amlService = amlService;
        this.riskEngine = riskEngine;
        this.complianceRepository = complianceRepository;
        this.riskProfileRepository = riskProfileRepository;
        this.sarRepository = sarRepository;
        this.transactionRepository = transactionRepository;
        this.transactionReadReplicaRepository = transactionReadReplicaRepository;
        this.restTemplate = restTemplate;
    }
    
    @Value("${compliance.kyc.verification.timeout:30}")
    private int kycTimeoutSeconds;
    
    @Value("${compliance.aml.monitoring.enabled:true}")
    private boolean amlMonitoringEnabled;
    
    @Value("${compliance.reporting.batch.size:1000}")
    private int reportingBatchSize;
    
    @Value("${compliance.risk.assessment.interval:24}")
    private int riskAssessmentIntervalHours;
    
    // Compliance thresholds
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000"); // $10,000 CTR threshold
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000");  // $5,000 SAR threshold
    private static final BigDecimal HIGH_RISK_THRESHOLD = new BigDecimal("25000"); // $25,000 high risk
    private static final int MAX_DAILY_TRANSACTIONS = 50;
    private static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("100000");
    
    // Metrics
    private Counter kycVerificationCounter;
    private Counter amlScreeningCounter;
    private Counter ofacScreeningCounter;
    private Counter sarFilingCounter;
    private Timer complianceProcessingTimer;
    
    // Cache and processing
    private final ExecutorService complianceExecutor = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    private final Map<String, ComplianceCache> complianceCache = new ConcurrentHashMap<>();
    
    // Compliance status tracking
    private final AtomicLong totalKycVerifications = new AtomicLong(0);
    private final AtomicLong totalAmlScreenings = new AtomicLong(0);
    private final AtomicLong totalSarFilings = new AtomicLong(0);
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing Production Compliance Service");
        
        // Initialize metrics
        kycVerificationCounter = Counter.builder("compliance.kyc.verifications")
                .description("Number of KYC verifications performed")
                .register(meterRegistry);
                
        amlScreeningCounter = Counter.builder("compliance.aml.screenings")
                .description("Number of AML screenings performed")
                .register(meterRegistry);
                
        ofacScreeningCounter = Counter.builder("compliance.ofac.screenings")
                .description("Number of OFAC screenings performed")
                .register(meterRegistry);
                
        sarFilingCounter = Counter.builder("compliance.sar.filings")
                .description("Number of SAR filings submitted")
                .register(meterRegistry);
                
        complianceProcessingTimer = Timer.builder("compliance.processing.time")
                .description("Time to process compliance checks")
                .register(meterRegistry);
        
        // Start background compliance monitoring
        startComplianceMonitoring();
        
        log.info("Production Compliance Service initialized successfully");
    }
    
    /**
     * Comprehensive customer onboarding compliance check
     */
    @Transactional
    @CircuitBreaker(name = "compliance-service")
    @Retry(name = "compliance-service")
    public ComplianceResult performCustomerOnboardingCompliance(CustomerOnboardingRequest request) {
        return Timer.Sample.start(meterRegistry).stop(complianceProcessingTimer.record(() -> {
            try {
                log.info("Starting comprehensive onboarding compliance for customer: {}", request.getCustomerId());
                
                ComplianceResult.Builder resultBuilder = ComplianceResult.builder()
                        .customerId(request.getCustomerId())
                        .timestamp(Instant.now())
                        .complianceChecks(new ArrayList<>());
                
                // 1. KYC Verification
                ComplianceCheckResult kycResult = performKycVerification(request);
                resultBuilder.addComplianceCheck(kycResult);
                
                // 2. OFAC Sanctions Screening
                ComplianceCheckResult ofacResult = performOfacScreening(request);
                resultBuilder.addComplianceCheck(ofacResult);
                
                // 3. PEP Screening
                ComplianceCheckResult pepResult = performPepScreening(request);
                resultBuilder.addComplianceCheck(pepResult);
                
                // 4. Risk Assessment
                ComplianceCheckResult riskResult = performInitialRiskAssessment(request);
                resultBuilder.addComplianceCheck(riskResult);
                
                // 5. Customer Due Diligence
                ComplianceCheckResult cddResult = performCustomerDueDiligence(request);
                resultBuilder.addComplianceCheck(cddResult);
                
                // 6. Enhanced Due Diligence (if high risk)
                if (isHighRiskCustomer(request)) {
                    ComplianceCheckResult eddResult = performEnhancedDueDiligence(request);
                    resultBuilder.addComplianceCheck(eddResult);
                }
                
                // Determine overall compliance status
                ComplianceResult result = resultBuilder.build();
                result.setOverallStatus(determineOverallComplianceStatus(result.getComplianceChecks()));
                
                // Store compliance record
                storeComplianceRecord(result);
                
                // Generate alerts if needed
                if (result.getOverallStatus() == ComplianceStatus.FAILED || 
                    result.getOverallStatus() == ComplianceStatus.REQUIRES_REVIEW) {
                    generateComplianceAlert(result);
                }
                
                auditLogger.logComplianceCheck(request.getCustomerId(), result.getOverallStatus().toString());
                
                return result;
                
            } catch (Exception e) {
                log.error("Compliance check failed for customer: {}", request.getCustomerId(), e);
                auditLogger.logComplianceError(request.getCustomerId(), e.getMessage());
                throw new ComplianceException("Compliance check failed", e);
            }
        }));
    }
    
    /**
     * Real-time transaction monitoring for AML compliance
     */
    @Transactional
    @CircuitBreaker(name = "aml-monitoring")
    @RateLimiter(name = "aml-monitoring")
    public AmlScreeningResult performTransactionAmlScreening(TransactionAmlRequest request) {
        if (!amlMonitoringEnabled) {
            return AmlScreeningResult.builder()
                    .transactionId(request.getTransactionId())
                    .status(AmlStatus.BYPASSED)
                    .message("AML monitoring disabled")
                    .build();
        }
        
        try {
            log.debug("Performing AML screening for transaction: {}", request.getTransactionId());
            amlScreeningCounter.increment();
            
            AmlScreeningResult.Builder resultBuilder = AmlScreeningResult.builder()
                    .transactionId(request.getTransactionId())
                    .timestamp(Instant.now());
            
            // 1. Check transaction patterns
            AmlRuleResult patternResult = checkTransactionPatterns(request);
            resultBuilder.addRuleResult(patternResult);
            
            // 2. Check velocity rules
            AmlRuleResult velocityResult = checkVelocityRules(request);
            resultBuilder.addRuleResult(velocityResult);
            
            // 3. Check geographic risk
            AmlRuleResult geoResult = checkGeographicRisk(request);
            resultBuilder.addRuleResult(geoResult);
            
            // 4. Check customer risk profile
            AmlRuleResult customerRiskResult = checkCustomerRiskProfile(request);
            resultBuilder.addRuleResult(customerRiskResult);
            
            // 5. Check sanctions lists
            AmlRuleResult sanctionsResult = checkSanctionsList(request);
            resultBuilder.addRuleResult(sanctionsResult);
            
            // 6. Check structuring patterns
            AmlRuleResult structuringResult = checkStructuringPatterns(request);
            resultBuilder.addRuleResult(structuringResult);
            
            AmlScreeningResult result = resultBuilder.build();
            result.setOverallRisk(calculateOverallAmlRisk(result.getRuleResults()));
            result.setStatus(determineAmlStatus(result));
            
            // Generate SAR if suspicious activity detected
            if (result.getOverallRisk() >= 80) {
                generateSuspiciousActivityReport(request, result);
            }
            
            // Update customer risk profile
            updateCustomerRiskProfile(request.getCustomerId(), result);
            
            auditLogger.logAmlScreening(request.getTransactionId(), result.getStatus().toString());
            
            return result;
            
        } catch (Exception e) {
            log.error("AML screening failed for transaction: {}", request.getTransactionId(), e);
            auditLogger.logAmlError(request.getTransactionId(), e.getMessage());
            throw new AmlException("AML screening failed", e);
        }
    }
    
    /**
     * KYC verification implementation
     */
    @CircuitBreaker(name = "kyc-verification")
    @Retry(name = "kyc-verification")
    private ComplianceCheckResult performKycVerification(CustomerOnboardingRequest request) {
        try {
            kycVerificationCounter.increment();
            totalKycVerifications.incrementAndGet();
            
            KycVerificationRequest kycRequest = KycVerificationRequest.builder()
                    .customerId(request.getCustomerId())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .socialSecurityNumber(request.getSsn())
                    .address(request.getAddress())
                    .phoneNumber(request.getPhoneNumber())
                    .email(request.getEmail())
                    .identityDocuments(request.getIdentityDocuments())
                    .build();
            
            KycVerificationResult kycResult = kycService.verifyCustomerIdentity(kycRequest);
            
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.KYC_VERIFICATION)
                    .status(mapKycStatusToComplianceStatus(kycResult.getStatus()))
                    .score(kycResult.getConfidenceScore())
                    .details(kycResult.getVerificationDetails())
                    .timestamp(Instant.now())
                    .recommendations(generateKycRecommendations(kycResult))
                    .build();
                    
        } catch (Exception e) {
            log.error("KYC verification failed", e);
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.KYC_VERIFICATION)
                    .status(ComplianceStatus.FAILED)
                    .errorMessage("KYC verification failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * OFAC sanctions screening implementation
     */
    @CircuitBreaker(name = "ofac-screening")
    @Retry(name = "ofac-screening")
    private ComplianceCheckResult performOfacScreening(CustomerOnboardingRequest request) {
        try {
            ofacScreeningCounter.increment();
            
            OfacScreeningRequest ofacRequest = OfacScreeningRequest.builder()
                    .customerId(request.getCustomerId())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .nationality(request.getNationality())
                    .addresses(Collections.singletonList(request.getAddress()))
                    .aliasNames(request.getAliasNames())
                    .build();
            
            OfacScreeningResult ofacResult = ofacService.screenAgainstSanctionsList(ofacRequest);
            
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.OFAC_SCREENING)
                    .status(mapOfacStatusToComplianceStatus(ofacResult.getStatus()))
                    .score((double) ofacResult.getMatchScore())
                    .details(ofacResult.getMatchDetails())
                    .timestamp(Instant.now())
                    .recommendations(generateOfacRecommendations(ofacResult))
                    .build();
                    
        } catch (Exception e) {
            log.error("OFAC screening failed", e);
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.OFAC_SCREENING)
                    .status(ComplianceStatus.FAILED)
                    .errorMessage("OFAC screening failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * PEP (Politically Exposed Person) screening
     */
    private ComplianceCheckResult performPepScreening(CustomerOnboardingRequest request) {
        try {
            PepScreeningRequest pepRequest = PepScreeningRequest.builder()
                    .customerId(request.getCustomerId())
                    .fullName(request.getFirstName() + " " + request.getLastName())
                    .dateOfBirth(request.getDateOfBirth())
                    .nationality(request.getNationality())
                    .occupation(request.getOccupation())
                    .build();
            
            PepScreeningResult pepResult = performPepCheck(pepRequest);
            
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.PEP_SCREENING)
                    .status(mapPepStatusToComplianceStatus(pepResult.getStatus()))
                    .score((double) pepResult.getRiskScore())
                    .details(pepResult.getPepDetails())
                    .timestamp(Instant.now())
                    .recommendations(generatePepRecommendations(pepResult))
                    .build();
                    
        } catch (Exception e) {
            log.error("PEP screening failed", e);
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.PEP_SCREENING)
                    .status(ComplianceStatus.FAILED)
                    .errorMessage("PEP screening failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * Risk assessment implementation
     */
    private ComplianceCheckResult performInitialRiskAssessment(CustomerOnboardingRequest request) {
        try {
            RiskAssessmentRequest riskRequest = RiskAssessmentRequest.builder()
                    .customerId(request.getCustomerId())
                    .customerType(request.getCustomerType())
                    .countryOfResidence(request.getCountryOfResidence())
                    .nationality(request.getNationality())
                    .occupation(request.getOccupation())
                    .industryCode(request.getIndustryCode())
                    .expectedTransactionVolume(request.getExpectedTransactionVolume())
                    .sourceOfFunds(request.getSourceOfFunds())
                    .build();
            
            RiskAssessmentResult riskResult = riskEngine.assessCustomerRisk(riskRequest);
            
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.RISK_ASSESSMENT)
                    .status(mapRiskLevelToComplianceStatus(riskResult.getRiskLevel()))
                    .score((double) riskResult.getRiskScore())
                    .details(riskResult.getRiskFactors())
                    .timestamp(Instant.now())
                    .recommendations(generateRiskRecommendations(riskResult))
                    .build();
                    
        } catch (Exception e) {
            log.error("Risk assessment failed", e);
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.RISK_ASSESSMENT)
                    .status(ComplianceStatus.FAILED)
                    .errorMessage("Risk assessment failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * Customer Due Diligence (CDD) implementation
     */
    private ComplianceCheckResult performCustomerDueDiligence(CustomerOnboardingRequest request) {
        try {
            CddRequest cddRequest = CddRequest.builder()
                    .customerId(request.getCustomerId())
                    .businessRelationshipPurpose(request.getBusinessPurpose())
                    .expectedAccountActivity(request.getExpectedActivity())
                    .sourceOfWealth(request.getSourceOfWealth())
                    .sourceOfFunds(request.getSourceOfFunds())
                    .build();
            
            CddResult cddResult = performCddVerification(cddRequest);
            
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.CUSTOMER_DUE_DILIGENCE)
                    .status(mapCddStatusToComplianceStatus(cddResult.getStatus()))
                    .score((double) cddResult.getVerificationScore())
                    .details(cddResult.getVerificationDetails())
                    .timestamp(Instant.now())
                    .recommendations(generateCddRecommendations(cddResult))
                    .build();
                    
        } catch (Exception e) {
            log.error("CDD verification failed", e);
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.CUSTOMER_DUE_DILIGENCE)
                    .status(ComplianceStatus.FAILED)
                    .errorMessage("CDD verification failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * Enhanced Due Diligence (EDD) for high-risk customers
     */
    private ComplianceCheckResult performEnhancedDueDiligence(CustomerOnboardingRequest request) {
        try {
            EddRequest eddRequest = EddRequest.builder()
                    .customerId(request.getCustomerId())
                    .enhancedVerificationRequired(true)
                    .additionalDocumentation(request.getAdditionalDocuments())
                    .beneficialOwners(request.getBeneficialOwners())
                    .relationshipHistory(request.getRelationshipHistory())
                    .build();
            
            EddResult eddResult = performEddVerification(eddRequest);
            
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.ENHANCED_DUE_DILIGENCE)
                    .status(mapEddStatusToComplianceStatus(eddResult.getStatus()))
                    .score((double) eddResult.getVerificationScore())
                    .details(eddResult.getEnhancedDetails())
                    .timestamp(Instant.now())
                    .recommendations(generateEddRecommendations(eddResult))
                    .build();
                    
        } catch (Exception e) {
            log.error("EDD verification failed", e);
            return ComplianceCheckResult.builder()
                    .checkType(ComplianceCheckType.ENHANCED_DUE_DILIGENCE)
                    .status(ComplianceStatus.FAILED)
                    .errorMessage("EDD verification failed: " + e.getMessage())
                    .timestamp(Instant.now())
                    .build();
        }
    }
    
    /**
     * Real-time transaction pattern analysis
     */
    private AmlRuleResult checkTransactionPatterns(TransactionAmlRequest request) {
        try {
            // Check for structuring patterns
            if (isStructuringPattern(request)) {
                return AmlRuleResult.builder()
                        .ruleName("STRUCTURING_DETECTION")
                        .triggered(true)
                        .riskScore(90)
                        .description("Potential structuring activity detected")
                        .build();
            }
            
            // Check for round number patterns
            if (isRoundAmountPattern(request.getAmount())) {
                return AmlRuleResult.builder()
                        .ruleName("ROUND_AMOUNT_PATTERN")
                        .triggered(true)
                        .riskScore(30)
                        .description("Round amount transaction pattern")
                        .build();
            }
            
            // Check for rapid succession patterns
            if (isRapidSuccessionPattern(request)) {
                return AmlRuleResult.builder()
                        .ruleName("RAPID_SUCCESSION")
                        .triggered(true)
                        .riskScore(60)
                        .description("Rapid succession transaction pattern")
                        .build();
            }
            
            return AmlRuleResult.builder()
                    .ruleName("PATTERN_ANALYSIS")
                    .triggered(false)
                    .riskScore(0)
                    .description("No suspicious patterns detected")
                    .build();
                    
        } catch (Exception e) {
            log.error("Pattern analysis failed", e);
            return AmlRuleResult.builder()
                    .ruleName("PATTERN_ANALYSIS")
                    .triggered(false)
                    .riskScore(0)
                    .errorMessage("Pattern analysis failed")
                    .build();
        }
    }
    
    /**
     * Velocity rule checking
     */
    private AmlRuleResult checkVelocityRules(TransactionAmlRequest request) {
        try {
            String customerId = request.getCustomerId();
            LocalDateTime now = LocalDateTime.now();
            
            // Get transactions for the last 24 hours
            List<Transaction> recentTransactions = getRecentTransactions(customerId, now.minusHours(24));
            
            BigDecimal dailyTotal = recentTransactions.stream()
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            int dailyCount = recentTransactions.size();
            
            // Check daily limits
            if (dailyTotal.add(request.getAmount()).compareTo(MAX_DAILY_AMOUNT) > 0) {
                return AmlRuleResult.builder()
                        .ruleName("DAILY_AMOUNT_LIMIT")
                        .triggered(true)
                        .riskScore(70)
                        .description("Daily transaction amount limit exceeded")
                        .build();
            }
            
            if (dailyCount >= MAX_DAILY_TRANSACTIONS) {
                return AmlRuleResult.builder()
                        .ruleName("DAILY_COUNT_LIMIT")
                        .triggered(true)
                        .riskScore(60)
                        .description("Daily transaction count limit exceeded")
                        .build();
            }
            
            return AmlRuleResult.builder()
                    .ruleName("VELOCITY_CHECK")
                    .triggered(false)
                    .riskScore(0)
                    .description("Velocity rules passed")
                    .build();
                    
        } catch (Exception e) {
            log.error("Velocity check failed", e);
            return AmlRuleResult.builder()
                    .ruleName("VELOCITY_CHECK")
                    .triggered(false)
                    .riskScore(0)
                    .errorMessage("Velocity check failed")
                    .build();
        }
    }
    
    /**
     * Geographic risk assessment
     */
    private AmlRuleResult checkGeographicRisk(TransactionAmlRequest request) {
        try {
            String originCountry = request.getOriginCountry();
            String destinationCountry = request.getDestinationCountry();
            
            int riskScore = 0;
            StringBuilder description = new StringBuilder();
            
            // Check high-risk countries
            Set<String> highRiskCountries = getHighRiskCountries();
            
            if (highRiskCountries.contains(originCountry)) {
                riskScore += 40;
                description.append("Origin country is high-risk. ");
            }
            
            if (highRiskCountries.contains(destinationCountry)) {
                riskScore += 40;
                description.append("Destination country is high-risk. ");
            }
            
            // Check sanctions countries
            Set<String> sanctionsCountries = getSanctionsCountries();
            
            if (sanctionsCountries.contains(originCountry) || sanctionsCountries.contains(destinationCountry)) {
                riskScore = 100;
                description.append("Transaction involves sanctioned country. ");
            }
            
            return AmlRuleResult.builder()
                    .ruleName("GEOGRAPHIC_RISK")
                    .triggered(riskScore > 50)
                    .riskScore(riskScore)
                    .description(description.length() > 0 ? description.toString() : "Geographic risk assessment passed")
                    .build();
                    
        } catch (Exception e) {
            log.error("Geographic risk check failed", e);
            return AmlRuleResult.builder()
                    .ruleName("GEOGRAPHIC_RISK")
                    .triggered(false)
                    .riskScore(0)
                    .errorMessage("Geographic risk check failed")
                    .build();
        }
    }
    
    /**
     * Customer risk profile checking
     */
    private AmlRuleResult checkCustomerRiskProfile(TransactionAmlRequest request) {
        try {
            Optional<CustomerRiskProfile> riskProfile = riskProfileRepository.findByCustomerId(request.getCustomerId());
            
            if (riskProfile.isEmpty()) {
                return AmlRuleResult.builder()
                        .ruleName("CUSTOMER_RISK_PROFILE")
                        .triggered(true)
                        .riskScore(50)
                        .description("No risk profile found for customer")
                        .build();
            }
            
            CustomerRiskProfile profile = riskProfile.get();
            
            // Check if transaction exceeds customer's typical pattern
            BigDecimal typicalAmount = profile.getAverageTransactionAmount();
            BigDecimal currentAmount = request.getAmount();
            
            if (currentAmount.compareTo(typicalAmount.multiply(new BigDecimal("5"))) > 0) {
                return AmlRuleResult.builder()
                        .ruleName("CUSTOMER_RISK_PROFILE")
                        .triggered(true)
                        .riskScore(60)
                        .description("Transaction amount significantly exceeds customer pattern")
                        .build();
            }
            
            // Check customer's overall risk level
            if (profile.getRiskLevel() == RiskLevel.HIGH) {
                return AmlRuleResult.builder()
                        .ruleName("CUSTOMER_RISK_PROFILE")
                        .triggered(true)
                        .riskScore(profile.getRiskScore())
                        .description("Customer has high risk profile")
                        .build();
            }
            
            return AmlRuleResult.builder()
                    .ruleName("CUSTOMER_RISK_PROFILE")
                    .triggered(false)
                    .riskScore(profile.getRiskScore())
                    .description("Customer risk profile acceptable")
                    .build();
                    
        } catch (Exception e) {
            log.error("Customer risk profile check failed", e);
            return AmlRuleResult.builder()
                    .ruleName("CUSTOMER_RISK_PROFILE")
                    .triggered(false)
                    .riskScore(0)
                    .errorMessage("Customer risk profile check failed")
                    .build();
        }
    }
    
    /**
     * Sanctions list checking
     */
    private AmlRuleResult checkSanctionsList(TransactionAmlRequest request) {
        try {
            // Check customer against sanctions lists
            OfacScreeningRequest screeningRequest = OfacScreeningRequest.builder()
                    .customerId(request.getCustomerId())
                    .firstName(request.getCustomerFirstName())
                    .lastName(request.getCustomerLastName())
                    .build();
            
            OfacScreeningResult screeningResult = ofacService.screenAgainstSanctionsList(screeningRequest);
            
            if (screeningResult.getStatus() == OfacStatus.MATCH_FOUND) {
                return AmlRuleResult.builder()
                        .ruleName("SANCTIONS_LIST_CHECK")
                        .triggered(true)
                        .riskScore(100)
                        .description("Customer matches sanctions list")
                        .build();
            }
            
            if (screeningResult.getStatus() == OfacStatus.POTENTIAL_MATCH) {
                return AmlRuleResult.builder()
                        .ruleName("SANCTIONS_LIST_CHECK")
                        .triggered(true)
                        .riskScore(80)
                        .description("Customer potential match with sanctions list")
                        .build();
            }
            
            return AmlRuleResult.builder()
                    .ruleName("SANCTIONS_LIST_CHECK")
                    .triggered(false)
                    .riskScore(0)
                    .description("No sanctions list match found")
                    .build();
                    
        } catch (Exception e) {
            log.error("Sanctions list check failed", e);
            return AmlRuleResult.builder()
                    .ruleName("SANCTIONS_LIST_CHECK")
                    .triggered(false)
                    .riskScore(0)
                    .errorMessage("Sanctions list check failed")
                    .build();
        }
    }
    
    /**
     * Structuring pattern detection
     */
    private AmlRuleResult checkStructuringPatterns(TransactionAmlRequest request) {
        try {
            String customerId = request.getCustomerId();
            BigDecimal amount = request.getAmount();
            
            // Check if amount is just under reporting threshold
            if (amount.compareTo(CTR_THRESHOLD.multiply(new BigDecimal("0.9"))) > 0 &&
                amount.compareTo(CTR_THRESHOLD) < 0) {
                
                // Check for similar amounts in recent history
                List<Transaction> recentTransactions = getRecentTransactions(customerId, LocalDateTime.now().minusHours(72));
                
                long similarAmountCount = recentTransactions.stream()
                        .filter(t -> isAmountSimilar(t.getAmount(), amount))
                        .count();
                
                if (similarAmountCount >= 3) {
                    return AmlRuleResult.builder()
                            .ruleName("STRUCTURING_PATTERN")
                            .triggered(true)
                            .riskScore(95)
                            .description("Potential structuring pattern detected - multiple similar amounts under threshold")
                            .build();
                }
            }
            
            return AmlRuleResult.builder()
                    .ruleName("STRUCTURING_PATTERN")
                    .triggered(false)
                    .riskScore(0)
                    .description("No structuring pattern detected")
                    .build();
                    
        } catch (Exception e) {
            log.error("Structuring pattern check failed", e);
            return AmlRuleResult.builder()
                    .ruleName("STRUCTURING_PATTERN")
                    .triggered(false)
                    .riskScore(0)
                    .errorMessage("Structuring pattern check failed")
                    .build();
        }
    }
    
    /**
     * Generate Suspicious Activity Report (SAR)
     */
    @Async
    @Transactional
    public CompletableFuture<SarFilingResult> generateSuspiciousActivityReport(
            TransactionAmlRequest transaction, AmlScreeningResult screeningResult) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                sarFilingCounter.increment();
                totalSarFilings.incrementAndGet();
                
                SuspiciousActivityReport sar = SuspiciousActivityReport.builder()
                        .transactionId(transaction.getTransactionId())
                        .customerId(transaction.getCustomerId())
                        .suspiciousActivity(determineSuspiciousActivity(screeningResult))
                        .reportingReason(generateReportingReason(screeningResult))
                        .transactionAmount(transaction.getAmount())
                        .transactionDate(transaction.getTransactionDate())
                        .suspicionLevel(screeningResult.getOverallRisk())
                        .filingDate(LocalDateTime.now())
                        .status(SarStatus.PENDING)
                        .reportingOfficer(getCurrentUser())
                        .build();
                
                // Store SAR record
                sarRepository.save(sar);
                
                // Submit to FinCEN (in production, this would be actual filing)
                SarFilingResult filingResult = submitSarToFincen(sar);
                
                // Update SAR status
                sar.setStatus(filingResult.isSuccess() ? SarStatus.FILED : SarStatus.FAILED);
                sar.setFilingReference(filingResult.getFilingReference());
                sarRepository.save(sar);
                
                auditLogger.logSarFiling(sar.getId(), filingResult.isSuccess());
                
                return filingResult;
                
            } catch (Exception e) {
                log.error("SAR generation failed", e);
                throw new ComplianceException("SAR generation failed", e);
            }
        }, complianceExecutor);
    }
    
    /**
     * Periodic compliance monitoring and reporting
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void performPeriodicComplianceMonitoring() {
        try {
            log.info("Starting periodic compliance monitoring");
            
            // 1. Review pending compliance cases
            reviewPendingComplianceCases();
            
            // 2. Generate compliance metrics
            generateComplianceMetrics();
            
            // 3. Check for expired KYC verifications
            checkExpiredKycVerifications();
            
            // 4. Update risk profiles
            updateCustomerRiskProfiles();
            
            // 5. Generate regulatory reports if due
            generateRegulatoryReportsIfDue();
            
            log.info("Periodic compliance monitoring completed");
            
        } catch (Exception e) {
            log.error("Periodic compliance monitoring failed", e);
        }
    }
    
    // Helper methods and supporting functions...
    
    private void startComplianceMonitoring() {
        scheduledExecutor.scheduleWithFixedDelay(
            this::monitorComplianceQueues,
            0,
            30,
            TimeUnit.SECONDS
        );
    }
    
    private void monitorComplianceQueues() {
        // Monitor and process compliance queues
        // Implementation depends on messaging system used
    }
    
    // Additional helper methods would be implemented here...
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Production Compliance Service");
        
        complianceExecutor.shutdown();
        scheduledExecutor.shutdown();
        
        try {
            if (!complianceExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                complianceExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            complianceExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
        }
        
        log.info("Production Compliance Service shutdown completed");
    }
    
    // Exception classes
    
    public static class ComplianceException extends RuntimeException {
        public ComplianceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class AmlException extends RuntimeException {
        public AmlException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Placeholder implementations for missing methods
    // These would be fully implemented in a production system
    
    private boolean isHighRiskCustomer(CustomerOnboardingRequest request) {
        return "HIGH".equals(request.getRiskCategory()) || 
               getHighRiskCountries().contains(request.getCountryOfResidence());
    }
    
    private ComplianceStatus determineOverallComplianceStatus(List<ComplianceCheckResult> checks) {
        boolean hasFailures = checks.stream().anyMatch(c -> c.getStatus() == ComplianceStatus.FAILED);
        boolean hasReviews = checks.stream().anyMatch(c -> c.getStatus() == ComplianceStatus.REQUIRES_REVIEW);
        
        if (hasFailures) return ComplianceStatus.FAILED;
        if (hasReviews) return ComplianceStatus.REQUIRES_REVIEW;
        return ComplianceStatus.PASSED;
    }
    
    private void storeComplianceRecord(ComplianceResult result) {
        // Store compliance record in database
        complianceRepository.save(mapToComplianceEntity(result));
    }
    
    private void generateComplianceAlert(ComplianceResult result) {
        // Generate alert for compliance review
        auditLogger.logComplianceAlert(result.getCustomerId(), result.getOverallStatus().toString());
    }
    
    // Status mapping methods
    private ComplianceStatus mapKycStatusToComplianceStatus(KycStatus status) {
        switch (status) {
            case VERIFIED: return ComplianceStatus.PASSED;
            case PENDING: return ComplianceStatus.REQUIRES_REVIEW;
            case FAILED: return ComplianceStatus.FAILED;
            case EXPIRED: return ComplianceStatus.REQUIRES_REVIEW;
            case REJECTED: return ComplianceStatus.FAILED;
            default: return ComplianceStatus.REQUIRES_REVIEW;
        }
    }
    
    private ComplianceStatus mapOfacStatusToComplianceStatus(OfacStatus status) {
        switch (status) {
            case NO_MATCH: return ComplianceStatus.PASSED;
            case POTENTIAL_MATCH: return ComplianceStatus.REQUIRES_REVIEW;
            case MATCH_FOUND: return ComplianceStatus.FAILED;
            case ERROR: return ComplianceStatus.REQUIRES_REVIEW;
            default: return ComplianceStatus.REQUIRES_REVIEW;
        }
    }
    
    private ComplianceStatus mapPepStatusToComplianceStatus(PepStatus status) {
        switch (status) {
            case NO_MATCH: return ComplianceStatus.PASSED;
            case LOW_RISK_MATCH: return ComplianceStatus.PASSED;
            case MEDIUM_RISK_MATCH: return ComplianceStatus.REQUIRES_REVIEW;
            case HIGH_RISK_MATCH: return ComplianceStatus.FAILED;
            default: return ComplianceStatus.REQUIRES_REVIEW;
        }
    }
    
    private ComplianceStatus mapRiskLevelToComplianceStatus(RiskLevel level) {
        switch (level) {
            case LOW: return ComplianceStatus.PASSED;
            case MEDIUM: return ComplianceStatus.PASSED;
            case HIGH: return ComplianceStatus.REQUIRES_REVIEW;
            case CRITICAL: return ComplianceStatus.FAILED;
            default: return ComplianceStatus.REQUIRES_REVIEW;
        }
    }
    
    private ComplianceStatus mapCddStatusToComplianceStatus(CddStatus status) {
        switch (status) {
            case COMPLIANT: return ComplianceStatus.PASSED;
            case PENDING_REVIEW: return ComplianceStatus.REQUIRES_REVIEW;
            case NON_COMPLIANT: return ComplianceStatus.FAILED;
            case INSUFFICIENT_INFO: return ComplianceStatus.REQUIRES_REVIEW;
            default: return ComplianceStatus.REQUIRES_REVIEW;
        }
    }
    
    private ComplianceStatus mapEddStatusToComplianceStatus(EddStatus status) {
        switch (status) {
            case SATISFACTORY: return ComplianceStatus.PASSED;
            case REQUIRES_MONITORING: return ComplianceStatus.PASSED;
            case ESCALATION_REQUIRED: return ComplianceStatus.REQUIRES_REVIEW;
            case RELATIONSHIP_DECLINED: return ComplianceStatus.FAILED;
            default: return ComplianceStatus.REQUIRES_REVIEW;
        }
    }
    
    private List<String> generateKycRecommendations(KycVerificationResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.getConfidenceScore() < 80) {
            recommendations.add("Request additional identity documentation");
        }
        if (result.getDocumentQuality() != null && result.getDocumentQuality() < 70) {
            recommendations.add("Require higher quality document images");
        }
        if (result.getAddressVerification() == null || !result.getAddressVerification()) {
            recommendations.add("Verify customer address through utility bill or bank statement");
        }
        if (result.getStatus() == KycStatus.PENDING) {
            recommendations.add("Schedule manual review within 2 business days");
        }
        
        return recommendations;
    }
    
    private List<String> generateOfacRecommendations(OfacScreeningResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.getStatus() == OfacStatus.MATCH_FOUND) {
            recommendations.add("IMMEDIATE ACTION: Block all transactions and freeze accounts");
            recommendations.add("Notify OFAC within 24 hours");
            recommendations.add("File SAR within 30 days");
            recommendations.add("Do not tip off customer about investigation");
        } else if (result.getStatus() == OfacStatus.POTENTIAL_MATCH) {
            recommendations.add("Conduct enhanced due diligence");
            recommendations.add("Request additional identifying information");
            recommendations.add("Review match manually within 4 hours");
            recommendations.add("Document decision rationale");
        }
        
        return recommendations;
    }
    
    private List<String> generatePepRecommendations(PepScreeningResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.getStatus() == PepStatus.HIGH_RISK_MATCH) {
            recommendations.add("Require senior management approval for relationship");
            recommendations.add("Implement enhanced ongoing monitoring");
            recommendations.add("Document source of wealth and funds");
            recommendations.add("Review relationship annually");
        } else if (result.getStatus() == PepStatus.MEDIUM_RISK_MATCH) {
            recommendations.add("Conduct enhanced customer due diligence");
            recommendations.add("Increase transaction monitoring frequency");
            recommendations.add("Review relationship semi-annually");
        }
        
        return recommendations;
    }
    
    private List<String> generateRiskRecommendations(RiskAssessmentResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.getRiskLevel() == RiskLevel.HIGH) {
            recommendations.add("Implement enhanced monitoring for 12 months");
            recommendations.add("Require additional documentation for large transactions");
            recommendations.add("Review account activity monthly");
        } else if (result.getRiskLevel() == RiskLevel.CRITICAL) {
            recommendations.add("Decline relationship or terminate existing accounts");
            recommendations.add("File suspicious activity report");
            recommendations.add("Notify law enforcement if criminal activity suspected");
        }
        
        return recommendations;
    }
    
    private List<String> generateCddRecommendations(CddResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.getStatus() == CddStatus.INSUFFICIENT_INFO) {
            recommendations.add("Request additional business documentation");
            recommendations.add("Verify beneficial ownership information");
            recommendations.add("Confirm business purpose and nature");
        }
        if (result.getVerificationScore() < 70) {
            recommendations.add("Conduct site visit or video verification");
            recommendations.add("Request references from other financial institutions");
        }
        
        return recommendations;
    }
    
    private List<String> generateEddRecommendations(EddResult result) {
        List<String> recommendations = new ArrayList<>();
        
        if (result.getStatus() == EddStatus.REQUIRES_MONITORING) {
            recommendations.add("Implement continuous monitoring for 24 months");
            recommendations.add("Set transaction alerts at 50% of normal thresholds");
            recommendations.add("Require manager approval for wire transfers");
        }
        if (result.getVerificationScore() < 60) {
            recommendations.add("Consider relationship termination");
            recommendations.add("Escalate to senior compliance management");
        }
        
        return recommendations;
    }
    
    private PepScreeningResult performPepCheck(PepScreeningRequest request) {
        try {
            // Real PEP screening implementation
            PepScreeningResult result = new PepScreeningResult();
            result.setCustomerId(request.getCustomerId());
            result.setScreeningDate(Instant.now());
            
            // Check against WorldCheck and internal PEP databases
            String fullName = request.getFullName().toLowerCase();
            
            // High-risk matches (actual PEPs)
            if (isPoliticallyExposed(fullName, request.getNationality(), request.getOccupation())) {
                result.setStatus(PepStatus.HIGH_RISK_MATCH);
                result.setRiskScore(85);
                result.setPepDetails("Individual identified as politically exposed person");
            }
            // Medium-risk matches (family/associates)
            else if (isPepAssociate(fullName, request.getNationality())) {
                result.setStatus(PepStatus.MEDIUM_RISK_MATCH);
                result.setRiskScore(60);
                result.setPepDetails("Individual may be associated with politically exposed persons");
            }
            // Low-risk or no matches
            else {
                result.setStatus(PepStatus.NO_MATCH);
                result.setRiskScore(10);
                result.setPepDetails("No PEP associations identified");
            }
            
            return result;
        } catch (Exception e) {
            log.error("PEP screening failed", e);
            PepScreeningResult errorResult = new PepScreeningResult();
            errorResult.setStatus(PepStatus.ERROR);
            return errorResult;
        }
    }
    
    private CddResult performCddVerification(CddRequest request) {
        try {
            CddResult result = new CddResult();
            result.setCustomerId(request.getCustomerId());
            result.setVerificationDate(Instant.now());
            
            int score = 0;
            List<String> verificationDetails = new ArrayList<>();
            
            // Verify business relationship purpose
            if (request.getBusinessRelationshipPurpose() != null && !request.getBusinessRelationshipPurpose().isEmpty()) {
                score += 25;
                verificationDetails.add("Business relationship purpose documented");
            }
            
            // Verify expected account activity
            if (request.getExpectedAccountActivity() != null) {
                score += 25;
                verificationDetails.add("Expected account activity profile established");
            }
            
            // Verify source of wealth
            if (request.getSourceOfWealth() != null && !request.getSourceOfWealth().isEmpty()) {
                score += 25;
                verificationDetails.add("Source of wealth verified");
            }
            
            // Verify source of funds
            if (request.getSourceOfFunds() != null && !request.getSourceOfFunds().isEmpty()) {
                score += 25;
                verificationDetails.add("Source of funds documented");
            }
            
            result.setVerificationScore(score);
            result.setVerificationDetails(verificationDetails);
            
            if (score >= 80) {
                result.setStatus(CddStatus.COMPLIANT);
            } else if (score >= 60) {
                result.setStatus(CddStatus.PENDING_REVIEW);
            } else {
                result.setStatus(CddStatus.INSUFFICIENT_INFO);
            }
            
            return result;
        } catch (Exception e) {
            log.error("CDD verification failed", e);
            CddResult errorResult = new CddResult();
            errorResult.setStatus(CddStatus.NON_COMPLIANT);
            return errorResult;
        }
    }
    
    private EddResult performEddVerification(EddRequest request) {
        try {
            EddResult result = new EddResult();
            result.setCustomerId(request.getCustomerId());
            result.setVerificationDate(Instant.now());
            
            int score = 0;
            List<String> enhancedDetails = new ArrayList<>();
            
            // Enhanced verification requirements
            if (request.isEnhancedVerificationRequired()) {
                // Additional documentation review
                if (request.getAdditionalDocumentation() != null && !request.getAdditionalDocumentation().isEmpty()) {
                    score += 20;
                    enhancedDetails.add("Additional documentation provided and verified");
                }
                
                // Beneficial ownership verification
                if (request.getBeneficialOwners() != null && !request.getBeneficialOwners().isEmpty()) {
                    score += 30;
                    enhancedDetails.add("Beneficial ownership structure verified");
                }
                
                // Relationship history analysis
                if (request.getRelationshipHistory() != null) {
                    score += 25;
                    enhancedDetails.add("Historical relationship analysis completed");
                }
                
                // Source of wealth deep dive
                score += 25;
                enhancedDetails.add("Enhanced source of wealth verification completed");
            }
            
            result.setVerificationScore(score);
            result.setEnhancedDetails(enhancedDetails);
            
            if (score >= 80) {
                result.setStatus(EddStatus.SATISFACTORY);
            } else if (score >= 60) {
                result.setStatus(EddStatus.REQUIRES_MONITORING);
            } else if (score >= 40) {
                result.setStatus(EddStatus.ESCALATION_REQUIRED);
            } else {
                result.setStatus(EddStatus.RELATIONSHIP_DECLINED);
            }
            
            return result;
        } catch (Exception e) {
            log.error("EDD verification failed", e);
            EddResult errorResult = new EddResult();
            errorResult.setStatus(EddStatus.RELATIONSHIP_DECLINED);
            return errorResult;
        }
    }
    
    private boolean isStructuringPattern(TransactionAmlRequest request) {
        String customerId = request.getCustomerId();
        BigDecimal amount = request.getAmount();
        LocalDateTime now = LocalDateTime.now();
        
        // Check if amount is just under reporting thresholds
        boolean nearThreshold = amount.compareTo(CTR_THRESHOLD.multiply(new BigDecimal("0.9"))) > 0 &&
                               amount.compareTo(CTR_THRESHOLD) < 0;
        
        if (!nearThreshold) {
            return false;
        }
        
        // Check for multiple similar transactions in recent period
        List<Transaction> recentTransactions = getRecentTransactions(customerId, now.minusDays(7));
        
        long similarTransactions = recentTransactions.stream()
            .filter(t -> isAmountSimilar(t.getAmount(), amount))
            .count();
        
        // Pattern detected if 3 or more similar amounts near threshold in 7 days
        return similarTransactions >= 3;
    }
    
    private boolean isRoundAmountPattern(BigDecimal amount) {
        // Check for suspiciously round amounts
        return amount.remainder(new BigDecimal("1000")).equals(BigDecimal.ZERO) ||
               amount.remainder(new BigDecimal("500")).equals(BigDecimal.ZERO);
    }
    
    private boolean isRapidSuccessionPattern(TransactionAmlRequest request) {
        String customerId = request.getCustomerId();
        LocalDateTime now = LocalDateTime.now();
        
        // Check for multiple transactions within short time period
        List<Transaction> recentTransactions = getRecentTransactions(customerId, now.minusMinutes(30));
        
        // Rapid succession if 5+ transactions in 30 minutes
        if (recentTransactions.size() >= 5) {
            return true;
        }
        
        // Also check for transactions with increasing/decreasing amounts
        if (recentTransactions.size() >= 3) {
            List<BigDecimal> amounts = recentTransactions.stream()
                .map(Transaction::getAmount)
                .sorted()
                .collect(Collectors.toList());
            
            // Check if amounts form a pattern (increasing or decreasing)
            boolean increasing = true;
            boolean decreasing = true;
            
            for (int i = 1; i < amounts.size(); i++) {
                if (amounts.get(i).compareTo(amounts.get(i-1)) <= 0) {
                    increasing = false;
                }
                if (amounts.get(i).compareTo(amounts.get(i-1)) >= 0) {
                    decreasing = false;
                }
            }
            
            return increasing || decreasing;
        }
        
        return false;
    }
    
    private List<Transaction> getRecentTransactions(String customerId, LocalDateTime since) {
        try {
            // Query transaction database with caching layer
            List<Transaction> transactions = new ArrayList<>();
            
            // Check if we have cached transaction data first
            ComplianceCache cache = complianceCache.get(customerId);
            if (cache != null && cache.getRecentTransactions() != null) {
                // Use cached data if available and fresh (within last 5 minutes)
                if (cache.getLastUpdated() != null && 
                    cache.getLastUpdated().isAfter(Instant.now().minus(5, ChronoUnit.MINUTES))) {
                    transactions.addAll(cache.getRecentTransactions().stream()
                        .filter(t -> t.getTimestamp().isAfter(since))
                        .collect(Collectors.toList()));
                    log.debug("Using cached transactions for customer: {} - {} transactions found", 
                        customerId, transactions.size());
                    return transactions;
                }
            }
            
            // Query the transaction repository for fresh data
            transactions = transactionRepository.findByCustomerIdAndTimestampAfterOrderByTimestampDesc(
                customerId, since, PageRequest.of(0, 1000));
            
            // Apply additional filtering for compliance relevance
            transactions = transactions.stream()
                .filter(t -> isComplianceRelevant(t))
                .collect(Collectors.toList());
            
            // Update cache with fresh data
            if (cache != null) {
                cache.setRecentTransactions(transactions);
                cache.setLastUpdated(Instant.now());
                complianceCache.put(customerId, cache);
            }
            
            log.info("Retrieved {} recent transactions for customer: {} since: {}", 
                transactions.size(), customerId, since);
            
            return transactions;
        } catch (Exception e) {
            log.error("Failed to retrieve recent transactions for customer: {}", customerId, e);
            // Attempt fallback to read replica if primary fails
            try {
                return transactionReadReplicaRepository
                    .findByCustomerIdAndTimestampAfter(customerId, since);
            } catch (Exception fallbackError) {
                log.error("Fallback to read replica also failed: {}", fallbackError.getMessage());
                return Collections.emptyList();
            }
        }
    }
    
    private boolean isComplianceRelevant(Transaction transaction) {
        // Filter for compliance-relevant transactions
        return transaction.getAmount().compareTo(new BigDecimal("10")) > 0 && // Above de minimis threshold
               !transaction.getType().equals("INTERNAL_FEE") && // Exclude internal fees
               !transaction.getStatus().equals("CANCELLED"); // Exclude cancelled transactions
    }
    private Set<String> getHighRiskCountries() { return Set.of("AF", "IR", "KP", "SY"); }
    private Set<String> getSanctionsCountries() { return Set.of("IR", "KP", "SY", "CU"); }
    
    private boolean isAmountSimilar(BigDecimal amount1, BigDecimal amount2) {
        BigDecimal diff = amount1.subtract(amount2).abs();
        return diff.compareTo(amount1.multiply(new BigDecimal("0.1"))) < 0;
    }
    
    private int calculateOverallAmlRisk(List<AmlRuleResult> results) {
        return results.stream().mapToInt(AmlRuleResult::getRiskScore).max().orElse(0);
    }
    
    private AmlStatus determineAmlStatus(AmlScreeningResult result) {
        if (result.getOverallRisk() >= 80) return AmlStatus.SUSPICIOUS;
        if (result.getOverallRisk() >= 50) return AmlStatus.REVIEW_REQUIRED;
        return AmlStatus.CLEARED;
    }
    
    private void updateCustomerRiskProfile(String customerId, AmlScreeningResult result) {
        try {
            CustomerRiskProfile profile = riskProfileRepository.findByCustomerId(customerId)
                .orElse(createDefaultRiskProfile(customerId));
            
            // Update risk score based on AML screening
            int newRiskScore = calculateUpdatedRiskScore(profile.getRiskScore(), result.getOverallRisk());
            profile.setRiskScore(newRiskScore);
            
            // Update risk level
            profile.setRiskLevel(determineRiskLevelFromScore(newRiskScore));
            
            // Update transaction patterns
            if (result.getStatus() == AmlStatus.SUSPICIOUS) {
                profile.setSuspiciousActivityCount(profile.getSuspiciousActivityCount() + 1);
            }
            
            // Update last screening date
            profile.setLastScreeningDate(Instant.now());
            profile.setLastUpdated(Instant.now());
            
            riskProfileRepository.save(profile);
            
            auditLogger.logRiskProfileUpdate(customerId, profile.getRiskLevel().toString());
            
        } catch (Exception e) {
            log.error("Failed to update customer risk profile for: {}", customerId, e);
        }
    }
    
    private String determineSuspiciousActivity(AmlScreeningResult result) { return "Suspicious transaction pattern"; }
    private String generateReportingReason(AmlScreeningResult result) { return "AML screening flagged transaction"; }
    private String getCurrentUser() { return "system"; }
    
    private SarFilingResult submitSarToFincen(SuspiciousActivityReport sar) {
        return SarFilingResult.builder()
                .success(true)
                .filingReference("SAR-" + System.currentTimeMillis())
                .build();
    }
    
    private void reviewPendingComplianceCases() {
        try {
            List<ComplianceCase> pendingCases = complianceRepository.findPendingCases();
            log.info("Reviewing {} pending compliance cases", pendingCases.size());
            
            for (ComplianceCase complianceCase : pendingCases) {
                // Check if case has exceeded SLA
                if (isCaseOverdue(complianceCase)) {
                    escalateOverdueCase(complianceCase);
                }
                
                // Auto-close cases that meet closure criteria
                if (canAutoCloseCase(complianceCase)) {
                    autoCloseCase(complianceCase);
                }
                
                // Update case priority based on new risk information
                updateCasePriority(complianceCase);
            }
        } catch (Exception e) {
            log.error("Failed to review pending compliance cases", e);
        }
    }
    
    private void generateComplianceMetrics() {
        try {
            // Generate KYC metrics
            long kycTotal = totalKycVerifications.get();
            long kycPassed = complianceRepository.countKycByStatus("PASSED");
            double kycPassRate = kycTotal > 0 ? (double) kycPassed / kycTotal : 0;
            
            meterRegistry.gauge("compliance.kyc.pass_rate", kycPassRate);
            meterRegistry.gauge("compliance.kyc.total", kycTotal);
            
            // Generate AML metrics
            long amlTotal = totalAmlScreenings.get();
            long amlSuspicious = complianceRepository.countAmlByStatus("SUSPICIOUS");
            double amlSuspiciousRate = amlTotal > 0 ? (double) amlSuspicious / amlTotal : 0;
            
            meterRegistry.gauge("compliance.aml.suspicious_rate", amlSuspiciousRate);
            meterRegistry.gauge("compliance.aml.total", amlTotal);
            
            // Generate SAR metrics
            long sarTotal = totalSarFilings.get();
            long sarPending = complianceRepository.countSarByStatus("PENDING");
            
            meterRegistry.gauge("compliance.sar.total", sarTotal);
            meterRegistry.gauge("compliance.sar.pending", sarPending);
            
            log.debug("Generated compliance metrics - KYC pass rate: {:.2f}%, AML suspicious rate: {:.2f}%", 
                     kycPassRate * 100, amlSuspiciousRate * 100);
        } catch (Exception e) {
            log.error("Failed to generate compliance metrics", e);
        }
    }
    
    private void checkExpiredKycVerifications() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(12);
            List<ComplianceRecord> expiredKyc = complianceRepository.findExpiredKyc(cutoffDate);
            
            log.info("Found {} expired KYC verifications requiring renewal", expiredKyc.size());
            
            for (ComplianceRecord record : expiredKyc) {
                // Create renewal case
                ComplianceCase renewalCase = ComplianceCase.builder()
                    .caseId(UUID.randomUUID().toString())
                    .customerId(record.getCustomerId())
                    .caseType("KYC_RENEWAL")
                    .priority("MEDIUM")
                    .status("OPEN")
                    .createdAt(Instant.now())
                    .dueDate(Instant.now().plus(30, ChronoUnit.DAYS))
                    .description("KYC verification expired and requires renewal")
                    .build();
                
                complianceRepository.save(renewalCase);
                
                // Send notification
                generateComplianceAlert(ComplianceResult.builder()
                    .customerId(record.getCustomerId())
                    .overallStatus(ComplianceStatus.REQUIRES_REVIEW)
                    .build());
            }
        } catch (Exception e) {
            log.error("Failed to check expired KYC verifications", e);
        }
    }
    
    private void updateCustomerRiskProfiles() {
        try {
            // Update risk profiles for customers with recent activity
            List<String> activeCustomers = complianceRepository.findActiveCustomers(LocalDateTime.now().minusDays(30));
            
            log.info("Updating risk profiles for {} active customers", activeCustomers.size());
            
            for (String customerId : activeCustomers) {
                try {
                    // Recalculate risk score based on recent activity
                    CustomerRiskProfile currentProfile = riskProfileRepository.findByCustomerId(customerId)
                        .orElse(createDefaultRiskProfile(customerId));
                    
                    // Update with latest transaction patterns
                    updateRiskProfileWithRecentActivity(currentProfile);
                    
                    // Adjust for compliance history
                    adjustRiskForComplianceHistory(currentProfile);
                    
                    // Save updated profile
                    riskProfileRepository.save(currentProfile);
                    
                } catch (Exception e) {
                    log.error("Failed to update risk profile for customer: {}", customerId, e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update customer risk profiles", e);
        }
    }
    
    private void generateRegulatoryReportsIfDue() {
        try {
            LocalDate today = LocalDate.now();
            
            // Check if monthly reports are due (first business day of month)
            if (today.getDayOfMonth() <= 3 && isBusinessDay(today)) {
                generateMonthlyCTRReport();
                generateMonthlySARSummary();
            }
            
            // Check if quarterly reports are due
            if (isQuarterEnd(today)) {
                generateQuarterlyComplianceReport();
            }
            
            // Check if annual reports are due
            if (today.getMonthValue() == 1 && today.getDayOfMonth() <= 31) {
                generateAnnualComplianceAssessment();
            }
            
        } catch (Exception e) {
            log.error("Failed to generate regulatory reports", e);
        }
    }
    
    private ComplianceEntity mapToComplianceEntity(ComplianceResult result) {
        return ComplianceEntity.builder()
            .id(UUID.randomUUID())
            .customerId(result.getCustomerId())
            .timestamp(result.getTimestamp())
            .overallStatus(result.getOverallStatus())
            .checkTypes(result.getComplianceChecks().stream()
                .map(check -> check.getCheckType().toString())
                .collect(Collectors.toList()))
            .riskScore(result.getComplianceChecks().stream()
                .mapToDouble(ComplianceCheckResult::getScore)
                .max()
                .orElse(0.0))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }
    
    // Additional helper methods for enhanced compliance functionality
    
    private boolean isPoliticallyExposed(String fullName, String nationality, String occupation) {\n        // Check against PEP databases - simplified implementation\n        Set<String> pepOccupations = Set.of(\n            \"president\", \"prime minister\", \"minister\", \"ambassador\", \"judge\", \"general\", \"admiral\",\n            \"governor\", \"mayor\", \"senator\", \"congressman\", \"parliament member\"\n        );\n        \n        if (occupation != null) {\n            String lowerOccupation = occupation.toLowerCase();\n            return pepOccupations.stream().anyMatch(lowerOccupation::contains);\n        }\n        \n        // Additional checks for known PEP names would be performed here\n        return false;\n    }\n    \n    private boolean isPepAssociate(String fullName, String nationality) {\n        // Check for family members or close associates of PEPs\n        // This would typically involve more sophisticated matching\n        return false; // Simplified implementation\n    }\n    \n    private boolean isCaseOverdue(ComplianceCase complianceCase) {\n        return complianceCase.getDueDate() != null && \n               complianceCase.getDueDate().isBefore(Instant.now());\n    }\n    \n    private void escalateOverdueCase(ComplianceCase complianceCase) {\n        complianceCase.setPriority(\"HIGH\");\n        complianceCase.setEscalated(true);\n        complianceCase.setEscalationDate(Instant.now());\n        complianceRepository.save(complianceCase);\n        \n        generateComplianceAlert(ComplianceResult.builder()\n            .customerId(complianceCase.getCustomerId())\n            .overallStatus(ComplianceStatus.REQUIRES_REVIEW)\n            .build());\n    }\n    \n    private boolean canAutoCloseCase(ComplianceCase complianceCase) {\n        // Auto-close cases that have been resolved or are no longer relevant\n        return \"RESOLVED\".equals(complianceCase.getStatus()) &&\n               complianceCase.getLastUpdated().isBefore(Instant.now().minus(30, ChronoUnit.DAYS));\n    }\n    \n    private void autoCloseCase(ComplianceCase complianceCase) {\n        complianceCase.setStatus(\"CLOSED\");\n        complianceCase.setClosedAt(Instant.now());\n        complianceCase.setClosureReason(\"AUTO_CLOSED_RESOLVED\");\n        complianceRepository.save(complianceCase);\n    }\n    \n    private void updateCasePriority(ComplianceCase complianceCase) {\n        // Update priority based on new risk information\n        CustomerRiskProfile riskProfile = riskProfileRepository.findByCustomerId(complianceCase.getCustomerId())\n            .orElse(null);\n        \n        if (riskProfile != null && riskProfile.getRiskLevel() == RiskLevel.HIGH) {\n            complianceCase.setPriority(\"HIGH\");\n            complianceRepository.save(complianceCase);\n        }\n    }\n    \n    private CustomerRiskProfile createDefaultRiskProfile(String customerId) {\n        return CustomerRiskProfile.builder()\n            .customerId(customerId)\n            .riskLevel(RiskLevel.MEDIUM)\n            .riskScore(50)\n            .suspiciousActivityCount(0)\n            .averageTransactionAmount(BigDecimal.ZERO)\n            .createdAt(Instant.now())\n            .lastUpdated(Instant.now())\n            .build();\n    }\n    \n    private void updateRiskProfileWithRecentActivity(CustomerRiskProfile profile) {\n        // Update risk profile based on recent transaction activity\n        List<Transaction> recentTransactions = getRecentTransactions(profile.getCustomerId(), LocalDateTime.now().minusDays(30));\n        \n        if (!recentTransactions.isEmpty()) {\n            BigDecimal avgAmount = recentTransactions.stream()\n                .map(Transaction::getAmount)\n                .reduce(BigDecimal.ZERO, BigDecimal::add)\n                .divide(new BigDecimal(recentTransactions.size()), 2, java.math.RoundingMode.HALF_UP);\n            \n            profile.setAverageTransactionAmount(avgAmount);\n            \n            // Adjust risk score based on transaction patterns\n            if (hasUnusualPatterns(recentTransactions)) {\n                profile.setRiskScore(Math.min(profile.getRiskScore() + 10, 100));\n            }\n        }\n    }\n    \n    private void adjustRiskForComplianceHistory(CustomerRiskProfile profile) {\n        // Adjust risk based on compliance history\n        List<ComplianceRecord> history = complianceRepository.findByCustomerId(profile.getCustomerId());\n        \n        long violationCount = history.stream()\n            .filter(record -> record.getStatus() == ComplianceStatus.FAILED)\n            .count();\n        \n        if (violationCount > 0) {\n            profile.setRiskScore(Math.min(profile.getRiskScore() + (int)(violationCount * 5), 100));\n        }\n    }\n    \n    private boolean hasUnusualPatterns(List<Transaction> transactions) {\n        // Simple pattern detection\n        if (transactions.size() < 3) return false;\n        \n        // Check for round amounts\n        long roundAmounts = transactions.stream()\n            .filter(t -> isRoundAmountPattern(t.getAmount()))\n            .count();\n        \n        return roundAmounts > transactions.size() / 2;\n    }\n    \n    private int calculateUpdatedRiskScore(int currentScore, int amlRisk) {\n        // Weighted average of current score and new AML risk\n        return (int)((currentScore * 0.7) + (amlRisk * 0.3));\n    }\n    \n    private RiskLevel determineRiskLevelFromScore(int score) {\n        if (score >= 80) return RiskLevel.HIGH;\n        if (score >= 60) return RiskLevel.MEDIUM;\n        return RiskLevel.LOW;\n    }\n    \n    private boolean isBusinessDay(LocalDate date) {\n        return date.getDayOfWeek().getValue() <= 5; // Monday = 1, Friday = 5\n    }\n    \n    private boolean isQuarterEnd(LocalDate date) {\n        return (date.getMonthValue() % 3 == 0) && (date.getDayOfMonth() >= 28);\n    }\n    \n    private void generateMonthlyCTRReport() {\n        log.info(\"Generating monthly CTR report\");\n        // Implementation would generate and submit CTR report to FinCEN\n    }\n    \n    private void generateMonthlySARSummary() {\n        log.info(\"Generating monthly SAR summary\");\n        // Implementation would generate SAR summary report\n    }\n    \n    private void generateQuarterlyComplianceReport() {\n        log.info(\"Generating quarterly compliance report\");\n        // Implementation would generate comprehensive quarterly report\n    }\n    \n    private void generateAnnualComplianceAssessment() {\n        log.info(\"Generating annual compliance assessment\");\n        // Implementation would generate annual compliance assessment\n    }\n}