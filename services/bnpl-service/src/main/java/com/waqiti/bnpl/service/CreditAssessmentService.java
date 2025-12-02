package com.waqiti.bnpl.service;

import com.waqiti.bnpl.domain.CreditAssessment;
import com.waqiti.bnpl.domain.enums.*;
import com.waqiti.bnpl.dto.request.CreditCheckRequest;
import com.waqiti.bnpl.dto.response.CreditScoreDto;
import com.waqiti.bnpl.exception.CreditCheckFailedException;
import com.waqiti.bnpl.repository.CreditAssessmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Service for performing credit assessments
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CreditAssessmentService {

    private final CreditAssessmentRepository assessmentRepository;
    private final WebClient webClient;
    
    @Value("${bnpl.credit.max-limit:5000}")
    private BigDecimal defaultMaxCreditLimit;
    
    @Value("${bnpl.credit.min-score:600}")
    private Integer minCreditScore;
    
    @Value("${bnpl.credit.assessment-validity-days:30}")
    private Integer assessmentValidityDays;

    /**
     * Perform credit assessment for a user
     */
    @Transactional
    public CreditAssessment performAssessment(String userId, BigDecimal requestedAmount) {
        log.info("Performing credit assessment for user: {} amount: {}", userId, requestedAmount);
        
        // Check for existing valid assessment
        CreditAssessment existingAssessment = assessmentRepository
                .findValidAssessmentForUser(userId, LocalDateTime.now().minusDays(assessmentValidityDays))
                .orElse(null);
        
        if (existingAssessment != null) {
            log.info("Using existing valid assessment: {}", existingAssessment.getId());
            return existingAssessment;
        }
        
        // Perform new assessment
        CreditAssessment assessment = new CreditAssessment();
        assessment.setUserId(userId);
        assessment.setRequestedAmount(requestedAmount);
        assessment.setAssessmentDate(LocalDateTime.now());
        assessment.setExpiryDate(LocalDateTime.now().plusDays(assessmentValidityDays));
        
        // Get credit score
        CreditScoreDto creditScore = getCreditScore(userId);
        assessment.setCreditScore(creditScore.getScore());
        assessment.setCreditScoreProvider(creditScore.getProvider());
        
        // Determine risk level
        RiskLevel riskLevel = calculateRiskLevel(creditScore.getScore());
        assessment.setRiskLevel(riskLevel);
        
        // Calculate credit limit based on score and risk
        BigDecimal creditLimit = calculateCreditLimit(creditScore.getScore(), riskLevel);
        assessment.setCreditLimit(creditLimit);
        assessment.setAvailableCredit(creditLimit);
        
        // Determine approval
        if (creditScore.getScore() >= minCreditScore && 
            requestedAmount.compareTo(creditLimit) <= 0) {
            assessment.setStatus(AssessmentStatus.APPROVED);
            assessment.setMaxApprovedAmount(requestedAmount);
            assessment.setDecisionReason("Credit score meets requirements");
            
            // Set terms based on risk level
            setApprovalTerms(assessment, riskLevel);
        } else {
            assessment.setStatus(AssessmentStatus.REJECTED);
            assessment.setMaxApprovedAmount(BigDecimal.ZERO);
            assessment.setDecisionReason(creditScore.getScore() < minCreditScore ? 
                    "Credit score below minimum" : "Requested amount exceeds credit limit");
        }
        
        // Add metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("income_verified", creditScore.isIncomeVerified());
        metadata.put("employment_status", creditScore.getEmploymentStatus());
        metadata.put("account_age_months", creditScore.getAccountAgeMonths());
        metadata.put("previous_defaults", creditScore.getPreviousDefaults());
        assessment.setMetadata(metadata);
        
        assessment = assessmentRepository.save(assessment);
        log.info("Credit assessment completed: {} status: {}", assessment.getId(), assessment.getStatus());
        
        return assessment;
    }

    /**
     * Update credit utilization
     */
    @Transactional
    public void updateCreditUtilization(Long assessmentId, BigDecimal amount) {
        CreditAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        BigDecimal newUtilization = assessment.getUtilizedCredit().add(amount);
        if (newUtilization.compareTo(assessment.getCreditLimit()) > 0) {
            throw new CreditCheckFailedException("Credit limit would be exceeded");
        }
        
        assessment.setUtilizedCredit(newUtilization);
        assessment.setAvailableCredit(assessment.getCreditLimit().subtract(newUtilization));
        assessmentRepository.save(assessment);
    }

    /**
     * Release credit utilization
     */
    @Transactional
    public void releaseCreditUtilization(Long assessmentId, BigDecimal amount) {
        CreditAssessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));
        
        BigDecimal newUtilization = assessment.getUtilizedCredit().subtract(amount);
        if (newUtilization.compareTo(BigDecimal.ZERO) < 0) {
            newUtilization = BigDecimal.ZERO;
        }
        
        assessment.setUtilizedCredit(newUtilization);
        assessment.setAvailableCredit(assessment.getCreditLimit().subtract(newUtilization));
        assessmentRepository.save(assessment);
    }

    /**
     * Get credit score from external provider
     */
    private CreditScoreDto getCreditScore(String userId) {
        // In production, this would call an actual credit bureau API
        // For demo purposes, we'll simulate the score
        
        return simulateCreditScore(userId);
    }

    /**
     * Simulate credit score for demo
     */
    private CreditScoreDto simulateCreditScore(String userId) {
        // Generate consistent score for the same user
        int hash = userId.hashCode();
        int score = 300 + Math.abs(hash % 550); // Score between 300-850
        
        CreditScoreDto creditScore = new CreditScoreDto();
        creditScore.setUserId(userId);
        creditScore.setScore(score);
        creditScore.setProvider("SimulatedBureau");
        creditScore.setRetrievedAt(LocalDateTime.now());
        creditScore.setIncomeVerified(score > 650);
        creditScore.setEmploymentStatus(score > 600 ? "EMPLOYED" : "UNKNOWN");
        creditScore.setAccountAgeMonths(12 + (hash % 48));
        creditScore.setPreviousDefaults(score < 550 ? 1 : 0);
        
        return creditScore;
    }

    /**
     * Calculate risk level based on credit score
     */
    private RiskLevel calculateRiskLevel(int creditScore) {
        if (creditScore >= 750) {
            return RiskLevel.LOW;
        } else if (creditScore >= 650) {
            return RiskLevel.MEDIUM;
        } else if (creditScore >= 550) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.VERY_HIGH;
        }
    }

    /**
     * Calculate credit limit based on score and risk
     */
    private BigDecimal calculateCreditLimit(int creditScore, RiskLevel riskLevel) {
        BigDecimal baseLimit = defaultMaxCreditLimit;
        
        // Adjust based on credit score
        BigDecimal scoreMultiplier = BigDecimal.valueOf(creditScore)
                .divide(BigDecimal.valueOf(850), 2, RoundingMode.HALF_UP);
        
        // Adjust based on risk level
        BigDecimal riskMultiplier = switch (riskLevel) {
            case LOW -> BigDecimal.ONE;
            case MEDIUM -> new BigDecimal("0.75");
            case HIGH -> new BigDecimal("0.50");
            case VERY_HIGH -> new BigDecimal("0.25");
        };
        
        return baseLimit.multiply(scoreMultiplier)
                .multiply(riskMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Set approval terms based on risk level
     */
    private void setApprovalTerms(CreditAssessment assessment, RiskLevel riskLevel) {
        switch (riskLevel) {
            case LOW -> {
                assessment.setInterestRate(BigDecimal.ZERO);
                assessment.setMaxInstallments(12);
                assessment.setMinDownPaymentPercentage(BigDecimal.ZERO);
            }
            case MEDIUM -> {
                assessment.setInterestRate(new BigDecimal("5.00"));
                assessment.setMaxInstallments(6);
                assessment.setMinDownPaymentPercentage(new BigDecimal("10.00"));
            }
            case HIGH -> {
                assessment.setInterestRate(new BigDecimal("10.00"));
                assessment.setMaxInstallments(4);
                assessment.setMinDownPaymentPercentage(new BigDecimal("20.00"));
            }
            case VERY_HIGH -> {
                assessment.setInterestRate(new BigDecimal("15.00"));
                assessment.setMaxInstallments(3);
                assessment.setMinDownPaymentPercentage(new BigDecimal("30.00"));
            }
        }
    }
}