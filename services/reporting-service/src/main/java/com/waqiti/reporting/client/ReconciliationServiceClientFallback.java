package com.waqiti.reporting.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ReconciliationServiceClientFallback implements ReconciliationServiceClient {

    @Override
    public Map<String, Object> getReconciliationStatus(LocalDateTime startDate, LocalDateTime endDate) {
        log.error("FALLBACK ACTIVATED: Cannot retrieve reconciliation status - Reconciliation Service unavailable. " +
                "DateRange: {} to {}", startDate, endDate);
        
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("status", "UNAVAILABLE");
        fallbackResponse.put("message", "Reconciliation status temporarily unavailable");
        fallbackResponse.put("startDate", startDate);
        fallbackResponse.put("endDate", endDate);
        fallbackResponse.put("isStale", true);
        
        return fallbackResponse;
    }

    @Override
    public Map<String, Object> getDiscrepancies(LocalDateTime startDate, LocalDateTime endDate) {
        log.error("FALLBACK ACTIVATED: CRITICAL - Cannot retrieve discrepancies - Reconciliation Service unavailable. " +
                "DateRange: {} to {}", startDate, endDate);
        
        Map<String, Object> fallbackResponse = new HashMap<>();
        fallbackResponse.put("status", "UNAVAILABLE");
        fallbackResponse.put("message", "Discrepancy data temporarily unavailable - manual review required");
        fallbackResponse.put("startDate", startDate);
        fallbackResponse.put("endDate", endDate);
        fallbackResponse.put("discrepancies", null);
        fallbackResponse.put("requiresManualReview", true);
        
        return fallbackResponse;
    }
}