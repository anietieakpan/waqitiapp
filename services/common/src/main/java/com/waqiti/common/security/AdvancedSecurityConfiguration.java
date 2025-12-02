package com.waqiti.common.security;

import com.waqiti.common.security.filters.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import org.redisson.api.RedissonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;

/**
 * Advanced security configuration with rate limiting and API key management
 */
@Configuration
@EnableWebSecurity
@Slf4j
public class AdvancedSecurityConfiguration {
    
    @Value("${security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Value("${security.api-keys.enabled:true}")
    private boolean apiKeysEnabled;
    
    /**
     * Security filter chain with advanced features
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitingFilter rateLimitingFilter,
            ApiKeyAuthenticationFilter apiKeyFilter,
            SecurityHeadersFilter securityHeadersFilter) throws Exception {
        
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/v*/auth/login", "/api/v*/auth/register").permitAll()
                .requestMatchers("/api/v*/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            );
        
        // Add custom security filters
        if (rateLimitEnabled) {
            http.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        }
        
        if (apiKeysEnabled) {
            http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        }
        
        http.addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    /**
     * CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        configuration.setAllowedOriginPatterns(java.util.List.of(
            "https://*.waqiti.com",
            "https://localhost:*",
            "http://localhost:*"
        ));
        
        configuration.setAllowedMethods(java.util.List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        configuration.setAllowedHeaders(java.util.List.of(
            "Authorization", "Content-Type", "X-Requested-With", 
            "API-Key", "API-Version", "X-Correlation-ID"
        ));
        
        configuration.setExposedHeaders(java.util.List.of(
            "X-Correlation-ID", "X-Rate-Limit-Remaining", "X-Rate-Limit-Reset"
        ));
        
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
    
    /**
     * Bucket4j configuration for Redis
     * PRODUCTION FIX: Updated to match current Bucket4j/Redisson API
     */
    @Bean
    public ProxyManager<String> bucketProxyManager(RedissonClient redissonClient) {
        return io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager.builderFor((org.redisson.command.CommandAsyncExecutor) redissonClient)
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                    Duration.ofMinutes(5)))
            .build();
    }
}