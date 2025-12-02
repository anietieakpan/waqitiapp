package com.waqiti.dispute.config;

import com.waqiti.dispute.security.JwtUserIdValidationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration - PRODUCTION READY
 *
 * Registers interceptors for security validation
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtUserIdValidationInterceptor jwtUserIdValidationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtUserIdValidationInterceptor)
                .addPathPatterns("/api/disputes/**")
                .excludePathPatterns("/actuator/**", "/health/**", "/swagger-ui/**", "/v3/api-docs/**");
    }
}
