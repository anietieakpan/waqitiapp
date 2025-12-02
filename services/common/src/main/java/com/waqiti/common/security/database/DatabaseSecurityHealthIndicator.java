package com.waqiti.common.security.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * CRITICAL SECURITY: Database Security Health Indicator
 * 
 * Monitors the health and proper configuration of database security features,
 * particularly Row-Level Security (RLS) policies.
 * 
 * This health check is crucial for:
 * - Detecting RLS policy misconfigurations
 * - Monitoring security context functionality
 * - Compliance reporting
 * - Production readiness validation
 */
@Component("databaseSecurity")
@RequiredArgsConstructor
@Slf4j
public class DatabaseSecurityHealthIndicator implements HealthIndicator {

    private final RLSValidationService rlsValidationService;

    @Override
    public Health health() {
        try {
            // Validate RLS policies
            RLSValidationService.RLSValidationResult rlsValidation = 
                    rlsValidationService.validateAllPolicies();
            
            // Check current security context
            RLSValidationService.SecurityContextStatus contextStatus = 
                    rlsValidationService.checkCurrentSecurityContext();
            
            // Build health result
            Health.Builder healthBuilder = rlsValidation.isValid() ? 
                    Health.up() : Health.down();
            
            // Add RLS validation details
            healthBuilder
                    .withDetail("rls_validation", Map.of(
                        "valid", rlsValidation.isValid(),
                        "total_tables", rlsValidation.getTotalTables(),
                        "properly_configured", rlsValidation.getProperlyConfigured(),
                        "rls_disabled", rlsValidation.getRlsDisabled(),
                        "missing_policies", rlsValidation.getMissingPolicies()
                    ));
            
            // Add security context status
            healthBuilder
                    .withDetail("security_context", Map.of(
                        "context_available", contextStatus.isContextSet(),
                        "admin_context", contextStatus.isAdminContext(),
                        "system_context", contextStatus.isSystemContext(),
                        "has_user_role", contextStatus.getUserRole() != null && 
                                       !contextStatus.getUserRole().isEmpty()
                    ));
            
            // Add recent policy violations
            var violations = rlsValidationService.getPolicyViolations(24);
            healthBuilder
                    .withDetail("policy_violations_24h", violations.size())
                    .withDetail("recent_violations", violations.stream().limit(5).toList());
            
            // Add error details if validation failed
            if (!rlsValidation.isValid() && rlsValidation.getErrorMessage() != null) {
                healthBuilder.withDetail("error", rlsValidation.getErrorMessage());
            }
            
            // Overall security status
            String securityStatus = determineSecurityStatus(rlsValidation, contextStatus);
            healthBuilder
                    .withDetail("security_status", securityStatus)
                    .withDetail("last_check", java.time.LocalDateTime.now());
            
            return healthBuilder.build();
            
        } catch (Exception e) {
            log.error("CRITICAL: Database security health check failed", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("check_failed", true)
                    .withDetail("last_check", java.time.LocalDateTime.now())
                    .build();
        }
    }

    private String determineSecurityStatus(
            RLSValidationService.RLSValidationResult rlsValidation,
            RLSValidationService.SecurityContextStatus contextStatus) {
        
        if (!rlsValidation.isValid()) {
            if (rlsValidation.getRlsDisabled() > 0) {
                return "CRITICAL - RLS disabled on sensitive tables";
            } else if (rlsValidation.getMissingPolicies() > 0) {
                return "CRITICAL - RLS enabled but missing policies";
            } else {
                return "ERROR - RLS validation failed";
            }
        }
        
        if (rlsValidation.getProperlyConfigured() == rlsValidation.getTotalTables()) {
            return "SECURE - All RLS policies properly configured";
        } else {
            return "WARNING - Some RLS policies may need attention";
        }
    }
}