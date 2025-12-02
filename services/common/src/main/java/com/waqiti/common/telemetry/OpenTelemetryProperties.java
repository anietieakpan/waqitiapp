package com.waqiti.common.telemetry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for OpenTelemetry
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Data
@Validated
@ConfigurationProperties(prefix = "opentelemetry")
public class OpenTelemetryProperties {
    
    /**
     * Whether OpenTelemetry is enabled
     */
    private boolean enabled = true;
    
    /**
     * Service name for traces
     */
    @NotBlank
    private String serviceName = "${spring.application.name}";
    
    /**
     * Service version
     */
    private String serviceVersion = "1.0.0";
    
    /**
     * Deployment environment
     */
    private String environment = "production";
    
    /**
     * Exporter configuration
     */
    private Exporter exporter = new Exporter();
    
    /**
     * Sampling configuration
     */
    private Sampling sampling = new Sampling();
    
    /**
     * Batch configuration
     */
    private Batch batch = new Batch();
    
    /**
     * Resource attributes
     */
    private Map<String, String> resourceAttributes = Map.of();
    
    /**
     * Instrumentation configuration
     */
    private Instrumentation instrumentation = new Instrumentation();
    
    @Data
    public static class Exporter {
        /**
         * Exporter type: otlp, jaeger, zipkin, logging
         */
        private String type = "otlp";
        
        /**
         * Exporter endpoint
         */
        private String endpoint = "http://localhost:4317";
        
        /**
         * Exporter timeout
         */
        private Duration timeout = Duration.ofSeconds(10);
        
        /**
         * Compression: none, gzip
         */
        private String compression = "gzip";
        
        /**
         * Headers for authentication
         */
        private Map<String, String> headers = Map.of();
        
        /**
         * API key for authentication
         */
        private String apiKey;
    }
    
    @Data
    public static class Sampling {
        /**
         * Sampling probability (0.0 to 1.0)
         */
        @Min(0)
        @Max(1)
        private double probability = 1.0;
        
        /**
         * Always sample operations
         */
        private List<String> alwaysSample = List.of();
        
        /**
         * Never sample operations
         */
        private List<String> neverSample = List.of();
        
        /**
         * Error boost factor
         */
        private double errorBoostFactor = 2.0;
        
        /**
         * Adaptive sampling enabled
         */
        private boolean adaptive = true;
    }
    
    @Data
    public static class Batch {
        /**
         * Batch delay in milliseconds
         */
        private long delayMillis = 5000;
        
        /**
         * Maximum queue size
         */
        private int maxQueueSize = 2048;
        
        /**
         * Maximum export batch size
         */
        private int maxExportBatchSize = 512;
        
        /**
         * Export timeout
         */
        private Duration exportTimeout = Duration.ofSeconds(30);
    }
    
    @Data
    public static class Instrumentation {
        /**
         * HTTP instrumentation enabled
         */
        private boolean httpEnabled = true;
        
        /**
         * Database instrumentation enabled
         */
        private boolean databaseEnabled = true;
        
        /**
         * Kafka instrumentation enabled
         */
        private boolean kafkaEnabled = true;
        
        /**
         * Redis instrumentation enabled
         */
        private boolean redisEnabled = true;
        
        /**
         * gRPC instrumentation enabled
         */
        private boolean grpcEnabled = true;
        
        /**
         * Custom instrumentation
         */
        private Map<String, Boolean> custom = Map.of();
    }
}