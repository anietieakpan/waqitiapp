package com.waqiti.common.bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for bulkhead monitoring and statistics
 */
@RestController
@RequestMapping("/api/bulkhead")
@RequiredArgsConstructor
@Slf4j
public class BulkheadMonitoringController {
    
    private final BulkheadService bulkheadService;
    
    /**
     * Get overall bulkhead statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('BULKHEAD_READ')")
    public ResponseEntity<BulkheadStatistics> getStatistics() {
        try {
            BulkheadStatistics statistics = bulkheadService.getStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Failed to get bulkhead statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Check capacity for specific resource type
     */
    @GetMapping("/capacity/{resourceType}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('BULKHEAD_READ')")
    public ResponseEntity<CapacityCheck> getCapacity(@PathVariable ResourceType resourceType) {
        try {
            boolean hasCapacity = bulkheadService.hasCapacity(resourceType);
            double utilization = bulkheadService.getResourceUtilization(resourceType);
            
            CapacityCheck check = CapacityCheck.builder()
                .resourceType(resourceType)
                .hasCapacity(hasCapacity)
                .utilization(utilization)
                .status(determineStatus(utilization))
                .build();
                
            return ResponseEntity.ok(check);
        } catch (Exception e) {
            log.error("Failed to check capacity for resource type: {}", resourceType, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get utilization for all resource types
     */
    @GetMapping("/utilization")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('BULKHEAD_READ')")
    public ResponseEntity<ResourceUtilizationSummary> getUtilization() {
        try {
            ResourceUtilizationSummary summary = ResourceUtilizationSummary.builder()
                .paymentProcessingUtilization(bulkheadService.getResourceUtilization(ResourceType.PAYMENT_PROCESSING))
                .kycVerificationUtilization(bulkheadService.getResourceUtilization(ResourceType.KYC_VERIFICATION))
                .fraudDetectionUtilization(bulkheadService.getResourceUtilization(ResourceType.FRAUD_DETECTION))
                .notificationUtilization(bulkheadService.getResourceUtilization(ResourceType.NOTIFICATION))
                .analyticsUtilization(bulkheadService.getResourceUtilization(ResourceType.ANALYTICS))
                .coreBankingUtilization(bulkheadService.getResourceUtilization(ResourceType.CORE_BANKING))
                .build();
                
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Failed to get resource utilization summary", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get health status for all resources
     */
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('BULKHEAD_READ')")
    public ResponseEntity<BulkheadHealthStatus> getHealth() {
        try {
            BulkheadStatistics statistics = bulkheadService.getStatistics();
            
            BulkheadHealthStatus health = BulkheadHealthStatus.builder()
                .overall(determineOverallHealth(statistics))
                .paymentProcessingHealth(statistics.getPaymentProcessingStats().getHealthStatus())
                .kycVerificationHealth(statistics.getKycVerificationStats().getHealthStatus())
                .fraudDetectionHealth(statistics.getFraudDetectionStats().getHealthStatus())
                .notificationHealth(statistics.getNotificationStats().getHealthStatus())
                .analyticsHealth(statistics.getAnalyticsStats().getHealthStatus())
                .coreBankingHealth(statistics.getCoreBankingStats().getHealthStatus())
                .overallUtilization(statistics.getOverallUtilization())
                .totalActiveOperations(statistics.getTotalActiveOperations())
                .totalCapacity(statistics.getTotalCapacity())
                .mostUtilizedResource(statistics.getMostUtilizedResource().getResourceType())
                .build();
                
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Failed to get bulkhead health status", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String determineStatus(double utilization) {
        if (utilization >= 95.0) {
            return "CRITICAL";
        } else if (utilization >= 80.0) {
            return "WARNING";
        } else if (utilization >= 50.0) {
            return "MODERATE";
        } else {
            return "HEALTHY";
        }
    }
    
    private String determineOverallHealth(BulkheadStatistics statistics) {
        double overallUtilization = statistics.getOverallUtilization();
        ResourceStats mostUtilized = statistics.getMostUtilizedResource();
        
        if (mostUtilized.isOverloaded() || overallUtilization >= 90.0) {
            return "CRITICAL";
        } else if (!mostUtilized.isHealthy() || overallUtilization >= 70.0) {
            return "WARNING";
        } else if (overallUtilization >= 40.0) {
            return "MODERATE";
        } else {
            return "HEALTHY";
        }
    }
}