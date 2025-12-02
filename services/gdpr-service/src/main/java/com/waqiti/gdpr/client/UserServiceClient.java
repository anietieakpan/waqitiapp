package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserPersonalDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for User Service integration
 * Used for GDPR data export to retrieve user personal information
 */
@FeignClient(
    name = "user-service",
    url = "${services.user-service.url:http://user-service:8081}",
    fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    /**
     * Get complete user personal data for GDPR export
     *
     * @param userId User ID
     * @param correlationId Tracing correlation ID
     * @return User personal data including profile, contact, KYC info
     */
    @GetMapping("/api/v1/users/{userId}/gdpr/personal-data")
    UserPersonalDataDTO getUserPersonalData(
        @PathVariable("userId") String userId,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
