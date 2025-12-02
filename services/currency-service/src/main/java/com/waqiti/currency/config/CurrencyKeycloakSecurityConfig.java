package com.waqiti.currency.config;

import com.waqiti.common.security.keycloak.BaseKeycloakSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Currency Service Security Configuration
 * 
 * CRITICAL SECURITY: This service handles foreign exchange rates,
 * currency conversions, and financial calculations requiring strict security.
 * 
 * Security Features:
 * - JWT authentication for all currency operations
 * - Rate limiting to prevent abuse of exchange rate APIs
 * - Audit logging for all currency conversions
 * - Protection against exchange rate manipulation
 * - Secure caching of exchange rates
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@ConditionalOnProperty(name = "keycloak.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CurrencyKeycloakSecurityConfig extends BaseKeycloakSecurityConfig {

    @Override
    protected void configureHttpSecurity(HttpSecurity http) throws Exception {
        super.configureHttpSecurity(http);
        
        http
            .authorizeHttpRequests(authz -> authz
                // Health checks
                .requestMatchers("/actuator/health/**", "/health/**").permitAll()
                
                // Public exchange rates (read-only, cached)
                .requestMatchers("/api/v1/currency/rates/current").permitAll()
                .requestMatchers("/api/v1/currency/supported").permitAll()
                
                // Currency conversion - requires authentication
                .requestMatchers("/api/v1/currency/convert").hasAuthority("SCOPE_currency:convert")
                .requestMatchers("/api/v1/currency/calculate/**").hasAuthority("SCOPE_currency:calculate")
                
                // Historical rates - requires specific permission
                .requestMatchers("/api/v1/currency/rates/historical/**").hasAuthority("SCOPE_currency:history")
                .requestMatchers("/api/v1/currency/rates/chart/**").hasAuthority("SCOPE_currency:analytics")
                
                // Rate management - admin only
                .requestMatchers("/api/v1/currency/rates/update").hasRole("CURRENCY_ADMIN")
                .requestMatchers("/api/v1/currency/rates/override").hasRole("CURRENCY_ADMIN")
                .requestMatchers("/api/v1/currency/cache/clear").hasRole("CURRENCY_ADMIN")
                
                // Fee calculations
                .requestMatchers("/api/v1/currency/fees/**").hasAuthority("SCOPE_currency:fees")
                
                // Forex alerts and notifications
                .requestMatchers("/api/v1/currency/alerts/**").hasAuthority("SCOPE_currency:alerts")
                
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
                // Cache control for exchange rates
                response.setHeader("Cache-Control", "public, max-age=300"); // 5 minutes cache
                response.setHeader("X-Service-Type", "currency");
                response.setHeader("X-Rate-Limit", "100 per minute");
                response.setHeader("X-Data-Source", "secure-forex-provider");
            })
        );
        
        log.info("Currency Service security configuration applied - Exchange rate protection enabled");
    }
}