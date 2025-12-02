package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserConsentsDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for Consent Service integration
 * Used for GDPR data export to retrieve user consent records
 */
@FeignClient(
    name = "gdpr-service-consents",
    url = "${services.gdpr-service.url:http://gdpr-service:8090}",
    fallback = ConsentServiceClientFallback.class
)
public interface ConsentServiceClient {

    /**
     * Get complete user consent history for GDPR export
     * Includes all consent grants, withdrawals, and current status
     *
     * @param userId User ID
     * @param correlationId Tracing correlation ID
     * @return Complete consent history
     */
    @GetMapping("/api/v1/gdpr/consents/users/{userId}/export")
    UserConsentsDataDTO getUserConsents(
        @PathVariable("userId") String userId,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
