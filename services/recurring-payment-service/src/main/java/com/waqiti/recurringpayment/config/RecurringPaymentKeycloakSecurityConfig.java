package com.waqiti.recurringpayment.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * Keycloak security configuration for Recurring Payment Service
 * Manages authentication and authorization for scheduled and subscription payments
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true")
@Order(1)
public class RecurringPaymentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain recurringPaymentKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        return createKeycloakSecurityFilterChain(http, "recurring-payment-service", httpSecurity -> {
            httpSecurity
                .authorizeHttpRequests(authz -> authz
                    // Public endpoints
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/recurring/public/**").permitAll()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    
                    // Subscription Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/subscriptions").hasAuthority("SCOPE_recurring:subscription-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/subscriptions").hasAuthority("SCOPE_recurring:subscription-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/subscriptions/*").hasAuthority("SCOPE_recurring:subscription-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/subscriptions/*").hasAuthority("SCOPE_recurring:subscription-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/recurring/subscriptions/*").hasAuthority("SCOPE_recurring:subscription-cancel")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/subscriptions/*/pause").hasAuthority("SCOPE_recurring:subscription-pause")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/subscriptions/*/resume").hasAuthority("SCOPE_recurring:subscription-resume")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/subscriptions/*/upgrade").hasAuthority("SCOPE_recurring:subscription-upgrade")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/subscriptions/*/downgrade").hasAuthority("SCOPE_recurring:subscription-downgrade")
                    
                    // Scheduled Payments
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/scheduled").hasAuthority("SCOPE_recurring:scheduled-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/scheduled").hasAuthority("SCOPE_recurring:scheduled-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/scheduled/*").hasAuthority("SCOPE_recurring:scheduled-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/scheduled/*").hasAuthority("SCOPE_recurring:scheduled-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/recurring/scheduled/*").hasAuthority("SCOPE_recurring:scheduled-cancel")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/scheduled/*/execute").hasAuthority("SCOPE_recurring:scheduled-execute")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/scheduled/*/skip").hasAuthority("SCOPE_recurring:scheduled-skip")
                    
                    // Standing Orders
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/standing-orders").hasAuthority("SCOPE_recurring:standing-order-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/standing-orders").hasAuthority("SCOPE_recurring:standing-order-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/standing-orders/*").hasAuthority("SCOPE_recurring:standing-order-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/recurring/standing-orders/*").hasAuthority("SCOPE_recurring:standing-order-delete")
                    
                    // Direct Debit Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/direct-debit/mandate").hasAuthority("SCOPE_recurring:mandate-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/direct-debit/mandates").hasAuthority("SCOPE_recurring:mandate-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/direct-debit/mandate/*").hasAuthority("SCOPE_recurring:mandate-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/recurring/direct-debit/mandate/*").hasAuthority("SCOPE_recurring:mandate-revoke")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/direct-debit/collection").hasAuthority("SCOPE_recurring:collection-initiate")
                    
                    // Bill Pay
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/bills").hasAuthority("SCOPE_recurring:bill-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/bills").hasAuthority("SCOPE_recurring:bill-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/bills/upcoming").hasAuthority("SCOPE_recurring:bill-upcoming")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/bills/*/pay").hasAuthority("SCOPE_recurring:bill-pay")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/bills/*/schedule").hasAuthority("SCOPE_recurring:bill-schedule")
                    
                    // Payment Plans
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/payment-plans").hasAuthority("SCOPE_recurring:payment-plan-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/payment-plans").hasAuthority("SCOPE_recurring:payment-plan-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/payment-plans/*").hasAuthority("SCOPE_recurring:payment-plan-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/payment-plans/*/restructure").hasAuthority("SCOPE_recurring:payment-plan-restructure")
                    
                    // Auto-pay Settings
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/autopay").hasAuthority("SCOPE_recurring:autopay-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/autopay").hasAuthority("SCOPE_recurring:autopay-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/autopay/enable").hasAuthority("SCOPE_recurring:autopay-enable")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/autopay/disable").hasAuthority("SCOPE_recurring:autopay-disable")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/autopay/rules").hasAuthority("SCOPE_recurring:autopay-rules-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/autopay/rules").hasAuthority("SCOPE_recurring:autopay-rules-create")
                    
                    // Retry Management
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/retries").hasAuthority("SCOPE_recurring:retry-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/retries/*/process").hasAuthority("SCOPE_recurring:retry-process")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/retries/*/reschedule").hasAuthority("SCOPE_recurring:retry-reschedule")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/retries/*/cancel").hasAuthority("SCOPE_recurring:retry-cancel")
                    
                    // Payment History
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/history").hasAuthority("SCOPE_recurring:history-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/history/export").hasAuthority("SCOPE_recurring:history-export")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/history/failed").hasAuthority("SCOPE_recurring:history-failed")
                    
                    // Notifications and Reminders
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/notifications").hasAuthority("SCOPE_recurring:notification-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/notifications/preferences").hasAuthority("SCOPE_recurring:notification-update")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/reminders").hasAuthority("SCOPE_recurring:reminder-create")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/recurring/reminders/*").hasAuthority("SCOPE_recurring:reminder-delete")
                    
                    // Analytics and Reporting
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/analytics").hasAuthority("SCOPE_recurring:analytics-read")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/analytics/forecast").hasAuthority("SCOPE_recurring:analytics-forecast")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/reports").hasAuthority("SCOPE_recurring:report-read")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/reports/generate").hasAuthority("SCOPE_recurring:report-generate")
                    
                    // Webhook Management
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/webhooks").hasAuthority("SCOPE_recurring:webhook-create")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/webhooks").hasAuthority("SCOPE_recurring:webhook-read")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/recurring/webhooks/*").hasAuthority("SCOPE_recurring:webhook-update")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/recurring/webhooks/*").hasAuthority("SCOPE_recurring:webhook-delete")
                    
                    // Admin Operations
                    .requestMatchers("/api/v1/recurring/admin/**").hasRole("RECURRING_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/admin/process-all").hasRole("RECURRING_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/admin/failed-payments").hasRole("RECURRING_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/admin/retry-failed").hasRole("RECURRING_ADMIN")
                    
                    // Scheduler Management (Admin only)
                    .requestMatchers("/api/v1/recurring/scheduler/**").hasRole("RECURRING_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/recurring/scheduler/jobs").hasRole("RECURRING_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/recurring/scheduler/trigger").hasRole("RECURRING_ADMIN")
                    
                    // Internal service-to-service endpoints
                    .requestMatchers("/internal/recurring/**").hasRole("SERVICE")
                    .requestMatchers("/internal/process/**").hasRole("SERVICE")
                    .requestMatchers("/internal/validate/**").hasRole("SERVICE")
                    
                    // Webhook receipt endpoints (public but validated)
                    .requestMatchers("/api/v1/recurring/webhooks/receive/**").permitAll()
                    
                    // All other endpoints require authentication
                    .anyRequest().authenticated()
                );
        });
    }
}