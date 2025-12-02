package com.waqiti.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready tracing configuration properties
 * Supports OpenTelemetry, Jaeger, Zipkin, and custom tracing backends
 */
@Data
@Component
@ConfigurationProperties(prefix = "waqiti.tracing")
public class TracingProperties {
    
    /**
     * Enable or disable distributed tracing
     */
    private boolean enabled = true;
    
    /**
     * Service name for tracing
     */
    private String serviceName = "waqiti-service";
    
    /**
     * Environment name (dev, staging, production)
     */
    private String environment = "development";
    
    /**
     * Sampling configuration
     */
    private SamplingConfig sampling = new SamplingConfig();
    
    /**
     * Exporter configuration
     */
    private ExporterConfig exporter = new ExporterConfig();
    
    /**
     * Propagation configuration
     */
    private PropagationConfig propagation = new PropagationConfig();
    
    /**
     * Resource attributes
     */
    private Map<String, String> resourceAttributes = new HashMap<>();
    
    /**
     * Performance configuration
     */
    private PerformanceConfig performance = new PerformanceConfig();
    
    /**
     * Security configuration
     */
    private SecurityConfig security = new SecurityConfig();
    
    @Data
    public static class SamplingConfig {
        /**
         * Sampling probability (0.0 to 1.0)
         */
        private double probability = 1.0;
        
        /**
         * Rate limiting for traces per second
         */
        private int rateLimit = 1000;
        
        /**
         * Enable adaptive sampling based on load
         */
        private boolean adaptiveSampling = true;
        
        /**
         * Minimum sampling rate during high load
         */
        private double minSamplingRate = 0.001;
        
        /**
         * Maximum sampling rate during low load
         */
        private double maxSamplingRate = 1.0;
    }
    
    @Data
    public static class ExporterConfig {
        /**
         * Exporter type (jaeger, zipkin, otlp, console)
         */
        private String type = "jaeger";
        
        /**
         * Endpoint URL for trace exporter
         */
        private String endpoint = "http://localhost:14250";
        
        /**
         * Export timeout in milliseconds
         */
        private long timeoutMs = 10000;
        
        /**
         * Batch size for exporting
         */
        private int batchSize = 512;
        
        /**
         * Queue size for pending exports
         */
        private int queueSize = 2048;
        
        /**
         * Export interval in milliseconds
         */
        private long exportIntervalMs = 5000;
        
        /**
         * Enable compression for exports
         */
        private boolean compressionEnabled = true;
        
        /**
         * Authentication token for secure endpoints
         */
        private String authToken;
        
        /**
         * TLS configuration
         */
        private TlsConfig tls = new TlsConfig();
    }
    
    @Data
    public static class PropagationConfig {
        /**
         * Propagation format (w3c, b3, jaeger, aws-xray)
         */
        private String format = "w3c";
        
        /**
         * Enable baggage propagation
         */
        private boolean baggageEnabled = true;
        
        /**
         * Maximum baggage items
         */
        private int maxBaggageItems = 100;
        
        /**
         * Maximum baggage value size
         */
        private int maxBaggageValueSize = 2048;
        
        /**
         * Custom headers to propagate
         */
        private Map<String, String> customHeaders = new HashMap<>();
    }
    
    @Data
    public static class PerformanceConfig {
        /**
         * Maximum number of attributes per span
         */
        private int maxAttributesPerSpan = 128;
        
        /**
         * Maximum number of events per span
         */
        private int maxEventsPerSpan = 128;
        
        /**
         * Maximum number of links per span
         */
        private int maxLinksPerSpan = 128;
        
        /**
         * Maximum attribute value length
         */
        private int maxAttributeValueLength = 2048;
        
        /**
         * Enable span metrics
         */
        private boolean spanMetricsEnabled = true;
        
        /**
         * Enable detailed timing metrics
         */
        private boolean detailedTimingEnabled = false;
        
        /**
         * Buffer size for span processor
         */
        private int spanProcessorBufferSize = 2048;
        
        /**
         * Number of worker threads for processing
         */
        private int processorThreads = 2;
    }
    
    /**
     * CRITICAL P0 SECURITY FIX: Tracing Security Configuration
     *
     * Fixed vulnerabilities:
     * 1. REMOVED wildcard origin "*" from trace collection
     * 2. DEFINED specific allowed origins for trace collectors
     * 3. Enhanced PII detection and sensitive data masking
     *
     * @author Waqiti Engineering Team - Production Security Fix
     * @version 2.0.0
     */
    @Data
    public static class SecurityConfig {
        /**
         * Enable sensitive data masking
         */
        private boolean maskSensitiveData = true;

        /**
         * Patterns for sensitive data
         */
        private String[] sensitivePatterns = {
            ".*password.*",
            ".*secret.*",
            ".*token.*",
            ".*key.*",
            ".*credential.*",
            ".*ssn.*",
            ".*social.?security.*",
            ".*credit.?card.*",
            ".*cvv.*",
            ".*pin.*",
            ".*account.?number.*"
        };

        /**
         * Enable trace encryption at rest
         */
        private boolean encryptionAtRest = true;

        /**
         * SECURITY FIX: Specific allowed origins for trace collection (NO WILDCARD!)
         * Only allow trusted observability platforms and internal monitoring tools
         */
        private String[] allowedOrigins = {
            "https://api.example.com",
            "https://api.example.com",
            "https://api.example.com",
            "https://api.example.com"
        };

        /**
         * Enable audit logging for traces
         */
        private boolean auditLoggingEnabled = true;

        /**
         * PII detection and removal
         */
        private boolean piiDetectionEnabled = true;
    }
    
    @Data
    public static class TlsConfig {
        /**
         * Enable TLS for exporter
         */
        private boolean enabled = false;
        
        /**
         * Path to certificate file
         */
        private String certPath;
        
        /**
         * Path to private key file
         */
        private String keyPath;
        
        /**
         * Path to CA certificate
         */
        private String caPath;
        
        /**
         * Enable hostname verification
         */
        private boolean hostnameVerification = true;
        
        /**
         * TLS version (TLSv1.2, TLSv1.3)
         */
        private String version = "TLSv1.3";
        
        /**
         * Cipher suites
         */
        private String[] cipherSuites = {
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256"
        };
    }
    
    /**
     * Get effective sampling rate based on configuration
     */
    public double getEffectiveSamplingRate() {
        if (!sampling.isAdaptiveSampling()) {
            return sampling.getProbability();
        }
        
        // In production, this would be calculated based on current system load
        // For now, return the configured probability
        return sampling.getProbability();
    }
    
    /**
     * Check if a specific propagation format is enabled
     */
    public boolean isPropagationFormatEnabled(String format) {
        return propagation.getFormat().toLowerCase().contains(format.toLowerCase());
    }
    
    /**
     * Get resource attributes with defaults
     */
    public Map<String, String> getEffectiveResourceAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("service.name", serviceName);
        attributes.put("service.environment", environment);
        attributes.put("service.version", System.getProperty("app.version", "unknown"));
        attributes.put("host.name", System.getProperty("hostname", "unknown"));
        attributes.putAll(resourceAttributes);
        return attributes;
    }
}