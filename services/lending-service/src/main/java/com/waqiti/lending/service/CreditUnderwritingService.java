package com.waqiti.lending.service;

import com.waqiti.lending.domain.LoanApplication;
import com.waqiti.lending.domain.enums.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Credit Underwriting Service
 * Performs automated credit decisioning
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreditUnderwritingService {

    @Value("${lending.underwriting.auto-approval-threshold:750}")
    private int autoApprovalThreshold;

    @Value("${lending.underwriting.auto-reject-threshold:580}")
    private int autoRejectThreshold;

    @Value("${lending.underwriting.manual-review-threshold:650}")
    private int manualReviewThreshold;

    @Value("${lending.loan.max-dti-ratio:0.43}")
    private BigDecimal maxDtiRatio;

    /**
     * Perform automated underwriting decision
     */
    public UnderwritingDecision performUnderwriting(LoanApplication application) {
        log.info("Performing underwriting for application: {}", application.getApplicationId());

        UnderwritingDecision decision = new UnderwritingDecision();
        decision.setApplicationId(application.getApplicationId());

        // Credit score check
        if (application.getCreditScore() == null) {
            decision.setDecision("MANUAL_REVIEW");
            decision.setReason("Missing credit score");
            return decision;
        }

        int creditScore = application.getCreditScore();

        // Auto rejection for very low scores
        if (creditScore < autoRejectThreshold) {
            decision.setDecision("REJECTED");
            decision.setReason("Credit score below minimum threshold");
            decision.setRecommendedStatus(ApplicationStatus.REJECTED);
            return decision;
        }

        // DTI ratio check
        if (application.getDebtToIncomeRatio() != null) {
            if (application.getDebtToIncomeRatio().compareTo(maxDtiRatio) > 0) {
                decision.setDecision("REJECTED");
                decision.setReason("Debt-to-income ratio too high");
                decision.setRecommendedStatus(ApplicationStatus.REJECTED);
                return decision;
            }
        }

        // Manual review for borderline cases
        if (creditScore < manualReviewThreshold) {
            decision.setDecision("MANUAL_REVIEW");
            decision.setReason("Credit score requires manual review");
            decision.setRecommendedStatus(ApplicationStatus.MANUAL_REVIEW);
            return decision;
        }

        // Auto approval for high scores
        if (creditScore >= autoApprovalThreshold) {
            decision.setDecision("APPROVED");
            decision.setReason("Strong credit profile");
            decision.setRecommendedStatus(ApplicationStatus.APPROVED);
            decision.setApprovedAmount(application.getRequestedAmount());
            decision.setApprovedTermMonths(application.getRequestedTermMonths());
            decision.setApprovedInterestRate(calculateInterestRate(creditScore, application.getRequestedTermMonths()));
            return decision;
        }

        // Conditional approval for good scores
        decision.setDecision("CONDITIONALLY_APPROVED");
        decision.setReason("Good credit profile with conditions");
        decision.setRecommendedStatus(ApplicationStatus.CONDITIONALLY_APPROVED);
        decision.setApprovedAmount(application.getRequestedAmount());
        decision.setApprovedTermMonths(application.getRequestedTermMonths());
        decision.setApprovedInterestRate(calculateInterestRate(creditScore, application.getRequestedTermMonths()));

        return decision;
    }

    /**
     * Calculate risk-based interest rate
     */
    public BigDecimal calculateInterestRate(int creditScore, int termMonths) {
        // Risk-based pricing
        BigDecimal baseRate = BigDecimal.valueOf(0.0500); // 5% base rate

        // Adjust based on credit score
        if (creditScore >= 750) {
            baseRate = BigDecimal.valueOf(0.0450); // 4.5%
        } else if (creditScore >= 700) {
            baseRate = BigDecimal.valueOf(0.0500); // 5.0%
        } else if (creditScore >= 650) {
            baseRate = BigDecimal.valueOf(0.0600); // 6.0%
        } else {
            baseRate = BigDecimal.valueOf(0.0750); // 7.5%
        }

        // Adjust based on term
        if (termMonths > 60) {
            baseRate = baseRate.add(BigDecimal.valueOf(0.0050)); // +0.5% for longer terms
        }

        return baseRate.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Underwriting Decision DTO
     */
    @lombok.Data
    public static class UnderwritingDecision {
        private String applicationId;
        private String decision;
        private String reason;
        private ApplicationStatus recommendedStatus;
        private BigDecimal approvedAmount;
        private Integer approvedTermMonths;
        private BigDecimal approvedInterestRate;
    }
}
