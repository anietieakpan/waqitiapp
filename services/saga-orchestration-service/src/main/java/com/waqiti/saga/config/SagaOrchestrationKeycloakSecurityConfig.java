package com.waqiti.saga.config;

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
 * Keycloak security configuration for Saga Orchestration Service
 * CRITICAL SECURITY IMPLEMENTATION - Distributed transaction orchestration
 * Manages complex multi-step transactions with compensation and rollback capabilities
 * Essential for maintaining data consistency across microservices
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class SagaOrchestrationKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain sagaOrchestrationKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for Saga Orchestration Service - Distributed Transactions");
        
        return createKeycloakSecurityFilterChain(http, "saga-orchestration-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Extremely limited
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Saga Execution - CRITICAL OPERATIONS
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/execute").hasAuthority("SCOPE_saga:execute")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/executions").hasAuthority("SCOPE_saga:executions-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/executions/*").hasAuthority("SCOPE_saga:execution-details")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/executions/*/status").hasAuthority("SCOPE_saga:execution-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/executions/*/steps").hasAuthority("SCOPE_saga:execution-steps")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/executions/*/retry").hasAuthority("SCOPE_saga:execution-retry")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/executions/*/cancel").hasAuthority("SCOPE_saga:execution-cancel")
                
                // Transaction Orchestration
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/p2p-transfer").hasAuthority("SCOPE_saga:p2p-transfer")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/merchant-payment").hasAuthority("SCOPE_saga:merchant-payment")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/bill-payment").hasAuthority("SCOPE_saga:bill-payment")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/international-transfer").hasAuthority("SCOPE_saga:international-transfer")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/crypto-exchange").hasAuthority("SCOPE_saga:crypto-exchange")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/loan-disbursement").hasAuthority("SCOPE_saga:loan-disbursement")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/investment-purchase").hasAuthority("SCOPE_saga:investment-purchase")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/transactions/group-payment").hasAuthority("SCOPE_saga:group-payment")
                
                // Compensation & Rollback
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/compensate/*").hasAuthority("SCOPE_saga:compensate")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/compensations").hasAuthority("SCOPE_saga:compensations-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/compensations/*").hasAuthority("SCOPE_saga:compensation-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/rollback/*").hasAuthority("SCOPE_saga:rollback")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/rollbacks").hasAuthority("SCOPE_saga:rollbacks-list")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/force-rollback/*").hasAuthority("SCOPE_saga:force-rollback")
                
                // Step Management
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/steps").hasAuthority("SCOPE_saga:steps-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/steps/*").hasAuthority("SCOPE_saga:step-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/steps/*/execute").hasAuthority("SCOPE_saga:step-execute")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/steps/*/compensate").hasAuthority("SCOPE_saga:step-compensate")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/steps/*/status").hasAuthority("SCOPE_saga:step-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/steps/*/retry").hasAuthority("SCOPE_saga:step-retry")
                
                // Saga Definition Management
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/definitions").hasAuthority("SCOPE_saga:definition-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/definitions").hasAuthority("SCOPE_saga:definitions-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/definitions/*").hasAuthority("SCOPE_saga:definition-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/saga/definitions/*").hasAuthority("SCOPE_saga:definition-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/saga/definitions/*").hasAuthority("SCOPE_saga:definition-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/definitions/*/activate").hasAuthority("SCOPE_saga:definition-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/definitions/*/deactivate").hasAuthority("SCOPE_saga:definition-deactivate")
                
                // Monitoring & Health
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/monitoring/active").hasAuthority("SCOPE_saga:monitoring-active")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/monitoring/failed").hasAuthority("SCOPE_saga:monitoring-failed")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/monitoring/stuck").hasAuthority("SCOPE_saga:monitoring-stuck")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/monitoring/metrics").hasAuthority("SCOPE_saga:monitoring-metrics")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/monitoring/performance").hasAuthority("SCOPE_saga:monitoring-performance")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/monitoring/alert").hasAuthority("SCOPE_saga:monitoring-alert")
                
                // Audit & History
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/audit/trail").hasAuthority("SCOPE_saga:audit-trail")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/audit/events").hasAuthority("SCOPE_saga:audit-events")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/history").hasAuthority("SCOPE_saga:history-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/history/*/timeline").hasAuthority("SCOPE_saga:history-timeline")
                
                // Recovery & Intervention
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/recovery/initiate").hasAuthority("SCOPE_saga:recovery-initiate")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/recovery/pending").hasAuthority("SCOPE_saga:recovery-pending")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/recovery/*/process").hasAuthority("SCOPE_saga:recovery-process")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/intervention/manual").hasAuthority("SCOPE_saga:intervention-manual")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/intervention/required").hasAuthority("SCOPE_saga:intervention-required")
                
                // Distributed Locking
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/locks/acquire").hasAuthority("SCOPE_saga:lock-acquire")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/locks/release").hasAuthority("SCOPE_saga:lock-release")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/locks").hasAuthority("SCOPE_saga:locks-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/locks/*/force-release").hasAuthority("SCOPE_saga:lock-force-release")
                
                // Admin Operations - Saga Management
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/admin/stuck-transactions").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/*/force-complete").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/*/force-compensate").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/*/manual-intervention").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/admin/critical-failures").hasRole("SAGA_ADMIN")
                
                // Admin Operations - Recovery
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/recovery/batch").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/recovery/all-failed").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/admin/recovery/report").hasRole("SAGA_ADMIN")
                
                // Admin Operations - Performance
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/admin/performance/slow-sagas").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/admin/performance/bottlenecks").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/performance/optimize").hasRole("SAGA_ADMIN")
                
                // Admin Operations - System
                .requestMatchers("/api/v1/saga/admin/**").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/saga/admin/system/health").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/system/maintenance").hasRole("SAGA_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/saga/admin/system/reset-locks").hasRole("SAGA_ADMIN")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/saga/**").hasRole("SERVICE")
                .requestMatchers("/internal/orchestration/**").hasRole("SERVICE")
                .requestMatchers("/internal/compensation/**").hasRole("SERVICE")
                .requestMatchers("/internal/transaction-coordination/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}