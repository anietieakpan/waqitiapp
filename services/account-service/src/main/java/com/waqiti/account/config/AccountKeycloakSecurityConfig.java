package com.waqiti.account.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Account Service
 * CRITICAL SECURITY IMPLEMENTATION - Core account management for the entire platform
 * This service handles all account operations including creation, validation, balance management,
 * and account lifecycle operations with strict regulatory compliance
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class AccountKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain accountKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Account Service - Core Platform Component");
        
        return createKeycloakSecurityFilterChain(http, "account-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Extremely limited for security
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Account Creation & Onboarding - CRITICAL OPERATIONS
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/create").hasAuthority("SCOPE_account:create")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/create-business").hasAuthority("SCOPE_account:create-business")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/create-joint").hasAuthority("SCOPE_account:create-joint")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/validate").hasAuthority("SCOPE_account:validate")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/activate").hasAuthority("SCOPE_account:activate")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/number/generate").hasAuthority("SCOPE_account:number-generate")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/verify").hasAuthority("SCOPE_account:verify")
                
                // Account Information & Retrieval
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts").hasAuthority("SCOPE_account:list")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*").hasAuthority("SCOPE_account:details")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/summary").hasAuthority("SCOPE_account:summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/full-details").hasAuthority("SCOPE_account:full-details")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/search").hasAuthority("SCOPE_account:search")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/history").hasAuthority("SCOPE_account:history")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/audit-trail").hasAuthority("SCOPE_account:audit-trail")
                
                // Account Updates & Modifications
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*").hasAuthority("SCOPE_account:update")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/profile").hasAuthority("SCOPE_account:profile-update")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/settings").hasAuthority("SCOPE_account:settings-update")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/preferences").hasAuthority("SCOPE_account:preferences-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/upgrade").hasAuthority("SCOPE_account:upgrade")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/downgrade").hasAuthority("SCOPE_account:downgrade")
                
                // Balance Operations - CRITICAL FINANCIAL OPERATIONS
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/balance").hasAuthority("SCOPE_account:balance-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/balance/available").hasAuthority("SCOPE_account:balance-available")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/balance/pending").hasAuthority("SCOPE_account:balance-pending")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/balance/reserved").hasAuthority("SCOPE_account:balance-reserved")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/debit").hasAuthority("SCOPE_account:debit")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/credit").hasAuthority("SCOPE_account:credit")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/transfer").hasAuthority("SCOPE_account:transfer")
                
                // Fund Reservation & Management
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/reserve-funds").hasAuthority("SCOPE_account:reserve-funds")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/release-funds").hasAuthority("SCOPE_account:release-funds")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/reservations").hasAuthority("SCOPE_account:reservations-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/reservations/*/cancel").hasAuthority("SCOPE_account:reservation-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/reservations/*/extend").hasAuthority("SCOPE_account:reservation-extend")
                
                // Account Status Management - COMPLIANCE CRITICAL
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/freeze").hasAuthority("SCOPE_account:freeze")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/unfreeze").hasAuthority("SCOPE_account:unfreeze")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/suspend").hasAuthority("SCOPE_account:suspend")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/reactivate").hasAuthority("SCOPE_account:reactivate")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/close").hasAuthority("SCOPE_account:close")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/reopen").hasAuthority("SCOPE_account:reopen")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/lock").hasAuthority("SCOPE_account:lock")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/unlock").hasAuthority("SCOPE_account:unlock")
                
                // Account Limits & Controls
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/limits").hasAuthority("SCOPE_account:limits-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/limits").hasAuthority("SCOPE_account:limits-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/limits/increase-request").hasAuthority("SCOPE_account:limits-increase-request")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/restrictions").hasAuthority("SCOPE_account:restrictions-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/restrictions").hasAuthority("SCOPE_account:restrictions-update")
                
                // Account Linking & Relationships
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/link").hasAuthority("SCOPE_account:link")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/*/unlink").hasAuthority("SCOPE_account:unlink")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/linked-accounts").hasAuthority("SCOPE_account:linked-accounts")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/add-signatory").hasAuthority("SCOPE_account:signatory-add")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/*/remove-signatory").hasAuthority("SCOPE_account:signatory-remove")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/signatories").hasAuthority("SCOPE_account:signatories-view")
                
                // Account Verification & KYC
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/kyc/initiate").hasAuthority("SCOPE_account:kyc-initiate")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/kyc/status").hasAuthority("SCOPE_account:kyc-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/kyc/documents").hasAuthority("SCOPE_account:kyc-documents")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/verify-identity").hasAuthority("SCOPE_account:identity-verify")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/verification-status").hasAuthority("SCOPE_account:verification-status")
                
                // Compliance & Regulatory
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/compliance/status").hasAuthority("SCOPE_account:compliance-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/compliance/check").hasAuthority("SCOPE_account:compliance-check")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/aml/status").hasAuthority("SCOPE_account:aml-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/sanctions/check").hasAuthority("SCOPE_account:sanctions-check")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/risk-score").hasAuthority("SCOPE_account:risk-score")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/flag-suspicious").hasAuthority("SCOPE_account:flag-suspicious")
                
                // Account Statements & Documents
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/statements").hasAuthority("SCOPE_account:statements-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/statements/*/download").hasAuthority("SCOPE_account:statement-download")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/statements/generate").hasAuthority("SCOPE_account:statement-generate")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/documents").hasAuthority("SCOPE_account:documents-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/documents/upload").hasAuthority("SCOPE_account:document-upload")
                
                // Transaction History & Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/transactions").hasAuthority("SCOPE_account:transactions-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/transactions/pending").hasAuthority("SCOPE_account:transactions-pending")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/transactions/failed").hasAuthority("SCOPE_account:transactions-failed")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/transactions/export").hasAuthority("SCOPE_account:transactions-export")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/analytics").hasAuthority("SCOPE_account:analytics-view")
                
                // Account Notifications & Alerts
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/notifications").hasAuthority("SCOPE_account:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/*/notifications/preferences").hasAuthority("SCOPE_account:notifications-preferences")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/alerts/balance").hasAuthority("SCOPE_account:alerts-balance")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/alerts/transaction").hasAuthority("SCOPE_account:alerts-transaction")
                
                // Account Recovery & Support
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/recovery/initiate").hasAuthority("SCOPE_account:recovery-initiate")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/recovery/verify").hasAuthority("SCOPE_account:recovery-verify")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/recovery/complete").hasAuthority("SCOPE_account:recovery-complete")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/dispute").hasAuthority("SCOPE_account:dispute-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/disputes").hasAuthority("SCOPE_account:disputes-view")
                
                // Bulk Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/bulk/create").hasAuthority("SCOPE_account:bulk-create")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/bulk/update").hasAuthority("SCOPE_account:bulk-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/bulk/freeze").hasAuthority("SCOPE_account:bulk-freeze")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/bulk/close").hasAuthority("SCOPE_account:bulk-close")
                
                // Admin Operations - Account Management
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/all").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/force-close").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/override-limits").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/bypass-freeze").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/suspicious").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/investigate").hasRole("COMPLIANCE_OFFICER")
                
                // Admin Operations - Financial Control
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/adjust-balance").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/reverse-transaction").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/reconciliation").hasRole("FINANCIAL_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/write-off").hasRole("FINANCIAL_CONTROLLER")
                
                // Admin Operations - Risk Management
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/high-risk").hasRole("RISK_MANAGER")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/*/risk-assessment").hasRole("RISK_MANAGER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/admin/*/risk-rating").hasRole("RISK_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/monitoring-list").hasRole("RISK_MANAGER")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/accounts/admin/**").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/system/health").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/system/maintenance").hasRole("ACCOUNT_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/admin/bulk-operations").hasRole("ACCOUNT_ADMIN")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/accounts/**").hasRole("SERVICE")
                .requestMatchers("/internal/balance/**").hasRole("SERVICE")
                .requestMatchers("/internal/account-validation/**").hasRole("SERVICE")
                .requestMatchers("/internal/compliance-check/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}