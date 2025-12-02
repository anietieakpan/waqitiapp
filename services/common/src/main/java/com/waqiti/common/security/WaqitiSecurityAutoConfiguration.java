package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for Waqiti Security Features
 * Automatically applies comprehensive security headers and configurations
 * across all Waqiti microservices
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "waqiti.security.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WaqitiSecurityProperties.class)
@Import(ComprehensiveSecurityHeadersConfig.class)
@Slf4j
public class WaqitiSecurityAutoConfiguration implements WebMvcConfigurer {

    private final WaqitiSecurityProperties securityProperties;
    private final ComprehensiveSecurityHeadersConfig securityHeadersConfig;

    public WaqitiSecurityAutoConfiguration(WaqitiSecurityProperties securityProperties,
                                         ComprehensiveSecurityHeadersConfig securityHeadersConfig) {
        this.securityProperties = securityProperties;
        this.securityHeadersConfig = securityHeadersConfig;
        log.info("Initializing Waqiti Security Auto-Configuration with comprehensive security headers");
    }

    /**
     * Configure security filter chain with comprehensive security headers
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.headers.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring comprehensive security filter chain");

        http
            // Disable default configurations we'll configure manually
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure session management
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authorization - allow all for now, services will override
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            
            // Apply comprehensive security headers
            .with(new SecurityHeadersConfigurer(securityHeadersConfig), customizer -> {})
            
            // Configure CORS
            .cors(cors -> cors.configurationSource(securityHeadersConfig.corsConfigurationSource()));

        return http.build();
    }

    /**
     * Custom configurer for security headers
     */
    public static class SecurityHeadersConfigurer extends AbstractHttpConfigurer<SecurityHeadersConfigurer, HttpSecurity> {
        
        private final ComprehensiveSecurityHeadersConfig securityHeadersConfig;

        public SecurityHeadersConfigurer(ComprehensiveSecurityHeadersConfig securityHeadersConfig) {
            this.securityHeadersConfig = securityHeadersConfig;
        }

        @Override
        public void configure(HttpSecurity http) throws Exception {
            // Apply comprehensive security headers configuration
            securityHeadersConfig.configureSecurityHeaders(http);
            
            log.info("Applied comprehensive security headers configuration");
        }
    }

    /**
     * Security event listener for monitoring security events
     */
    @Bean
    public SecurityEventListener securityEventListener() {
        return new SecurityEventListener();
    }

    /**
     * Security metrics for monitoring security-related metrics
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.security.metrics.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityMetricsCollector securityMetricsCollector() {
        return new SecurityMetricsCollector();
    }
}