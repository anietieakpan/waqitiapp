package com.waqiti.gdpr.config;

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
 * Keycloak security configuration for GDPR Service
 * CRITICAL SECURITY IMPLEMENTATION - Manages data privacy compliance and user rights
 * Compliance: GDPR, CCPA, PIPEDA, LGPD
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class GdprKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain gdprKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring CRITICAL Keycloak security for GDPR Service");
        
        return createKeycloakSecurityFilterChain(http, "gdpr-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited for security
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/gdpr/public/privacy-policy").permitAll()
                .requestMatchers("/api/v1/gdpr/public/cookie-policy").permitAll()
                .requestMatchers("/api/v1/gdpr/public/data-protection-notice").permitAll()
                
                // Data Subject Rights - Core GDPR Implementation
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/requests/access").hasAuthority("SCOPE_gdpr:data-access-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/requests/portability").hasAuthority("SCOPE_gdpr:data-portability-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/requests/deletion").hasAuthority("SCOPE_gdpr:data-deletion-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/requests/rectification").hasAuthority("SCOPE_gdpr:data-rectification-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/requests/restriction").hasAuthority("SCOPE_gdpr:data-restriction-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/requests/objection").hasAuthority("SCOPE_gdpr:data-objection-request")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/requests").hasAuthority("SCOPE_gdpr:requests-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/requests/*").hasAuthority("SCOPE_gdpr:request-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gdpr/requests/*/cancel").hasAuthority("SCOPE_gdpr:request-cancel")
                
                // Data Export & Download
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/exports").hasAuthority("SCOPE_gdpr:exports-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/exports/*/download").hasAuthority("SCOPE_gdpr:export-download")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/exports/*/status").hasAuthority("SCOPE_gdpr:export-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/exports/*/regenerate").hasAuthority("SCOPE_gdpr:export-regenerate")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/gdpr/exports/*").hasAuthority("SCOPE_gdpr:export-delete")
                
                // Consent Management - Critical for Compliance
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/consent").hasAuthority("SCOPE_gdpr:consent-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/consent/grant").hasAuthority("SCOPE_gdpr:consent-grant")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/consent/withdraw").hasAuthority("SCOPE_gdpr:consent-withdraw")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gdpr/consent/update").hasAuthority("SCOPE_gdpr:consent-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/consent/history").hasAuthority("SCOPE_gdpr:consent-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/consent/categories").hasAuthority("SCOPE_gdpr:consent-categories")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/consent/bulk-update").hasAuthority("SCOPE_gdpr:consent-bulk-update")
                
                // Privacy Preferences & Settings
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/privacy/preferences").hasAuthority("SCOPE_gdpr:privacy-preferences")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gdpr/privacy/preferences").hasAuthority("SCOPE_gdpr:privacy-preferences-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/privacy/settings").hasAuthority("SCOPE_gdpr:privacy-settings")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/privacy/opt-out").hasAuthority("SCOPE_gdpr:privacy-opt-out")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/privacy/opt-in").hasAuthority("SCOPE_gdpr:privacy-opt-in")
                
                // Data Processing & Lawful Basis
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/processing/lawful-basis").hasAuthority("SCOPE_gdpr:lawful-basis-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/processing/purposes").hasAuthority("SCOPE_gdpr:processing-purposes")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/processing/activities").hasAuthority("SCOPE_gdpr:processing-activities")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/processing/impact-assessment").hasAuthority("SCOPE_gdpr:impact-assessment")
                
                // Data Breach Notification - Legal Requirement
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/breach/report").hasAuthority("SCOPE_gdpr:breach-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/breach/notifications").hasAuthority("SCOPE_gdpr:breach-notifications")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/breach/*/status").hasAuthority("SCOPE_gdpr:breach-status")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gdpr/breach/*/update").hasAuthority("SCOPE_gdpr:breach-update")
                
                // Data Retention & Lifecycle Management
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/retention/policies").hasAuthority("SCOPE_gdpr:retention-policies")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/retention/schedules").hasAuthority("SCOPE_gdpr:retention-schedules")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/retention/review").hasAuthority("SCOPE_gdpr:retention-review")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/lifecycle/data-age").hasAuthority("SCOPE_gdpr:data-age-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/lifecycle/cleanup").hasAuthority("SCOPE_gdpr:data-cleanup")
                
                // Third-Party Data Sharing & Transfers
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/transfers/international").hasAuthority("SCOPE_gdpr:international-transfers")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/transfers/adequacy-decisions").hasAuthority("SCOPE_gdpr:adequacy-decisions")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/sharing/third-parties").hasAuthority("SCOPE_gdpr:third-party-sharing")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/sharing/consent-record").hasAuthority("SCOPE_gdpr:sharing-consent")
                
                // Children's Data Protection (Article 8)
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/children/verification").hasAuthority("SCOPE_gdpr:children-verification")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/children/parental-consent").hasAuthority("SCOPE_gdpr:parental-consent")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/children/age-verification").hasAuthority("SCOPE_gdpr:age-verification")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gdpr/children/*/update-consent").hasAuthority("SCOPE_gdpr:children-consent-update")
                
                // Automated Decision Making & Profiling (Articles 13-15, 22)
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/automated-decisions").hasAuthority("SCOPE_gdpr:automated-decisions")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/automated-decisions/opt-out").hasAuthority("SCOPE_gdpr:automated-decisions-opt-out")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/profiling/activities").hasAuthority("SCOPE_gdpr:profiling-activities")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/profiling/object").hasAuthority("SCOPE_gdpr:profiling-objection")
                
                // Compliance Monitoring & Reporting
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/compliance/status").hasAuthority("SCOPE_gdpr:compliance-status")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/compliance/metrics").hasAuthority("SCOPE_gdpr:compliance-metrics")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/compliance/violations").hasAuthority("SCOPE_gdpr:compliance-violations")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/compliance/audit-trail").hasAuthority("SCOPE_gdpr:audit-trail")
                
                // Documentation & Transparency
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/transparency/report").hasAuthority("SCOPE_gdpr:transparency-report")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/documentation/privacy-notices").hasAuthority("SCOPE_gdpr:privacy-notices")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/documentation/ropa").hasAuthority("SCOPE_gdpr:ropa-view") // Record of Processing Activities
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/documentation/impact-assessment").hasAuthority("SCOPE_gdpr:dpia-create")
                
                // Data Protection Officer (DPO) Communication
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/dpo/contact").hasAuthority("SCOPE_gdpr:dpo-contact")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/dpo/consultations").hasAuthority("SCOPE_gdpr:dpo-consultations")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/dpo/escalation").hasAuthority("SCOPE_gdpr:dpo-escalation")
                
                // Legal Basis & Legitimate Interest Assessment
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/legal-basis/assessment").hasAuthority("SCOPE_gdpr:legal-basis-assessment")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/legitimate-interest/test").hasAuthority("SCOPE_gdpr:legitimate-interest-test")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/legitimate-interest/balancing").hasAuthority("SCOPE_gdpr:legitimate-interest-balancing")
                
                // Admin Operations - Data Protection Officer & Compliance Team
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/requests/pending").hasRole("DPO")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/requests/*/approve").hasRole("DPO")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/requests/*/reject").hasRole("DPO")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/requests/*/extend-deadline").hasRole("DPO")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/breach/investigations").hasRole("DPO")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/breach/*/notify-authority").hasRole("DPO")
                
                // Admin Operations - Compliance Monitoring
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/compliance/dashboard").hasRole("GDPR_COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/compliance/audit").hasRole("GDPR_COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/compliance/violations/review").hasRole("GDPR_COMPLIANCE_OFFICER")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/retention/enforce").hasRole("GDPR_COMPLIANCE_OFFICER")
                
                // Admin Operations - Data Processing Activities
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/processing/register").hasRole("GDPR_DATA_CONTROLLER")
                .requestMatchers(HttpMethod.PUT, "/api/v1/gdpr/admin/processing/*/update").hasRole("GDPR_DATA_CONTROLLER")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/processors/register").hasRole("GDPR_DATA_CONTROLLER")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/impact-assessments").hasRole("GDPR_DATA_CONTROLLER")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/gdpr/admin/**").hasRole("GDPR_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/system/health").hasRole("GDPR_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/system/cleanup").hasRole("GDPR_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/admin/audit/logs").hasRole("AUDITOR")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/admin/bulk-operations").hasRole("GDPR_ADMIN")
                
                // Supervisory Authority Communication - Critical for Legal Compliance
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/authority/notifications").hasRole("DPO")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/authority/communications").hasRole("DPO")
                .requestMatchers(HttpMethod.POST, "/api/v1/gdpr/authority/breach-notification").hasRole("DPO")
                .requestMatchers(HttpMethod.GET, "/api/v1/gdpr/authority/compliance-reports").hasRole("GDPR_COMPLIANCE_OFFICER")
                
                // High-Security Internal service-to-service endpoints
                .requestMatchers("/internal/gdpr/**").hasRole("SERVICE")
                .requestMatchers("/internal/privacy/**").hasRole("SERVICE")
                .requestMatchers("/internal/consent/**").hasRole("SERVICE")
                .requestMatchers("/internal/breach/**").hasRole("SERVICE")
                .requestMatchers("/internal/compliance/**").hasRole("SERVICE")
                
                // All other endpoints require authentication - NO EXCEPTIONS
                .anyRequest().authenticated()
            );
        });
    }
}