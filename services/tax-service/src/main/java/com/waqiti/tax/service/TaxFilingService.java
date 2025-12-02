package com.waqiti.tax.service;

import com.waqiti.tax.client.*;
import com.waqiti.tax.domain.*;
import com.waqiti.tax.dto.*;
import com.waqiti.tax.repository.*;
import com.waqiti.tax.integration.*;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.payment.service.TransactionService;
import com.waqiti.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Tax Filing Service - CashApp-style tax preparation and filing
 * 
 * Features:
 * - Free tax filing for simple returns
 * - Automatic import of 1099 forms
 * - Cryptocurrency tax reporting
 * - Investment income reporting
 * - Maximum refund guarantee
 * - Direct deposit of refunds
 * - Year-round tax estimation
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaxFilingService {
    
    @Lazy
    private final TaxFilingService self;
    
    private final TaxDocumentRepository documentRepository;
    private final TaxReturnRepository returnRepository;
    private final TaxEstimateRepository estimateRepository;
    private final TaxFormRepository formRepository;
    private final TransactionService transactionService;
    private final UserService userService;
    private final CryptoTaxService cryptoTaxService;
    private final InvestmentTaxService investmentTaxService;
    private final IRSIntegrationService irsIntegration;
    private final TaxOptimizationEngine optimizationEngine;
    private final EncryptionService encryptionService;
    private final NotificationService notificationService;
    
    @Value("${tax.filing.free.income.limit:73000}")
    private BigDecimal freeFilingIncomeLimit;
    
    @Value("${tax.filing.premium.fee:0}")
    private BigDecimal premiumFilingFee;
    
    /**
     * Starts a new tax return for the user
     */
    public TaxReturn startTaxReturn(UUID userId, TaxReturnRequest request) {
        try {
            log.info("Starting tax return for user: {} for year: {}", userId, request.getTaxYear());
            
            // Check if return already exists
            Optional<TaxReturn> existing = returnRepository.findByUserIdAndTaxYear(userId, request.getTaxYear());
            if (existing.isPresent() && existing.get().getStatus() != TaxReturnStatus.DRAFT) {
                throw new BusinessException("Tax return already exists for year " + request.getTaxYear());
            }
            
            // Get user profile
            UserProfile userProfile = userService.getUserProfile(userId);
            
            // Create new tax return
            TaxReturn taxReturn = TaxReturn.builder()
                    .userId(userId)
                    .taxYear(request.getTaxYear())
                    .filingStatus(request.getFilingStatus())
                    .personalInfo(buildPersonalInfo(userProfile, request))
                    .status(TaxReturnStatus.DRAFT)
                    .createdAt(LocalDateTime.now())
                    .lastModified(LocalDateTime.now())
                    .estimatedRefund(BigDecimal.ZERO)
                    .isPremium(false)
                    .build();
            
            taxReturn = returnRepository.save(taxReturn);
            
            // Import available tax documents
            importTaxDocuments(taxReturn);
            
            // Import transaction data
            importTransactionData(taxReturn);
            
            // Import crypto transactions if applicable
            if (request.isIncludeCrypto()) {
                importCryptoTransactions(taxReturn);
            }
            
            // Import investment data if applicable
            if (request.isIncludeInvestments()) {
                importInvestmentData(taxReturn);
            }
            
            // Calculate initial estimate
            TaxEstimate estimate = calculateTaxEstimate(taxReturn);
            taxReturn.setEstimatedRefund(estimate.getEstimatedRefund());
            taxReturn.setEstimatedTax(estimate.getTotalTax());
            
            returnRepository.save(taxReturn);
            
            log.info("Tax return created with ID: {}", taxReturn.getId());
            
            return taxReturn;
            
        } catch (Exception e) {
            log.error("Error starting tax return", e);
            throw new BusinessException("Failed to start tax return: " + e.getMessage());
        }
    }
    
    /**
     * Imports W-2 forms from employers
     */
    public List<TaxDocument> importW2Forms(UUID returnId) {
        try {
            TaxReturn taxReturn = getTaxReturn(returnId);
            
            log.info("Importing W-2 forms for return: {}", returnId);
            
            List<TaxDocument> w2Forms = new ArrayList<>();
            
            // Import from IRS (if user has authorized)
            if (taxReturn.isIrsAuthorized()) {
                List<W2Form> irsW2s = irsIntegration.fetchW2Forms(
                    taxReturn.getPersonalInfo().getSsn(),
                    taxReturn.getTaxYear()
                );
                
                for (W2Form w2 : irsW2s) {
                    TaxDocument doc = createTaxDocument(taxReturn, w2);
                    w2Forms.add(doc);
                }
            }
            
            // Import from connected payroll providers
            List<W2Form> payrollW2s = importFromPayrollProviders(taxReturn);
            for (W2Form w2 : payrollW2s) {
                TaxDocument doc = createTaxDocument(taxReturn, w2);
                w2Forms.add(doc);
            }
            
            // Save all documents
            documentRepository.saveAll(w2Forms);
            
            // Update tax calculations
            recalculateTaxes(taxReturn);
            
            log.info("Imported {} W-2 forms", w2Forms.size());
            
            return w2Forms;
            
        } catch (Exception e) {
            log.error("Error importing W-2 forms", e);
            throw new BusinessException("Failed to import W-2 forms");
        }
    }
    
    /**
     * Imports 1099 forms for independent contractors
     */
    public List<TaxDocument> import1099Forms(UUID returnId) {
        try {
            TaxReturn taxReturn = getTaxReturn(returnId);
            
            log.info("Importing 1099 forms for return: {}", returnId);
            
            List<TaxDocument> forms1099 = new ArrayList<>();
            
            // Import 1099-K from payment processors (Waqiti transactions)
            TaxDocument form1099K = generate1099K(taxReturn);
            if (form1099K != null) {
                forms1099.add(form1099K);
            }
            
            // Import 1099-MISC/1099-NEC from gig platforms
            List<Form1099> gigForms = importFromGigPlatforms(taxReturn);
            for (Form1099 form : gigForms) {
                TaxDocument doc = createTaxDocument(taxReturn, form);
                forms1099.add(doc);
            }
            
            // Import 1099-INT for interest income
            List<Form1099INT> interestForms = importInterestIncome(taxReturn);
            for (Form1099INT form : interestForms) {
                TaxDocument doc = createTaxDocument(taxReturn, form);
                forms1099.add(doc);
            }
            
            // Import 1099-B for investment sales
            if (taxReturn.isIncludeInvestments()) {
                List<Form1099B> investmentForms = investmentTaxService.generate1099B(
                    taxReturn.getUserId(),
                    taxReturn.getTaxYear()
                );
                for (Form1099B form : investmentForms) {
                    TaxDocument doc = createTaxDocument(taxReturn, form);
                    forms1099.add(doc);
                }
            }
            
            // Save all documents
            documentRepository.saveAll(forms1099);
            
            // Update tax calculations
            recalculateTaxes(taxReturn);
            
            log.info("Imported {} 1099 forms", forms1099.size());
            
            return forms1099;
            
        } catch (Exception e) {
            log.error("Error importing 1099 forms", e);
            throw new BusinessException("Failed to import 1099 forms");
        }
    }
    
    /**
     * Generates cryptocurrency tax report (Form 8949)
     */
    public CryptoTaxReport generateCryptoTaxReport(UUID returnId) {
        try {
            TaxReturn taxReturn = getTaxReturn(returnId);
            
            log.info("Generating crypto tax report for return: {}", returnId);
            
            // Get all crypto transactions for the tax year
            List<CryptoTransaction> transactions = cryptoTaxService.getTransactionsForYear(
                taxReturn.getUserId(),
                taxReturn.getTaxYear()
            );
            
            // Calculate gains/losses using FIFO method
            CryptoTaxCalculation calculation = cryptoTaxService.calculateTaxes(
                transactions,
                CostBasisMethod.FIFO
            );
            
            // Generate Form 8949
            Form8949 form8949 = Form8949.builder()
                    .taxYear(taxReturn.getTaxYear())
                    .shortTermTransactions(calculation.getShortTermTransactions())
                    .longTermTransactions(calculation.getLongTermTransactions())
                    .totalShortTermGain(calculation.getTotalShortTermGain())
                    .totalLongTermGain(calculation.getTotalLongTermGain())
                    .build();
            
            // Create tax document
            TaxDocument cryptoDoc = TaxDocument.builder()
                    .returnId(returnId)
                    .documentType(DocumentType.FORM_8949)
                    .documentName("Cryptocurrency Gains and Losses")
                    .documentData(encryptionService.encrypt(form8949.toJson()))
                    .taxYear(taxReturn.getTaxYear())
                    .uploadedAt(LocalDateTime.now())
                    .isVerified(true)
                    .build();
            
            documentRepository.save(cryptoDoc);
            
            // Create crypto tax report
            CryptoTaxReport report = CryptoTaxReport.builder()
                    .returnId(returnId)
                    .form8949(form8949)
                    .totalTransactions(transactions.size())
                    .totalProceeds(calculation.getTotalProceeds())
                    .totalCostBasis(calculation.getTotalCostBasis())
                    .totalGainLoss(calculation.getTotalGainLoss())
                    .shortTermGainLoss(calculation.getTotalShortTermGain())
                    .longTermGainLoss(calculation.getTotalLongTermGain())
                    .highestTaxableEvent(calculation.getHighestTaxableEvent())
                    .taxOptimizationSuggestions(calculation.getOptimizationSuggestions())
                    .generatedAt(LocalDateTime.now())
                    .build();
            
            // Update tax return with crypto gains
            taxReturn.setCapitalGains(taxReturn.getCapitalGains().add(calculation.getTotalGainLoss()));
            returnRepository.save(taxReturn);
            
            // Recalculate taxes
            recalculateTaxes(taxReturn);
            
            log.info("Generated crypto tax report with {} transactions", transactions.size());
            
            return report;
            
        } catch (Exception e) {
            log.error("Error generating crypto tax report", e);
            throw new BusinessException("Failed to generate crypto tax report");
        }
    }
    
    /**
     * Calculates tax estimate and potential refund
     */
    @Cacheable(value = "taxEstimate", key = "#taxReturn.id")
    public TaxEstimate calculateTaxEstimate(TaxReturn taxReturn) {
        try {
            log.debug("Calculating tax estimate for return: {}", taxReturn.getId());
            
            // Get all income sources
            BigDecimal totalIncome = calculateTotalIncome(taxReturn);
            
            // Calculate adjusted gross income (AGI)
            BigDecimal agi = calculateAGI(taxReturn, totalIncome);
            
            // Get standard or itemized deductions
            BigDecimal deductions = calculateDeductions(taxReturn);
            
            // Calculate taxable income
            BigDecimal taxableIncome = agi.subtract(deductions).max(BigDecimal.ZERO);
            
            // Calculate federal tax
            BigDecimal federalTax = calculateFederalTax(taxableIncome, taxReturn.getFilingStatus());
            
            // Calculate state tax
            BigDecimal stateTax = calculateStateTax(taxReturn, taxableIncome);
            
            // Calculate credits
            BigDecimal taxCredits = calculateTaxCredits(taxReturn);
            
            // Calculate total tax liability
            BigDecimal totalTax = federalTax.add(stateTax).subtract(taxCredits).max(BigDecimal.ZERO);
            
            // Get total withholdings
            BigDecimal totalWithheld = calculateTotalWithheld(taxReturn);
            
            // Calculate refund or amount owed
            BigDecimal refundOrOwed = totalWithheld.subtract(totalTax);
            
            // Apply optimization strategies
            TaxOptimizationResult optimization = optimizationEngine.optimize(taxReturn);
            if (optimization.getSavings().compareTo(BigDecimal.ZERO) > 0) {
                refundOrOwed = refundOrOwed.add(optimization.getSavings());
            }
            
            TaxEstimate estimate = TaxEstimate.builder()
                    .returnId(taxReturn.getId())
                    .totalIncome(totalIncome)
                    .adjustedGrossIncome(agi)
                    .deductions(deductions)
                    .taxableIncome(taxableIncome)
                    .federalTax(federalTax)
                    .stateTax(stateTax)
                    .taxCredits(taxCredits)
                    .totalTax(totalTax)
                    .totalWithheld(totalWithheld)
                    .estimatedRefund(refundOrOwed.max(BigDecimal.ZERO))
                    .amountOwed(refundOrOwed.min(BigDecimal.ZERO).abs())
                    .effectiveTaxRate(calculateEffectiveTaxRate(totalTax, totalIncome))
                    .marginalTaxRate(getMarginalTaxRate(taxableIncome, taxReturn.getFilingStatus()))
                    .optimizationSuggestions(optimization.getSuggestions())
                    .potentialSavings(optimization.getSavings())
                    .calculatedAt(LocalDateTime.now())
                    .build();
            
            // Save estimate
            estimateRepository.save(estimate);
            
            log.info("Tax estimate calculated - Refund: {}, Owed: {}", 
                    estimate.getEstimatedRefund(), estimate.getAmountOwed());
            
            return estimate;
            
        } catch (Exception e) {
            log.error("Error calculating tax estimate", e);
            throw new BusinessException("Failed to calculate tax estimate");
        }
    }
    
    /**
     * Files the tax return electronically with IRS
     */
    @Async
    public CompletableFuture<FilingResult> fileTaxReturn(UUID returnId, FilingRequest request) {
        try {
            TaxReturn taxReturn = getTaxReturn(returnId);
            
            log.info("Filing tax return: {} electronically", returnId);
            
            // Validate return is complete
            validateReturnComplete(taxReturn);
            
            // Apply electronic signature
            applyElectronicSignature(taxReturn, request);
            
            // Generate final tax forms
            TaxFormPackage formPackage = generateFinalForms(taxReturn);
            
            // Submit to IRS
            IRSSubmissionResult irsResult = irsIntegration.submitReturn(formPackage);
            
            // Submit state return if applicable
            StateSubmissionResult stateResult = null;
            if (taxReturn.isStateReturnRequired()) {
                stateResult = submitStateReturn(taxReturn, formPackage);
            }
            
            // Update return status
            taxReturn.setStatus(TaxReturnStatus.FILED);
            taxReturn.setFiledAt(LocalDateTime.now());
            taxReturn.setIrsConfirmationNumber(irsResult.getConfirmationNumber());
            taxReturn.setStateConfirmationNumber(stateResult != null ? stateResult.getConfirmationNumber() : null);
            returnRepository.save(taxReturn);
            
            // Schedule refund tracking
            if (taxReturn.getEstimatedRefund().compareTo(BigDecimal.ZERO) > 0) {
                scheduleRefundTracking(taxReturn);
            }
            
            // Send confirmation
            notificationService.sendFilingConfirmation(taxReturn);
            
            FilingResult result = FilingResult.builder()
                    .returnId(returnId)
                    .irsConfirmationNumber(irsResult.getConfirmationNumber())
                    .stateConfirmationNumber(stateResult != null ? stateResult.getConfirmationNumber() : null)
                    .filedAt(LocalDateTime.now())
                    .estimatedRefundDate(irsResult.getEstimatedRefundDate())
                    .estimatedRefundAmount(taxReturn.getEstimatedRefund())
                    .status(FilingStatus.SUCCESS)
                    .build();
            
            log.info("Tax return filed successfully with confirmation: {}", 
                    irsResult.getConfirmationNumber());
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error filing tax return", e);
            
            FilingResult errorResult = FilingResult.builder()
                    .returnId(returnId)
                    .status(FilingStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build();
            
            return CompletableFuture.completedFuture(errorResult);
        }
    }
    
    /**
     * Tracks refund status with IRS
     */
    public RefundStatus trackRefund(UUID returnId) {
        try {
            TaxReturn taxReturn = getTaxReturn(returnId);
            
            if (taxReturn.getIrsConfirmationNumber() == null) {
                throw new BusinessException("Tax return has not been filed yet");
            }
            
            log.debug("Tracking refund for return: {}", returnId);
            
            // Check with IRS
            IRSRefundStatus irsStatus = irsIntegration.checkRefundStatus(
                taxReturn.getPersonalInfo().getSsn(),
                taxReturn.getFilingStatus(),
                taxReturn.getEstimatedRefund()
            );
            
            RefundStatus status = RefundStatus.builder()
                    .returnId(returnId)
                    .irsStatus(irsStatus.getStatus())
                    .statusMessage(irsStatus.getMessage())
                    .estimatedDepositDate(irsStatus.getEstimatedDate())
                    .refundAmount(taxReturn.getEstimatedRefund())
                    .lastChecked(LocalDateTime.now())
                    .build();
            
            // Update if refund is received
            if (irsStatus.getStatus() == RefundStatusType.DEPOSITED) {
                taxReturn.setRefundReceived(true);
                taxReturn.setRefundReceivedDate(LocalDateTime.now());
                returnRepository.save(taxReturn);
                
                // Credit user's wallet
                creditRefundToWallet(taxReturn);
            }
            
            return status;
            
        } catch (Exception e) {
            log.error("Error tracking refund", e);
            throw new BusinessException("Failed to track refund status");
        }
    }
    
    /**
     * Provides year-round tax planning and estimation
     */
    public TaxPlanningReport generateTaxPlanning(UUID userId) {
        try {
            log.info("Generating tax planning report for user: {}", userId);
            
            int currentYear = Year.now().getValue();
            
            // Get YTD income and withholdings
            YearToDateSummary ytdSummary = calculateYTDSummary(userId, currentYear);
            
            // Project full year income
            BigDecimal projectedIncome = projectAnnualIncome(ytdSummary);
            
            // Estimate tax liability
            BigDecimal estimatedTax = estimateAnnualTax(userId, projectedIncome);
            
            // Calculate required quarterly payments
            List<QuarterlyPayment> quarterlyPayments = calculateQuarterlyPayments(
                userId, 
                estimatedTax, 
                ytdSummary.getTotalWithheld()
            );
            
            // Generate tax saving opportunities
            List<TaxSavingOpportunity> opportunities = identifyTaxSavingOpportunities(userId);
            
            // Calculate optimal withholding
            WithholdingRecommendation withholding = calculateOptimalWithholding(
                userId,
                estimatedTax,
                ytdSummary
            );
            
            TaxPlanningReport report = TaxPlanningReport.builder()
                    .userId(userId)
                    .taxYear(currentYear)
                    .ytdIncome(ytdSummary.getTotalIncome())
                    .ytdWithheld(ytdSummary.getTotalWithheld())
                    .projectedIncome(projectedIncome)
                    .estimatedTax(estimatedTax)
                    .estimatedRefund(ytdSummary.getTotalWithheld().subtract(estimatedTax))
                    .quarterlyPayments(quarterlyPayments)
                    .taxSavingOpportunities(opportunities)
                    .withholdingRecommendation(withholding)
                    .effectiveTaxRate(calculateEffectiveTaxRate(estimatedTax, projectedIncome))
                    .generatedAt(LocalDateTime.now())
                    .build();
            
            log.info("Tax planning report generated with {} opportunities", opportunities.size());
            
            return report;
            
        } catch (Exception e) {
            log.error("Error generating tax planning report", e);
            throw new BusinessException("Failed to generate tax planning report");
        }
    }
    
    // Helper methods
    
    private void importTaxDocuments(TaxReturn taxReturn) {
        // Import various tax documents from connected sources
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> importW2Forms(taxReturn.getId())),
            CompletableFuture.runAsync(() -> import1099Forms(taxReturn.getId()))
        ).join();
    }
    
    private void importTransactionData(TaxReturn taxReturn) {
        // Import relevant transaction data for tax purposes
        List<Transaction> transactions = transactionService.getTransactionsForYear(
            taxReturn.getUserId(),
            taxReturn.getTaxYear()
        );
        
        // Categorize transactions for tax purposes
        categorizeTransactions(taxReturn, transactions);
    }
    
    private void importCryptoTransactions(TaxReturn taxReturn) {
        // Import and process cryptocurrency transactions
        generateCryptoTaxReport(taxReturn.getId());
    }
    
    private void importInvestmentData(TaxReturn taxReturn) {
        // Import investment income and transactions
        investmentTaxService.importInvestmentData(taxReturn);
    }
    
    private TaxDocument generate1099K(TaxReturn taxReturn) {
        // Generate 1099-K for payment card transactions
        BigDecimal totalPayments = transactionService.getTotalPaymentsReceived(
            taxReturn.getUserId(),
            taxReturn.getTaxYear()
        );
        
        // 1099-K is required if payments exceed $600
        if (totalPayments.compareTo(new BigDecimal("600")) > 0) {
            Form1099K form = Form1099K.builder()
                    .payerName("Waqiti Inc.")
                    .payerTIN("XX-XXXXXXX")
                    .recipientName(taxReturn.getPersonalInfo().getFullName())
                    .recipientTIN(taxReturn.getPersonalInfo().getSsn())
                    .grossAmount(totalPayments)
                    .cardNotPresent(true)
                    .numberOfTransactions(transactionService.getTransactionCount(
                        taxReturn.getUserId(),
                        taxReturn.getTaxYear()
                    ))
                    .taxYear(taxReturn.getTaxYear())
                    .build();
            
            return createTaxDocument(taxReturn, form);
        }
        
        // Return empty tax document indicating no 1099-K required
        return TaxDocument.builder()
                .taxReturnId(taxReturn.getId())
                .documentType(TaxDocumentType.FORM_1099K)
                .status(TaxDocumentStatus.NOT_REQUIRED)
                .reason("Total payments of " + totalPayments + " do not exceed $600 threshold")
                .generatedDate(LocalDateTime.now())
                .build();
    }
    
    private BigDecimal calculateFederalTax(BigDecimal taxableIncome, FilingStatus filingStatus) {
        // Calculate federal tax based on current tax brackets
        return TaxBracketCalculator.calculateFederalTax(taxableIncome, filingStatus, Year.now().getValue());
    }
    
    private BigDecimal calculateStateTax(TaxReturn taxReturn, BigDecimal taxableIncome) {
        // Calculate state tax based on user's state
        String state = taxReturn.getPersonalInfo().getState();
        return StateTaxCalculator.calculateStateTax(taxableIncome, state, taxReturn.getFilingStatus());
    }
    
    private void creditRefundToWallet(TaxReturn taxReturn) {
        // Credit tax refund directly to user's wallet
        transactionService.creditTaxRefund(
            taxReturn.getUserId(),
            taxReturn.getEstimatedRefund(),
            taxReturn.getIrsConfirmationNumber()
        );
        
        notificationService.sendRefundReceivedNotification(taxReturn);
    }
    
    private TaxReturn getTaxReturn(UUID returnId) {
        return returnRepository.findById(returnId)
                .orElseThrow(() -> new BusinessException("Tax return not found"));
    }
    
    private PersonalInfo buildPersonalInfo(UserProfile profile, TaxReturnRequest request) {
        return PersonalInfo.builder()
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .ssn(request.getSsn())
                .dateOfBirth(profile.getDateOfBirth())
                .address(profile.getAddress())
                .city(profile.getCity())
                .state(profile.getState())
                .zipCode(profile.getZipCode())
                .phone(profile.getPhone())
                .email(profile.getEmail())
                .build();
    }
    
    private void recalculateTaxes(TaxReturn taxReturn) {
        TaxEstimate newEstimate = self.calculateTaxEstimate(taxReturn);
        taxReturn.setEstimatedRefund(newEstimate.getEstimatedRefund());
        taxReturn.setEstimatedTax(newEstimate.getTotalTax());
        returnRepository.save(taxReturn);
    }
    
    private BigDecimal calculateEffectiveTaxRate(BigDecimal totalTax, BigDecimal totalIncome) {
        if (totalIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return totalTax.divide(totalIncome, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }
    
    // Enums and inner classes
    
    public enum FilingStatus {
        SINGLE,
        MARRIED_FILING_JOINTLY,
        MARRIED_FILING_SEPARATELY,
        HEAD_OF_HOUSEHOLD,
        QUALIFYING_WIDOW
    }
    
    public enum TaxReturnStatus {
        DRAFT,
        IN_PROGRESS,
        READY_TO_FILE,
        FILED,
        ACCEPTED,
        REJECTED,
        AMENDED
    }
    
    public enum DocumentType {
        W2,
        FORM_1099,
        FORM_1099K,
        FORM_1099B,
        FORM_1099INT,
        FORM_1099DIV,
        FORM_8949,
        SCHEDULE_C,
        SCHEDULE_D,
        OTHER
    }
    
    public enum RefundStatusType {
        NOT_FILED,
        RECEIVED,
        PROCESSING,
        APPROVED,
        SENT,
        DEPOSITED
    }
    
    public enum CostBasisMethod {
        FIFO,
        LIFO,
        HIFO,
        SPECIFIC_ID
    }
}