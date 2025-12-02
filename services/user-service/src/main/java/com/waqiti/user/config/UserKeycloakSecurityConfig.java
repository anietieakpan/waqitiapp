package com.waqiti.user.config;

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
 * Keycloak security configuration for User Service
 * Manages authentication and authorization for user management operations
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class UserKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain userKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for User Service");
        
        return createKeycloakSecurityFilterChain(http, "user-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Registration and authentication
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/users/public/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/verify-email").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/forgot-password").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/users/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/verify-email/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/reset-password/*").permitAll()
                
                // OAuth2 & Social Login endpoints
                .requestMatchers("/api/v1/users/oauth2/**").permitAll()
                .requestMatchers("/api/v1/users/social/**").permitAll()
                
                // User Profile Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/profile").hasAuthority("SCOPE_user:profile-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/profile").hasAuthority("SCOPE_user:profile-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/profile").hasAuthority("SCOPE_user:profile-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/profile/avatar").hasAuthority("SCOPE_user:avatar-upload")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/profile/avatar").hasAuthority("SCOPE_user:avatar-delete")
                
                // Account Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/account").hasAuthority("SCOPE_user:account-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/account/status").hasAuthority("SCOPE_user:account-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/account/deactivate").hasAuthority("SCOPE_user:account-deactivate")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/account/reactivate").hasAuthority("SCOPE_user:account-reactivate")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/account").hasAuthority("SCOPE_user:account-delete")
                
                // Password Management
                .requestMatchers(HttpMethod.POST, "/api/v1/users/password/change").hasAuthority("SCOPE_user:password-change")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/password/policy").hasAuthority("SCOPE_user:password-policy")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/password/strength").hasAuthority("SCOPE_user:password-strength")
                
                // Two-Factor Authentication (2FA)
                .requestMatchers(HttpMethod.POST, "/api/v1/users/2fa/enable").hasAuthority("SCOPE_user:2fa-enable")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/2fa/disable").hasAuthority("SCOPE_user:2fa-disable")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/2fa/verify").hasAuthority("SCOPE_user:2fa-verify")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/2fa/backup-codes").hasAuthority("SCOPE_user:2fa-backup-codes")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/2fa/backup-codes/regenerate").hasAuthority("SCOPE_user:2fa-regenerate")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/2fa/qr-code").hasAuthority("SCOPE_user:2fa-qr-code")
                
                // Device Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/devices").hasAuthority("SCOPE_user:devices-view")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/devices/trust").hasAuthority("SCOPE_user:device-trust")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/devices/*").hasAuthority("SCOPE_user:device-remove")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/devices/*/revoke").hasAuthority("SCOPE_user:device-revoke")
                
                // Sessions Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/sessions").hasAuthority("SCOPE_user:sessions-view")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/sessions/*").hasAuthority("SCOPE_user:session-terminate")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/sessions/terminate-all").hasAuthority("SCOPE_user:sessions-terminate-all")
                
                // Preferences & Settings
                .requestMatchers(HttpMethod.GET, "/api/v1/users/preferences").hasAuthority("SCOPE_user:preferences-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/preferences").hasAuthority("SCOPE_user:preferences-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/privacy-settings").hasAuthority("SCOPE_user:privacy-read")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/privacy-settings").hasAuthority("SCOPE_user:privacy-update")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/notification-settings").hasAuthority("SCOPE_user:notification-settings")
                .requestMatchers(HttpMethod.PUT, "/api/v1/users/notification-settings").hasAuthority("SCOPE_user:notification-update")
                
                // KYC (Know Your Customer)
                .requestMatchers(HttpMethod.GET, "/api/v1/users/kyc/status").hasAuthority("SCOPE_user:kyc-status")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/kyc/submit").hasAuthority("SCOPE_user:kyc-submit")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/kyc/documents").hasAuthority("SCOPE_user:kyc-documents")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/kyc/documents/upload").hasAuthority("SCOPE_user:kyc-upload")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/kyc/documents/*").hasAuthority("SCOPE_user:kyc-document-delete")
                
                // User Search & Directory (for authenticated users)
                .requestMatchers(HttpMethod.GET, "/api/v1/users/search").hasAuthority("SCOPE_user:search")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/*/public-profile").hasAuthority("SCOPE_user:public-profile")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/contacts/add").hasAuthority("SCOPE_user:contacts-add")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/contacts").hasAuthority("SCOPE_user:contacts-view")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/contacts/*").hasAuthority("SCOPE_user:contacts-remove")
                
                // Activity & Audit
                .requestMatchers(HttpMethod.GET, "/api/v1/users/activity").hasAuthority("SCOPE_user:activity-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/login-history").hasAuthority("SCOPE_user:login-history")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/security-events").hasAuthority("SCOPE_user:security-events")
                
                // Data Export & Import
                .requestMatchers(HttpMethod.POST, "/api/v1/users/export").hasAuthority("SCOPE_user:data-export")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/export/*").hasAuthority("SCOPE_user:export-download")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/gdpr/request").hasAuthority("SCOPE_user:gdpr-request")
                
                // Support & Help
                .requestMatchers(HttpMethod.POST, "/api/v1/users/support/ticket").hasAuthority("SCOPE_user:support-ticket")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/support/tickets").hasAuthority("SCOPE_user:support-view")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/help/**").hasAuthority("SCOPE_user:help-access")
                
                // Admin Operations - User Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/admin/users").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/admin/users/*").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/users/*/suspend").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/users/*/unsuspend").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/users/*/lock").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/users/*/unlock").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/users/admin/users/*").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/users/*/force-password-reset").hasRole("USER_ADMIN")
                
                // Admin Operations - KYC Management
                .requestMatchers(HttpMethod.GET, "/api/v1/users/admin/kyc/pending").hasRole("KYC_REVIEWER")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/kyc/*/approve").hasRole("KYC_REVIEWER")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/kyc/*/reject").hasRole("KYC_REVIEWER")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/admin/kyc/*/documents").hasRole("KYC_REVIEWER")
                
                // Admin Operations - System Management
                .requestMatchers("/api/v1/users/admin/**").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/admin/system/stats").hasRole("USER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/users/admin/system/cleanup").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/users/admin/audit/logs").hasRole("AUDITOR")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/users/**").hasRole("SERVICE")
                .requestMatchers("/internal/auth/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}