package com.waqiti.voice.config;

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
 * Keycloak security configuration for Voice Payment Service
 * Manages voice-activated payment processing with advanced biometric authentication
 * Critical for hands-free payment operations and accessibility features
 */
@Slf4j
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Order(1)
public class VoicePaymentKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Bean
    @Primary
    public SecurityFilterChain voicePaymentKeycloakSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Keycloak security for Voice Payment Service");
        
        return createKeycloakSecurityFilterChain(http, "voice-payment-service", httpSecurity -> {
            httpSecurity.authorizeHttpRequests(authz -> authz
                // Public endpoints - Very limited for security
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/voice/public/capabilities").permitAll()
                .requestMatchers("/api/v1/voice/public/languages").permitAll()
                
                // Voice Profile Management
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/profiles/create").hasAuthority("SCOPE_voice:profile-create")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/profiles").hasAuthority("SCOPE_voice:profiles-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/profiles/*").hasAuthority("SCOPE_voice:profile-details")
                .requestMatchers(HttpMethod.PUT, "/api/v1/voice/profiles/*").hasAuthority("SCOPE_voice:profile-update")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/voice/profiles/*").hasAuthority("SCOPE_voice:profile-delete")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/profiles/*/train").hasAuthority("SCOPE_voice:profile-train")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/profiles/*/verify").hasAuthority("SCOPE_voice:profile-verify")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/profiles/*/activate").hasAuthority("SCOPE_voice:profile-activate")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/profiles/*/deactivate").hasAuthority("SCOPE_voice:profile-deactivate")
                
                // Voice Authentication & Session Management
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/auth/challenge").hasAuthority("SCOPE_voice:auth-challenge")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/auth/verify").hasAuthority("SCOPE_voice:auth-verify")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/auth/multi-factor").hasAuthority("SCOPE_voice:auth-mfa")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/sessions/start").hasAuthority("SCOPE_voice:session-start")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/sessions/*").hasAuthority("SCOPE_voice:session-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/sessions/*/extend").hasAuthority("SCOPE_voice:session-extend")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/sessions/*/end").hasAuthority("SCOPE_voice:session-end")
                
                // Voice Command Processing
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/commands/process").hasAuthority("SCOPE_voice:command-process")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/commands/validate").hasAuthority("SCOPE_voice:command-validate")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/commands/history").hasAuthority("SCOPE_voice:command-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/commands/cancel").hasAuthority("SCOPE_voice:command-cancel")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/commands/*/status").hasAuthority("SCOPE_voice:command-status")
                
                // Voice Payment Operations
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/payments/send").hasAuthority("SCOPE_voice:payment-send")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/payments/request").hasAuthority("SCOPE_voice:payment-request")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/payments/split").hasAuthority("SCOPE_voice:payment-split")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/payments/balance").hasAuthority("SCOPE_voice:payment-balance")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/payments/history").hasAuthority("SCOPE_voice:payment-history")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/payments/schedule").hasAuthority("SCOPE_voice:payment-schedule")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/payments/*/confirm").hasAuthority("SCOPE_voice:payment-confirm")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/payments/*/cancel").hasAuthority("SCOPE_voice:payment-cancel")
                
                // Voice Transaction Management
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/transactions").hasAuthority("SCOPE_voice:transactions-list")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/transactions/*").hasAuthority("SCOPE_voice:transaction-details")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/transactions/*/dispute").hasAuthority("SCOPE_voice:transaction-dispute")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/transactions/*/refund").hasAuthority("SCOPE_voice:transaction-refund")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/transactions/*/receipt").hasAuthority("SCOPE_voice:transaction-receipt")
                
                // Voice Bill Pay & Merchant Payments
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/bills/pay").hasAuthority("SCOPE_voice:bill-pay")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/bills/upcoming").hasAuthority("SCOPE_voice:bills-upcoming")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/bills/schedule").hasAuthority("SCOPE_voice:bill-schedule")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/merchants/pay").hasAuthority("SCOPE_voice:merchant-pay")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/merchants/favorites").hasAuthority("SCOPE_voice:merchants-favorites")
                
                // Voice Banking Operations
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/accounts/balance").hasAuthority("SCOPE_voice:account-balance")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/accounts/transactions").hasAuthority("SCOPE_voice:account-transactions")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/accounts/transfer").hasAuthority("SCOPE_voice:account-transfer")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/cards/balance").hasAuthority("SCOPE_voice:card-balance")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/cards/freeze").hasAuthority("SCOPE_voice:card-freeze")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/cards/unfreeze").hasAuthority("SCOPE_voice:card-unfreeze")
                
                // Voice Settings & Preferences
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/settings").hasAuthority("SCOPE_voice:settings-view")
                .requestMatchers(HttpMethod.PUT, "/api/v1/voice/settings").hasAuthority("SCOPE_voice:settings-update")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/settings/language").hasAuthority("SCOPE_voice:settings-language")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/settings/notifications").hasAuthority("SCOPE_voice:settings-notifications")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/settings/security").hasAuthority("SCOPE_voice:settings-security")
                .requestMatchers(HttpMethod.PUT, "/api/v1/voice/settings/security").hasAuthority("SCOPE_voice:settings-security-update")
                
                // Voice Analytics & Insights
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/analytics/usage").hasAuthority("SCOPE_voice:analytics-usage")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/analytics/patterns").hasAuthority("SCOPE_voice:analytics-patterns")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/analytics/accuracy").hasAuthority("SCOPE_voice:analytics-accuracy")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/insights/spending").hasAuthority("SCOPE_voice:insights-spending")
                
                // Voice Emergency & Security
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/emergency/disable").hasAuthority("SCOPE_voice:emergency-disable")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/emergency/alert").hasAuthority("SCOPE_voice:emergency-alert")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/security/lockdown").hasAuthority("SCOPE_voice:security-lockdown")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/security/attempts").hasAuthority("SCOPE_voice:security-attempts")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/security/reset").hasAuthority("SCOPE_voice:security-reset")
                
                // Voice Accessibility Features
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/accessibility/options").hasAuthority("SCOPE_voice:accessibility-options")
                .requestMatchers(HttpMethod.PUT, "/api/v1/voice/accessibility/preferences").hasAuthority("SCOPE_voice:accessibility-preferences")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/accessibility/calibrate").hasAuthority("SCOPE_voice:accessibility-calibrate")
                
                // Admin Operations - Voice System Management
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/admin/profiles/all").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/admin/profiles/*/reset").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/admin/sessions/active").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/admin/sessions/*/terminate").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/admin/analytics/system").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/admin/security/suspicious").hasRole("SECURITY_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/admin/models/retrain").hasRole("AI_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/admin/fraud/patterns").hasRole("FRAUD_ANALYST")
                
                // Admin Operations - System Maintenance
                .requestMatchers("/api/v1/voice/admin/**").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/admin/system/maintenance").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/voice/admin/system/health").hasRole("VOICE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/voice/admin/system/calibrate").hasRole("VOICE_ADMIN")
                
                // Internal service-to-service endpoints
                .requestMatchers("/internal/voice/**").hasRole("SERVICE")
                .requestMatchers("/internal/voice-recognition/**").hasRole("SERVICE")
                .requestMatchers("/internal/biometric-auth/**").hasRole("SERVICE")
                
                // All other endpoints require authentication
                .anyRequest().authenticated()
            );
        });
    }
}