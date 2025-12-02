package com.waqiti.rewards.client;

import com.waqiti.rewards.dto.UserDetailsDto;
import com.waqiti.rewards.dto.UserPreferencesDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for User Service Client
 * 
 * User details are needed for rewards eligibility but should not block
 * reward accrual. Use cached data where possible.
 * 
 * @author Waqiti Platform Team
 * @since Phase 1 Remediation - Session 6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClientFallback implements UserServiceClient {
    
    @Override
    public UserDetailsDto getUserDetails(String userId, String authorization) {
        log.warn("FALLBACK: Cannot retrieve user details. UserId: {}", userId);
        
        return UserDetailsDto.builder()
                .userId(userId)
                .status("DETAILS_UNAVAILABLE")
                .tier("UNKNOWN")
                .fallbackActivated(true)
                .build();
    }
    
    @Override
    public UserPreferencesDto getUserPreferences(String userId, String authorization) {
        log.warn("FALLBACK: Cannot retrieve user preferences. UserId: {}", userId);
        
        return UserPreferencesDto.builder()
                .userId(userId)
                .rewardsEnabled(true) // Default to enabled
                .fallbackActivated(true)
                .build();
    }
}