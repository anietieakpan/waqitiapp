package com.waqiti.common.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.*;

/**
 * API versioning configuration and annotations for Waqiti services
 */
@Configuration
@Slf4j
public class ApiVersioning implements WebMvcConfigurer {
    
    /**
     * Configure path matching for API versioning
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v{version}", 
            c -> c.isAnnotationPresent(RestController.class) && 
                 c.isAnnotationPresent(ApiVersion.class));
    }
    
    /**
     * API version resolver bean
     */
    @Bean
    public ApiVersionResolver apiVersionResolver() {
        return new DefaultApiVersionResolver();
    }
    
    /**
     * Annotation to specify API version for controllers
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface ApiVersion {
        /**
         * API version number (e.g., "1", "2", "1.1")
         */
        String value();
        
        /**
         * Whether this version is deprecated
         */
        boolean deprecated() default false;
        
        /**
         * Deprecation message for clients
         */
        String deprecationMessage() default "";
        
        /**
         * Sunset date for this API version (ISO 8601 format)
         */
        String sunsetDate() default "";
    }
    
    /**
     * Annotation for API endpoint documentation
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface ApiEndpoint {
        /**
         * Endpoint summary description
         */
        String summary();
        
        /**
         * Detailed description
         */
        String description() default "";
        
        /**
         * Tags for grouping endpoints
         */
        String[] tags() default {};
        
        /**
         * Whether authentication is required
         */
        boolean requiresAuth() default true;
        
        /**
         * Required permissions
         */
        String[] permissions() default {};
        
        /**
         * Rate limit tier
         */
        RateLimitTier rateLimitTier() default RateLimitTier.STANDARD;
    }
    
    /**
     * Rate limit tiers for API endpoints
     */
    public enum RateLimitTier {
        UNRESTRICTED(0),
        HIGH(1000),
        STANDARD(100),
        LOW(10),
        RESTRICTED(1);
        
        private final int requestsPerMinute;
        
        RateLimitTier(int requestsPerMinute) {
            this.requestsPerMinute = requestsPerMinute;
        }
        
        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }
    }
    
    /**
     * Interface for API version resolution
     */
    public interface ApiVersionResolver {
        /**
         * Resolve API version from request
         */
        String resolveVersion(HttpServletRequest request);
        
        /**
         * Get default version if none specified
         */
        String getDefaultVersion();
        
        /**
         * Check if version is supported
         */
        boolean isVersionSupported(String version);
        
        /**
         * Check if version is deprecated
         */
        boolean isVersionDeprecated(String version);
    }
    
    /**
     * Default implementation of API version resolver
     */
    @Slf4j
    public static class DefaultApiVersionResolver implements ApiVersionResolver {

        private static final String DEFAULT_VERSION = "1";
        private static final String VERSION_HEADER = "API-Version";
        private static final String ACCEPT_HEADER = "Accept";
        
        @Override
        public String resolveVersion(HttpServletRequest request) {
            // 1. Check custom header
            String version = request.getHeader(VERSION_HEADER);
            if (version != null && !version.isEmpty()) {
                log.debug("API version resolved from header: {}", version);
                return version;
            }
            
            // 2. Check Accept header (application/vnd.waqiti.v1+json)
            String acceptHeader = request.getHeader(ACCEPT_HEADER);
            if (acceptHeader != null && acceptHeader.contains("vnd.waqiti.v")) {
                version = extractVersionFromAcceptHeader(acceptHeader);
                if (version != null) {
                    log.debug("API version resolved from Accept header: {}", version);
                    return version;
                }
            }
            
            // 3. Check URL path parameter
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.contains("/v")) {
                version = extractVersionFromPath(pathInfo);
                if (version != null) {
                    log.debug("API version resolved from path: {}", version);
                    return version;
                }
            }
            
            // 4. Default version
            log.debug("Using default API version: {}", DEFAULT_VERSION);
            return DEFAULT_VERSION;
        }
        
        @Override
        public String getDefaultVersion() {
            return DEFAULT_VERSION;
        }
        
        @Override
        public boolean isVersionSupported(String version) {
            // Support versions 1, 2, and 1.1
            return "1".equals(version) || "2".equals(version) || "1.1".equals(version);
        }
        
        @Override
        public boolean isVersionDeprecated(String version) {
            // Version 1 is deprecated in favor of 1.1
            return "1".equals(version);
        }
        
        private String extractVersionFromAcceptHeader(String acceptHeader) {
            // Extract version from application/vnd.waqiti.v1+json
            String[] parts = acceptHeader.split("vnd\\.waqiti\\.v");
            if (parts.length > 1) {
                String versionPart = parts[1].split("\\+")[0];
                if (isValidVersionFormat(versionPart)) {
                    return versionPart;
                }
            }
            return null;
        }
        
        private String extractVersionFromPath(String pathInfo) {
            // Extract version from /api/v1/users
            String[] segments = pathInfo.split("/");
            for (String segment : segments) {
                if (segment.startsWith("v") && segment.length() > 1) {
                    String version = segment.substring(1);
                    if (isValidVersionFormat(version)) {
                        return version;
                    }
                }
            }
            return null;
        }
        
        private boolean isValidVersionFormat(String version) {
            // Valid formats: 1, 2, 1.1, 2.0, etc.
            return version.matches("^\\d+(\\.\\d+)?$");
        }
    }
}