package com.waqiti.notification.client.fallback;

import com.waqiti.notification.client.UserServiceClient;
import com.waqiti.notification.client.dto.UserProfileResponse;
import com.waqiti.notification.client.dto.UserActivityResponse;
import com.waqiti.notification.client.dto.UserSecurityStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {
    
    @Override
    public UserProfileResponse getUserProfile(UUID userId) {
        log.warn("Fallback: Unable to fetch user profile for userId {} from user service", userId);
        return UserProfileResponse.builder()
            .userId(userId)
            .accountStatus("UNKNOWN")
            .build();
    }
    
    @Override
    public List<UserProfileResponse> getActiveUsers(int days) {
        log.warn("Fallback: Unable to fetch active users for last {} days from user service", days);
        return Collections.emptyList();
    }
    
    @Override
    public List<UserProfileResponse> getUsersWithBirthdayToday() {
        log.warn("Fallback: Unable to fetch users with birthday today from user service");
        return Collections.emptyList();
    }
    
    @Override
    public List<UserProfileResponse> getUsersWithBirthday(LocalDate date) {
        log.warn("Fallback: Unable to fetch users with birthday on {} from user service", date);
        return Collections.emptyList();
    }
    
    @Override
    public List<UserProfileResponse> getUsersWithAccountAnniversaryToday() {
        log.warn("Fallback: Unable to fetch users with account anniversary today from user service");
        return Collections.emptyList();
    }
    
    @Override
    public List<UserProfileResponse> getUsersWithAccountAnniversary(LocalDate date) {
        log.warn("Fallback: Unable to fetch users with account anniversary on {} from user service", date);
        return Collections.emptyList();
    }
    
    @Override
    public UserActivityResponse getUserActivity(UUID userId, LocalDateTime from, LocalDateTime to) {
        log.warn("Fallback: Unable to fetch user activity for userId {} from {} to {} from user service", 
            userId, from, to);
        return UserActivityResponse.builder()
            .userId(userId)
            .totalTransactions(0)
            .activityLevel("UNKNOWN")
            .build();
    }
    
    @Override
    public List<UserSecurityStatusResponse> getUsersNeedingSecurityReview(int monthsSinceReview) {
        log.warn("Fallback: Unable to fetch users needing security review ({} months) from user service", 
            monthsSinceReview);
        return Collections.emptyList();
    }
    
    @Override
    public LocalDateTime getLastSecurityReviewDate(UUID userId) {
        log.warn("Fallback: Unable to fetch last security review date for userId {} from user service", userId);
        return LocalDateTime.now().minusMonths(3);
    }
}