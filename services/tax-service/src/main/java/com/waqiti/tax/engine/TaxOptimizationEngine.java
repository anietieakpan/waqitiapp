package com.waqiti.tax.engine;

import com.waqiti.tax.domain.TaxReturn;
import com.waqiti.tax.dto.TaxOptimizationResult;
import com.waqiti.tax.dto.TaxSavingOpportunity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Tax Optimization Engine
 *
 * Analyzes tax returns and identifies optimization opportunities:
 * - Deduction maximization
 * - Credit eligibility
 * - Tax bracket optimization
 * - Retirement contribution strategies
 * - Capital loss harvesting
 * - Income timing strategies
 *
 * @author Waqiti Tax Team
 * @since 2025-10-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaxOptimizationEngine {

    private static final BigDecimal IRA_CONTRIBUTION_LIMIT = new BigDecimal("6500");
    private static final BigDecimal IRA_CONTRIBUTION_LIMIT_50_PLUS = new BigDecimal("7500");
    private static final BigDecimal HSA_CONTRIBUTION_LIMIT_SINGLE = new BigDecimal("4150");
    private static final BigDecimal HSA_CONTRIBUTION_LIMIT_FAMILY = new BigDecimal("8300");
    private static final BigDecimal STUDENT_LOAN_INTEREST_DEDUCTION_LIMIT = new BigDecimal("2500");

    /**
     * Optimize tax return for maximum savings
     *
     * @param taxReturn Tax return to optimize
     * @return Optimization results with suggestions
     */
    public TaxOptimizationResult optimize(TaxReturn taxReturn) {
        log.debug("Running tax optimization for return: {}", taxReturn.getId());

        List<TaxSavingOpportunity> suggestions = new ArrayList<>();
        BigDecimal totalPotentialSavings = BigDecimal.ZERO;

        // Check IRA contribution opportunities
        TaxSavingOpportunity iraOpportunity = checkIraContribution(taxReturn);
        if (iraOpportunity != null) {
            suggestions.add(iraOpportunity);
            totalPotentialSavings = totalPotentialSavings.add(iraOpportunity.getPotentialSavings());
        }

        // Check HSA contribution opportunities
        TaxSavingOpportunity hsaOpportunity = checkHsaContribution(taxReturn);
        if (hsaOpportunity != null) {
            suggestions.add(hsaOpportunity);
            totalPotentialSavings = totalPotentialSavings.add(hsaOpportunity.getPotentialSavings());
        }

        // Check student loan interest deduction
        TaxSavingOpportunity studentLoanOpportunity = checkStudentLoanInterest(taxReturn);
        if (studentLoanOpportunity != null) {
            suggestions.add(studentLoanOpportunity);
            totalPotentialSavings = totalPotentialSavings.add(studentLoanOpportunity.getPotentialSavings());
        }

        // Check itemized vs standard deduction
        TaxSavingOpportunity deductionOpportunity = checkDeductionStrategy(taxReturn);
        if (deductionOpportunity != null) {
            suggestions.add(deductionOpportunity);
            totalPotentialSavings = totalPotentialSavings.add(deductionOpportunity.getPotentialSavings());
        }

        // Check tax credit eligibility
        suggestions.addAll(checkTaxCredits(taxReturn));

        log.info("Tax optimization complete: {} opportunities identified, potential savings: ${}",
                suggestions.size(), totalPotentialSavings);

        return TaxOptimizationResult.builder()
                .suggestions(suggestions)
                .savings(totalPotentialSavings)
                .optimizationScore(calculateOptimizationScore(taxReturn, suggestions))
                .build();
    }

    private TaxSavingOpportunity checkIraContribution(TaxReturn taxReturn) {
        // Check if user can contribute to IRA for tax savings
        BigDecimal contributionLimit = IRA_CONTRIBUTION_LIMIT;

        // Additional contribution for age 50+
        if (taxReturn.getPersonalInfo() != null &&
            taxReturn.getPersonalInfo().getDateOfBirth() != null) {
            int age = Year.now().getValue() - taxReturn.getPersonalInfo().getDateOfBirth().getYear();
            if (age >= 50) {
                contributionLimit = IRA_CONTRIBUTION_LIMIT_50_PLUS;
            }
        }

        BigDecimal currentContribution = taxReturn.getIraContributions() != null
            ? taxReturn.getIraContributions()
            : BigDecimal.ZERO;
        BigDecimal remainingContribution = contributionLimit.subtract(currentContribution);

        if (remainingContribution.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimatedSavings = remainingContribution.multiply(new BigDecimal("0.22")); // Assume 22% bracket

            return TaxSavingOpportunity.builder()
                    .category("Retirement Contributions")
                    .title("Maximize IRA Contributions")
                    .description(String.format("You can contribute up to $%s more to your Traditional IRA " +
                            "before the tax deadline and reduce your taxable income.",
                            remainingContribution))
                    .potentialSavings(estimatedSavings)
                    .actionRequired("Contribute to Traditional IRA before April 15")
                    .priority(TaxSavingOpportunity.Priority.HIGH)
                    .build();
        }

        return null;
    }

    private TaxSavingOpportunity checkHsaContribution(TaxReturn taxReturn) {
        if (taxReturn.getHasHealthInsurance() != Boolean.TRUE) {
            return null;
        }

        BigDecimal limit = taxReturn.getFilingStatus().toString().contains("MARRIED")
                ? HSA_CONTRIBUTION_LIMIT_FAMILY
                : HSA_CONTRIBUTION_LIMIT_SINGLE;

        BigDecimal currentContribution = taxReturn.getHsaContributions() != null
                ? taxReturn.getHsaContributions()
                : BigDecimal.ZERO;

        BigDecimal remainingContribution = limit.subtract(currentContribution);

        if (remainingContribution.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimatedSavings = remainingContribution.multiply(new BigDecimal("0.30")); // Tax + FICA savings

            return TaxSavingOpportunity.builder()
                    .category("Health Savings")
                    .title("Maximize HSA Contributions")
                    .description(String.format("Contribute up to $%s more to your HSA to reduce taxable income " +
                            "and save for medical expenses tax-free.", remainingContribution))
                    .potentialSavings(estimatedSavings)
                    .actionRequired("Contribute to HSA before December 31")
                    .priority(TaxSavingOpportunity.Priority.HIGH)
                    .build();
        }

        return null;
    }

    private TaxSavingOpportunity checkStudentLoanInterest(TaxReturn taxReturn) {
        if (taxReturn.getPaidStudentLoanInterest() != Boolean.TRUE) {
            return null;
        }

        BigDecimal currentDeduction = taxReturn.getStudentLoanInterestPaid() != null
                ? taxReturn.getStudentLoanInterestPaid()
                : BigDecimal.ZERO;

        if (currentDeduction.compareTo(STUDENT_LOAN_INTEREST_DEDUCTION_LIMIT) < 0) {
            return TaxSavingOpportunity.builder()
                    .category("Education Deductions")
                    .title("Student Loan Interest Deduction")
                    .description("You may be eligible to deduct up to $2,500 of student loan interest paid.")
                    .potentialSavings(new BigDecimal("550")) // Estimated savings
                    .actionRequired("Gather student loan interest statements (Form 1098-E)")
                    .priority(TaxSavingOpportunity.Priority.MEDIUM)
                    .build();
        }

        return null;
    }

    private TaxSavingOpportunity checkDeductionStrategy(TaxReturn taxReturn) {
        BigDecimal standardDeduction = getStandardDeduction(taxReturn.getFilingStatus().toString());
        BigDecimal itemizedDeductions = taxReturn.getItemizedDeductions() != null
                ? taxReturn.getItemizedDeductions()
                : BigDecimal.ZERO;

        if (itemizedDeductions.compareTo(standardDeduction.multiply(new BigDecimal("0.8"))) > 0
                && itemizedDeductions.compareTo(standardDeduction) < 0) {
            BigDecimal additionalNeeded = standardDeduction.subtract(itemizedDeductions);

            return TaxSavingOpportunity.builder()
                    .category("Deduction Strategy")
                    .title("Consider Additional Charitable Contributions")
                    .description(String.format("You're close to exceeding the standard deduction. " +
                            "Consider additional charitable contributions of $%s to maximize deductions.",
                            additionalNeeded))
                    .potentialSavings(additionalNeeded.multiply(new BigDecimal("0.22")))
                    .actionRequired("Make charitable contributions before December 31")
                    .priority(TaxSavingOpportunity.Priority.MEDIUM)
                    .build();
        }

        return null;
    }

    private List<TaxSavingOpportunity> checkTaxCredits(TaxReturn taxReturn) {
        List<TaxSavingOpportunity> opportunities = new ArrayList<>();

        // Child Tax Credit
        if (taxReturn.getClaimDependents() == Boolean.TRUE &&
            taxReturn.getNumberOfDependents() != null &&
            taxReturn.getNumberOfDependents() > 0) {
            BigDecimal potentialCredit = new BigDecimal(taxReturn.getNumberOfDependents())
                    .multiply(new BigDecimal("2000"));

            opportunities.add(TaxSavingOpportunity.builder()
                    .category("Tax Credits")
                    .title("Child Tax Credit")
                    .description("You may be eligible for Child Tax Credit for your dependents.")
                    .potentialSavings(potentialCredit)
                    .actionRequired("Ensure all dependent information is complete")
                    .priority(TaxSavingOpportunity.Priority.HIGH)
                    .build());
        }

        // Earned Income Tax Credit
        if (taxReturn.getTotalIncome() != null &&
            taxReturn.getTotalIncome().compareTo(new BigDecimal("60000")) < 0) {
            opportunities.add(TaxSavingOpportunity.builder()
                    .category("Tax Credits")
                    .title("Earned Income Tax Credit (EITC)")
                    .description("You may qualify for the Earned Income Tax Credit based on your income level.")
                    .potentialSavings(new BigDecimal("3000")) // Estimated
                    .actionRequired("Review EITC eligibility requirements")
                    .priority(TaxSavingOpportunity.Priority.HIGH)
                    .build());
        }

        return opportunities;
    }

    private BigDecimal getStandardDeduction(String filingStatus) {
        return switch (filingStatus) {
            case "SINGLE" -> new BigDecimal("13850");
            case "MARRIED_FILING_JOINTLY" -> new BigDecimal("27700");
            case "MARRIED_FILING_SEPARATELY" -> new BigDecimal("13850");
            case "HEAD_OF_HOUSEHOLD" -> new BigDecimal("20800");
            default -> new BigDecimal("13850");
        };
    }

    private int calculateOptimizationScore(TaxReturn taxReturn, List<TaxSavingOpportunity> suggestions) {
        // Score from 0-100 based on optimization opportunities captured
        int maxOpportunities = 10;
        int opportunitiesMissed = suggestions.size();
        return Math.max(0, 100 - (opportunitiesMissed * 10));
    }
}
