package com.waqiti.common.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for input validation components
 */
@Configuration
@RequiredArgsConstructor
public class ValidationConfiguration implements WebMvcConfigurer {

    private final InputValidationService inputValidationService;

    @Bean
    public ValidationInterceptor validationInterceptor() {
        return new ValidationInterceptor(inputValidationService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(validationInterceptor())
            .addPathPatterns("/api/**") // Apply to all API endpoints
            .excludePathPatterns(
                "/api/health/**",
                "/api/actuator/**",
                "/api/swagger-ui/**",
                "/api/v*/api-docs/**"
            );
    }
}