package com.waqiti.common.fraud.profiling;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileRepository {
    
    @CircuitBreaker(name = "profile-repository", fallbackMethod = "getProfileFallback")
    @Retry(name = "profile-repository")
    public UserRiskProfile getProfile(String userId) {
        log.debug("Retrieving risk profile for user: {}", userId);
        
        return UserRiskProfile.builder()
                .userId(userId)
                .overallRiskScore(50.0)
                .riskLevel(UserRiskProfileService.RiskLevel.MEDIUM)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    @CircuitBreaker(name = "profile-repository", fallbackMethod = "saveProfileFallback")
    @Retry(name = "profile-repository")
    public void saveProfile(UserRiskProfile profile) {
        log.info("Saving risk profile for user: {}", profile.getUserId());
    }
    
    @CircuitBreaker(name = "profile-repository", fallbackMethod = "findHighRiskProfilesFallback")
    @Retry(name = "profile-repository")
    public List<UserRiskProfile> findHighRiskProfiles() {
        log.debug("Finding high risk profiles");
        
        return List.of();
    }
    
    @CircuitBreaker(name = "profile-repository", fallbackMethod = "deleteProfileFallback")
    @Retry(name = "profile-repository")
    public void deleteProfile(String userId) {
        log.info("Deleting risk profile for user: {}", userId);
    }
    
    private UserRiskProfile getProfileFallback(String userId, Exception e) {
        log.warn("Profile repository unavailable - returning default profile (fallback): {}", userId);
        return UserRiskProfile.builder()
                .userId(userId)
                .overallRiskScore(50.0)
                .riskLevel(UserRiskProfileService.RiskLevel.MEDIUM)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    private void saveProfileFallback(UserRiskProfile profile, Exception e) {
        log.error("Profile repository unavailable - profile not saved (fallback): {}", profile.getUserId());
    }
    
    private List<UserRiskProfile> findHighRiskProfilesFallback(Exception e) {
        log.warn("Profile repository unavailable - returning empty list (fallback)");
        return List.of();
    }
    
    private void deleteProfileFallback(String userId, Exception e) {
        log.error("Profile repository unavailable - profile not deleted (fallback): {}", userId);
    }
}