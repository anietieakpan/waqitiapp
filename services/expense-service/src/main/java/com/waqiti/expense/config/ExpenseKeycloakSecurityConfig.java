package com.waqiti.expense.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Expense Service Security Configuration
 * 
 * CRITICAL SECURITY: This service manages expense tracking, budgets,
 * and financial categorization requiring comprehensive security controls.
 * 
 * Security Features:
 * - JWT authentication with user isolation
 * - Budget access controls and sharing permissions
 * - Receipt upload security and validation
 * - Expense report generation with audit trail
 * - Team expense management with role-based access
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ExpenseKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                
                // Personal expense management
                .requestMatchers("/api/v1/expenses/personal/**").hasAuthority("SCOPE_expense:personal")
                .requestMatchers("/api/v1/expenses/create").hasAuthority("SCOPE_expense:create")
                .requestMatchers("/api/v1/expenses/*/edit").hasAuthority("SCOPE_expense:edit")
                .requestMatchers("/api/v1/expenses/*/delete").hasAuthority("SCOPE_expense:delete")
                
                // Budget management
                .requestMatchers("/api/v1/budgets/create").hasAuthority("SCOPE_budget:create")
                .requestMatchers("/api/v1/budgets/*/update").hasAuthority("SCOPE_budget:update")
                .requestMatchers("/api/v1/budgets/*/share").hasAuthority("SCOPE_budget:share")
                .requestMatchers("/api/v1/budgets/alerts/**").hasAuthority("SCOPE_budget:alerts")
                
                // Receipt management with file upload security
                .requestMatchers("/api/v1/receipts/upload").hasAuthority("SCOPE_receipt:upload")
                .requestMatchers("/api/v1/receipts/*/ocr").hasAuthority("SCOPE_receipt:process")
                .requestMatchers("/api/v1/receipts/*/verify").hasAuthority("SCOPE_receipt:verify")
                
                // Category management
                .requestMatchers("/api/v1/categories/**").hasAuthority("SCOPE_expense:categories")
                .requestMatchers("/api/v1/categories/custom/**").hasAuthority("SCOPE_expense:custom_categories")
                
                // Expense reports and analytics
                .requestMatchers("/api/v1/reports/generate").hasAuthority("SCOPE_expense:reports")
                .requestMatchers("/api/v1/reports/export/**").hasAuthority("SCOPE_expense:export")
                .requestMatchers("/api/v1/analytics/spending/**").hasAuthority("SCOPE_expense:analytics")
                
                // Team expense management
                .requestMatchers("/api/v1/team/expenses/**").hasAuthority("SCOPE_team:expenses")
                .requestMatchers("/api/v1/team/approve/**").hasRole("EXPENSE_APPROVER")
                .requestMatchers("/api/v1/team/reimburse/**").hasRole("EXPENSE_ADMIN")
                
                // Recurring expenses
                .requestMatchers("/api/v1/recurring/**").hasAuthority("SCOPE_expense:recurring")
                
                // Internal service endpoints
                .requestMatchers("/internal/**").hasAuthority("SCOPE_service:internal")
                
                // All API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            );
    }

    @Override
    protected void additionalConfiguration(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
            .addHeaderWriter((request, response) -> {
                // Security headers for file uploads
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Service-Type", "expense-management");
                response.setHeader("X-Max-Upload-Size", "10MB");
                response.setHeader("X-Allowed-File-Types", "image/jpeg,image/png,application/pdf");
            })
        );
        
        log.info("Expense Service security configuration applied - Budget and receipt protection enabled");
    }
}