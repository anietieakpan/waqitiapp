package com.waqiti.lending.consumer;

import com.waqiti.common.events.LoanApplicationSubmittedEvent;
import com.waqiti.lending.service.CreditUnderwritingService;
import com.waqiti.lending.service.IncomeVerificationService;
import com.waqiti.lending.service.RiskAssessmentService;
import com.waqiti.lending.service.ComplianceService;
import com.waqiti.lending.service.NotificationService;
import com.waqiti.lending.repository.ProcessedEventRepository;
import com.waqiti.lending.repository.LoanApplicationRepository;
import com.waqiti.lending.model.ProcessedEvent;
import com.waqiti.lending.model.LoanApplication;
import com.waqiti.lending.model.ApplicationStatus;
import com.waqiti.lending.model.RiskTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for LoanApplicationSubmittedEvent - Critical for lending workflow
 * Handles credit underwriting, income verification, risk assessment, and compliance
 * ZERO TOLERANCE: All loan applications must go through complete underwriting process
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoanApplicationSubmittedEventConsumer {
    
    private final CreditUnderwritingService creditUnderwritingService;
    private final IncomeVerificationService incomeVerificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal LARGE_LOAN_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal JUMBO_LOAN_THRESHOLD = new BigDecimal("500000");
    private static final int MINIMUM_CREDIT_SCORE = 580;
    
    @KafkaListener(
        topics = "lending.application.submitted",
        groupId = "lending-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for lending decisions
    public void handleLoanApplicationSubmitted(LoanApplicationSubmittedEvent event) {
        log.info("Processing loan application: {} - Applicant: {} - Amount: ${} - Type: {}", 
            event.getApplicationId(), event.getApplicantId(), event.getLoanAmount(), event.getLoanType());
        
        // IDEMPOTENCY CHECK - Prevent duplicate processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Loan application already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Get loan application record
            LoanApplication application = loanApplicationRepository.findById(event.getApplicationId())
                .orElseThrow(() -> new RuntimeException("Loan application not found: " + event.getApplicationId()));
            
            // STEP 1: Perform initial eligibility screening
            performInitialEligibilityScreening(application, event);
            
            // STEP 2: Execute comprehensive credit underwriting
            performCreditUnderwriting(application, event);
            
            // STEP 3: Verify income and employment
            verifyIncomeAndEmployment(application, event);
            
            // STEP 4: Assess debt-to-income ratio and affordability
            assessDebtToIncomeRatio(application, event);
            
            // STEP 5: Perform risk-based pricing analysis
            performRiskBasedPricing(application, event);
            
            // STEP 6: Execute TILA compliance and disclosure generation
            executeTILACompliance(application, event);
            
            // STEP 7: Perform anti-discrimination compliance (ECOA)
            performECOACompliance(application, event);
            
            // STEP 8: Execute automated decision engine
            executeAutomatedDecisionEngine(application, event);
            
            // STEP 9: Generate loan terms and conditions
            generateLoanTerms(application, event);
            
            // STEP 10: Create regulatory reporting entries
            createRegulatoryReporting(application, event);
            
            // STEP 11: Send applicant notifications and disclosures
            sendApplicantNotifications(application, event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("LoanApplicationSubmittedEvent")
                .processedAt(Instant.now())
                .applicationId(event.getApplicationId())
                .applicantId(event.getApplicantId())
                .loanAmount(event.getLoanAmount())
                .loanType(event.getLoanType())
                .creditScore(application.getCreditScore())
                .riskTier(application.getRiskTier())
                .decision(application.getDecision())
                .apr(application.getOfferedAPR())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed loan application: {} - Decision: {}, APR: {}%, Amount: ${}", 
                event.getApplicationId(), application.getDecision(), 
                application.getOfferedAPR(), application.getApprovedAmount());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process loan application: {}", 
                event.getApplicationId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Loan application processing failed", e);
        }
    }
    
    private void performInitialEligibilityScreening(LoanApplication application, LoanApplicationSubmittedEvent event) {
        List<String> eligibilityFlags = new ArrayList<>();
        
        // Check minimum age requirement (18+ for most states, 19+ for some)
        if (event.getApplicantAge() < 18) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("MINIMUM_AGE_REQUIREMENT");
            eligibilityFlags.add("UNDER_MINIMUM_AGE");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - applicant under minimum age: {}", 
                event.getApplicationId(), event.getApplicantAge());
            
            throw new RuntimeException("Applicant does not meet minimum age requirement");
        }
        
        // Check US citizenship or permanent residency
        if (!event.isUSCitizenOrResident()) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("CITIZENSHIP_REQUIREMENT");
            eligibilityFlags.add("NON_US_RESIDENT");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - citizenship requirement not met", 
                event.getApplicationId());
            
            throw new RuntimeException("Citizenship/residency requirement not met");
        }
        
        // Check state lending license compliance
        boolean stateLicensed = complianceService.checkStateLendingLicense(
            event.getApplicantState(),
            event.getLoanType(),
            event.getLoanAmount()
        );
        
        if (!stateLicensed) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("STATE_LICENSING");
            eligibilityFlags.add("STATE_NOT_LICENSED");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - not licensed in state: {}", 
                event.getApplicationId(), event.getApplicantState());
            
            throw new RuntimeException("Not licensed to lend in applicant's state");
        }
        
        // Check loan amount limits
        BigDecimal maxLoanAmount = complianceService.getMaxLoanAmount(
            event.getApplicantState(),
            event.getLoanType()
        );
        
        if (event.getLoanAmount().compareTo(maxLoanAmount) > 0) {
            application.addEligibilityFlag("EXCEEDS_MAX_LOAN_AMOUNT");
            eligibilityFlags.add("EXCEEDS_MAX_AMOUNT");
        }
        
        application.setEligibilityFlags(eligibilityFlags);
        application.setEligibilityScreeningCompleted(true);
        application.setEligibilityScreeningDate(LocalDateTime.now());
        
        loanApplicationRepository.save(application);
        
        log.info("Initial eligibility screening completed for application {}: Flags: {}", 
            event.getApplicationId(), eligibilityFlags.size());
    }
    
    private void performCreditUnderwriting(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Pull comprehensive credit report
        Map<String, Object> creditReport = creditUnderwritingService.pullCreditReport(
            event.getApplicantId(),
            event.getApplicantSSN(),
            event.getApplicationId()
        );
        
        application.setCreditReportData(creditReport);
        
        // Extract credit score (using middle score of three bureaus)
        int creditScore = creditUnderwritingService.calculateMiddleCreditScore(creditReport);
        application.setCreditScore(creditScore);
        
        // Check minimum credit score requirement
        if (creditScore < MINIMUM_CREDIT_SCORE) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("INSUFFICIENT_CREDIT_SCORE");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - credit score too low: {}", 
                event.getApplicationId(), creditScore);
            
            throw new RuntimeException("Credit score below minimum requirement");
        }
        
        // Analyze credit history depth
        int creditHistoryMonths = creditUnderwritingService.calculateCreditHistoryDepth(creditReport);
        application.setCreditHistoryMonths(creditHistoryMonths);
        
        if (creditHistoryMonths < 12) {
            application.addUnderwritingFlag("THIN_CREDIT_FILE");
        }
        
        // Check for recent bankruptcies
        boolean recentBankruptcy = creditUnderwritingService.checkRecentBankruptcy(creditReport);
        
        if (recentBankruptcy) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("RECENT_BANKRUPTCY");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - recent bankruptcy found", 
                event.getApplicationId());
            
            throw new RuntimeException("Recent bankruptcy on credit report");
        }
        
        // Analyze payment history
        double paymentHistoryScore = creditUnderwritingService.analyzePaymentHistory(creditReport);
        application.setPaymentHistoryScore(paymentHistoryScore);
        
        // Check for recent late payments
        int latePayments90Days = creditUnderwritingService.countLatePayments(creditReport, 90);
        application.setLatePayments90Days(latePayments90Days);
        
        if (latePayments90Days > 2) {
            application.addUnderwritingFlag("RECENT_LATE_PAYMENTS");
        }
        
        // Calculate credit utilization
        double creditUtilization = creditUnderwritingService.calculateCreditUtilization(creditReport);
        application.setCreditUtilization(creditUtilization);
        
        if (creditUtilization > 0.80) {
            application.addUnderwritingFlag("HIGH_CREDIT_UTILIZATION");
        }
        
        // Check for recent hard inquiries
        int hardInquiries6Months = creditUnderwritingService.countHardInquiries(creditReport, 180);
        application.setHardInquiries6Months(hardInquiries6Months);
        
        if (hardInquiries6Months > 6) {
            application.addUnderwritingFlag("EXCESSIVE_RECENT_INQUIRIES");
        }
        
        loanApplicationRepository.save(application);
        
        log.info("Credit underwriting completed for application {}: Score: {}, Utilization: {}%, History: {} months", 
            event.getApplicationId(), creditScore, creditUtilization * 100, creditHistoryMonths);
    }
    
    private void verifyIncomeAndEmployment(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Verify employment status
        Map<String, Object> employmentVerification = incomeVerificationService.verifyEmployment(
            event.getApplicantId(),
            event.getEmployerName(),
            event.getEmployerPhone(),
            event.getJobTitle(),
            event.getEmploymentStartDate()
        );
        
        application.setEmploymentVerificationData(employmentVerification);
        
        boolean employmentVerified = (Boolean) employmentVerification.get("verified");
        application.setEmploymentVerified(employmentVerified);
        
        if (!employmentVerified) {
            application.addUnderwritingFlag("EMPLOYMENT_NOT_VERIFIED");
            application.setRequiresManualReview(true);
        }
        
        // Verify stated income
        BigDecimal verifiedIncome = incomeVerificationService.verifyIncome(
            event.getApplicantId(),
            event.getStatedAnnualIncome(),
            event.getPayStubs(),
            event.getTaxReturns(),
            event.getBankStatements()
        );
        
        application.setVerifiedAnnualIncome(verifiedIncome);
        
        // Calculate income variance
        BigDecimal incomeVariance = event.getStatedAnnualIncome().subtract(verifiedIncome)
            .divide(event.getStatedAnnualIncome(), 4, RoundingMode.HALF_UP)
            .abs();
        
        application.setIncomeVariance(incomeVariance);
        
        if (incomeVariance.compareTo(new BigDecimal("0.10")) > 0) { // 10% variance threshold
            application.addUnderwritingFlag("INCOME_VARIANCE_HIGH");
            application.setRequiresManualReview(true);
        }
        
        // Check minimum income requirement
        BigDecimal minimumIncome = getMinimumIncomeRequirement(event.getLoanAmount(), event.getLoanType());
        
        if (verifiedIncome.compareTo(minimumIncome) < 0) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("INSUFFICIENT_INCOME");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - insufficient income: ${} vs required ${}", 
                event.getApplicationId(), verifiedIncome, minimumIncome);
            
            throw new RuntimeException("Verified income below minimum requirement");
        }
        
        // Verify additional income sources
        if (event.getOtherIncomeAmount() != null && event.getOtherIncomeAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal verifiedOtherIncome = incomeVerificationService.verifyOtherIncome(
                event.getApplicantId(),
                event.getOtherIncomeAmount(),
                event.getOtherIncomeSource(),
                event.getOtherIncomeDocuments()
            );
            
            application.setVerifiedOtherIncome(verifiedOtherIncome);
            
            // Add to total verified income
            application.setTotalVerifiedIncome(verifiedIncome.add(verifiedOtherIncome));
        } else {
            application.setTotalVerifiedIncome(verifiedIncome);
        }
        
        loanApplicationRepository.save(application);
        
        log.info("Income and employment verification completed for application {}: Income: ${}, Employment: {}", 
            event.getApplicationId(), application.getTotalVerifiedIncome(), employmentVerified);
    }
    
    private void assessDebtToIncomeRatio(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Calculate monthly gross income
        BigDecimal monthlyGrossIncome = application.getTotalVerifiedIncome()
            .divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        
        application.setMonthlyGrossIncome(monthlyGrossIncome);
        
        // Calculate existing monthly debt payments from credit report
        BigDecimal existingMonthlyDebt = creditUnderwritingService.calculateMonthlyDebtPayments(
            application.getCreditReportData()
        );
        
        application.setExistingMonthlyDebt(existingMonthlyDebt);
        
        // Calculate proposed loan payment
        BigDecimal monthlyLoanPayment = calculateMonthlyPayment(
            event.getLoanAmount(),
            event.getRequestedTermMonths(),
            estimateAPR(application.getCreditScore(), event.getLoanType())
        );
        
        application.setProposedMonthlyPayment(monthlyLoanPayment);
        
        // Calculate total monthly debt including new loan
        BigDecimal totalMonthlyDebt = existingMonthlyDebt.add(monthlyLoanPayment);
        application.setTotalMonthlyDebt(totalMonthlyDebt);
        
        // Calculate debt-to-income ratio
        BigDecimal dtiRatio = totalMonthlyDebt.divide(monthlyGrossIncome, 4, RoundingMode.HALF_UP);
        application.setDebtToIncomeRatio(dtiRatio);
        
        // Check DTI thresholds by loan type
        BigDecimal maxDTI = getMaxDebtToIncomeRatio(event.getLoanType());
        
        if (dtiRatio.compareTo(maxDTI) > 0) {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason("DEBT_TO_INCOME_TOO_HIGH");
            
            loanApplicationRepository.save(application);
            
            log.warn("Loan application {} rejected - DTI too high: {}% vs max {}%", 
                event.getApplicationId(), dtiRatio.multiply(new BigDecimal("100")), 
                maxDTI.multiply(new BigDecimal("100")));
            
            throw new RuntimeException("Debt-to-income ratio exceeds maximum allowed");
        }
        
        // Apply DTI-based risk adjustments
        if (dtiRatio.compareTo(new BigDecimal("0.40")) > 0) { // 40% threshold
            application.addUnderwritingFlag("HIGH_DTI_RATIO");
        }
        
        loanApplicationRepository.save(application);
        
        log.info("DTI assessment completed for application {}: DTI: {}%, Monthly income: ${}, Total debt: ${}", 
            event.getApplicationId(), dtiRatio.multiply(new BigDecimal("100")), 
            monthlyGrossIncome, totalMonthlyDebt);
    }
    
    private void performRiskBasedPricing(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Determine risk tier based on multiple factors
        RiskTier riskTier = riskAssessmentService.calculateRiskTier(
            application.getCreditScore(),
            application.getDebtToIncomeRatio(),
            application.getCreditUtilization(),
            application.getPaymentHistoryScore(),
            application.getUnderwritingFlags()
        );
        
        application.setRiskTier(riskTier);
        
        // Calculate risk-based APR
        BigDecimal baseAPR = getBaseAPR(event.getLoanType());
        BigDecimal riskAdjustment = getRiskAdjustment(riskTier);
        BigDecimal offeredAPR = baseAPR.add(riskAdjustment);
        
        application.setBaseAPR(baseAPR);
        application.setRiskAdjustment(riskAdjustment);
        application.setOfferedAPR(offeredAPR);
        
        // Apply competitive pricing adjustments
        BigDecimal marketAdjustment = riskAssessmentService.getCompetitivePricingAdjustment(
            event.getLoanType(),
            event.getLoanAmount(),
            application.getCreditScore()
        );
        
        application.setMarketAdjustment(marketAdjustment);
        application.setFinalAPR(offeredAPR.add(marketAdjustment));
        
        // Calculate loan profitability
        BigDecimal expectedProfit = riskAssessmentService.calculateExpectedProfit(
            event.getLoanAmount(),
            application.getFinalAPR(),
            event.getRequestedTermMonths(),
            riskTier
        );
        
        application.setExpectedProfit(expectedProfit);
        
        // Check minimum profitability threshold
        BigDecimal minimumProfit = event.getLoanAmount().multiply(new BigDecimal("0.05")); // 5% minimum
        
        if (expectedProfit.compareTo(minimumProfit) < 0) {
            application.addUnderwritingFlag("LOW_PROFITABILITY");
            application.setRequiresManualReview(true);
        }
        
        loanApplicationRepository.save(application);
        
        log.info("Risk-based pricing completed for application {}: Risk Tier: {}, APR: {}%, Profit: ${}", 
            event.getApplicationId(), riskTier, application.getFinalAPR(), expectedProfit);
    }
    
    private void executeTILACompliance(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Generate Truth in Lending Act (TILA) disclosures
        Map<String, Object> tilaDisclosures = complianceService.generateTILADisclosures(
            event.getApplicationId(),
            event.getLoanAmount(),
            application.getFinalAPR(),
            event.getRequestedTermMonths(),
            calculateFinanceCharge(event.getLoanAmount(), application.getFinalAPR(), event.getRequestedTermMonths()),
            calculateTotalPayments(event.getLoanAmount(), application.getFinalAPR(), event.getRequestedTermMonths())
        );
        
        application.setTilaDisclosures(tilaDisclosures);
        
        // Calculate right of rescission period (3 business days for certain loans)
        boolean rescissionApplicable = complianceService.isRescissionApplicable(
            event.getLoanType(),
            event.getLoanPurpose()
        );
        
        application.setRescissionApplicable(rescissionApplicable);
        
        if (rescissionApplicable) {
            LocalDateTime rescissionExpiry = complianceService.calculateRescissionExpiry(
                LocalDateTime.now()
            );
            application.setRescissionExpiry(rescissionExpiry);
        }
        
        // Generate required fee disclosures
        Map<String, BigDecimal> feeDisclosures = complianceService.generateFeeDisclosures(
            event.getLoanAmount(),
            event.getLoanType(),
            application.getRiskTier()
        );
        
        application.setFeeDisclosures(feeDisclosures);
        
        // Check state-specific disclosure requirements
        List<String> stateDisclosures = complianceService.getStateSpecificDisclosures(
            event.getApplicantState(),
            event.getLoanType(),
            event.getLoanAmount()
        );
        
        application.setStateDisclosures(stateDisclosures);
        
        loanApplicationRepository.save(application);
        
        log.info("TILA compliance completed for application {}: Rescission applicable: {}, State disclosures: {}", 
            event.getApplicationId(), rescissionApplicable, stateDisclosures.size());
    }
    
    private void performECOACompliance(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Equal Credit Opportunity Act (ECOA) compliance checks
        
        // Ensure no prohibited basis discrimination
        List<String> prohibitedFactors = Arrays.asList(
            "race", "color", "religion", "national_origin", "sex", 
            "marital_status", "age", "receipt_of_public_assistance"
        );
        
        // Document that decision is based only on creditworthiness factors
        Map<String, Object> decisionFactors = Map.of(
            "creditScore", application.getCreditScore(),
            "debtToIncomeRatio", application.getDebtToIncomeRatio(),
            "paymentHistory", application.getPaymentHistoryScore(),
            "creditUtilization", application.getCreditUtilization(),
            "verifiedIncome", application.getTotalVerifiedIncome(),
            "employmentVerified", application.isEmploymentVerified()
        );
        
        application.setDecisionFactors(decisionFactors);
        
        // Generate adverse action notice if required
        if (application.getStatus() == ApplicationStatus.REJECTED) {
            Map<String, Object> adverseActionNotice = complianceService.generateAdverseActionNotice(
                event.getApplicationId(),
                application.getRejectionReason(),
                application.getCreditScore(),
                application.getCreditReportData()
            );
            
            application.setAdverseActionNotice(adverseActionNotice);
        }
        
        // Document compliance with ECOA timing requirements
        application.setEcoaComplianceDate(LocalDateTime.now());
        application.setEcoaCompliant(true);
        
        loanApplicationRepository.save(application);
        
        log.info("ECOA compliance completed for application {}: Compliant: {}", 
            event.getApplicationId(), application.isEcoaCompliant());
    }
    
    private void executeAutomatedDecisionEngine(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Run automated decision engine with all collected data
        String decision = creditUnderwritingService.executeDecisionEngine(
            application.getCreditScore(),
            application.getDebtToIncomeRatio(),
            application.getTotalVerifiedIncome(),
            application.isEmploymentVerified(),
            application.getUnderwritingFlags(),
            application.getRiskTier(),
            event.getLoanAmount()
        );
        
        application.setDecision(decision);
        application.setDecisionDate(LocalDateTime.now());
        
        switch (decision) {
            case "APPROVED" -> {
                application.setStatus(ApplicationStatus.APPROVED);
                application.setApprovedAmount(event.getLoanAmount());
                application.setApprovedTermMonths(event.getRequestedTermMonths());
                application.setApprovedAPR(application.getFinalAPR());
            }
            case "CONDITIONAL_APPROVAL" -> {
                application.setStatus(ApplicationStatus.CONDITIONAL_APPROVAL);
                // Reduce loan amount for borderline cases
                BigDecimal reducedAmount = event.getLoanAmount().multiply(new BigDecimal("0.8"));
                application.setApprovedAmount(reducedAmount);
                application.setApprovedTermMonths(event.getRequestedTermMonths());
                application.setApprovedAPR(application.getFinalAPR().add(new BigDecimal("1.0"))); // +1% APR
            }
            case "REJECTED" -> {
                application.setStatus(ApplicationStatus.REJECTED);
                // Rejection reason should already be set by underwriting process
            }
            case "MANUAL_REVIEW" -> {
                application.setStatus(ApplicationStatus.MANUAL_REVIEW);
                application.setRequiresManualReview(true);
            }
        }
        
        loanApplicationRepository.save(application);
        
        log.info("Automated decision completed for application {}: Decision: {}, Amount: ${}", 
            event.getApplicationId(), decision, 
            application.getApprovedAmount() != null ? application.getApprovedAmount() : "N/A");
    }
    
    private void generateLoanTerms(LoanApplication application, LoanApplicationSubmittedEvent event) {
        if (application.getStatus() == ApplicationStatus.APPROVED || 
            application.getStatus() == ApplicationStatus.CONDITIONAL_APPROVAL) {
            
            // Generate comprehensive loan terms
            Map<String, Object> loanTerms = Map.of(
                "principalAmount", application.getApprovedAmount(),
                "apr", application.getApprovedAPR(),
                "termMonths", application.getApprovedTermMonths(),
                "monthlyPayment", calculateMonthlyPayment(
                    application.getApprovedAmount(),
                    application.getApprovedTermMonths(),
                    application.getApprovedAPR()
                ),
                "totalInterest", calculateTotalInterest(
                    application.getApprovedAmount(),
                    application.getApprovedAPR(),
                    application.getApprovedTermMonths()
                ),
                "totalPayments", calculateTotalPayments(
                    application.getApprovedAmount(),
                    application.getApprovedAPR(),
                    application.getApprovedTermMonths()
                ),
                "firstPaymentDate", LocalDateTime.now().plusMonths(1).toLocalDate(),
                "maturityDate", LocalDateTime.now().plusMonths(application.getApprovedTermMonths()).toLocalDate()
            );
            
            application.setLoanTerms(loanTerms);
            
            // Generate amortization schedule
            List<Map<String, Object>> amortizationSchedule = generateAmortizationSchedule(
                application.getApprovedAmount(),
                application.getApprovedAPR(),
                application.getApprovedTermMonths()
            );
            
            application.setAmortizationSchedule(amortizationSchedule);
        }
        
        loanApplicationRepository.save(application);
        
        log.info("Loan terms generated for application {}: Status: {}", 
            event.getApplicationId(), application.getStatus());
    }
    
    private void createRegulatoryReporting(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Create HMDA (Home Mortgage Disclosure Act) reporting for applicable loans
        if (isHMDAReportable(event.getLoanType(), event.getLoanPurpose())) {
            String hmdaRecordId = complianceService.createHMDARecord(
                event.getApplicationId(),
                event.getApplicantId(),
                event.getLoanAmount(),
                event.getLoanType(),
                event.getLoanPurpose(),
                application.getDecision(),
                application.getApprovedAPR()
            );
            
            application.setHmdaRecordId(hmdaRecordId);
        }
        
        // Create CRA (Community Reinvestment Act) data
        String craRecordId = complianceService.createCRARecord(
            event.getApplicationId(),
            event.getApplicantZipCode(),
            event.getLoanAmount(),
            application.getDecision()
        );
        
        application.setCraRecordId(craRecordId);
        
        loanApplicationRepository.save(application);
        
        log.info("Regulatory reporting created for application {}: HMDA: {}, CRA: {}", 
            event.getApplicationId(), application.getHmdaRecordId(), craRecordId);
    }
    
    private void sendApplicantNotifications(LoanApplication application, LoanApplicationSubmittedEvent event) {
        // Send application received confirmation
        notificationService.sendApplicationReceivedEmail(
            event.getApplicantId(),
            event.getApplicationId(),
            event.getLoanAmount(),
            event.getLoanType()
        );
        
        // Send decision notification
        switch (application.getStatus()) {
            case APPROVED -> {
                notificationService.sendApprovalNotification(
                    event.getApplicantId(),
                    application,
                    application.getLoanTerms(),
                    application.getTilaDisclosures()
                );
            }
            case CONDITIONAL_APPROVAL -> {
                notificationService.sendConditionalApprovalNotification(
                    event.getApplicantId(),
                    application,
                    application.getLoanTerms()
                );
            }
            case REJECTED -> {
                notificationService.sendRejectionNotification(
                    event.getApplicantId(),
                    application,
                    application.getAdverseActionNotice()
                );
            }
            case MANUAL_REVIEW -> {
                notificationService.sendManualReviewNotification(
                    event.getApplicantId(),
                    application
                );
            }
        }
        
        // Send TILA disclosures separately if approved
        if (application.getStatus() == ApplicationStatus.APPROVED) {
            notificationService.sendTILADisclosures(
                event.getApplicantId(),
                application.getTilaDisclosures(),
                application.isRescissionApplicable()
            );
        }
        
        log.info("Applicant notifications sent for application {}: Status: {}", 
            event.getApplicationId(), application.getStatus());
    }
    
    private BigDecimal getMinimumIncomeRequirement(BigDecimal loanAmount, String loanType) {
        // Different minimums by loan type
        return switch (loanType.toUpperCase()) {
            case "PERSONAL" -> new BigDecimal("25000");
            case "AUTO" -> new BigDecimal("20000");
            case "HOME_EQUITY" -> new BigDecimal("40000");
            case "MORTGAGE" -> loanAmount.multiply(new BigDecimal("0.28")); // 28% front-end DTI
            default -> new BigDecimal("30000");
        };
    }
    
    private BigDecimal getMaxDebtToIncomeRatio(String loanType) {
        return switch (loanType.toUpperCase()) {
            case "MORTGAGE" -> new BigDecimal("0.43"); // 43%
            case "HOME_EQUITY" -> new BigDecimal("0.45"); // 45%
            case "AUTO" -> new BigDecimal("0.40"); // 40%
            case "PERSONAL" -> new BigDecimal("0.35"); // 35%
            default -> new BigDecimal("0.36"); // 36%
        };
    }
    
    private BigDecimal estimateAPR(int creditScore, String loanType) {
        BigDecimal baseRate = getBaseAPR(loanType);
        
        if (creditScore >= 750) return baseRate.add(new BigDecimal("1.0"));
        if (creditScore >= 700) return baseRate.add(new BigDecimal("2.0"));
        if (creditScore >= 650) return baseRate.add(new BigDecimal("4.0"));
        if (creditScore >= 600) return baseRate.add(new BigDecimal("6.0"));
        return baseRate.add(new BigDecimal("8.0"));
    }
    
    private BigDecimal getBaseAPR(String loanType) {
        return switch (loanType.toUpperCase()) {
            case "MORTGAGE" -> new BigDecimal("6.5");
            case "HOME_EQUITY" -> new BigDecimal("7.0");
            case "AUTO" -> new BigDecimal("5.5");
            case "PERSONAL" -> new BigDecimal("8.0");
            default -> new BigDecimal("9.0");
        };
    }
    
    private BigDecimal getRiskAdjustment(RiskTier riskTier) {
        return switch (riskTier) {
            case SUPER_PRIME -> new BigDecimal("0.0");
            case PRIME -> new BigDecimal("1.0");
            case NEAR_PRIME -> new BigDecimal("2.5");
            case SUBPRIME -> new BigDecimal("4.0");
            case DEEP_SUBPRIME -> new BigDecimal("6.0");
        };
    }
    
    private BigDecimal calculateMonthlyPayment(BigDecimal principal, int termMonths, BigDecimal apr) {
        BigDecimal monthlyRate = apr.divide(new BigDecimal("1200"), 6, RoundingMode.HALF_UP);
        BigDecimal denominator = BigDecimal.ONE.subtract(
            BigDecimal.ONE.divide(
                BigDecimal.ONE.add(monthlyRate).pow(termMonths), 6, RoundingMode.HALF_UP
            )
        );
        return principal.multiply(monthlyRate).divide(denominator, 2, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateFinanceCharge(BigDecimal principal, BigDecimal apr, int termMonths) {
        BigDecimal monthlyPayment = calculateMonthlyPayment(principal, termMonths, apr);
        return monthlyPayment.multiply(new BigDecimal(termMonths)).subtract(principal);
    }
    
    private BigDecimal calculateTotalPayments(BigDecimal principal, BigDecimal apr, int termMonths) {
        return calculateMonthlyPayment(principal, termMonths, apr).multiply(new BigDecimal(termMonths));
    }
    
    private BigDecimal calculateTotalInterest(BigDecimal principal, BigDecimal apr, int termMonths) {
        return calculateTotalPayments(principal, apr, termMonths).subtract(principal);
    }
    
    private List<Map<String, Object>> generateAmortizationSchedule(BigDecimal principal, BigDecimal apr, int termMonths) {
        List<Map<String, Object>> schedule = new ArrayList<>();
        BigDecimal monthlyPayment = calculateMonthlyPayment(principal, apr, termMonths);
        BigDecimal monthlyRate = apr.divide(new BigDecimal("1200"), 6, RoundingMode.HALF_UP);
        BigDecimal balance = principal;
        
        for (int i = 1; i <= termMonths; i++) {
            BigDecimal interestPayment = balance.multiply(monthlyRate);
            BigDecimal principalPayment = monthlyPayment.subtract(interestPayment);
            balance = balance.subtract(principalPayment);
            
            schedule.add(Map.of(
                "paymentNumber", i,
                "paymentAmount", monthlyPayment,
                "principalAmount", principalPayment,
                "interestAmount", interestPayment,
                "remainingBalance", balance.max(BigDecimal.ZERO)
            ));
        }
        
        return schedule;
    }
    
    private boolean isHMDAReportable(String loanType, String loanPurpose) {
        return loanType.toUpperCase().contains("MORTGAGE") || 
               loanType.toUpperCase().contains("HOME");
    }
    
    private void createManualInterventionRecord(LoanApplicationSubmittedEvent event, Exception exception) {
        manualInterventionService.createTask(
            "LOAN_APPLICATION_PROCESSING_FAILED",
            String.format(
                "Failed to process loan application. " +
                "Application ID: %s, Applicant ID: %s, Amount: $%.2f, Type: %s. " +
                "Applicant may not have received decision notification. " +
                "Exception: %s. Manual intervention required.",
                event.getApplicationId(),
                event.getApplicantId(),
                event.getLoanAmount(),
                event.getLoanType(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}