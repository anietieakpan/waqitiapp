package com.waqiti.compliance.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Law Enforcement API Client Fallback
 *
 * Provides fallback responses when law enforcement APIs are unavailable
 * to ensure graceful degradation and system resilience.
 *
 * Fallback Strategy:
 * - Queue notification for later submission
 * - Return pending status
 * - Log failure for manual follow-up
 * - Alert compliance team for critical cases
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Slf4j
@Component
public class LawEnforcementApiClientFallback implements LawEnforcementApiClient {

    @Override
    public Map<String, Object> notifyFBI(Map<String, Object> crimeData, String apiKey) {
        log.error("FBI IC3 API unavailable - notification fallback triggered. Crime data: {}", crimeData);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "PENDING_RETRY");
        response.put("caseNumber", "FBI_QUEUED_" + System.currentTimeMillis());
        response.put("message", "FBI IC3 API temporarily unavailable. Notification queued for submission.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "FBI_API_UNAVAILABLE");
        response.put("acknowledged", false);

        return response;
    }

    @Override
    public Map<String, Object> notifySEC(Map<String, Object> crimeData, String apiKey) {
        log.error("SEC API unavailable - notification fallback triggered. Crime data: {}", crimeData);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "PENDING_RETRY");
        response.put("tcrNumber", "SEC_QUEUED_" + System.currentTimeMillis());
        response.put("message", "SEC API temporarily unavailable. Notification queued for submission.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "SEC_API_UNAVAILABLE");
        response.put("acknowledged", false);

        return response;
    }

    @Override
    public Map<String, Object> notifyLocalLawEnforcement(Map<String, Object> crimeData, String apiKey) {
        log.error("Local LE API unavailable - notification fallback triggered. Crime data: {}", crimeData);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "PENDING_RETRY");
        response.put("caseNumber", "LOCAL_QUEUED_" + System.currentTimeMillis());
        response.put("message", "Local law enforcement API temporarily unavailable. Notification queued.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "LOCAL_LE_API_UNAVAILABLE");
        response.put("acknowledged", false);

        return response;
    }

    @Override
    public Map<String, Object> notifyEmergency(Map<String, Object> crimeData, String apiKey) {
        log.error("Emergency notification API unavailable - fallback triggered. Crime data: {}", crimeData);
        log.error("CRITICAL: Emergency law enforcement notification failed. Manual intervention required immediately.");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "FAILED_MANUAL_NOTIFICATION_REQUIRED");
        response.put("caseNumber", "EMERGENCY_FAILED_" + System.currentTimeMillis());
        response.put("message", "CRITICAL: Emergency notification API unavailable. Manual notification to FBI/SEC required immediately.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "EMERGENCY_NOTIFICATION_FAILED");
        response.put("acknowledged", false);
        response.put("manualActionRequired", true);
        response.put("contacts", Map.of(
            "FBI", "1-800-CALL-FBI",
            "SEC", "1-800-SEC-0330",
            "IC3", "https://www.ic3.gov/complaint"
        ));

        return response;
    }
}
