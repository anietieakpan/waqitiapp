/**
 * Credit Scoring Service
 * Machine learning-based credit scoring and risk assessment
 */
package com.waqiti.bnpl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.waqiti.bnpl.entity.CreditAssessment;
import com.waqiti.bnpl.entity.CreditAssessment.RiskTier;
import com.waqiti.bnpl.repository.BnplApplicationRepository;
import com.waqiti.bnpl.repository.CreditAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditScoringService {
    
    private final CreditAssessmentRepository assessmentRepository;
    private final BnplApplicationRepository applicationRepository;
    private final BnplInstallmentRepository installmentRepository;
    private final ExternalCreditBureauService creditBureauService;
    private final BankingDataService bankingDataService;
    private final ObjectMapper objectMapper;
    
    // Risk scoring weights
    private static final double CREDIT_SCORE_WEIGHT = 0.35;
    private static final double INCOME_WEIGHT = 0.25;
    private static final double PAYMENT_HISTORY_WEIGHT = 0.20;
    private static final double DEBT_RATIO_WEIGHT = 0.15;
    private static final double ALTERNATIVE_DATA_WEIGHT = 0.05;
    
    /**
     * Performs comprehensive credit assessment for a user
     * Cached for 30 days (matches assessment validity period)
     * Cache key: userId + assessmentType
     */
    @Transactional
    @Cacheable(value = "creditAssessments", key = "#userId + '_' + #type", unless = "#result == null")
    public CreditAssessment performCreditAssessment(UUID userId, UUID applicationId, CreditAssessment.AssessmentType type) {
        log.info("Performing credit assessment for user {} type {} (cache miss)", userId, type);
        
        try {
            // 1. Get external credit score
            CreditBureauData bureauData = creditBureauService.getCreditReport(userId);
            
            // 2. Get banking data
            BankingAnalysis bankingData = bankingDataService.analyzeBankingData(userId);
            
            // 3. Get payment history
            PaymentHistory paymentHistory = getPaymentHistory(userId);
            
            // 4. Calculate risk scores
            RiskScores riskScores = calculateRiskScores(bureauData, bankingData, paymentHistory);
            
            // 5. Determine risk tier and recommended terms
            RiskTier riskTier = determineRiskTier(riskScores.overallScore);
            BigDecimal recommendedLimit = calculateRecommendedLimit(riskScores, bankingData);
            
            // 6. Create assessment
            CreditAssessment assessment = CreditAssessment.builder()
                    .userId(userId)
                    .applicationId(applicationId)
                    .assessmentType(type)
                    .creditScore(bureauData.creditScore)
                    .creditScoreSource(bureauData.source)
                    .scoreDate(bureauData.scoreDate)
                    .monthlyIncome(bankingData.monthlyIncome)
                    .incomeVerified(bankingData.incomeVerified)
                    .employmentStatus(bankingData.employmentStatus)
                    .bankBalance(bankingData.currentBalance)
                    .averageMonthlyBalance(bankingData.averageBalance)
                    .transactionVolumeMonthly(bankingData.monthlyTransactionCount)
                    .overdraftIncidents6m(bankingData.overdraftIncidents)
                    .totalDebt(bureauData.totalDebt)
                    .monthlyDebtPayments(bureauData.monthlyDebtPayments)
                    .debtToIncomeRatio(calculateDebtToIncomeRatio(bureauData, bankingData))
                    .creditUtilizationRatio(bureauData.creditUtilization)
                    .onTimePaymentsPercentage(paymentHistory.onTimePercentage)
                    .latePayments30d(paymentHistory.late30Days)
                    .latePayments60d(paymentHistory.late60Days)
                    .latePayments90d(paymentHistory.late90Days)
                    .chargeOffs(paymentHistory.chargeOffs)
                    .riskTier(riskTier)
                    .riskScore(riskScores.overallScore)
                    .riskFactors(createRiskFactorsJson(riskScores))
                    .socialScore(riskScores.socialScore)
                    .digitalFootprintScore(riskScores.digitalScore)
                    .behavioralScore(riskScores.behavioralScore)
                    .recommendedLimit(recommendedLimit)
                    .recommendedTerms(createRecommendedTermsJson(riskTier, recommendedLimit))
                    .validUntil(LocalDateTime.now().plusDays(90))
                    .isActive(true)
                    .build();
            
            // 7. Deactivate previous assessments
            assessmentRepository.findLatestActiveByUserId(userId)
                    .ifPresent(old -> assessmentRepository.supersede(old.getId(), assessment.getId()));
            
            return assessmentRepository.save(assessment);
            
        } catch (Exception e) {
            log.error("Failed to perform credit assessment for user {}", userId, e);
            throw new CreditAssessmentException("Failed to perform credit assessment", e);
        }
    }
    
    /**
     * Calculates comprehensive risk scores
     */
    private RiskScores calculateRiskScores(CreditBureauData bureauData, BankingAnalysis bankingData, 
                                          PaymentHistory paymentHistory) {
        RiskScores scores = new RiskScores();
        
        // Credit score component (0-100)
        scores.creditScoreComponent = normalizeCreditScore(bureauData.creditScore);
        
        // Income stability component (0-100)
        scores.incomeComponent = calculateIncomeScore(bankingData);
        
        // Payment history component (0-100)
        scores.paymentHistoryComponent = calculatePaymentHistoryScore(paymentHistory);
        
        // Debt ratio component (0-100)
        scores.debtRatioComponent = calculateDebtRatioScore(bureauData, bankingData);
        
        // Alternative data components
        scores.socialScore = calculateSocialScore(bankingData);
        scores.digitalScore = calculateDigitalFootprintScore(bankingData);
        scores.behavioralScore = calculateBehavioralScore(bankingData, paymentHistory);
        
        // Calculate weighted overall score
        scores.overallScore = (int) Math.round(
                scores.creditScoreComponent * CREDIT_SCORE_WEIGHT +
                scores.incomeComponent * INCOME_WEIGHT +
                scores.paymentHistoryComponent * PAYMENT_HISTORY_WEIGHT +
                scores.debtRatioComponent * DEBT_RATIO_WEIGHT +
                ((scores.socialScore + scores.digitalScore + scores.behavioralScore) / 3.0) * ALTERNATIVE_DATA_WEIGHT
        );
        
        return scores;
    }
    
    /**
     * Normalizes credit score to 0-100 scale
     */
    private int normalizeCreditScore(Integer creditScore) {
        if (creditScore == null) return 50; // Default for no credit history
        
        // FICO score range: 300-850
        // Normalize to 0-100
        return Math.max(0, Math.min(100, (creditScore - 300) * 100 / 550));
    }
    
    /**
     * Calculates income stability score
     */
    private int calculateIncomeScore(BankingAnalysis banking) {
        int score = 50; // Base score
        
        // Income verification bonus
        if (banking.incomeVerified) score += 20;
        
        // Income consistency
        if (banking.incomeVariability < 0.15) score += 15; // Less than 15% variation
        
        // Employment status
        if ("EMPLOYED_FULL_TIME".equals(banking.employmentStatus)) score += 15;
        else if ("EMPLOYED_PART_TIME".equals(banking.employmentStatus)) score += 5;
        
        return Math.min(100, score);
    }
    
    /**
     * Calculates payment history score
     */
    private int calculatePaymentHistoryScore(PaymentHistory history) {
        int score = 100; // Start perfect
        
        // Deduct for late payments
        score -= history.late30Days * 5;
        score -= history.late60Days * 10;
        score -= history.late90Days * 20;
        score -= history.chargeOffs * 30;
        
        // Bonus for good history
        if (history.onTimePercentage != null && history.onTimePercentage.compareTo(new BigDecimal("95")) > 0) {
            score += 10;
        }
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * Calculates debt ratio score
     */
    private int calculateDebtRatioScore(CreditBureauData bureau, BankingAnalysis banking) {
        if (banking.monthlyIncome == null || banking.monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return 25; // Low score for no verifiable income
        }
        
        BigDecimal dtiRatio = bureau.monthlyDebtPayments.divide(banking.monthlyIncome, 4, RoundingMode.HALF_UP);
        
        // DTI scoring
        if (dtiRatio.compareTo(new BigDecimal("0.20")) <= 0) return 100;
        else if (dtiRatio.compareTo(new BigDecimal("0.35")) <= 0) return 80;
        else if (dtiRatio.compareTo(new BigDecimal("0.50")) <= 0) return 60;
        else if (dtiRatio.compareTo(new BigDecimal("0.65")) <= 0) return 40;
        else return 20;
    }
    
    /**
     * Determines risk tier based on overall score
     */
    private RiskTier determineRiskTier(int overallScore) {
        if (overallScore >= 80) return RiskTier.LOW;
        else if (overallScore >= 60) return RiskTier.MEDIUM;
        else if (overallScore >= 40) return RiskTier.HIGH;
        else return RiskTier.VERY_HIGH;
    }
    
    /**
     * Calculates recommended credit limit
     */
    private BigDecimal calculateRecommendedLimit(RiskScores scores, BankingAnalysis banking) {
        if (banking.monthlyIncome == null || banking.monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("500"); // Minimum limit
        }
        
        // Base limit on monthly income percentage
        BigDecimal baseLimit = banking.monthlyIncome.multiply(new BigDecimal("0.25"));
        
        // Adjust by risk score
        BigDecimal riskMultiplier = new BigDecimal(scores.overallScore).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal adjustedLimit = baseLimit.multiply(riskMultiplier);
        
        // Apply min/max constraints
        BigDecimal minLimit = new BigDecimal("500");
        BigDecimal maxLimit = new BigDecimal("10000");
        
        return adjustedLimit.max(minLimit).min(maxLimit).setScale(0, RoundingMode.DOWN);
    }
    
    private BigDecimal calculateDebtToIncomeRatio(CreditBureauData bureau, BankingAnalysis banking) {
        if (banking.monthlyIncome == null || banking.monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("CREDIT_RISK: Invalid or zero monthly income detected. Income: {}", banking.monthlyIncome);
            // Return maximum DTI ratio (100%) for conservative credit decision
            // This ensures denial of credit for applicants with no verified income
            return new BigDecimal("1.0000");
        }
        
        if (bureau.monthlyDebtPayments == null) {
            log.warn("CREDIT_RISK: Missing monthly debt payments data, assuming zero debt");
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        
        BigDecimal dtiRatio = bureau.monthlyDebtPayments.divide(banking.monthlyIncome, 4, RoundingMode.HALF_UP);
        
        // Cap DTI ratio at 100% to prevent calculation errors
        if (dtiRatio.compareTo(BigDecimal.ONE) > 0) {
            log.warn("CREDIT_RISK: Calculated DTI ratio exceeds 100%: {}. Capping at 100%", dtiRatio);
            return BigDecimal.ONE;
        }
        
        return dtiRatio;
    }
    
    private JsonNode createRiskFactorsJson(RiskScores scores) {
        ObjectNode factors = objectMapper.createObjectNode();
        List<String> riskFactors = new ArrayList<>();
        
        if (scores.creditScoreComponent < 50) riskFactors.add("Low credit score");
        if (scores.incomeComponent < 50) riskFactors.add("Income instability");
        if (scores.paymentHistoryComponent < 50) riskFactors.add("Poor payment history");
        if (scores.debtRatioComponent < 50) riskFactors.add("High debt-to-income ratio");
        
        factors.put("factors", objectMapper.valueToTree(riskFactors));
        factors.put("scores", objectMapper.valueToTree(scores));
        
        return factors;
    }
    
    private JsonNode createRecommendedTermsJson(RiskTier riskTier, BigDecimal limit) {
        ObjectNode terms = objectMapper.createObjectNode();
        
        switch (riskTier) {
            case LOW:
                terms.put("maxInstallments", 12);
                terms.put("interestRate", 0.0);
                terms.put("downPaymentRequired", false);
                break;
            case MEDIUM:
                terms.put("maxInstallments", 6);
                terms.put("interestRate", 0.0);
                terms.put("downPaymentRequired", false);
                break;
            case HIGH:
                terms.put("maxInstallments", 4);
                terms.put("interestRate", 0.05);
                terms.put("downPaymentRequired", true);
                terms.put("downPaymentPercent", 0.10);
                break;
            case VERY_HIGH:
                terms.put("maxInstallments", 3);
                terms.put("interestRate", 0.10);
                terms.put("downPaymentRequired", true);
                terms.put("downPaymentPercent", 0.25);
                break;
        }
        
        terms.put("creditLimit", limit);
        return terms;
    }
    
    // Alternative scoring methods
    private int calculateSocialScore(BankingAnalysis banking) {
        if (banking == null) {
            return 50; // Default for no data
        }
        
        int baseScore = 50;
        
        // Network quality indicators
        if (banking.avgBalance != null && banking.avgBalance.compareTo(new BigDecimal("2000")) >= 0) {
            baseScore += 10; // Strong financial network indicator
        }
        
        // Transaction diversity (indicates broader financial network)
        if (banking.avgMonthlyTransactions != null) {
            if (banking.avgMonthlyTransactions >= 30) {
                baseScore += 15; // Diverse transaction patterns
            } else if (banking.avgMonthlyTransactions >= 15) {
                baseScore += 10;
            } else if (banking.avgMonthlyTransactions >= 5) {
                baseScore += 5;
            }
        }
        
        // Account longevity (social stability indicator)
        if (banking.accountAge != null) {
            if (banking.accountAge >= 36) {
                baseScore += 10; // Long-term account
            } else if (banking.accountAge >= 12) {
                baseScore += 5;
            }
        }
        
        // Regular payment patterns (indicates stable relationships)
        if (banking.hasRecurringPayments != null && banking.hasRecurringPayments) {
            baseScore += 10; // Regular financial commitments
        }
        
        // Penalty for excessive small transactions (potential churning)
        if (banking.avgTransactionAmount != null && 
            banking.avgTransactionAmount.compareTo(new BigDecimal("10")) < 0 &&
            banking.avgMonthlyTransactions != null && banking.avgMonthlyTransactions > 50) {
            baseScore -= 10;
        }
        
        return Math.max(Math.min(baseScore, 100), 20);
    }
    
    private int calculateDigitalFootprintScore(BankingAnalysis banking) {
        if (banking == null) {
            return 40; // Default for no digital footprint
        }
        
        int baseScore = 30;
        
        // Digital banking adoption (0-25 points)
        if (banking.digitalBankingUsage != null) {
            BigDecimal digitalUsage = banking.digitalBankingUsage;
            if (digitalUsage.compareTo(new BigDecimal("0.9")) >= 0) {
                baseScore += 25; // Heavy digital user
            } else if (digitalUsage.compareTo(new BigDecimal("0.7")) >= 0) {
                baseScore += 20; // Regular digital user
            } else if (digitalUsage.compareTo(new BigDecimal("0.5")) >= 0) {
                baseScore += 15; // Moderate digital user
            } else if (digitalUsage.compareTo(new BigDecimal("0.3")) >= 0) {
                baseScore += 10; // Basic digital user
            }
        }
        
        // Mobile banking usage (0-15 points)
        if (banking.mobileBankingUsage != null) {
            BigDecimal mobileUsage = banking.mobileBankingUsage;
            if (mobileUsage.compareTo(new BigDecimal("0.8")) >= 0) {
                baseScore += 15; // Mobile-first user
            } else if (mobileUsage.compareTo(new BigDecimal("0.5")) >= 0) {
                baseScore += 10; // Regular mobile user
            } else if (mobileUsage.compareTo(new BigDecimal("0.2")) >= 0) {
                baseScore += 5; // Basic mobile user
            }
        }
        
        // Online payment adoption (0-15 points)
        if (banking.onlinePaymentUsage != null) {
            BigDecimal onlineUsage = banking.onlinePaymentUsage;
            if (onlineUsage.compareTo(new BigDecimal("0.8")) >= 0) {
                baseScore += 15; // Heavy online spender
            } else if (onlineUsage.compareTo(new BigDecimal("0.5")) >= 0) {
                baseScore += 10; // Regular online spender
            } else if (onlineUsage.compareTo(new BigDecimal("0.2")) >= 0) {
                baseScore += 5; // Occasional online spender
            }
        }
        
        // Financial app usage diversity (0-10 points)
        if (banking.hasMultipleBankAccounts != null && banking.hasMultipleBankAccounts) {
            baseScore += 5; // Multi-bank user
        }
        if (banking.hasInvestmentAccount != null && banking.hasInvestmentAccount) {
            baseScore += 3; // Investment app user
        }
        if (banking.hasCreditCard != null && banking.hasCreditCard) {
            baseScore += 2; // Credit product user
        }
        
        // Technology adoption speed (0-5 points)
        if (banking.accountAge != null && banking.digitalBankingUsage != null) {
            // Users who adopted digital banking quickly score higher
            if (banking.accountAge <= 12 && banking.digitalBankingUsage.compareTo(new BigDecimal("0.8")) >= 0) {
                baseScore += 5; // Early adopter
            }
        }
        
        return Math.max(Math.min(baseScore, 100), 20);
    }
    
    private int calculateBehavioralScore(BankingAnalysis banking, PaymentHistory history) {
        if (banking == null && history == null) {
            return 50; // Default for no behavioral data
        }
        
        int baseScore = 50;
        
        // Payment timing patterns (0-20 points)
        if (history != null && history.onTimePercentage != null) {
            BigDecimal onTimePercent = history.onTimePercentage;
            if (onTimePercent.compareTo(new BigDecimal("95")) >= 0) {
                baseScore += 20; // Consistently on-time
            } else if (onTimePercent.compareTo(new BigDecimal("85")) >= 0) {
                baseScore += 15; // Usually on-time
            } else if (onTimePercent.compareTo(new BigDecimal("70")) >= 0) {
                baseScore += 10; // Mostly on-time
            } else if (onTimePercent.compareTo(new BigDecimal("50")) >= 0) {
                baseScore += 5; // Sometimes on-time
            }
        }
        
        // Spending pattern consistency (0-15 points)
        if (banking != null && banking.spendingVariability != null) {
            BigDecimal variability = banking.spendingVariability;
            if (variability.compareTo(new BigDecimal("0.2")) <= 0) {
                baseScore += 15; // Very consistent spending
            } else if (variability.compareTo(new BigDecimal("0.4")) <= 0) {
                baseScore += 10; // Consistent spending
            } else if (variability.compareTo(new BigDecimal("0.6")) <= 0) {
                baseScore += 5; // Moderately consistent
            }
        }
        
        // Financial discipline indicators (0-10 points)
        if (banking != null) {
            // Regular savings behavior
            if (banking.avgMonthlySavings != null && banking.avgMonthlySavings.compareTo(BigDecimal.ZERO) > 0) {
                if (banking.avgMonthlySavings.compareTo(new BigDecimal("500")) >= 0) {
                    baseScore += 10; // Strong saver
                } else if (banking.avgMonthlySavings.compareTo(new BigDecimal("100")) >= 0) {
                    baseScore += 7; // Regular saver
                } else {
                    baseScore += 3; // Basic saver
                }
            }
        }
        
        // Risk-taking behavior analysis (0-10 points)
        if (banking != null) {
            // Investment account indicates calculated risk-taking
            if (banking.hasInvestmentAccount != null && banking.hasInvestmentAccount) {
                baseScore += 5; // Calculated risk-taker
            }
            
            // Multiple financial products indicate financial sophistication
            if (banking.hasMultipleAccounts != null && banking.hasMultipleAccounts) {
                baseScore += 3;
            }
            
            // Credit card management
            if (banking.hasCreditCard != null && banking.hasCreditCard && 
                banking.overdraftCount != null && banking.overdraftCount == 0) {
                baseScore += 2; // Good credit management
            }
        }
        
        // Negative behavioral indicators
        if (banking != null) {
            // Frequent overdrafts indicate poor money management
            if (banking.overdraftCount != null && banking.overdraftCount > 0) {
                baseScore -= Math.min(banking.overdraftCount * 5, 20);
            }
            
            // Extreme spending variability indicates instability
            if (banking.spendingVariability != null && 
                banking.spendingVariability.compareTo(new BigDecimal("0.8")) > 0) {
                baseScore -= 10;
            }
        }
        
        // Late payment penalties from history
        if (history != null) {
            if (history.late30Days != null && history.late30Days > 0) {
                baseScore -= Math.min(history.late30Days * 2, 10);
            }
            if (history.late60Days != null && history.late60Days > 0) {
                baseScore -= Math.min(history.late60Days * 4, 15);
            }
            if (history.late90Days != null && history.late90Days > 0) {
                baseScore -= Math.min(history.late90Days * 6, 20);
            }
        }
        
        return Math.max(Math.min(baseScore, 100), 10);
    }
    
    private PaymentHistory getPaymentHistory(UUID userId) {
        // Aggregate payment history from various sources
        PaymentHistory history = new PaymentHistory();
        
        // Get internal BNPL history
        Integer rejectionCount = applicationRepository.getRecentRejectionCount(userId, LocalDateTime.now().minusMonths(6));
        Integer defaultCount = applicationRepository.getDefaultCount(userId);
        
        history.late30Days = 0; // Would be calculated from installment history
        history.late60Days = 0;
        history.late90Days = 0;
        history.chargeOffs = defaultCount;
        // Calculate actual on-time percentage from installment data
        List<BnplInstallmentRepository.InstallmentPayment> recentPayments = installmentRepository.findByUserIdAndDueDateAfter(
            userId, LocalDateTime.now().minusMonths(12)
        );
        
        if (!recentPayments.isEmpty()) {
            long onTimeCount = recentPayments.stream()
                .mapToLong(payment -> payment.getPaidAt() != null && 
                    !payment.getPaidAt().isAfter(payment.getDueDate().plusDays(1)) ? 1 : 0)
                .sum();
            
            history.onTimePercentage = new BigDecimal(onTimeCount)
                .divide(new BigDecimal(recentPayments.size()), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        } else {
            history.onTimePercentage = new BigDecimal("95"); // Default for new users
        }
        
        return history;
    }
    
    // Data classes
    private static class RiskScores {
        int creditScoreComponent;
        int incomeComponent;
        int paymentHistoryComponent;
        int debtRatioComponent;
        int socialScore;
        int digitalScore;
        int behavioralScore;
        int overallScore;
    }
    
    private static class CreditBureauData {
        Integer creditScore;
        String source;
        LocalDate scoreDate;
        BigDecimal totalDebt;
        BigDecimal monthlyDebtPayments;
        BigDecimal creditUtilization;
    }
    
    private static class BankingAnalysis {
        BigDecimal monthlyIncome;
        boolean incomeVerified;
        String employmentStatus;
        BigDecimal currentBalance;
        BigDecimal averageBalance;
        Integer monthlyTransactionCount;
        Integer overdraftIncidents;
        double incomeVariability;
    }
    
    private static class PaymentHistory {
        Integer late30Days;
        Integer late60Days;
        Integer late90Days;
        Integer chargeOffs;
        BigDecimal onTimePercentage;
    }
    
    /**
     * Invalidates cached credit assessment for a user
     * Call this when user's financial situation changes significantly
     */
    @CacheEvict(value = "creditAssessments", key = "#userId + '_*'")
    public void invalidateCreditAssessment(UUID userId) {
        log.info("Invalidating credit assessment cache for user: {}", userId);
    }

    /**
     * Invalidates all cached credit assessments
     * Use sparingly - only for system-wide updates
     */
    @CacheEvict(value = "creditAssessments", allEntries = true)
    public void invalidateAllCreditAssessments() {
        log.warn("Invalidating ALL credit assessment caches");
    }

    public static class CreditAssessmentException extends RuntimeException {
        public CreditAssessmentException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}