package com.waqiti.reconciliation.security;

import com.waqiti.reconciliation.command.ReconciliationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Security context for reconciliation operations
 * Enforces role-based access control and permission validation
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationSecurityContext {
    
    // Role definitions
    private static final String ROLE_RECONCILIATION_ADMIN = "ROLE_RECONCILIATION_ADMIN";
    private static final String ROLE_RECONCILIATION_OPERATOR = "ROLE_RECONCILIATION_OPERATOR";
    private static final String ROLE_RECONCILIATION_VIEWER = "ROLE_RECONCILIATION_VIEWER";
    private static final String ROLE_SYSTEM = "ROLE_SYSTEM";
    private static final String ROLE_EMERGENCY_ACCESS = "ROLE_EMERGENCY_ACCESS";
    
    // Permission definitions
    private static final String PERMISSION_RECONCILE_ALL = "reconciliation:all";
    private static final String PERMISSION_RECONCILE_SCHEDULED = "reconciliation:scheduled";
    private static final String PERMISSION_RECONCILE_MANUAL = "reconciliation:manual";
    private static final String PERMISSION_RECONCILE_EMERGENCY = "reconciliation:emergency";
    
    /**
     * Check if current user can execute reconciliation command
     */
    public boolean canExecuteReconciliation(ReconciliationCommand command) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated attempt to execute reconciliation: {}", command.getCommandId());
            return false;
        }
        
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        
        // System-initiated reconciliations
        if ("SYSTEM".equals(command.getInitiatedBy())) {
            return authorities.contains(ROLE_SYSTEM);
        }
        
        // Check based on reconciliation type
        switch (command.getType()) {
            case STARTUP_RECONCILIATION:
                return authorities.contains(ROLE_SYSTEM) || 
                       authorities.contains(ROLE_RECONCILIATION_ADMIN);
                
            case SCHEDULED_RECONCILIATION:
                return authorities.contains(ROLE_SYSTEM) || 
                       authorities.contains(ROLE_RECONCILIATION_ADMIN) ||
                       authorities.contains(PERMISSION_RECONCILE_SCHEDULED);
                
            case MANUAL_RECONCILIATION:
                return authorities.contains(ROLE_RECONCILIATION_ADMIN) ||
                       authorities.contains(ROLE_RECONCILIATION_OPERATOR) ||
                       authorities.contains(PERMISSION_RECONCILE_MANUAL);
                
            case EMERGENCY_RECONCILIATION:
                return authorities.contains(ROLE_EMERGENCY_ACCESS) ||
                       authorities.contains(PERMISSION_RECONCILE_EMERGENCY);
                
            case PARTIAL_RECONCILIATION:
                return authorities.contains(ROLE_RECONCILIATION_ADMIN) ||
                       authorities.contains(ROLE_RECONCILIATION_OPERATOR);
                
            case FULL_RECONCILIATION:
                return authorities.contains(ROLE_RECONCILIATION_ADMIN);
                
            default:
                log.warn("Unknown reconciliation type: {}", command.getType());
                return false;
        }
    }
    
    /**
     * Check if current user has emergency access
     */
    public boolean hasEmergencyAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(ROLE_EMERGENCY_ACCESS) || 
                                 auth.equals(PERMISSION_RECONCILE_EMERGENCY));
    }
    
    /**
     * Get current authenticated user
     */
    public String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return "ANONYMOUS";
        }
        
        return authentication.getName();
    }
    
    /**
     * Check if user has admin privileges
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals(ROLE_RECONCILIATION_ADMIN));
    }
    
    /**
     * Validate reconciliation scope access
     */
    public boolean canAccessScope(ReconciliationCommand.ReconciliationScope scope) {
        if (scope == null) {
            return true; // No specific scope restriction
        }
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        
        // Admin can access all scopes
        if (authorities.contains(ROLE_RECONCILIATION_ADMIN)) {
            return true;
        }
        
        // Check specific scope permissions
        switch (scope) {
            case ALL_TRANSACTIONS:
                return authorities.contains(PERMISSION_RECONCILE_ALL);
                
            case HIGH_VALUE_ONLY:
                // High-value reconciliation requires special permission
                return authorities.contains("reconciliation:high-value");
                
            case SPECIFIC_PROVIDERS:
                // Provider-specific reconciliation
                return authorities.contains("reconciliation:providers");
                
            default:
                // Default scopes available to operators
                return authorities.contains(ROLE_RECONCILIATION_OPERATOR);
        }
    }
}