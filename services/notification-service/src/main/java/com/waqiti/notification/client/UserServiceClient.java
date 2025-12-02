package com.waqiti.notification.client;

import com.waqiti.notification.client.dto.UserProfileResponse;
import com.waqiti.notification.client.dto.UserActivityResponse;
import com.waqiti.notification.client.dto.UserSecurityStatusResponse;
import com.waqiti.notification.client.fallback.UserServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "user-service",
    fallback = UserServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface UserServiceClient {
    
    @GetMapping("/api/v1/users/{userId}")
    UserProfileResponse getUserProfile(@PathVariable("userId") UUID userId);
    
    @GetMapping("/api/v1/users/active")
    List<UserProfileResponse> getActiveUsers(
        @RequestParam(value = "days", defaultValue = "7") int days
    );
    
    @GetMapping("/api/v1/users/birthdays/today")
    List<UserProfileResponse> getUsersWithBirthdayToday();
    
    @GetMapping("/api/v1/users/birthdays")
    List<UserProfileResponse> getUsersWithBirthday(
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );
    
    @GetMapping("/api/v1/users/anniversaries/today")
    List<UserProfileResponse> getUsersWithAccountAnniversaryToday();
    
    @GetMapping("/api/v1/users/anniversaries")
    List<UserProfileResponse> getUsersWithAccountAnniversary(
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    );
    
    @GetMapping("/api/v1/users/{userId}/activity")
    UserActivityResponse getUserActivity(
        @PathVariable("userId") UUID userId,
        @RequestParam(value = "from", required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(value = "to", required = false) 
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    );
    
    @GetMapping("/api/v1/users/security/review-needed")
    List<UserSecurityStatusResponse> getUsersNeedingSecurityReview(
        @RequestParam(value = "monthsSinceReview", defaultValue = "3") int monthsSinceReview
    );
    
    @GetMapping("/api/v1/users/{userId}/security/last-review")
    LocalDateTime getLastSecurityReviewDate(@PathVariable("userId") UUID userId);
}