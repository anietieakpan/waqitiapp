package com.waqiti.rewards.client;

import com.waqiti.rewards.dto.UserDetailsDto;
import com.waqiti.rewards.dto.UserPreferencesDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
    name = "user-service",
    url = "${services.user.url:http://user-service:8081}",
    configuration = UserServiceClientConfig.class,
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}")
    UserDetailsDto getUserDetails(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authorization
    );

    @GetMapping("/api/v1/users/{userId}/preferences")
    UserPreferencesDto getUserPreferences(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authorization
    );

    @GetMapping("/api/v1/users/{userId}/tier")
    String getUserTier(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authorization
    );
}