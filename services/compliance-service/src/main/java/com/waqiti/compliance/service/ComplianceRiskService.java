package com.waqiti.compliance.service;

import com.waqiti.compliance.model.ComplianceRiskProfile;
import com.waqiti.compliance.repository.ComplianceRiskProfileRepository;
import com.waqiti.common.audit.ComprehensiveAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Compliance Risk Service
 * 
 * CRITICAL: Manages compliance risk profiles and risk scoring for users.
 * Provides comprehensive risk assessment and monitoring capabilities.
 * 
 * COMPLIANCE IMPACT:
 * - Supports risk-based compliance monitoring per BSA requirements
 * - Maintains risk profiles for regulatory reporting
 * - Enables risk-based transaction monitoring
 * - Supports enhanced due diligence requirements
 * 
 * BUSINESS IMPACT:
 * - Enables risk-based customer management
 * - Supports automated compliance decisions
 * - Reduces false positives through accurate risk scoring
 * - Prevents regulatory penalties through proper risk management
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ComplianceRiskService {

    private final ComplianceRiskProfileRepository riskProfileRepository;
    private final ComprehensiveAuditService auditService;

    /**
     * Get user compliance risk profile
     */
    public ComplianceRiskProfile getUserComplianceProfile(UUID userId) {
        return riskProfileRepository.findByUserId(userId)
            .orElse(createDefaultRiskProfile(userId));
    }

    /**
     * Update user risk profile
     */
    public ComplianceRiskProfile updateRiskProfile(UUID userId, double newRiskScore, 
                                                 String tier, String reason, LocalDateTime effectiveDate) {
        
        log.info("RISK_UPDATE: Updating risk profile for user {} score: {} tier: {}", 
                userId, newRiskScore, tier);
        
        try {
            ComplianceRiskProfile profile = getUserComplianceProfile(userId);
            
            Double previousScore = profile.getCurrentRiskScore();
            String previousRating = profile.getRiskRating();
            
            // Update risk profile
            profile.setPreviousRiskScore(previousScore);
            profile.setCurrentRiskScore(newRiskScore);
            profile.setRiskRating(calculateRiskRating(newRiskScore));
            profile.setKycTier(tier);
            profile.setLastUpdateReason(reason);
            profile.setLastAssessmentDate(effectiveDate);
            profile.setUpdatedAt(LocalDateTime.now());
            
            ComplianceRiskProfile savedProfile = riskProfileRepository.save(profile);
            
            // Audit risk profile update
            auditService.auditCriticalComplianceEvent(
                "RISK_PROFILE_UPDATED",
                userId.toString(),
                "Risk profile updated for user",
                Map.of(
                    "userId", userId,
                    "previousScore", previousScore != null ? previousScore : 0.0,
                    "newScore", newRiskScore,
                    "previousRating", previousRating != null ? previousRating : "UNKNOWN",
                    "newRating", profile.getRiskRating(),
                    "tier", tier,
                    "reason", reason
                )
            );
            
            log.info("RISK_UPDATE: Risk profile updated for user {} from {} to {}", 
                    userId, previousScore, newRiskScore);
            
            return savedProfile;
            
        } catch (Exception e) {
            log.error("RISK_UPDATE: Failed to update risk profile for user {}", userId, e);
            throw new RuntimeException("Failed to update risk profile", e);
        }
    }

    /**
     * Calculate risk score for user
     */
    public double calculateRiskScore(UUID userId, String tier, Map<String, Object> riskFactors) {
        try {
            double baseScore = getBaseTierRiskScore(tier);
            double factorAdjustment = calculateRiskFactorAdjustment(riskFactors);
            
            double finalScore = Math.max(0.0, Math.min(100.0, baseScore + factorAdjustment));
            
            log.debug("RISK_CALC: Calculated risk score for user {} tier: {} score: {}", 
                    userId, tier, finalScore);
            
            return finalScore;
            
        } catch (Exception e) {
            log.error("Failed to calculate risk score for user {}", userId, e);
            return 50.0; // Default medium risk
        }
    }

    /**
     * Assess risk for user
     */
    public ComplianceRiskProfile assessUserRisk(UUID userId, Map<String, Object> assessmentData) {
        log.info("RISK_ASSESS: Assessing risk for user {}", userId);
        
        try {
            ComplianceRiskProfile profile = getUserComplianceProfile(userId);
            
            // Calculate new risk score
            double newScore = calculateComprehensiveRiskScore(userId, assessmentData);
            String newRating = calculateRiskRating(newScore);
            
            // Update profile
            profile.setPreviousRiskScore(profile.getCurrentRiskScore());
            profile.setCurrentRiskScore(newScore);
            profile.setRiskRating(newRating);
            profile.setLastAssessmentDate(LocalDateTime.now());
            profile.setAssessmentCount(profile.getAssessmentCount() + 1);
            
            // Update risk factors
            updateRiskFactors(profile, assessmentData);
            
            ComplianceRiskProfile savedProfile = riskProfileRepository.save(profile);
            
            // Audit risk assessment
            auditService.auditCriticalComplianceEvent(
                "RISK_ASSESSMENT_COMPLETED",
                userId.toString(),
                "Risk assessment completed for user",
                Map.of(
                    "userId", userId,
                    "newScore", newScore,
                    "newRating", newRating,
                    "assessmentCount", profile.getAssessmentCount()
                )
            );
            
            log.info("RISK_ASSESS: Risk assessment completed for user {} score: {} rating: {}", 
                    userId, newScore, newRating);
            
            return savedProfile;
            
        } catch (Exception e) {
            log.error("RISK_ASSESS: Failed to assess risk for user {}", userId, e);
            throw new RuntimeException("Failed to assess user risk", e);
        }
    }

    /**
     * Check if user requires enhanced monitoring
     */
    public boolean requiresEnhancedMonitoring(UUID userId) {
        try {
            ComplianceRiskProfile profile = getUserComplianceProfile(userId);
            return profile.getCurrentRiskScore() >= 70.0 || 
                   "HIGH".equals(profile.getRiskRating()) || 
                   "VERY_HIGH".equals(profile.getRiskRating());
        } catch (Exception e) {
            log.error("Failed to check enhanced monitoring requirement for user {}", userId, e);
            return false;
        }
    }

    // Helper methods

    private ComplianceRiskProfile createDefaultRiskProfile(UUID userId) {
        ComplianceRiskProfile profile = new ComplianceRiskProfile();
        profile.setUserId(userId);
        profile.setCurrentRiskScore(25.0); // Default low-medium risk
        profile.setRiskRating("MEDIUM");
        profile.setKycTier("BASIC");
        profile.setAssessmentCount(0);
        profile.setLastAssessmentDate(LocalDateTime.now());
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        return profile;
    }

    private String calculateRiskRating(double riskScore) {
        if (riskScore >= 80.0) return "VERY_HIGH";
        if (riskScore >= 60.0) return "HIGH";
        if (riskScore >= 40.0) return "MEDIUM";
        if (riskScore >= 20.0) return "LOW";
        return "VERY_LOW";
    }

    private double getBaseTierRiskScore(String tier) {
        return switch (tier) {
            case "BASIC" -> 35.0;
            case "STANDARD" -> 25.0;
            case "PREMIUM" -> 15.0;
            case "VIP" -> 10.0;
            default -> 50.0;
        };
    }

    private double calculateRiskFactorAdjustment(Map<String, Object> riskFactors) {
        double adjustment = 0.0;
        
        if (riskFactors == null) return adjustment;
        
        // Country risk
        String country = (String) riskFactors.get("country");
        if (isHighRiskCountry(country)) {
            adjustment += 15.0;
        }
        
        // Transaction patterns
        Boolean hasUnusualPatterns = (Boolean) riskFactors.get("unusualPatterns");
        if (Boolean.TRUE.equals(hasUnusualPatterns)) {
            adjustment += 10.0;
        }
        
        // PEP status
        Boolean isPep = (Boolean) riskFactors.get("isPep");
        if (Boolean.TRUE.equals(isPep)) {
            adjustment += 20.0;
        }
        
        // Sanctions screening
        Boolean hasSanctionsMatch = (Boolean) riskFactors.get("sanctionsMatch");
        if (Boolean.TRUE.equals(hasSanctionsMatch)) {
            adjustment += 25.0;
        }
        
        return adjustment;
    }

    private double calculateComprehensiveRiskScore(UUID userId, Map<String, Object> assessmentData) {
        double riskScore = 0.0;
        
        if (assessmentData.containsKey("transactionVolume")) {
            BigDecimal volume = (BigDecimal) assessmentData.get("transactionVolume");
            if (volume.compareTo(new BigDecimal("100000")) > 0) {
                riskScore += 15.0;
            } else if (volume.compareTo(new BigDecimal("50000")) > 0) {
                riskScore += 10.0;
            }
        }
        
        if (assessmentData.containsKey("country")) {
            String country = (String) assessmentData.get("country");
            if (isHighRiskCountry(country)) {
                riskScore += 20.0;
            }
        }
        
        if (assessmentData.containsKey("transactionCount")) {
            Integer count = (Integer) assessmentData.get("transactionCount");
            if (count != null && count > 100) {
                riskScore += 10.0;
            }
        }
        
        if (assessmentData.containsKey("hasViolations")) {
            Boolean hasViolations = (Boolean) assessmentData.get("hasViolations");
            if (Boolean.TRUE.equals(hasViolations)) {
                riskScore += 25.0;
            }
        }
        
        return Math.min(riskScore, 100.0);
    }

    private void updateRiskFactors(ComplianceRiskProfile profile, Map<String, Object> assessmentData) {
        // Update risk factors in the profile based on assessment data
        // Implementation would map assessment data to profile fields
    }

    private boolean isHighRiskCountry(String country) {
        // Implementation would check against FATF high-risk countries list
        return country != null && 
               (country.equals("Iran") || country.equals("North Korea") || 
                country.equals("Syria") || country.equals("Cuba"));
    }
}