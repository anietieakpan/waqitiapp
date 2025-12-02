package com.waqiti.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS Configuration
 *
 * HIGH-01 FIX (2025-11-22): Centralized CORS configuration for all endpoints
 *
 * This configuration applies to all endpoints globally, eliminating the need
 * for @CrossOrigin annotations on individual controllers.
 *
 * BENEFITS:
 * 1. Single source of truth for CORS configuration
 * 2. Environment-specific configuration (dev/staging/prod)
 * 3. Consistent CORS behavior across all services
 * 4. Security validation at startup
 * 5. Easy to audit and modify
 *
 * @author Waqiti Platform Team
 * @since 2025-11-22
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class GlobalCorsConfiguration implements WebMvcConfigurer {

    private final CorsConfigurationProperties corsProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!corsProperties.isEnabled()) {
            log.info("CORS is disabled globally");
            return;
        }

        log.info("Configuring global CORS: origins={}, credentials={}",
                corsProperties.getAllowedOrigins(), corsProperties.isAllowCredentials());

        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowedOriginsArray())
                .allowedMethods(corsProperties.getAllowedMethodsArray())
                .allowedHeaders(corsProperties.getAllowedHeadersArray())
                .exposedHeaders(corsProperties.getExposedHeadersArray())
                .allowCredentials(corsProperties.isAllowCredentials())
                .maxAge(corsProperties.getMaxAge());
    }

    /**
     * CORS configuration source for Spring Security
     * This bean is automatically used by Spring Security for CORS support
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (!corsProperties.isEnabled()) {
            log.info("CORS configuration source disabled");
            return request -> {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(java.util.List.of());
                return config;
            };
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configuration source created for all paths");
        return source;
    }
}
