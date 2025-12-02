/**
 * Traditional Loan Service
 * Comprehensive loan management service supporting various loan products
 */
package com.waqiti.bnpl.service;

import com.waqiti.bnpl.domain.enums.RiskLevel;
import com.waqiti.bnpl.entity.LoanApplication;
import com.waqiti.bnpl.entity.LoanInstallment;
import com.waqiti.bnpl.entity.LoanTransaction;
import com.waqiti.bnpl.exception.BnplException;
import com.waqiti.bnpl.repository.LoanApplicationRepository;
import com.waqiti.bnpl.repository.LoanInstallmentRepository;
import com.waqiti.bnpl.repository.LoanTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class TraditionalLoanService {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanInstallmentRepository loanInstallmentRepository;
    private final LoanTransactionRepository loanTransactionRepository;
    private final CreditScoringService creditScoringService;
    private final NotificationService notificationService;
    
    /**
     * Submit a new loan application
     */
    @Transactional
    public LoanApplication submitLoanApplication(LoanApplication application) {
        log.info("Submitting loan application for user: {}", application.getUserId());
        
        // Generate loan number
        application.setLoanNumber(generateLoanNumber(application.getLoanType()));
        
        // Calculate loan metrics
        calculateLoanMetrics(application);
        
        // Perform initial credit assessment
        performCreditAssessment(application);
        
        // Save application
        LoanApplication savedApplication = loanApplicationRepository.save(application);
        
        // Send notification
        notificationService.sendLoanApplicationSubmitted(savedApplication);
        
        log.info("Loan application submitted successfully: {}", savedApplication.getLoanNumber());
        return savedApplication;
    }
    
    /**
     * Approve loan application and generate repayment schedule
     */
    @Transactional
    public LoanApplication approveLoan(UUID loanId, UUID approverId, String approvalNotes) {
        log.info("Approving loan: {}", loanId);
        
        LoanApplication loan = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new BnplException("Loan not found: " + loanId));
        
        if (loan.getStatus() != LoanApplication.LoanStatus.PENDING) {
            throw new BnplException("Loan is not in pending status");
        }
        
        // Update loan status
        loan.setStatus(LoanApplication.LoanStatus.APPROVED);
        loan.setApprovalDate(LocalDateTime.now());
        loan.setDecisionBy(approverId.toString());
        loan.setDecision("APPROVED");
        loan.setDecisionReason(approvalNotes);
        loan.setDecisionDate(LocalDateTime.now());
        
        // Generate repayment schedule
        generateRepaymentSchedule(loan);
        
        // Save loan
        LoanApplication approvedLoan = loanApplicationRepository.save(loan);
        
        // Send notification
        notificationService.sendLoanApproved(approvedLoan);
        
        log.info("Loan approved successfully: {}", approvedLoan.getLoanNumber());
        return approvedLoan;
    }
    
    /**
     * Disburse approved loan
     */
    @Transactional
    public LoanApplication disburseLoan(UUID loanId, BigDecimal disbursementAmount, String disbursementMethod) {
        log.info("Disbursing loan: {} amount: {}", loanId, disbursementAmount);
        
        LoanApplication loan = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new BnplException("Loan not found: " + loanId));
        
        if (loan.getStatus() != LoanApplication.LoanStatus.APPROVED) {
            throw new BnplException("Loan is not approved for disbursement");
        }
        
        if (disbursementAmount.compareTo(loan.getApprovedAmount()) > 0) {
            throw new BnplException("Disbursement amount exceeds approved amount");
        }
        
        // Update loan status
        loan.setStatus(LoanApplication.LoanStatus.ACTIVE);
        loan.setDisbursementDate(LocalDateTime.now());
        loan.setDisbursedAmount(disbursementAmount);
        loan.setOutstandingBalance(disbursementAmount);
        
        // Create disbursement transaction
        LoanTransaction disbursementTx = LoanTransaction.builder()
            .loanApplication(loan)
            .transactionReference(generateTransactionReference())
            .transactionType(LoanTransaction.TransactionType.DISBURSEMENT)
            .amount(disbursementAmount)
            .principalAmount(disbursementAmount)
            .currency(loan.getCurrency())
            .status(LoanTransaction.TransactionStatus.COMPLETED)
            .paymentMethod(disbursementMethod)
            .description("Loan disbursement")
            .transactionDate(LocalDateTime.now())
            .processedDate(LocalDateTime.now())
            .balanceBefore(BigDecimal.ZERO)
            .balanceAfter(disbursementAmount)
            .build();
        
        loanTransactionRepository.save(disbursementTx);
        
        // Save loan
        LoanApplication disbursedLoan = loanApplicationRepository.save(loan);
        
        // Send notification
        notificationService.sendLoanDisbursed(disbursedLoan);
        
        log.info("Loan disbursed successfully: {}", disbursedLoan.getLoanNumber());
        return disbursedLoan;
    }
    
    /**
     * Process loan repayment
     */
    @Transactional
    public LoanTransaction processRepayment(UUID loanId, BigDecimal paymentAmount, String paymentMethod, String paymentReference) {
        log.info("Processing repayment for loan: {} amount: {}", loanId, paymentAmount);
        
        LoanApplication loan = loanApplicationRepository.findById(loanId)
            .orElseThrow(() -> new BnplException("Loan not found: " + loanId));
        
        if (loan.getStatus() != LoanApplication.LoanStatus.ACTIVE) {
            throw new BnplException("Loan is not active for repayment");
        }
        
        // Get due installments
        List<LoanInstallment> dueInstallments = loanInstallmentRepository
            .findByLoanApplicationAndStatusInOrderByDueDate(
                loan, 
                List.of(LoanInstallment.InstallmentStatus.PENDING, 
                       LoanInstallment.InstallmentStatus.DUE, 
                       LoanInstallment.InstallmentStatus.OVERDUE,
                       LoanInstallment.InstallmentStatus.PARTIALLY_PAID)
            );
        
        if (dueInstallments.isEmpty()) {
            throw new BnplException("No due installments found for this loan");
        }
        
        // Apply payment to installments
        BigDecimal remainingPayment = paymentAmount;
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        
        for (LoanInstallment installment : dueInstallments) {
            if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) break;
            
            BigDecimal installmentOutstanding = installment.getOutstandingAmount();
            BigDecimal paymentForInstallment = remainingPayment.min(installmentOutstanding);
            
            // Update installment
            BigDecimal newPaidAmount = installment.getPaidAmount().add(paymentForInstallment);
            installment.setPaidAmount(newPaidAmount);
            installment.setOutstandingAmount(installment.getTotalAmount().subtract(newPaidAmount));
            
            if (installment.getOutstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                installment.setStatus(LoanInstallment.InstallmentStatus.PAID);
                installment.setPaymentDate(LocalDateTime.now());
            } else {
                installment.setStatus(LoanInstallment.InstallmentStatus.PARTIALLY_PAID);
            }
            
            installment.setPaymentMethod(paymentMethod);
            installment.setPaymentReference(paymentReference);
            
            loanInstallmentRepository.save(installment);
            
            // Track payment allocation
            totalPrincipal = totalPrincipal.add(installment.getPrincipalAmount());
            totalInterest = totalInterest.add(installment.getInterestAmount());
            
            remainingPayment = remainingPayment.subtract(paymentForInstallment);
        }
        
        // Update loan outstanding balance
        BigDecimal newOutstandingBalance = loan.getOutstandingBalance().subtract(paymentAmount.subtract(remainingPayment));
        loan.setOutstandingBalance(newOutstandingBalance);
        
        // Check if loan is fully paid
        if (newOutstandingBalance.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanApplication.LoanStatus.COMPLETED);
            notificationService.sendLoanCompleted(loan);
        }
        
        loanApplicationRepository.save(loan);
        
        // Create repayment transaction
        LoanTransaction repaymentTx = LoanTransaction.builder()
            .loanApplication(loan)
            .transactionReference(generateTransactionReference())
            .transactionType(LoanTransaction.TransactionType.REPAYMENT)
            .amount(paymentAmount.subtract(remainingPayment))
            .principalAmount(totalPrincipal)
            .interestAmount(totalInterest)
            .feeAmount(totalFees)
            .currency(loan.getCurrency())
            .status(LoanTransaction.TransactionStatus.COMPLETED)
            .paymentMethod(paymentMethod)
            .externalReference(paymentReference)
            .description("Loan repayment")
            .transactionDate(LocalDateTime.now())
            .processedDate(LocalDateTime.now())
            .balanceBefore(loan.getOutstandingBalance().add(paymentAmount.subtract(remainingPayment)))
            .balanceAfter(newOutstandingBalance)
            .build();
        
        LoanTransaction savedTransaction = loanTransactionRepository.save(repaymentTx);
        
        // Send notification
        notificationService.sendRepaymentReceived(loan, savedTransaction);
        
        log.info("Repayment processed successfully: {} for loan: {}", savedTransaction.getTransactionReference(), loan.getLoanNumber());
        return savedTransaction;
    }
    
    /**
     * Calculate loan metrics (monthly payment, total interest, etc.)
     */
    private void calculateLoanMetrics(LoanApplication loan) {
        BigDecimal principal = loan.getRequestedAmount();
        BigDecimal annualRate = loan.getInterestRate().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        int months = loan.getLoanTermMonths();
        
        // Calculate monthly payment using formula: P * [r(1+r)^n] / [(1+r)^n - 1]
        if (loan.getInterestType() == LoanApplication.InterestType.REDUCING_BALANCE) {
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
            BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
            BigDecimal onePlusRPowerN = onePlusR.pow(months);
            
            BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowerN);
            BigDecimal denominator = onePlusRPowerN.subtract(BigDecimal.ONE);
            
            BigDecimal monthlyPayment = numerator.divide(denominator, 2, RoundingMode.HALF_UP);
            loan.setMonthlyPayment(monthlyPayment);
            
            BigDecimal totalRepayment = monthlyPayment.multiply(BigDecimal.valueOf(months));
            loan.setTotalRepayment(totalRepayment);
            loan.setTotalInterest(totalRepayment.subtract(principal));
        } else {
            // Simple interest calculation
            BigDecimal totalInterest = principal.multiply(annualRate).multiply(BigDecimal.valueOf(months)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            loan.setTotalInterest(totalInterest);
            
            BigDecimal totalRepayment = principal.add(totalInterest);
            loan.setTotalRepayment(totalRepayment);
            loan.setMonthlyPayment(totalRepayment.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP));
        }
        
        // Set default values
        if (loan.getApprovedAmount() == null) {
            loan.setApprovedAmount(loan.getRequestedAmount());
        }
    }
    
    /**
     * Perform credit assessment
     */
    private void performCreditAssessment(LoanApplication loan) {
        // Get credit score
        Integer creditScore = creditScoringService.calculateCreditScore(loan.getUserId());
        loan.setCreditScore(creditScore);
        
        // Calculate debt-to-income ratio
        if (loan.getAnnualIncome() != null && loan.getAnnualIncome().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal monthlyIncome = loan.getAnnualIncome().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal dtiRatio = loan.getMonthlyPayment().divide(monthlyIncome, 4, RoundingMode.HALF_UP);
            loan.setDebtToIncomeRatio(dtiRatio);
        }
        
        // Determine risk grade
        String riskGrade = determineRiskGrade(creditScore, loan.getDebtToIncomeRatio());
        loan.setRiskGrade(riskGrade);
    }
    
    /**
     * Generate repayment schedule
     */
    private void generateRepaymentSchedule(LoanApplication loan) {
        List<LoanInstallment> installments = new ArrayList<>();
        BigDecimal principal = loan.getApprovedAmount();
        BigDecimal monthlyRate = loan.getInterestRate().divide(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(12), 6, RoundingMode.HALF_UP);
        LocalDate currentDate = LocalDate.now().plusMonths(1); // First payment next month
        
        BigDecimal remainingPrincipal = principal;
        
        for (int i = 1; i <= loan.getLoanTermMonths(); i++) {
            LoanInstallment installment = new LoanInstallment();
            installment.setLoanApplication(loan);
            installment.setInstallmentNumber(i);
            installment.setDueDate(currentDate);
            
            if (loan.getInterestType() == LoanApplication.InterestType.REDUCING_BALANCE) {
                BigDecimal interestAmount = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                BigDecimal principalAmount = loan.getMonthlyPayment().subtract(interestAmount);
                
                installment.setInterestAmount(interestAmount);
                installment.setPrincipalAmount(principalAmount);
                installment.setTotalAmount(loan.getMonthlyPayment());
                
                remainingPrincipal = remainingPrincipal.subtract(principalAmount);
            } else {
                // Simple interest - equal principal payments
                BigDecimal principalAmount = principal.divide(BigDecimal.valueOf(loan.getLoanTermMonths()), 2, RoundingMode.HALF_UP);
                BigDecimal interestAmount = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                
                installment.setPrincipalAmount(principalAmount);
                installment.setInterestAmount(interestAmount);
                installment.setTotalAmount(principalAmount.add(interestAmount));
                
                remainingPrincipal = remainingPrincipal.subtract(principalAmount);
            }
            
            installment.setOutstandingAmount(installment.getTotalAmount());
            installment.setStatus(LoanInstallment.InstallmentStatus.PENDING);
            
            installments.add(installment);
            
            // Move to next month
            currentDate = currentDate.plusMonths(1);
        }
        
        // Set loan dates
        loan.setFirstPaymentDate(installments.get(0).getDueDate());
        loan.setMaturityDate(installments.get(installments.size() - 1).getDueDate());
        
        // Save installments
        loanInstallmentRepository.saveAll(installments);
    }
    
    /**
     * Determine risk grade based on credit score and DTI
     */
    private String determineRiskGrade(Integer creditScore, BigDecimal dtiRatio) {
        if (creditScore == null) return "UNRATED";
        
        if (creditScore >= 750 && (dtiRatio == null || dtiRatio.compareTo(BigDecimal.valueOf(0.3)) <= 0)) {
            return "AAA";
        } else if (creditScore >= 700 && (dtiRatio == null || dtiRatio.compareTo(BigDecimal.valueOf(0.4)) <= 0)) {
            return "AA";
        } else if (creditScore >= 650 && (dtiRatio == null || dtiRatio.compareTo(BigDecimal.valueOf(0.5)) <= 0)) {
            return "A";
        } else if (creditScore >= 600) {
            return "BBB";
        } else if (creditScore >= 550) {
            return "BB";
        } else if (creditScore >= 500) {
            return "B";
        } else {
            return "C";
        }
    }
    
    /**
     * Generate loan number
     */
    private String generateLoanNumber(LoanApplication.LoanType loanType) {
        String prefix = switch (loanType) {
            case PERSONAL_LOAN -> "PL";
            case BUSINESS_LOAN -> "BL";
            case EDUCATION_LOAN -> "EL";
            case HOME_LOAN -> "HL";
            case AUTO_LOAN -> "AL";
            case AGRICULTURE_LOAN -> "AG";
            case MICROFINANCE -> "MF";
            case EMERGENCY_LOAN -> "EM";
            case PAYDAY_LOAN -> "PD";
            case CONSOLIDATION_LOAN -> "CL";
        };
        
        return prefix + System.currentTimeMillis() + String.valueOf(SECURE_RANDOM.nextInt(1000));
    }
    
    /**
     * Generate transaction reference
     */
    private String generateTransactionReference() {
        return "TXN" + System.currentTimeMillis() + String.valueOf(SECURE_RANDOM.nextInt(10000));
    }
}