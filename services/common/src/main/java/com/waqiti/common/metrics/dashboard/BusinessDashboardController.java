package com.waqiti.common.metrics.dashboard;

import com.waqiti.common.metrics.dashboard.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * REST controller for business metrics dashboards
 */
@RestController
@RequestMapping("/api/metrics/dashboard")
@RequiredArgsConstructor
@Slf4j
public class BusinessDashboardController {
    
    private final BusinessMetricsDashboardService dashboardService;
    
    /**
     * Get comprehensive business dashboard
     */
    @GetMapping("/business")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('METRICS_READ')")
    public ResponseEntity<BusinessDashboard> getBusinessDashboard() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Failed to generate business dashboard", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get real-time metrics summary
     */
    @GetMapping("/realtime")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('METRICS_READ')")
    public ResponseEntity<RealTimeMetrics> getRealTimeMetrics() {
        try {
            RealTimeMetrics metrics = dashboardService.generateRealTimeMetrics();
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Failed to generate real-time metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get financial performance dashboard
     */
    @GetMapping("/financial")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('FINANCIAL_METRICS_READ')")
    public ResponseEntity<FinancialDashboard> getFinancialDashboard(
            @RequestParam(defaultValue = "24") int timeWindowHours) {
        try {
            Duration timeWindow = Duration.ofHours(timeWindowHours);
            FinancialDashboard dashboard = dashboardService.generateFinancialDashboard(timeWindow);
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Failed to generate financial dashboard", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get operational health dashboard
     */
    @GetMapping("/operational")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('OPERATIONS_READ')")
    public ResponseEntity<OperationalDashboard> getOperationalDashboard() {
        try {
            OperationalDashboard dashboard = dashboardService.generateOperationalDashboard();
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Failed to generate operational dashboard", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get transaction metrics only
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TRANSACTION_METRICS_READ')")
    public ResponseEntity<TransactionMetrics> getTransactionMetrics() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            
            if (dashboard == null || dashboard.getTransactionMetrics() == null) {
                return ResponseEntity.internalServerError().build();
            }
            
            return ResponseEntity.ok(dashboard.getTransactionMetrics());
            
        } catch (Exception e) {
            log.error("Failed to get transaction metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get fraud detection metrics only
     */
    @GetMapping("/fraud")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('FRAUD_METRICS_READ')")
    public ResponseEntity<Object> getFraudMetrics() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            
            if (dashboard == null || dashboard.getCustomMetrics() == null) {
                return ResponseEntity.internalServerError().build();
            }
            
            Object fraudMetrics = dashboard.getCustomMetrics().get("fraudMetrics");
            return ResponseEntity.ok(fraudMetrics);
            
        } catch (Exception e) {
            log.error("Failed to get fraud metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get KYC verification metrics only
     */
    @GetMapping("/kyc")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('KYC_METRICS_READ')")
    public ResponseEntity<Object> getKycMetrics() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            
            if (dashboard == null || dashboard.getCustomMetrics() == null) {
                return ResponseEntity.internalServerError().build();
            }
            
            Object kycMetrics = dashboard.getCustomMetrics().get("kycMetrics");
            return ResponseEntity.ok(kycMetrics);
            
        } catch (Exception e) {
            log.error("Failed to get KYC metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get system health status
     */
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('HEALTH_READ')")
    public ResponseEntity<SystemMetrics> getSystemHealth() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            
            if (dashboard == null || dashboard.getSystemMetrics() == null) {
                return ResponseEntity.internalServerError().build();
            }
            
            return ResponseEntity.ok(dashboard.getSystemMetrics());
            
        } catch (Exception e) {
            log.error("Failed to get system health", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get API performance metrics
     */
    @GetMapping("/api-performance")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('API_METRICS_READ')")
    public ResponseEntity<Object> getApiMetrics() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            
            if (dashboard == null || dashboard.getCustomMetrics() == null) {
                return ResponseEntity.internalServerError().build();
            }
            
            Object apiMetrics = dashboard.getCustomMetrics().get("apiMetrics");
            return ResponseEntity.ok(apiMetrics);
            
        } catch (Exception e) {
            log.error("Failed to get API metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get external service metrics
     */
    @GetMapping("/external-services")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('EXTERNAL_SERVICE_METRICS_READ')")
    public ResponseEntity<Object> getExternalServiceMetrics() {
        try {
            BusinessDashboard dashboard = dashboardService.generateBusinessDashboard();
            
            if (dashboard == null || dashboard.getCustomMetrics() == null) {
                return ResponseEntity.internalServerError().build();
            }
            
            Object externalServiceMetrics = dashboard.getCustomMetrics().get("externalServiceMetrics");
            return ResponseEntity.ok(externalServiceMetrics);
        } catch (Exception e) {
            log.error("Failed to get external service metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}