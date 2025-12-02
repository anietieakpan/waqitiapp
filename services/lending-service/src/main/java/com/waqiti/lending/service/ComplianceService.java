package com.waqiti.lending.service;

import com.waqiti.lending.domain.Loan;
import com.waqiti.lending.domain.LoanApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Compliance Service
 * Handles regulatory compliance (TILA, ECOA, FCRA, HMDA)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceService {

    /**
     * Generate TILA disclosure
     */
    public TilaDisclosure generateTilaDisclosure(Loan loan) {
        log.info("Generating TILA disclosure for loan: {}", loan.getLoanId());

        TilaDisclosure disclosure = new TilaDisclosure();
        disclosure.setLoanId(loan.getLoanId());
        disclosure.setAnnualPercentageRate(loan.getInterestRate());
        disclosure.setFinanceCharge(calculateTotalInterest(loan));
        disclosure.setAmountFinanced(loan.getPrincipalAmount());
        disclosure.setTotalOfPayments(loan.getMonthlyPayment().multiply(
                java.math.BigDecimal.valueOf(loan.getTermMonths())));
        disclosure.setPaymentSchedule(String.format("%d monthly payments of $%.2f",
                loan.getTermMonths(), loan.getMonthlyPayment()));

        return disclosure;
    }

    /**
     * Perform ECOA compliance check
     */
    public boolean performEcoaComplianceCheck(LoanApplication application) {
        log.info("Performing ECOA compliance check for application: {}", application.getApplicationId());

        // ECOA prohibits discrimination based on protected characteristics
        // This would validate that no prohibited factors influenced the decision

        // For now, return true (actual implementation would check decision factors)
        return true;
    }

    /**
     * Report to HMDA (Home Mortgage Disclosure Act)
     */
    public void reportToHmda(LoanApplication application, String decision) {
        log.info("Reporting to HMDA for application: {} - Decision: {}",
                application.getApplicationId(), decision);

        // TODO: Integrate with HMDA reporting system
        // Required for mortgage loans
    }

    /**
     * Report to credit bureaus (FCRA)
     */
    public void reportToCreditBureaus(Loan loan, String reportType, int daysPastDue) {
        log.info("Reporting to credit bureaus for loan: {} - Type: {}, Days past due: {}",
                loan.getLoanId(), reportType, daysPastDue);

        // TODO: Integrate with credit bureau reporting
        // Required to report payment history, defaults, etc.
    }

    /**
     * Calculate total interest for TILA disclosure
     */
    private java.math.BigDecimal calculateTotalInterest(Loan loan) {
        java.math.BigDecimal totalPayments = loan.getMonthlyPayment().multiply(
                java.math.BigDecimal.valueOf(loan.getTermMonths()));
        return totalPayments.subtract(loan.getPrincipalAmount());
    }

    /**
     * TILA Disclosure DTO
     */
    @lombok.Data
    public static class TilaDisclosure {
        private String loanId;
        private java.math.BigDecimal annualPercentageRate;
        private java.math.BigDecimal financeCharge;
        private java.math.BigDecimal amountFinanced;
        private java.math.BigDecimal totalOfPayments;
        private String paymentSchedule;
    }
}
