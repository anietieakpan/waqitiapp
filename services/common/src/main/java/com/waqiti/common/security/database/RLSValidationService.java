package com.waqiti.common.security.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL SECURITY: Row-Level Security Validation Service
 * 
 * Provides validation and monitoring capabilities for RLS policies.
 * Used for testing, health checks, and compliance verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RLSValidationService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Validate all RLS policies are properly configured
     */
    public RLSValidationResult validateAllPolicies() {
        log.info("SECURITY: Starting comprehensive RLS policy validation");
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM validate_rls_policies() ORDER BY table_name"
            );
            
            RLSValidationResult.RLSValidationResultBuilder resultBuilder = 
                    RLSValidationResult.builder();
            
            int totalTables = 0;
            int properlyConfigured = 0;
            int rlsDisabled = 0;
            int missingPolicies = 0;
            
            for (Map<String, Object> row : results) {
                totalTables++;
                String status = (String) row.get("status");
                String tableName = (String) row.get("table_name");
                Boolean rlsEnabled = (Boolean) row.get("rls_enabled");
                Integer policyCount = (Integer) row.get("policy_count");
                
                switch (status) {
                    case "PROPERLY_CONFIGURED" -> properlyConfigured++;
                    case "RLS_DISABLED" -> {
                        rlsDisabled++;
                        log.warn("SECURITY: Table {} has RLS disabled", tableName);
                    }
                    case "RLS_ENABLED_NO_POLICIES" -> {
                        missingPolicies++;
                        log.error("SECURITY: Table {} has RLS enabled but no policies", tableName);
                    }
                }
                
                log.debug("SECURITY: Table {}: RLS={}, Policies={}, Status={}", 
                         tableName, rlsEnabled, policyCount, status);
            }
            
            boolean overallValid = (rlsDisabled == 0 && missingPolicies == 0);
            
            RLSValidationResult result = resultBuilder
                    .valid(overallValid)
                    .totalTables(totalTables)
                    .properlyConfigured(properlyConfigured)
                    .rlsDisabled(rlsDisabled)
                    .missingPolicies(missingPolicies)
                    .validationResults(results)
                    .build();
            
            log.info("SECURITY: RLS validation completed - Valid: {}, Total: {}, Configured: {}, Issues: {}", 
                    overallValid, totalTables, properlyConfigured, (rlsDisabled + missingPolicies));
            
            return result;
            
        } catch (DataAccessException e) {
            log.error("CRITICAL: RLS validation failed", e);
            return RLSValidationResult.builder()
                    .valid(false)
                    .errorMessage("Validation query failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Test RLS policy for specific table and user
     */
    public PolicyTestResult testUserPolicy(String tableName, UUID testUserId, String testRole) {
        log.debug("SECURITY: Testing RLS policy for table: {}, user: {}, role: {}", 
                 tableName, testUserId, testRole);
        
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM test_rls_policy(?, ?, ?)",
                tableName, testUserId, testRole
            );
            
            if (!results.isEmpty()) {
                Map<String, Object> result = results.get(0);
                
                return PolicyTestResult.builder()
                        .tableName(tableName)
                        .testUserId(testUserId)
                        .testRole(testRole)
                        .testDescription((String) result.get("test_description"))
                        .expectedResult((String) result.get("expected_result"))
                        .actualResult((String) result.get("actual_result"))
                        .status((String) result.get("status"))
                        .success("PASS".equals(result.get("status")))
                        .build();
            } else {
                return PolicyTestResult.builder()
                        .tableName(tableName)
                        .testUserId(testUserId)
                        .testRole(testRole)
                        .success(false)
                        .status("NO_RESULTS")
                        .build();
            }
            
        } catch (DataAccessException e) {
            log.error("SECURITY: RLS policy test failed for table: {}", tableName, e);
            return PolicyTestResult.builder()
                    .tableName(tableName)
                    .testUserId(testUserId)
                    .testRole(testRole)
                    .success(false)
                    .status("ERROR")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Check if security context is properly set in current session
     */
    public SecurityContextStatus checkCurrentSecurityContext() {
        try {
            String userId = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.current_user_id', true)", String.class);
            String userRole = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.user_role', true)", String.class);
            String sessionId = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.session_id', true)", String.class);
            String clientIp = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.client_ip', true)", String.class);
            String isAdmin = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.admin_context', true)", String.class);
            String isSystem = jdbcTemplate.queryForObject(
                "SELECT current_setting('app.system_context', true)", String.class);
            
            boolean contextSet = (userId != null && !userId.isEmpty()) || 
                               "true".equals(isSystem);
            
            return SecurityContextStatus.builder()
                    .contextSet(contextSet)
                    .userId(userId)
                    .userRole(userRole)
                    .sessionId(sessionId)
                    .clientIp(clientIp)
                    .isAdminContext("true".equals(isAdmin))
                    .isSystemContext("true".equals(isSystem))
                    .build();
                    
        } catch (DataAccessException e) {
            log.warn("SECURITY: Failed to check current security context", e);
            return SecurityContextStatus.builder()
                    .contextSet(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * Get RLS policy violations from the last N hours
     */
    public List<Map<String, Object>> getPolicyViolations(int lastHours) {
        try {
            return jdbcTemplate.queryForList(
                """
                SELECT table_name, attempted_operation, user_id, user_role, 
                       client_ip, violation_details, created_at
                FROM rls_policy_violations
                WHERE created_at > NOW() - INTERVAL '%d hours'
                ORDER BY created_at DESC
                LIMIT 100
                """, lastHours
            );
        } catch (DataAccessException e) {
            log.error("SECURITY: Failed to retrieve RLS policy violations", e);
            return List.of();
        }
    }

    /**
     * Test emergency access functionality
     */
    public boolean testEmergencyAccess(String emergencyToken) {
        log.warn("SECURITY: Testing emergency access with token");
        
        try {
            // Set emergency context
            jdbcTemplate.execute(String.format(
                "SELECT set_config('app.emergency_access', 'true', true)"));
            jdbcTemplate.execute(String.format(
                "SELECT set_config('app.emergency_token', '%s', true)", emergencyToken));
            jdbcTemplate.execute(String.format(
                "SELECT set_config('app.user_role', 'EMERGENCY_ADMIN', true)"));
            
            // Test access to restricted data
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wallets", Integer.class);
            
            // Clear emergency context
            jdbcTemplate.execute("SELECT set_config('app.emergency_access', 'false', true)");
            jdbcTemplate.execute("SELECT set_config('app.emergency_token', '', true)");
            jdbcTemplate.execute("SELECT set_config('app.user_role', '', true)");
            
            log.warn("SECURITY: Emergency access test completed, accessed {} records", count);
            return count != null && count >= 0;
            
        } catch (DataAccessException e) {
            log.error("SECURITY: Emergency access test failed", e);
            return false;
        }
    }

    /**
     * DTOs for validation results
     */
    @lombok.Builder
    @lombok.Data
    public static class RLSValidationResult {
        private boolean valid;
        private int totalTables;
        private int properlyConfigured;
        private int rlsDisabled;
        private int missingPolicies;
        private String errorMessage;
        private List<Map<String, Object>> validationResults;
    }

    @lombok.Builder
    @lombok.Data
    public static class PolicyTestResult {
        private String tableName;
        private UUID testUserId;
        private String testRole;
        private String testDescription;
        private String expectedResult;
        private String actualResult;
        private String status;
        private boolean success;
        private String errorMessage;
    }

    @lombok.Builder
    @lombok.Data
    public static class SecurityContextStatus {
        private boolean contextSet;
        private String userId;
        private String userRole;
        private String sessionId;
        private String clientIp;
        private boolean isAdminContext;
        private boolean isSystemContext;
        private String errorMessage;
    }
}