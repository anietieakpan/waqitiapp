package com.waqiti.billpayment.config;

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
 * Keycloak security configuration for Bill Payment Service
 * Manages authentication and authorization for utility and bill payment operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class BillPaymentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain billPaymentKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Bill Payment Service");
        
        return createKeycloakSecurityFilterChain(http, "bill-payment-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/bills/public/**").permitAll()
                
                // Biller Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/billers").hasAuthority("SCOPE_bill-payment:biller-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/billers/*").hasAuthority("SCOPE_bill-payment:biller-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/billers/categories").hasAuthority("SCOPE_bill-payment:biller-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/billers/search").hasAuthority("SCOPE_bill-payment:biller-search")
                
                // Bill Account Management
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/accounts/add").hasAuthority("SCOPE_bill-payment:account-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/accounts").hasAuthority("SCOPE_bill-payment:account-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/accounts/*").hasAuthority("SCOPE_bill-payment:account-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bills/accounts/*").hasAuthority("SCOPE_bill-payment:account-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/accounts/*").hasAuthority("SCOPE_bill-payment:account-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/accounts/*/verify").hasAuthority("SCOPE_bill-payment:account-verify")
                
                // Bill Inquiry and Validation
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/inquiry").hasAuthority("SCOPE_bill-payment:inquiry")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/validate").hasAuthority("SCOPE_bill-payment:validate")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/balance/*").hasAuthority("SCOPE_bill-payment:balance-check")
                
                // Bill Payment Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/pay").hasAuthority("SCOPE_bill-payment:pay")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/pay/instant").hasAuthority("SCOPE_bill-payment:pay-instant")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/pay/scheduled").hasAuthority("SCOPE_bill-payment:pay-scheduled")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/pay/recurring").hasAuthority("SCOPE_bill-payment:pay-recurring")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/pay/bulk").hasAuthority("SCOPE_bill-payment:pay-bulk")
                
                // Payment Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/payments").hasAuthority("SCOPE_bill-payment:payment-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/payments/*").hasAuthority("SCOPE_bill-payment:payment-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/payments/*/status").hasAuthority("SCOPE_bill-payment:payment-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/payments/*/cancel").hasAuthority("SCOPE_bill-payment:payment-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/payments/*/retry").hasAuthority("SCOPE_bill-payment:payment-retry")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/payments/*/receipt").hasAuthority("SCOPE_bill-payment:receipt-download")
                
                // Scheduled Payment Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/scheduled").hasAuthority("SCOPE_bill-payment:scheduled-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bills/scheduled/*").hasAuthority("SCOPE_bill-payment:scheduled-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/scheduled/*").hasAuthority("SCOPE_bill-payment:scheduled-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/scheduled/*/pause").hasAuthority("SCOPE_bill-payment:scheduled-pause")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/scheduled/*/resume").hasAuthority("SCOPE_bill-payment:scheduled-resume")
                
                // Recurring Payment Management
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/recurring").hasAuthority("SCOPE_bill-payment:recurring-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bills/recurring/*").hasAuthority("SCOPE_bill-payment:recurring-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/recurring/*").hasAuthority("SCOPE_bill-payment:recurring-cancel")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/recurring/*/skip").hasAuthority("SCOPE_bill-payment:recurring-skip")
                
                // Auto-pay Settings
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/autopay/setup").hasAuthority("SCOPE_bill-payment:autopay-setup")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/autopay").hasAuthority("SCOPE_bill-payment:autopay-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bills/autopay/*").hasAuthority("SCOPE_bill-payment:autopay-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/autopay/*").hasAuthority("SCOPE_bill-payment:autopay-cancel")
                
                // Payment History and Reports
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/history").hasAuthority("SCOPE_bill-payment:history-read")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/reports/**").hasAuthority("SCOPE_bill-payment:reports")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/analytics/**").hasAuthority("SCOPE_bill-payment:analytics")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/export/**").hasAuthority("SCOPE_bill-payment:export")
                
                // Notifications and Reminders
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/reminders").hasAuthority("SCOPE_bill-payment:reminder-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/reminders/setup").hasAuthority("SCOPE_bill-payment:reminder-setup")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bills/reminders/*").hasAuthority("SCOPE_bill-payment:reminder-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/reminders/*").hasAuthority("SCOPE_bill-payment:reminder-delete")
                
                // Favorites and Templates
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/favorites").hasAuthority("SCOPE_bill-payment:favorite-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/favorites/add").hasAuthority("SCOPE_bill-payment:favorite-add")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/favorites/*").hasAuthority("SCOPE_bill-payment:favorite-delete")
                .requestMatchers(HttpMethod.GET, "/api/v1/bills/templates").hasAuthority("SCOPE_bill-payment:template-read")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/templates/create").hasAuthority("SCOPE_bill-payment:template-create")
                
                // Admin Operations
                .requestMatchers("/api/v1/bills/admin/**").hasRole("BILL_PAYMENT_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/bills/billers/add").hasRole("BILL_PAYMENT_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/bills/billers/*").hasRole("BILL_PAYMENT_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bills/billers/*").hasRole("BILL_PAYMENT_ADMIN")
                .requestMatchers("/api/v1/bills/config/**").hasRole("BILL_PAYMENT_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/bills/**").hasRole("SERVICE")
                .requestMatchers("/internal/bill-payment/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}