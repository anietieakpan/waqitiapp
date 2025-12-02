package com.waqiti.virtualcard.config;

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
 * Keycloak security configuration for Virtual Card Service
 * CRITICAL SECURITY IMPLEMENTATION - Manages virtual payment card operations and PCI DSS compliance
 * Compliance: PCI DSS, EMV, 3DS, Tokenization Standards
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class VirtualCardKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain virtualCardKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Virtual Card Service");
        
        return createKeycloakSecurityFilterChain(http, "virtual-card-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited for security
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/virtual-cards/public/supported-networks").permitAll()
                
                // Card Management - Core Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/create").hasAuthority("SCOPE_virtual-card:create")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards").hasAuthority("SCOPE_virtual-card:list")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*").hasAuthority("SCOPE_virtual-card:details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/activate").hasAuthority("SCOPE_virtual-card:activate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/deactivate").hasAuthority("SCOPE_virtual-card:deactivate")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/suspend").hasAuthority("SCOPE_virtual-card:suspend")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/virtual-cards/*").hasAuthority("SCOPE_virtual-card:delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/replace").hasAuthority("SCOPE_virtual-card:replace")
                
                // Card Details & Security - PCI DSS Compliance
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/details/secure").hasAuthority("SCOPE_virtual-card:secure-details")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/cvv").hasAuthority("SCOPE_virtual-card:cvv-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/cvv/regenerate").hasAuthority("SCOPE_virtual-card:cvv-regenerate")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/pin").hasAuthority("SCOPE_virtual-card:pin-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/pin/set").hasAuthority("SCOPE_virtual-card:pin-set")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/pin/change").hasAuthority("SCOPE_virtual-card:pin-change")
                
                // Card Tokenization & Apple/Google Pay
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/tokenize").hasAuthority("SCOPE_virtual-card:tokenize")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/tokens").hasAuthority("SCOPE_virtual-card:tokens-view")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/virtual-cards/tokens/*").hasAuthority("SCOPE_virtual-card:token-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/apple-pay/provision").hasAuthority("SCOPE_virtual-card:apple-pay-provision")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/google-pay/provision").hasAuthority("SCOPE_virtual-card:google-pay-provision")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/digital-wallets").hasAuthority("SCOPE_virtual-card:digital-wallets-view")
                
                // Transaction History & Details
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/transactions").hasAuthority("SCOPE_virtual-card:transactions-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/transactions/*").hasAuthority("SCOPE_virtual-card:transaction-details")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/transactions/pending").hasAuthority("SCOPE_virtual-card:transactions-pending")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/balance").hasAuthority("SCOPE_virtual-card:balance-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/statements").hasAuthority("SCOPE_virtual-card:statements-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/statements/*/download").hasAuthority("SCOPE_virtual-card:statement-download")
                
                // Card Controls & Limits
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/controls").hasAuthority("SCOPE_virtual-card:controls-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/controls/spending-limits").hasAuthority("SCOPE_virtual-card:spending-limits-set")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/controls/merchant-categories").hasAuthority("SCOPE_virtual-card:merchant-controls-set")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/controls/location").hasAuthority("SCOPE_virtual-card:location-controls-set")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/controls/time-based").hasAuthority("SCOPE_virtual-card:time-controls-set")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/controls/temporary-block").hasAuthority("SCOPE_virtual-card:temporary-block")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/virtual-cards/*/controls/temporary-block").hasAuthority("SCOPE_virtual-card:temporary-unblock")
                
                // Fraud & Security Monitoring
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/fraud/alerts").hasAuthority("SCOPE_virtual-card:fraud-alerts")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/fraud/report").hasAuthority("SCOPE_virtual-card:fraud-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/security/activities").hasAuthority("SCOPE_virtual-card:security-activities")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/security/verify-transaction").hasAuthority("SCOPE_virtual-card:verify-transaction")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/security/risk-score").hasAuthority("SCOPE_virtual-card:risk-score")
                
                // 3D Secure Authentication
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/3ds/authenticate").hasAuthority("SCOPE_virtual-card:3ds-authenticate")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/3ds/*/status").hasAuthority("SCOPE_virtual-card:3ds-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/3ds/*/complete").hasAuthority("SCOPE_virtual-card:3ds-complete")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/3ds/history").hasAuthority("SCOPE_virtual-card:3ds-history")
                
                // Card Funding & Top-up
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/funding/add").hasAuthority("SCOPE_virtual-card:funding-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/funding/sources").hasAuthority("SCOPE_virtual-card:funding-sources")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/funding/auto-reload").hasAuthority("SCOPE_virtual-card:auto-reload-set")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/funding/transfer").hasAuthority("SCOPE_virtual-card:funding-transfer")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/funding/history").hasAuthority("SCOPE_virtual-card:funding-history")
                
                // Card Disputes & Chargebacks
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/disputes/create").hasAuthority("SCOPE_virtual-card:dispute-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/disputes").hasAuthority("SCOPE_virtual-card:disputes-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/disputes/*").hasAuthority("SCOPE_virtual-card:dispute-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/disputes/*").hasAuthority("SCOPE_virtual-card:dispute-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/disputes/*/evidence").hasAuthority("SCOPE_virtual-card:dispute-evidence")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/chargebacks").hasAuthority("SCOPE_virtual-card:chargebacks-view")
                
                // Merchant & Authorization Management
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/authorizations").hasAuthority("SCOPE_virtual-card:authorizations-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/authorizations/*/approve").hasAuthority("SCOPE_virtual-card:authorization-approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/authorizations/*/decline").hasAuthority("SCOPE_virtual-card:authorization-decline")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/merchants/blocked").hasAuthority("SCOPE_virtual-card:blocked-merchants")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/merchants/*/block").hasAuthority("SCOPE_virtual-card:merchant-block")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/virtual-cards/merchants/*/block").hasAuthority("SCOPE_virtual-card:merchant-unblock")
                
                // Recurring Payments & Subscriptions
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/recurring-payments").hasAuthority("SCOPE_virtual-card:recurring-payments-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/recurring-payments/authorize").hasAuthority("SCOPE_virtual-card:recurring-authorize")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/virtual-cards/recurring-payments/*").hasAuthority("SCOPE_virtual-card:recurring-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/subscriptions").hasAuthority("SCOPE_virtual-card:subscriptions-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/subscriptions/*/pause").hasAuthority("SCOPE_virtual-card:subscription-pause")
                
                // Card Analytics & Insights
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/analytics/spending").hasAuthority("SCOPE_virtual-card:analytics-spending")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/analytics/categories").hasAuthority("SCOPE_virtual-card:analytics-categories")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/analytics/trends").hasAuthority("SCOPE_virtual-card:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/insights/cashback").hasAuthority("SCOPE_virtual-card:insights-cashback")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/insights/rewards").hasAuthority("SCOPE_virtual-card:insights-rewards")
                
                // Card Notifications & Alerts
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/notifications").hasAuthority("SCOPE_virtual-card:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/notifications/preferences").hasAuthority("SCOPE_virtual-card:notification-preferences")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/alerts/transaction-limits").hasAuthority("SCOPE_virtual-card:transaction-alerts")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/alerts/security").hasAuthority("SCOPE_virtual-card:security-alerts")
                
                // Card Physical Delivery (if supported)
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/physical/order").hasAuthority("SCOPE_virtual-card:physical-order")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/physical/delivery-status").hasAuthority("SCOPE_virtual-card:physical-delivery-status")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/physical/activate").hasAuthority("SCOPE_virtual-card:physical-activate")
                
                // International Usage & Travel
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/*/travel/notify").hasAuthority("SCOPE_virtual-card:travel-notify")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/*/international/fees").hasAuthority("SCOPE_virtual-card:international-fees")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/international/enable").hasAuthority("SCOPE_virtual-card:international-enable")
                .requestMatchers(HttpMethod.PUT, "/api/v1/virtual-cards/*/international/disable").hasAuthority("SCOPE_virtual-card:international-disable")
                
                // Admin Operations - Card Management
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/cards/all").hasRole("VIRTUAL_CARD_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/admin/cards/*/force-block").hasRole("VIRTUAL_CARD_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/admin/cards/*/force-activate").hasRole("VIRTUAL_CARD_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/fraud/investigations").hasRole("FRAUD_ANALYST")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/admin/fraud/*/resolve").hasRole("FRAUD_ANALYST")
                
                // Admin Operations - Compliance & Monitoring
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/compliance/pci-audit").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/monitoring/transactions").hasRole("TRANSACTION_MONITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/admin/limits/global/set").hasRole("RISK_MANAGER")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/reports/settlement").hasRole("SETTLEMENT_MANAGER")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/virtual-cards/admin/**").hasRole("VIRTUAL_CARD_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/system/health").hasRole("VIRTUAL_CARD_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/admin/system/reconcile").hasRole("VIRTUAL_CARD_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/virtual-cards/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/admin/bulk-operations").hasRole("VIRTUAL_CARD_ADMIN")
                
                // Webhook endpoints - Network processors (Mastercard, Visa, Marqeta)
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/webhooks/mastercard").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/webhooks/visa").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/webhooks/marqeta").hasRole("SERVICE")
                .requestMatchers(HttpMethod.POST, "/api/v1/virtual-cards/webhooks/3ds-provider").hasRole("SERVICE")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/virtual-cards/**").hasRole("SERVICE")
                .requestMatchers("/internal/card-processing/**").hasRole("SERVICE")
                .requestMatchers("/internal/tokenization/**").hasRole("SERVICE")
                .requestMatchers("/internal/fraud-detection/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}