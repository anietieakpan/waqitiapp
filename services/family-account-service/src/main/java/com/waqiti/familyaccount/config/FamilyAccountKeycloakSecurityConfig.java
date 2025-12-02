package com.waqiti.familyaccount.config;

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
 * Keycloak security configuration for Family Account Service
 * Manages authentication and authorization for family account and parental control features
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class FamilyAccountKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain familyAccountKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Family Account Service");
        
        return createKeycloakSecurityFilterChain(http, "family-account-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/family/public/**").permitAll()
                .requestMatchers("/api/v1/family/features").permitAll() // Public features information
                
                // Family Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/family/accounts/create").hasAuthority("SCOPE_family:account-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/accounts").hasAuthority("SCOPE_family:account-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/accounts/*").hasAuthority("SCOPE_family:account-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/accounts/*").hasAuthority("SCOPE_family:account-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/family/accounts/*").hasAuthority("SCOPE_family:account-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/accounts/*/activate").hasAuthority("SCOPE_family:account-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/accounts/*/deactivate").hasAuthority("SCOPE_family:account-deactivate")
                
                // Member Management
                .requestMatchers(HttpMethod.POST, "/api/v1/family/members/add").hasAuthority("SCOPE_family:member-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/members").hasAuthority("SCOPE_family:members-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/members/*").hasAuthority("SCOPE_family:member-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/members/*").hasAuthority("SCOPE_family:member-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/family/members/*").hasAuthority("SCOPE_family:member-remove")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/members/*/promote").hasAuthority("SCOPE_family:member-promote")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/members/*/invite").hasAuthority("SCOPE_family:member-invite")
                
                // Child Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/family/children/add").hasAuthority("SCOPE_family:child-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/children").hasAuthority("SCOPE_family:children-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/children/*").hasAuthority("SCOPE_family:child-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/children/*").hasAuthority("SCOPE_family:child-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/children/*/activate-card").hasAuthority("SCOPE_family:child-card-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/children/*/deactivate-card").hasAuthority("SCOPE_family:child-card-deactivate")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/children/*/activity").hasAuthority("SCOPE_family:child-activity")
                
                // Allowance Management
                .requestMatchers(HttpMethod.POST, "/api/v1/family/allowance/setup").hasAuthority("SCOPE_family:allowance-setup")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/allowance").hasAuthority("SCOPE_family:allowance-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/allowance/*").hasAuthority("SCOPE_family:allowance-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/family/allowance/*").hasAuthority("SCOPE_family:allowance-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/allowance/*/pause").hasAuthority("SCOPE_family:allowance-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/allowance/*/resume").hasAuthority("SCOPE_family:allowance-resume")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/allowance/bonus").hasAuthority("SCOPE_family:allowance-bonus")
                
                // Spending Controls
                .requestMatchers(HttpMethod.POST, "/api/v1/family/controls/limits/set").hasAuthority("SCOPE_family:limits-set")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/controls/limits").hasAuthority("SCOPE_family:limits-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/controls/limits/*").hasAuthority("SCOPE_family:limits-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/controls/categories/block").hasAuthority("SCOPE_family:categories-block")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/controls/categories/unblock").hasAuthority("SCOPE_family:categories-unblock")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/controls/categories").hasAuthority("SCOPE_family:categories-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/controls/merchants/block").hasAuthority("SCOPE_family:merchants-block")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/controls/merchants/whitelist").hasAuthority("SCOPE_family:merchants-whitelist")
                
                // Transaction Approval
                .requestMatchers(HttpMethod.GET, "/api/v1/family/approvals/pending").hasAuthority("SCOPE_family:approvals-pending")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/approvals/*/approve").hasAuthority("SCOPE_family:approval-approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/approvals/*/reject").hasAuthority("SCOPE_family:approval-reject")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/approvals/history").hasAuthority("SCOPE_family:approvals-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/approvals/rules").hasAuthority("SCOPE_family:approval-rules-set")
                
                // Chores & Tasks
                .requestMatchers(HttpMethod.POST, "/api/v1/family/chores/create").hasAuthority("SCOPE_family:chore-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/chores").hasAuthority("SCOPE_family:chores-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/chores/*").hasAuthority("SCOPE_family:chore-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/family/chores/*").hasAuthority("SCOPE_family:chore-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/chores/*/assign").hasAuthority("SCOPE_family:chore-assign")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/chores/*/complete").hasAuthority("SCOPE_family:chore-complete")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/chores/*/verify").hasAuthority("SCOPE_family:chore-verify")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/chores/*/pay").hasAuthority("SCOPE_family:chore-pay")
                
                // Savings Goals
                .requestMatchers(HttpMethod.POST, "/api/v1/family/goals/create").hasAuthority("SCOPE_family:goal-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/goals").hasAuthority("SCOPE_family:goals-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/goals/*").hasAuthority("SCOPE_family:goal-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/family/goals/*").hasAuthority("SCOPE_family:goal-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/goals/*/contribute").hasAuthority("SCOPE_family:goal-contribute")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/goals/*/progress").hasAuthority("SCOPE_family:goal-progress")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/goals/*/withdraw").hasAuthority("SCOPE_family:goal-withdraw")
                
                // Financial Education
                .requestMatchers(HttpMethod.GET, "/api/v1/family/education/modules").hasAuthority("SCOPE_family:education-modules")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/education/modules/*").hasAuthority("SCOPE_family:education-module-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/education/modules/*/start").hasAuthority("SCOPE_family:education-start")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/education/modules/*/complete").hasAuthority("SCOPE_family:education-complete")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/education/quiz/*").hasAuthority("SCOPE_family:education-quiz")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/education/quiz/*/submit").hasAuthority("SCOPE_family:education-quiz-submit")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/education/progress").hasAuthority("SCOPE_family:education-progress")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/education/certificates").hasAuthority("SCOPE_family:education-certificates")
                
                // Notifications & Alerts
                .requestMatchers(HttpMethod.GET, "/api/v1/family/notifications").hasAuthority("SCOPE_family:notifications-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/family/notifications/settings").hasAuthority("SCOPE_family:notifications-settings")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/notifications/*/mark-read").hasAuthority("SCOPE_family:notification-mark-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/alerts").hasAuthority("SCOPE_family:alerts-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/alerts/configure").hasAuthority("SCOPE_family:alerts-configure")
                
                // Reports & Analytics
                .requestMatchers(HttpMethod.GET, "/api/v1/family/reports/spending").hasAuthority("SCOPE_family:reports-spending")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/reports/savings").hasAuthority("SCOPE_family:reports-savings")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/reports/allowance").hasAuthority("SCOPE_family:reports-allowance")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/reports/activity").hasAuthority("SCOPE_family:reports-activity")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/analytics/trends").hasAuthority("SCOPE_family:analytics-trends")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/analytics/insights").hasAuthority("SCOPE_family:analytics-insights")
                
                // Family Vault (Shared Savings)
                .requestMatchers(HttpMethod.POST, "/api/v1/family/vault/create").hasAuthority("SCOPE_family:vault-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/vault").hasAuthority("SCOPE_family:vault-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/vault/deposit").hasAuthority("SCOPE_family:vault-deposit")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/vault/withdraw").hasAuthority("SCOPE_family:vault-withdraw")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/vault/history").hasAuthority("SCOPE_family:vault-history")
                
                // Parental Controls (Parent Only)
                .requestMatchers(HttpMethod.POST, "/api/v1/family/parental/lock").hasRole("FAMILY_PARENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/parental/unlock").hasRole("FAMILY_PARENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/parental/emergency-freeze").hasRole("FAMILY_PARENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/parental/audit-log").hasRole("FAMILY_PARENT")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/parental/override").hasRole("FAMILY_PARENT")
                
                // Child-Specific Endpoints (Child Access)
                .requestMatchers(HttpMethod.GET, "/api/v1/family/child/balance").hasRole("FAMILY_CHILD")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/child/transactions").hasRole("FAMILY_CHILD")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/child/request-money").hasRole("FAMILY_CHILD")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/child/chores").hasRole("FAMILY_CHILD")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/child/chores/*/claim").hasRole("FAMILY_CHILD")
                
                // Admin Operations
                .requestMatchers("/api/v1/family/admin/**").hasRole("FAMILY_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/family/admin/accounts/all").hasRole("FAMILY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/admin/accounts/*/suspend").hasRole("FAMILY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/family/admin/accounts/*/reinstate").hasRole("FAMILY_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/family/**").hasRole("SERVICE")
                .requestMatchers("/internal/parental/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}