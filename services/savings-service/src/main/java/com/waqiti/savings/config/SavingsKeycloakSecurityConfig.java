package com.waqiti.savings.config;

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
 * Keycloak security configuration for Savings Service
 * Manages authentication and authorization for savings accounts and goals
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class SavingsKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain savingsKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Savings Service");
        
        return createKeycloakSecurityFilterChain(http, "savings-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/savings/public/**").permitAll()
                .requestMatchers("/api/v1/savings/rates").permitAll() // Public interest rates
                .requestMatchers("/api/v1/savings/calculators/**").permitAll() // Public calculators
                
                // Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/accounts/create").hasAuthority("SCOPE_savings:account-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/accounts").hasAuthority("SCOPE_savings:accounts-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/accounts/*").hasAuthority("SCOPE_savings:account-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/accounts/*").hasAuthority("SCOPE_savings:account-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/savings/accounts/*").hasAuthority("SCOPE_savings:account-close")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/accounts/*/activate").hasAuthority("SCOPE_savings:account-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/accounts/*/freeze").hasAuthority("SCOPE_savings:account-freeze")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/accounts/*/balance").hasAuthority("SCOPE_savings:balance-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/accounts/*/statement").hasAuthority("SCOPE_savings:statement-view")
                
                // Deposit Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/deposit").hasAuthority("SCOPE_savings:deposit")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/deposit/schedule").hasAuthority("SCOPE_savings:deposit-schedule")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/deposits/history").hasAuthority("SCOPE_savings:deposit-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/deposit/cancel").hasAuthority("SCOPE_savings:deposit-cancel")
                
                // Withdrawal Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/withdraw").hasAuthority("SCOPE_savings:withdraw")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/withdraw/request").hasAuthority("SCOPE_savings:withdraw-request")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/withdrawals/history").hasAuthority("SCOPE_savings:withdrawal-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/withdraw/cancel").hasAuthority("SCOPE_savings:withdrawal-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/withdraw/limits").hasAuthority("SCOPE_savings:withdrawal-limits")
                
                // Goals Management
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/goals/create").hasAuthority("SCOPE_savings:goal-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/goals").hasAuthority("SCOPE_savings:goals-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/goals/*").hasAuthority("SCOPE_savings:goal-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/goals/*").hasAuthority("SCOPE_savings:goal-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/savings/goals/*").hasAuthority("SCOPE_savings:goal-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/goals/*/contribute").hasAuthority("SCOPE_savings:goal-contribute")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/goals/*/progress").hasAuthority("SCOPE_savings:goal-progress")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/goals/*/pause").hasAuthority("SCOPE_savings:goal-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/goals/*/resume").hasAuthority("SCOPE_savings:goal-resume")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/goals/*/achieve").hasAuthority("SCOPE_savings:goal-achieve")
                
                // Fixed Deposits
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/fixed-deposit/create").hasAuthority("SCOPE_savings:fd-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/fixed-deposits").hasAuthority("SCOPE_savings:fd-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/fixed-deposit/*").hasAuthority("SCOPE_savings:fd-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/fixed-deposit/*/renew").hasAuthority("SCOPE_savings:fd-renew")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/fixed-deposit/*/break").hasAuthority("SCOPE_savings:fd-break")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/fixed-deposit/*/maturity").hasAuthority("SCOPE_savings:fd-maturity")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/fixed-deposit/calculator").hasAuthority("SCOPE_savings:fd-calculate")
                
                // Recurring Deposits
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/recurring-deposit/create").hasAuthority("SCOPE_savings:rd-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/recurring-deposits").hasAuthority("SCOPE_savings:rd-list")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/recurring-deposit/*").hasAuthority("SCOPE_savings:rd-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/recurring-deposit/*/skip").hasAuthority("SCOPE_savings:rd-skip")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/recurring-deposit/*/stop").hasAuthority("SCOPE_savings:rd-stop")
                
                // Auto-Save Features
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/auto-save/round-up/enable").hasAuthority("SCOPE_savings:roundup-enable")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/auto-save/round-up/disable").hasAuthority("SCOPE_savings:roundup-disable")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/auto-save/round-up/settings").hasAuthority("SCOPE_savings:roundup-settings")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/auto-save/round-up/settings").hasAuthority("SCOPE_savings:roundup-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/auto-save/percentage/setup").hasAuthority("SCOPE_savings:percentage-setup")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/auto-save/rules").hasAuthority("SCOPE_savings:autosave-rules")
                
                // Interest & Earnings
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/interest/earned").hasAuthority("SCOPE_savings:interest-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/interest/projection").hasAuthority("SCOPE_savings:interest-projection")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/interest/history").hasAuthority("SCOPE_savings:interest-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/tax/certificate").hasAuthority("SCOPE_savings:tax-certificate")
                
                // Transfers
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/transfer/internal").hasAuthority("SCOPE_savings:transfer-internal")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/transfer/external").hasAuthority("SCOPE_savings:transfer-external")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/transfers/history").hasAuthority("SCOPE_savings:transfer-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/transfer/schedule").hasAuthority("SCOPE_savings:transfer-schedule")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/savings/transfer/schedule/*").hasAuthority("SCOPE_savings:transfer-cancel")
                
                // Analytics & Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/analytics/summary").hasAuthority("SCOPE_savings:analytics-summary")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/analytics/trends").hasAuthority("SCOPE_savings:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/analytics/projections").hasAuthority("SCOPE_savings:analytics-projections")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/reports/monthly").hasAuthority("SCOPE_savings:reports-monthly")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/reports/annual").hasAuthority("SCOPE_savings:reports-annual")
                
                // Notifications & Preferences
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/preferences").hasAuthority("SCOPE_savings:preferences-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/preferences").hasAuthority("SCOPE_savings:preferences-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/notifications").hasAuthority("SCOPE_savings:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/notifications/settings").hasAuthority("SCOPE_savings:notifications-update")
                
                // Rewards & Bonuses
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/rewards/available").hasAuthority("SCOPE_savings:rewards-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/rewards/claim").hasAuthority("SCOPE_savings:rewards-claim")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/rewards/history").hasAuthority("SCOPE_savings:rewards-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/milestones").hasAuthority("SCOPE_savings:milestones-view")
                
                // Joint Accounts
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/joint/create").hasAuthority("SCOPE_savings:joint-create")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/joint/*/add-member").hasAuthority("SCOPE_savings:joint-add-member")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/joint/*/remove-member").hasAuthority("SCOPE_savings:joint-remove-member")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/joint/*/members").hasAuthority("SCOPE_savings:joint-members")
                
                // Admin Operations
                .requestMatchers("/api/v1/savings/admin/**").hasRole("SAVINGS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/admin/interest/calculate").hasRole("SAVINGS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/admin/interest/post").hasRole("SAVINGS_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/savings/admin/rates").hasRole("SAVINGS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/admin/accounts/*/freeze").hasRole("SAVINGS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/savings/admin/accounts/*/unfreeze").hasRole("SAVINGS_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/savings/admin/reports").hasRole("SAVINGS_ANALYST")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/savings/**").hasRole("SERVICE")
                .requestMatchers("/internal/interest/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}