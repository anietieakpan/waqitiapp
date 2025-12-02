package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserPreferencesDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for User Preferences integration
 * Used for GDPR data export to retrieve user preferences and settings
 */
@FeignClient(
    name = "user-service-preferences",
    url = "${services.user-service.url:http://user-service:8081}",
    fallback = PreferencesServiceClientFallback.class
)
public interface PreferencesServiceClient {

    /**
     * Get complete user preferences for GDPR export
     * Includes notification preferences, privacy settings, app configurations
     *
     * @param userId User ID
     * @param correlationId Tracing correlation ID
     * @return Complete user preferences
     */
    @GetMapping("/api/v1/users/{userId}/gdpr/preferences")
    UserPreferencesDataDTO getUserPreferences(
        @PathVariable("userId") String userId,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
