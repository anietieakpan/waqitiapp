package com.waqiti.common.fraud.profiling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced user risk profiling service for fraud detection.
 * Creates and maintains comprehensive risk profiles for users based on
 * transaction behavior, patterns, and external risk indicators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRiskProfileService {
    
    private final BehavioralAnalysisEngine behavioralEngine;
    private final RiskFactorCalculator riskCalculator;
    private final ProfileRepository profileRepository;
    private final ExternalRiskDataProvider externalDataProvider;
    
    /**
     * Create or update user risk profile
     */
    public CompletableFuture<UserRiskProfile> createOrUpdateProfile(String userId) {
        log.info("Creating/updating risk profile for user: {}", userId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get existing profile or create new one
                UserRiskProfile existingProfile = profileRepository.getProfile(userId);
                
                // Gather risk data
                BehavioralRiskData behavioralData = behavioralEngine.analyzeBehavior(userId);
                TransactionalRiskData transactionalData = analyzeTransactionalRisk(userId);
                ExternalRiskData externalData = externalDataProvider.getRiskData(userId);
                
                // Calculate risk scores
                RiskScores riskScores = calculateComprehensiveRiskScores(
                    behavioralData, transactionalData, externalData);
                
                // Build updated profile
                UserRiskProfile updatedProfile = buildRiskProfile(
                    userId, existingProfile, behavioralData, transactionalData, 
                    externalData, riskScores);
                
                // Save profile
                profileRepository.saveProfile(updatedProfile);
                
                log.info("Risk profile updated for user: {} with overall score: {}", 
                    userId, updatedProfile.getOverallRiskScore());
                
                return updatedProfile;
                
            } catch (Exception e) {
                log.error("Error creating/updating risk profile for user: {}", userId, e);
                throw new RuntimeException("Failed to update risk profile", e);
            }
        });
    }
    
    /**
     * Get current user risk profile
     */
    public CompletableFuture<UserRiskProfile> getUserProfile(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            UserRiskProfile profile = profileRepository.getProfile(userId);
            
            if (profile == null) {
                // Create initial profile
                return createOrUpdateProfile(userId).join();
            }
            
            // Check if profile needs refresh
            if (profile.needsRefresh()) {
                return createOrUpdateProfile(userId).join();
            }
            
            return profile;
        });
    }
    
    /**
     * Calculate comprehensive risk scores
     */
    private RiskScores calculateComprehensiveRiskScores(BehavioralRiskData behavioral,
                                                       TransactionalRiskData transactional,
                                                       ExternalRiskData external) {
        
        // Individual risk component scores
        double behavioralScore = behavioral.calculateRiskScore();
        double transactionalScore = transactional.calculateRiskScore();
        double externalScore = external.calculateRiskScore();
        
        // Weighted overall score
        double overallScore = (behavioralScore * 0.4) + 
                             (transactionalScore * 0.4) + 
                             (externalScore * 0.2);
        
        // Risk level determination
        RiskLevel riskLevel = determineRiskLevel(overallScore);
        
        return RiskScores.builder()
                .behavioralRisk(behavioralScore)
                .transactionalRisk(transactionalScore)
                .externalRisk(externalScore)
                .overallRisk(overallScore)
                .riskLevel(riskLevel)
                .calculatedAt(LocalDateTime.now())
                .build();
    }
    
    /**
     * Analyze transactional risk
     */
    private TransactionalRiskData analyzeTransactionalRisk(String userId) {
        // Implementation would analyze transaction patterns, amounts, frequency, etc.
        return TransactionalRiskData.builder()
                .userId(userId)
                .velocityRisk(0.3)
                .amountRisk(0.2)
                .patternRisk(0.1)
                .merchantRisk(0.1)
                .analysisDate(LocalDateTime.now())
                .build();
    }
    
    /**
     * Build complete risk profile
     */
    private UserRiskProfile buildRiskProfile(String userId,
                                           UserRiskProfile existingProfile,
                                           BehavioralRiskData behavioral,
                                           TransactionalRiskData transactional,
                                           ExternalRiskData external,
                                           RiskScores riskScores) {
        
        UserRiskProfile.UserRiskProfileBuilder builder = UserRiskProfile.builder()
                .userId(userId)
                .profileVersion(getNextVersion(existingProfile))
                .behavioralData(behavioral)
                .transactionalData(transactional)
                .externalData(external)
                .riskScores(riskScores)
                .overallRiskScore(riskScores.getOverallRisk())
                .riskLevel(riskScores.getRiskLevel())
                .lastUpdated(LocalDateTime.now())
                .profileStatus(ProfileStatus.ACTIVE);
        
        // Preserve creation date from existing profile
        if (existingProfile != null) {
            builder.createdAt(existingProfile.getCreatedAt());
            builder.updateHistory(appendUpdateHistory(existingProfile, riskScores));
        } else {
            builder.createdAt(LocalDateTime.now());
        }
        
        return builder.build();
    }
    
    // Helper methods
    
    private RiskLevel determineRiskLevel(double score) {
        if (score >= 0.8) return RiskLevel.CRITICAL;
        if (score >= 0.6) return RiskLevel.HIGH;
        if (score >= 0.4) return RiskLevel.MEDIUM;
        if (score >= 0.2) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }
    
    private String getNextVersion(UserRiskProfile existingProfile) {
        if (existingProfile == null) {
            return "1.0";
        }
        
        String[] parts = existingProfile.getProfileVersion().split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        
        return major + "." + (minor + 1);
    }
    
    private List<ProfileUpdate> appendUpdateHistory(UserRiskProfile existing, RiskScores newScores) {
        List<ProfileUpdate> history = existing.getUpdateHistory();
        if (history == null) {
            history = new java.util.ArrayList<>();
        }
        
        ProfileUpdate update = ProfileUpdate.builder()
                .updateDate(LocalDateTime.now())
                .previousScore(existing.getOverallRiskScore())
                .newScore(newScores.getOverallRisk())
                .updateReason("Scheduled profile refresh")
                .build();
        
        history.add(update);
        
        // Keep only last 50 updates
        if (history.size() > 50) {
            history = history.subList(history.size() - 50, history.size());
        }
        
        return history;
    }
    
    // Supporting enums and interfaces
    
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum ProfileStatus {
        ACTIVE, SUSPENDED, UNDER_REVIEW, CLOSED
    }
    
    // Placeholder interfaces - would be implemented by concrete classes
    
    interface BehavioralAnalysisEngine {
        BehavioralRiskData analyzeBehavior(String userId);
    }
    
    interface RiskFactorCalculator {
        double calculateRisk(Map<String, Object> factors);
    }
    
    interface ProfileRepository {
        UserRiskProfile getProfile(String userId);
        void saveProfile(UserRiskProfile profile);
    }
    
    interface ExternalRiskDataProvider {
        ExternalRiskData getRiskData(String userId);
    }
}