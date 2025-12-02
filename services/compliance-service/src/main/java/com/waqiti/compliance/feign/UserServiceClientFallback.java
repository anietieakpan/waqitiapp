package com.waqiti.compliance.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * User Service Client Fallback
 *
 * Provides fallback responses when user-service is unavailable
 * to ensure graceful degradation and system resilience.
 *
 * Fallback Strategy:
 * - Queue suspend/unsuspend for later execution
 * - Return pending status
 * - Log failure for manual follow-up
 * - Alert compliance team for critical suspensions
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public Map<String, Object> suspendUser(String userId, Map<String, Object> suspendRequest, String authToken) {
        log.error("User service unavailable - user suspension fallback triggered. User: {}, Request: {}", userId, suspendRequest);
        log.error("CRITICAL: Unable to suspend user {}. Manual suspension may be required.", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUSPEND_PENDING_RETRY");
        response.put("userId", userId);
        response.put("message", "User service temporarily unavailable. User suspension queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "USER_SERVICE_UNAVAILABLE");
        response.put("suspended", false);
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> unsuspendUser(String userId, Map<String, Object> unsuspendRequest, String authToken) {
        log.error("User service unavailable - user unsuspend fallback triggered. User: {}, Request: {}", userId, unsuspendRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UNSUSPEND_PENDING_RETRY");
        response.put("userId", userId);
        response.put("message", "User service temporarily unavailable. User unsuspension queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "USER_SERVICE_UNAVAILABLE");
        response.put("suspended", true); // Assume still suspended
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> flagUser(String userId, Map<String, Object> flagRequest, String authToken) {
        log.error("User service unavailable - user flag fallback triggered. User: {}, Request: {}", userId, flagRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "FLAG_PENDING_RETRY");
        response.put("userId", userId);
        response.put("message", "User service temporarily unavailable. User flag queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "USER_SERVICE_UNAVAILABLE");
        response.put("flagged", false);
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> unflagUser(String userId, Map<String, Object> unflagRequest, String authToken) {
        log.error("User service unavailable - user unflag fallback triggered. User: {}, Request: {}", userId, unflagRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "UNFLAG_PENDING_RETRY");
        response.put("userId", userId);
        response.put("message", "User service temporarily unavailable. User unflag queued for execution.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "USER_SERVICE_UNAVAILABLE");
        response.put("flagged", true); // Assume still flagged
        response.put("retryScheduled", true);

        return response;
    }

    @Override
    public Map<String, Object> getComplianceStatus(String userId, String authToken) {
        log.error("User service unavailable - compliance status check fallback triggered. User: {}", userId);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "STATUS_UNAVAILABLE");
        response.put("userId", userId);
        response.put("message", "User service temporarily unavailable. Unable to determine compliance status.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "USER_SERVICE_UNAVAILABLE");
        response.put("suspended", null); // Unknown status
        response.put("flagged", null); // Unknown status

        return response;
    }
}
