package com.waqiti.reporting.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;

@FeignClient(
    name = "reconciliation-service", 
    path = "/api/v1/reconciliation",
    fallback = ReconciliationServiceClientFallback.class
)
public interface ReconciliationServiceClient {
    
    @GetMapping("/status")
    Map<String, Object> getReconciliationStatus(@RequestParam LocalDateTime startDate, 
                                               @RequestParam LocalDateTime endDate);
    
    @GetMapping("/discrepancies")
    Map<String, Object> getDiscrepancies(@RequestParam LocalDateTime startDate, 
                                       @RequestParam LocalDateTime endDate);
}