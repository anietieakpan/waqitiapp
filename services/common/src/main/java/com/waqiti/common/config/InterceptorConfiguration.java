package com.waqiti.common.config;

import com.waqiti.common.interceptor.RequestValidationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class InterceptorConfiguration implements WebMvcConfigurer {
    
    private final RequestValidationInterceptor requestValidationInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestValidationInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/health",
                "/api/metrics",
                "/api/actuator/**",
                "/api/swagger-ui/**",
                "/api/v3/api-docs/**"
            )
            .order(1);
    }
}