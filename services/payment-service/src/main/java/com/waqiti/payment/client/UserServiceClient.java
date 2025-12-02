package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.UserResponse;
import com.waqiti.payment.security.UserSecurityDetails;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "user-service", 
    url = "${user-service.url}",
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {
    
    @GetMapping("/api/v1/users/{userId}")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserFallback")
    @Retry(name = "user-service")
    @TimeLimiter(name = "user-service")
    UserResponse getUser(@PathVariable UUID userId);
    
    @GetMapping("/api/v1/users")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUsersFallback")
    @Retry(name = "user-service")
    @TimeLimiter(name = "user-service")
    List<UserResponse> getUsers(@RequestParam List<UUID> userIds);
    
    @GetMapping("/api/v1/users/{userId}/security-details")
    @CircuitBreaker(name = "user-service", fallbackMethod = "getUserSecurityDetailsFallback")
    @Retry(name = "user-service")
    @TimeLimiter(name = "user-service")
    UserSecurityDetails getUserSecurityDetails(@PathVariable String userId);
}