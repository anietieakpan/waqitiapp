package com.waqiti.common.tracing;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Configuration for distributed tracing functionality.
 * Integrates with Spring Boot's tracing infrastructure and configures
 * interceptors for automatic correlation ID management.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Configuration
@ConditionalOnProperty(name = "waqiti.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingConfiguration implements WebMvcConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(TracingConfiguration.class);
    
    private final TracingInterceptor tracingInterceptor;
    
    public TracingConfiguration(TracingInterceptor tracingInterceptor) {
        this.tracingInterceptor = tracingInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tracingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/actuator/**",
                    "/health",
                    "/metrics",
                    "/info",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/favicon.ico",
                    "/error"
                );
        
        logger.info("Tracing interceptor registered for all paths except actuator endpoints");
    }
    
    @Bean
    @ConfigurationProperties(prefix = "waqiti.tracing")
    public TracingProperties tracingProperties() {
        return new TracingProperties();
    }
    
    /**
     * Properties for tracing configuration.
     */
    public static class TracingProperties {
        
        /**
         * Whether tracing is enabled
         */
        private boolean enabled = true;
        
        /**
         * Sampling rate for traces (0.0 to 1.0)
         */
        @DecimalMin(value = "0.0", message = "Sample rate must be between 0.0 and 1.0")
        @DecimalMax(value = "1.0", message = "Sample rate must be between 0.0 and 1.0")
        private double sampleRate = 0.1;
        
        /**
         * Service name for tracing
         */
        @NotBlank(message = "Service name cannot be blank")
        private String serviceName;
        
        /**
         * Whether to include request/response bodies in traces
         */
        private boolean includeRequestBodies = false;
        
        /**
         * Whether to include response bodies in traces
         */
        private boolean includeResponseBodies = false;
        
        /**
         * Maximum body size to include in traces (in bytes)
         */
        private int maxBodySize = 1024;
        
        /**
         * Jaeger configuration
         */
        private JaegerProperties jaeger = new JaegerProperties();
        
        /**
         * AWS X-Ray configuration
         */
        private XRayProperties xray = new XRayProperties();
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public double getSampleRate() { return sampleRate; }
        public void setSampleRate(double sampleRate) { this.sampleRate = sampleRate; }
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public boolean isIncludeRequestBodies() { return includeRequestBodies; }
        public void setIncludeRequestBodies(boolean includeRequestBodies) { this.includeRequestBodies = includeRequestBodies; }
        
        public boolean isIncludeResponseBodies() { return includeResponseBodies; }
        public void setIncludeResponseBodies(boolean includeResponseBodies) { this.includeResponseBodies = includeResponseBodies; }
        
        public int getMaxBodySize() { return maxBodySize; }
        public void setMaxBodySize(int maxBodySize) { this.maxBodySize = maxBodySize; }
        
        public JaegerProperties getJaeger() { return jaeger; }
        public void setJaeger(JaegerProperties jaeger) { this.jaeger = jaeger; }
        
        public XRayProperties getXray() { return xray; }
        public void setXray(XRayProperties xray) { this.xray = xray; }
    }
    
    /**
     * Jaeger-specific configuration properties.
     */
    public static class JaegerProperties {
        
        /**
         * Whether Jaeger tracing is enabled
         */
        private boolean enabled = false;
        
        /**
         * Jaeger agent endpoint
         */
        private String agentEndpoint = "http://localhost:14268/api/traces";
        
        /**
         * Jaeger collector endpoint
         */
        private String collectorEndpoint;
        
        /**
         * Service tags to include in all spans
         */
        private List<String> serviceTags = List.of();
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getAgentEndpoint() { return agentEndpoint; }
        public void setAgentEndpoint(String agentEndpoint) { this.agentEndpoint = agentEndpoint; }
        
        public String getCollectorEndpoint() { return collectorEndpoint; }
        public void setCollectorEndpoint(String collectorEndpoint) { this.collectorEndpoint = collectorEndpoint; }
        
        public List<String> getServiceTags() { return serviceTags; }
        public void setServiceTags(List<String> serviceTags) { this.serviceTags = serviceTags; }
    }
    
    /**
     * AWS X-Ray specific configuration properties.
     */
    public static class XRayProperties {
        
        /**
         * Whether X-Ray tracing is enabled
         */
        private boolean enabled = false;
        
        /**
         * X-Ray daemon endpoint
         */
        private String daemonEndpoint = "127.0.0.1:2000";
        
        /**
         * AWS region for X-Ray
         */
        private String region = "us-east-1";
        
        /**
         * Service name for X-Ray
         */
        private String serviceName;
        
        /**
         * Whether to use sampling rules
         */
        private boolean useSamplingRules = true;
        
        // Getters and setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public String getDaemonEndpoint() { return daemonEndpoint; }
        public void setDaemonEndpoint(String daemonEndpoint) { this.daemonEndpoint = daemonEndpoint; }
        
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public boolean isUseSamplingRules() { return useSamplingRules; }
        public void setUseSamplingRules(boolean useSamplingRules) { this.useSamplingRules = useSamplingRules; }
    }
}