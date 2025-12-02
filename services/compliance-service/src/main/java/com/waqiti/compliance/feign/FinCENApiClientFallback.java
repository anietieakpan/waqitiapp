package com.waqiti.compliance.feign;

import com.waqiti.compliance.dto.SARFiling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * FinCEN API Client Fallback
 *
 * Provides fallback responses when FinCEN API is unavailable
 * to ensure graceful degradation and system resilience.
 *
 * Fallback Strategy:
 * - Queue SAR for later submission
 * - Return pending status
 * - Log failure for manual follow-up
 * - Alert compliance team
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Slf4j
@Component
public class FinCENApiClientFallback implements FinCENApiClient {

    @Override
    public SARFiling fileSAR(Map<String, Object> sarData, String apiKey) {
        log.error("FinCEN API unavailable - SAR filing fallback triggered. SAR data: {}", sarData);

        // Return pending status - SAR will be queued for retry
        return SARFiling.builder()
            .status("PENDING_RETRY")
            .referenceNumber("QUEUED_" + System.currentTimeMillis())
            .message("FinCEN API temporarily unavailable. SAR queued for submission.")
            .filedAt(null) // Not yet filed
            .acknowledged(false)
            .error("FINCEN_API_UNAVAILABLE")
            .build();
    }

    @Override
    public SARFiling fileEmergencySAR(Map<String, Object> sarData, String apiKey) {
        log.error("FinCEN API unavailable - Emergency SAR filing fallback triggered. SAR data: {}", sarData);
        log.error("CRITICAL: Emergency SAR could not be filed. Manual intervention required immediately.");

        // Emergency SAR fallback - requires immediate manual intervention
        return SARFiling.builder()
            .status("FAILED_MANUAL_FILING_REQUIRED")
            .referenceNumber("EMERGENCY_FAILED_" + System.currentTimeMillis())
            .message("CRITICAL: FinCEN API unavailable for emergency SAR. Manual filing required immediately via FinCEN BSA E-Filing System.")
            .filedAt(null)
            .acknowledged(false)
            .error("EMERGENCY_SAR_FILING_FAILED")
            .build();
    }

    @Override
    public Map<String, Object> verifySARStatus(Map<String, String> sarReferenceNumber, String apiKey) {
        log.error("FinCEN API unavailable - SAR verification fallback triggered. Reference: {}", sarReferenceNumber);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "VERIFICATION_UNAVAILABLE");
        response.put("message", "FinCEN API temporarily unavailable. Unable to verify SAR status.");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("error", "FINCEN_API_UNAVAILABLE");

        return response;
    }
}
