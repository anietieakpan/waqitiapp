package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserActivityLogsDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for Activity Log/Audit Service integration
 * Used for GDPR data export to retrieve user activity logs
 */
@FeignClient(
    name = "audit-service",
    url = "${services.audit-service.url:http://audit-service:8089}",
    fallback = ActivityLogServiceClientFallback.class
)
public interface ActivityLogServiceClient {

    /**
     * Get complete user activity logs for GDPR export
     * Includes login history, actions, security events
     *
     * @param userId User ID
     * @param correlationId Tracing correlation ID
     * @return Complete activity log history
     */
    @GetMapping("/api/v1/audit/users/{userId}/gdpr/activity-logs")
    UserActivityLogsDataDTO getUserActivityLogs(
        @PathVariable("userId") String userId,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
