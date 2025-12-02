package com.waqiti.purchaseprotection.config;

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
 * Keycloak security configuration for Purchase Protection Service
 * CRITICAL SECURITY IMPLEMENTATION - Financial guarantees and buyer protection
 * This service handles purchase protection policies, claims, disputes, and refunds
 * Essential for maintaining trust and regulatory compliance in e-commerce transactions
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class PurchaseProtectionKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain purchaseProtectionKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Purchase Protection Service - Financial Guarantees");
        
        return createKeycloakSecurityFilterChain(http, "purchase-protection-service", httpSecurity -> {
            try {
                httpSecurity.authorizeHttpRequests(authz -> authz
                    // Public endpoints - Extremely limited
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    .requestMatchers("/actuator/info").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers("/api/v1/purchase-protection/public/terms").permitAll()
                    .requestMatchers("/api/v1/purchase-protection/public/coverage-info").permitAll()

                    // Protection Policy Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/policies/activate").hasAuthority("SCOPE_protection:policy-activate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/policies").hasAuthority("SCOPE_protection:policies-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/policies/*").hasAuthority("SCOPE_protection:policy-details")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/purchase-protection/policies/*").hasAuthority("SCOPE_protection:policy-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/policies/*/cancel").hasAuthority("SCOPE_protection:policy-cancel")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/policies/*/renew").hasAuthority("SCOPE_protection:policy-renew")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/policies/*/coverage").hasAuthority("SCOPE_protection:coverage-view")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/policies/*/extend").hasAuthority("SCOPE_protection:policy-extend")

                    // Coverage Eligibility & Verification
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/eligibility/check").hasAuthority("SCOPE_protection:eligibility-check")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/coverage/calculate").hasAuthority("SCOPE_protection:coverage-calculate")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/coverage/verify").hasAuthority("SCOPE_protection:coverage-verify")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/coverage/limits").hasAuthority("SCOPE_protection:coverage-limits")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/coverage/exclusions").hasAuthority("SCOPE_protection:coverage-exclusions")

                    // Claim Submission & Management - CRITICAL OPERATIONS
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/submit").hasAuthority("SCOPE_protection:claim-submit")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims").hasAuthority("SCOPE_protection:claims-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims/*").hasAuthority("SCOPE_protection:claim-details")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/purchase-protection/claims/*").hasAuthority("SCOPE_protection:claim-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/withdraw").hasAuthority("SCOPE_protection:claim-withdraw")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims/*/status").hasAuthority("SCOPE_protection:claim-status")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims/*/timeline").hasAuthority("SCOPE_protection:claim-timeline")

                    // Evidence & Documentation
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/evidence/upload").hasAuthority("SCOPE_protection:evidence-upload")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims/*/evidence").hasAuthority("SCOPE_protection:evidence-view")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/purchase-protection/claims/*/evidence/*").hasAuthority("SCOPE_protection:evidence-delete")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/documents/submit").hasAuthority("SCOPE_protection:documents-submit")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims/*/documents").hasAuthority("SCOPE_protection:documents-view")

                    // Claim Processing & Resolution
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/process").hasAuthority("SCOPE_protection:claim-process")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/approve").hasAuthority("SCOPE_protection:claim-approve")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/reject").hasAuthority("SCOPE_protection:claim-reject")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/request-info").hasAuthority("SCOPE_protection:claim-request-info")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/claims/*/escalate").hasAuthority("SCOPE_protection:claim-escalate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/claims/*/decision").hasAuthority("SCOPE_protection:claim-decision")

                    // Dispute Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/disputes/create").hasAuthority("SCOPE_protection:dispute-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/disputes").hasAuthority("SCOPE_protection:disputes-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/disputes/*").hasAuthority("SCOPE_protection:dispute-details")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/purchase-protection/disputes/*").hasAuthority("SCOPE_protection:dispute-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/disputes/*/respond").hasAuthority("SCOPE_protection:dispute-respond")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/disputes/*/escalate").hasAuthority("SCOPE_protection:dispute-escalate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/disputes/*/resolution").hasAuthority("SCOPE_protection:dispute-resolution")

                    // Refund & Reimbursement Processing
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/refunds/initiate").hasAuthority("SCOPE_protection:refund-initiate")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/refunds").hasAuthority("SCOPE_protection:refunds-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/refunds/*").hasAuthority("SCOPE_protection:refund-details")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/refunds/*/process").hasAuthority("SCOPE_protection:refund-process")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/refunds/*/cancel").hasAuthority("SCOPE_protection:refund-cancel")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/refunds/*/status").hasAuthority("SCOPE_protection:refund-status")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/refunds/*/retry").hasAuthority("SCOPE_protection:refund-retry")

                    // Merchant & Seller Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/merchants/*/rating").hasAuthority("SCOPE_protection:merchant-rating")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/merchants/*/report").hasAuthority("SCOPE_protection:merchant-report")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/merchants/*/claims-history").hasAuthority("SCOPE_protection:merchant-claims-history")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/merchants/*/blacklist").hasAuthority("SCOPE_protection:merchant-blacklist")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/merchants/trusted").hasAuthority("SCOPE_protection:merchants-trusted")

                    // Premium & Billing
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/premiums/calculate").hasAuthority("SCOPE_protection:premium-calculate")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/premiums/pay").hasAuthority("SCOPE_protection:premium-pay")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/billing/history").hasAuthority("SCOPE_protection:billing-history")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/billing/invoices").hasAuthority("SCOPE_protection:invoices-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/billing/statements").hasAuthority("SCOPE_protection:statements-view")

                    // Risk Assessment & Fraud Detection
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/risk/assess").hasAuthority("SCOPE_protection:risk-assess")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/risk/score").hasAuthority("SCOPE_protection:risk-score")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/fraud/check").hasAuthority("SCOPE_protection:fraud-check")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/fraud/report").hasAuthority("SCOPE_protection:fraud-report")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/fraud/indicators").hasAuthority("SCOPE_protection:fraud-indicators")

                    // Analytics & Reporting
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/analytics/claims").hasAuthority("SCOPE_protection:analytics-claims")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/analytics/coverage").hasAuthority("SCOPE_protection:analytics-coverage")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/analytics/disputes").hasAuthority("SCOPE_protection:analytics-disputes")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/reports/monthly").hasAuthority("SCOPE_protection:reports-monthly")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/reports/annual").hasAuthority("SCOPE_protection:reports-annual")

                    // Notifications & Communications
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/notifications").hasAuthority("SCOPE_protection:notifications-view")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/purchase-protection/notifications/preferences").hasAuthority("SCOPE_protection:notifications-preferences")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/communications/send").hasAuthority("SCOPE_protection:communications-send")

                    // Appeals Process
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/appeals/submit").hasAuthority("SCOPE_protection:appeal-submit")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/appeals").hasAuthority("SCOPE_protection:appeals-view")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/appeals/*").hasAuthority("SCOPE_protection:appeal-details")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/appeals/*/review").hasAuthority("SCOPE_protection:appeal-review")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/appeals/*/decision").hasAuthority("SCOPE_protection:appeal-decision")

                    // Arbitration & Mediation
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/arbitration/request").hasAuthority("SCOPE_protection:arbitration-request")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/arbitration/cases").hasAuthority("SCOPE_protection:arbitration-cases")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/mediation/request").hasAuthority("SCOPE_protection:mediation-request")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/mediation/status").hasAuthority("SCOPE_protection:mediation-status")

                    // Admin Operations - Claims Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/claims/pending").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/claims/*/force-approve").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/claims/*/force-reject").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/claims/*/override").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/claims/high-value").hasRole("PROTECTION_MANAGER")

                    // Admin Operations - Fraud Investigation
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/fraud/investigations").hasRole("FRAUD_INVESTIGATOR")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/fraud/*/investigate").hasRole("FRAUD_INVESTIGATOR")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/merchants/*/suspend").hasRole("FRAUD_INVESTIGATOR")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/fraud/patterns").hasRole("FRAUD_ANALYST")

                    // Admin Operations - Financial Control
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/financials/payouts").hasRole("FINANCIAL_CONTROLLER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/financials/*/process").hasRole("FINANCIAL_CONTROLLER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/financials/reconciliation").hasRole("FINANCIAL_CONTROLLER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/refunds/bulk-process").hasRole("FINANCIAL_CONTROLLER")

                    // Admin Operations - System Management
                    .requestMatchers("/api/v1/purchase-protection/admin/**").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/system/health").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/system/maintenance").hasRole("PROTECTION_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/purchase-protection/admin/audit/logs").hasRole("AUDITOR")
                    .requestMatchers(HttpMethod.POST, "/api/v1/purchase-protection/admin/bulk-operations").hasRole("PROTECTION_ADMIN")

                    // High-Security Internal service-to-service endpoints
                    .requestMatchers("/internal/purchase-protection/**").hasRole("SERVICE")
                    .requestMatchers("/internal/claims/**").hasRole("SERVICE")
                    .requestMatchers("/internal/refunds/**").hasRole("SERVICE")
                    .requestMatchers("/internal/risk-assessment/**").hasRole("SERVICE")

                    // All other endpoints require authentication - NO EXCEPTIONS
                    .anyRequest().authenticated()
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}