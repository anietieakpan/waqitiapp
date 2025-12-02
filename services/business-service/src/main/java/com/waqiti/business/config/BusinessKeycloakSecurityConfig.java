package com.waqiti.business.config;

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
 * Keycloak security configuration for Business Service
 * Manages business accounts, invoicing, expense tracking, and business financial operations
 * Critical for B2B and business customer operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class BusinessKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain businessKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Business Service");
        
        return createKeycloakSecurityFilterChain(http, "business-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/business/public/types").permitAll()
                .requestMatchers("/api/v1/business/public/requirements").permitAll()
                
                // Business Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/register").hasAuthority("SCOPE_business:account-register")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/accounts").hasAuthority("SCOPE_business:accounts-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/accounts/*").hasAuthority("SCOPE_business:account-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/accounts/*").hasAuthority("SCOPE_business:account-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/business/accounts/*").hasAuthority("SCOPE_business:account-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/verify").hasAuthority("SCOPE_business:account-verify")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/activate").hasAuthority("SCOPE_business:account-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/suspend").hasAuthority("SCOPE_business:account-suspend")
                
                // Business Profile Management
                .requestMatchers(HttpMethod.GET, "/api/v1/business/accounts/*/profile").hasAuthority("SCOPE_business:profile-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/accounts/*/profile").hasAuthority("SCOPE_business:profile-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/logo").hasAuthority("SCOPE_business:logo-upload")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/accounts/*/documents").hasAuthority("SCOPE_business:documents-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/documents").hasAuthority("SCOPE_business:document-upload")
                
                // Team & Employee Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/employees").hasAuthority("SCOPE_business:employee-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/accounts/*/employees").hasAuthority("SCOPE_business:employees-list")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/accounts/*/employees/*").hasAuthority("SCOPE_business:employee-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/business/accounts/*/employees/*").hasAuthority("SCOPE_business:employee-remove")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/accounts/*/employees/*/permissions").hasAuthority("SCOPE_business:employee-permissions")
                
                // Invoice Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/invoices/create").hasAuthority("SCOPE_business:invoice-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/invoices").hasAuthority("SCOPE_business:invoices-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/invoices/*").hasAuthority("SCOPE_business:invoice-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/invoices/*").hasAuthority("SCOPE_business:invoice-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/business/invoices/*").hasAuthority("SCOPE_business:invoice-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/invoices/*/send").hasAuthority("SCOPE_business:invoice-send")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/invoices/*/cancel").hasAuthority("SCOPE_business:invoice-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/invoices/*/mark-paid").hasAuthority("SCOPE_business:invoice-mark-paid")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/invoices/*/pdf").hasAuthority("SCOPE_business:invoice-download")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/invoices/*/reminder").hasAuthority("SCOPE_business:invoice-reminder")
                
                // Expense Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/expenses").hasAuthority("SCOPE_business:expense-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/expenses").hasAuthority("SCOPE_business:expenses-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/expenses/*").hasAuthority("SCOPE_business:expense-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/expenses/*").hasAuthority("SCOPE_business:expense-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/business/expenses/*").hasAuthority("SCOPE_business:expense-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/expenses/*/categorize").hasAuthority("SCOPE_business:expense-categorize")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/expenses/*/receipt").hasAuthority("SCOPE_business:expense-receipt-upload")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/expenses/*/approve").hasAuthority("SCOPE_business:expense-approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/expenses/*/reject").hasAuthority("SCOPE_business:expense-reject")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/expenses/*/reimburse").hasAuthority("SCOPE_business:expense-reimburse")
                
                // Vendor Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/vendors").hasAuthority("SCOPE_business:vendor-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/vendors").hasAuthority("SCOPE_business:vendors-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/vendors/*").hasAuthority("SCOPE_business:vendor-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/vendors/*").hasAuthority("SCOPE_business:vendor-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/business/vendors/*").hasAuthority("SCOPE_business:vendor-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/vendors/*/payments").hasAuthority("SCOPE_business:vendor-payment")
                
                // Customer Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/customers").hasAuthority("SCOPE_business:customer-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/customers").hasAuthority("SCOPE_business:customers-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/customers/*").hasAuthority("SCOPE_business:customer-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/business/customers/*").hasAuthority("SCOPE_business:customer-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/business/customers/*").hasAuthority("SCOPE_business:customer-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/customers/*/transactions").hasAuthority("SCOPE_business:customer-transactions")
                
                // Payroll Management
                .requestMatchers(HttpMethod.POST, "/api/v1/business/payroll/process").hasAuthority("SCOPE_business:payroll-process")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/payroll").hasAuthority("SCOPE_business:payroll-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/payroll/*/details").hasAuthority("SCOPE_business:payroll-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/payroll/*/approve").hasAuthority("SCOPE_business:payroll-approve")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/payroll/schedule").hasAuthority("SCOPE_business:payroll-schedule")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/payroll/tax-calculations").hasAuthority("SCOPE_business:payroll-tax")
                
                // Financial Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/business/reports/profit-loss").hasAuthority("SCOPE_business:report-profit-loss")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/reports/cash-flow").hasAuthority("SCOPE_business:report-cash-flow")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/reports/balance-sheet").hasAuthority("SCOPE_business:report-balance-sheet")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/reports/expense-summary").hasAuthority("SCOPE_business:report-expense")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/reports/revenue").hasAuthority("SCOPE_business:report-revenue")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/reports/tax").hasAuthority("SCOPE_business:report-tax")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/reports/export").hasAuthority("SCOPE_business:report-export")
                
                // Business Banking
                .requestMatchers(HttpMethod.GET, "/api/v1/business/banking/accounts").hasAuthority("SCOPE_business:banking-accounts")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/banking/accounts/link").hasAuthority("SCOPE_business:banking-link")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/banking/transactions").hasAuthority("SCOPE_business:banking-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/banking/transfer").hasAuthority("SCOPE_business:banking-transfer")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/banking/statements").hasAuthority("SCOPE_business:banking-statements")
                
                // Tax Management
                .requestMatchers(HttpMethod.GET, "/api/v1/business/tax/calculations").hasAuthority("SCOPE_business:tax-calculate")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/tax/filings").hasAuthority("SCOPE_business:tax-filings")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/tax/file").hasAuthority("SCOPE_business:tax-file")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/tax/deductions").hasAuthority("SCOPE_business:tax-deductions")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/tax/payment").hasAuthority("SCOPE_business:tax-payment")
                
                // Business Loans & Credit
                .requestMatchers(HttpMethod.POST, "/api/v1/business/loans/apply").hasAuthority("SCOPE_business:loan-apply")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/loans").hasAuthority("SCOPE_business:loans-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/loans/*/details").hasAuthority("SCOPE_business:loan-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/loans/*/repay").hasAuthority("SCOPE_business:loan-repay")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/credit/score").hasAuthority("SCOPE_business:credit-score")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/credit/report").hasAuthority("SCOPE_business:credit-report")
                
                // Admin Operations - Business Management
                .requestMatchers(HttpMethod.GET, "/api/v1/business/admin/accounts/pending").hasRole("BUSINESS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/admin/accounts/*/approve").hasRole("BUSINESS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/admin/accounts/*/reject").hasRole("BUSINESS_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/admin/accounts/high-risk").hasRole("BUSINESS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/admin/accounts/*/freeze").hasRole("BUSINESS_ADMIN")
                
                // Admin Operations - Compliance
                .requestMatchers(HttpMethod.GET, "/api/v1/business/admin/compliance/review").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/admin/compliance/*/investigate").hasRole("COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/admin/tax/audits").hasRole("TAX_AUDITOR")
                
                // Admin Operations - System
                .requestMatchers("/api/v1/business/admin/**").hasRole("BUSINESS_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/business/admin/system/health").hasRole("BUSINESS_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/business/admin/system/maintenance").hasRole("BUSINESS_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/business/**").hasRole("SERVICE")
                .requestMatchers("/internal/payroll/**").hasRole("SERVICE")
                .requestMatchers("/internal/tax-calculation/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}