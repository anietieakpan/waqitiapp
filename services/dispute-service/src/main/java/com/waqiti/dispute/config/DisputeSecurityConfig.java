package com.waqiti.dispute.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dispute Service Security Configuration
 *
 * HIGH SECURITY RISK: This service handles financial disputes, chargebacks,
 * and resolution processes involving customer funds and merchant relationships.
 *
 * Security Features:
 * - JWT authentication with role-based access control
 * - Fine-grained permissions for dispute operations
 * - Comprehensive audit logging for compliance
 * - Document security for dispute evidence
 * - Customer data protection
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class DisputeSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    public SecurityFilterChain disputeSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring dispute service security with Keycloak");

        return createKeycloakSecurityFilterChain(http, "dispute-service", httpSecurity -> {
            try {
                httpSecurity
                    .authorizeHttpRequests(authz -> authz
                        // Health checks - allow for monitoring
                        .requestMatchers("/actuator/health/**", "/health/**").permitAll()

                        // Dispute creation - users can create disputes for their transactions
                        .requestMatchers("/api/v1/disputes/create").hasAuthority("SCOPE_dispute:create")

                        // Dispute management - admins and dispute managers
                        .requestMatchers("/api/v1/disputes/*/assign").hasRole("DISPUTE_MANAGER")
                        .requestMatchers("/api/v1/disputes/*/resolve").hasRole("DISPUTE_MANAGER")
                        .requestMatchers("/api/v1/disputes/*/escalate").hasRole("DISPUTE_MANAGER")

                        // Evidence management - participants can submit evidence
                        .requestMatchers("/api/v1/disputes/*/evidence").hasAuthority("SCOPE_dispute:evidence")
                        .requestMatchers("/api/v1/disputes/*/documents/**").hasAuthority("SCOPE_dispute:documents")

                        // Chargeback processing - financial operations team
                        .requestMatchers("/api/v1/disputes/*/chargeback").hasRole("FINANCIAL_OPS")
                        .requestMatchers("/api/v1/chargebacks/**").hasRole("FINANCIAL_OPS")

                        // Dispute analytics and reporting - managers and admins
                        .requestMatchers("/api/v1/disputes/analytics/**").hasRole("DISPUTE_MANAGER")
                        .requestMatchers("/api/v1/disputes/reports/**").hasRole("DISPUTE_ADMIN")

                        // User dispute views - users can view their own disputes
                        .requestMatchers("/api/v1/disputes/my-disputes").hasAuthority("SCOPE_dispute:read")
                        .requestMatchers("/api/v1/disputes/{disputeId}").hasAuthority("SCOPE_dispute:read")

                        // Internal service endpoints
                        .requestMatchers("/internal/**").hasAuthority("SCOPE_service:internal")

                        // Webhook endpoints for payment provider dispute notifications
                        .requestMatchers("/api/webhooks/**").permitAll() // Secured by signature verification

                        // Administrative endpoints - admins only
                        .requestMatchers("/api/v1/admin/**").hasRole("DISPUTE_ADMIN")

                        // All other API endpoints require authentication
                        .requestMatchers("/api/**").authenticated()

                        // Deny everything else
                        .anyRequest().denyAll()
                    )

                    // Add dispute-specific security headers
                    .headers(headers -> headers
                        .addHeaderWriter((request, response) -> {
                            // Prevent caching of sensitive dispute data
                            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                            response.setHeader("Pragma", "no-cache");
                            response.setHeader("Expires", "0");

                            // Add dispute service identifier
                            response.setHeader("X-Service-Type", "dispute-resolution");
                            response.setHeader("X-Security-Level", "high");

                            // Compliance headers for dispute data
                            response.setHeader("X-Data-Classification", "sensitive");
                            response.setHeader("X-Audit-Required", "true");
                        })
                    );

                log.info("Dispute Service security configuration applied - HIGH SECURITY ENABLED");

            } catch (Exception e) {
                log.error("Failed to configure dispute service security", e);
                throw new RuntimeException("Security configuration failed", e);
            }
        });
    }
}
