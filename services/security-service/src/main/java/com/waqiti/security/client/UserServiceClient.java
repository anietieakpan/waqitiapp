package com.waqiti.security.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.user-service.url:http://user-service:8080}")
    private String userServiceUrl;

    @Value("${services.user-service.timeout:5000}")
    private int timeoutMillis;

    @Cacheable(value = "userRoles", key = "#userId", unless = "#result == null || #result.isEmpty()")
    public Set<String> getUserRoles(String userId) {
        log.debug("Fetching user roles: userId={}", userId);
        
        try {
            UserDetailsResponse user = webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/api/users/{userId}", userId)
                .retrieve()
                .bodyToMono(UserDetailsResponse.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .block();
                
            return user != null ? user.getRoles() : Set.of();
                
        } catch (WebClientResponseException.NotFound e) {
            log.warn("User not found: userId={}", userId);
            return Set.of();
        } catch (Exception e) {
            log.error("Failed to fetch user roles: userId={}", userId, e);
            return Set.of();
        }
    }

    @Cacheable(value = "userHasRole", key = "#userId + ':' + #role")
    public boolean userHasRole(String userId, String role) {
        log.debug("Checking if user has role: userId={}, role={}", userId, role);
        
        try {
            Set<String> roles = getUserRoles(userId);
            return roles.contains(role) || roles.contains("ROLE_" + role);
            
        } catch (Exception e) {
            log.error("Failed to check user role: userId={}, role={}", userId, role, e);
            return false;
        }
    }

    public UserDetailsResponse getUserDetails(String userId) {
        log.debug("Fetching user details: userId={}", userId);
        
        try {
            return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/api/users/{userId}", userId)
                .retrieve()
                .bodyToMono(UserDetailsResponse.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .block();
                
        } catch (WebClientResponseException.NotFound e) {
            log.warn("User not found: userId={}", userId);
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch user details: userId={}", userId, e);
            return null;
        }
    }

    public static class UserDetailsResponse {
        private String id;
        private String username;
        private String email;
        private Set<String> roles;
        private String status;
        private String kycLevel;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public Set<String> getRoles() { return roles; }
        public void setRoles(Set<String> roles) { this.roles = roles; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getKycLevel() { return kycLevel; }
        public void setKycLevel(String kycLevel) { this.kycLevel = kycLevel; }
    }
}