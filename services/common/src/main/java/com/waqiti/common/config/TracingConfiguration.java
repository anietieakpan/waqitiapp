package com.waqiti.common.config;

import com.waqiti.common.tracing.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
// Temporarily commented - need to add contrib dependencies
// import io.opentelemetry.contrib.trace.propagation.b3.B3Propagator;
// import io.opentelemetry.contrib.trace.propagation.jaeger.JaegerPropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive tracing configuration that enables distributed tracing
 * across all microservices with support for HTTP, Kafka, and async operations
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(TracingProperties.class)
@ConditionalOnProperty(name = "waqiti.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingConfiguration implements WebMvcConfigurer {

    private final TracingProperties tracingProperties;
    private final DistributedTracingService distributedTracingService;
    private final OpenTelemetryTracingService openTelemetryService;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing distributed tracing configuration");
        log.info("Tracing enabled: {}", tracingProperties.isEnabled());
        log.info("OpenTelemetry enabled: {}", tracingProperties.getOpentelemetry().isEnabled());
        log.info("Tracing backend: {}", tracingProperties.getOpentelemetry().getBackend());
        log.info("Sampling strategy: {}", tracingProperties.getOpentelemetry().getSampling().getStrategy());
    }
    
    /**
     * Configure HTTP interceptors for tracing propagation
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (tracingProperties.isEnabled()) {
            TracingHttpInterceptor httpInterceptor = new TracingHttpInterceptor(
                distributedTracingService, 
                openTelemetryService
            );
            
            registry.addInterceptor(httpInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/actuator/**",
                    "/health/**",
                    "/metrics/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                );
            
            log.info("Registered HTTP tracing interceptor");
        }
    }
    
    /**
     * Configure RestTemplate with tracing interceptor
     */
    @Bean
    @Primary
    public RestTemplate tracingRestTemplate(RestTemplateBuilder builder) {
        RestTemplate restTemplate = builder
            .setConnectTimeout(Duration.ofSeconds(tracingProperties.getHttp().getConnectTimeoutSeconds()))
            .setReadTimeout(Duration.ofSeconds(tracingProperties.getHttp().getReadTimeoutSeconds()))
            .build();
        
        if (tracingProperties.isEnabled()) {
            TracingHttpInterceptor httpInterceptor = new TracingHttpInterceptor(
                distributedTracingService,
                openTelemetryService
            );
            
            restTemplate.setInterceptors(Collections.singletonList(httpInterceptor));
            log.info("Configured RestTemplate with tracing interceptor");
        }
        
        return restTemplate;
    }
    
    /**
     * Configure Kafka producer with tracing interceptor
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public ProducerFactory<Object, Object> tracingProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, tracingProperties.getKafka().getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");
        
        if (tracingProperties.isEnabled()) {
            props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, 
                TracingKafkaInterceptor.TracingProducerInterceptor.class.getName());
            log.info("Configured Kafka producer with tracing interceptor");
        }
        
        return new DefaultKafkaProducerFactory<>(props);
    }
    
    /**
     * Configure Kafka consumer with tracing interceptor
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public ConsumerFactory<Object, Object> tracingConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, tracingProperties.getKafka().getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, tracingProperties.getKafka().getConsumerGroupId());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonDeserializer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        if (tracingProperties.isEnabled()) {
            props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
                TracingKafkaInterceptor.TracingConsumerInterceptor.class.getName());
            log.info("Configured Kafka consumer with tracing interceptor");
        }
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Configure Kafka listener container factory with tracing
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConsumerFactory<Object, Object> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(tracingProperties.getKafka().getConsumerConcurrency());
        
        log.info("Configured Kafka listener container factory with concurrency: {}", 
            tracingProperties.getKafka().getConsumerConcurrency());
        
        return factory;
    }
    
    /**
     * Configure KafkaTemplate with tracing
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.kafka.enabled", havingValue = "true", matchIfMissing = true)
    public KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
    
    /**
     * Create tracing aspect for @Traced annotation
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.aspects.enabled", havingValue = "true", matchIfMissing = true)
    public TracingAspect tracingAspect(io.micrometer.tracing.Tracer micrometerTracer) {
        // Use the Micrometer tracer that is provided by Spring Boot
        TracingMetrics tracingMetrics = new TracingMetrics();
        return new TracingAspect(micrometerTracer, tracingMetrics);
    }
    
    /**
     * Create async tracing executor
     */
    @Bean
    @ConditionalOnProperty(name = "waqiti.tracing.async.enabled", havingValue = "true", matchIfMissing = true)
    public TracingAsyncExecutor tracingAsyncExecutor() {
        return new TracingAsyncExecutor(
            tracingProperties.getAsync().getCorePoolSize(),
            tracingProperties.getAsync().getMaxPoolSize(),
            tracingProperties.getAsync().getQueueCapacity(),
            distributedTracingService,
            openTelemetryService
        );
    }
    
    /**
     * Create tracing metrics exporter
     */
    @Bean
    public TracingMetricsExporter tracingMetricsExporter(MeterRegistry meterRegistry) {
        return new TracingMetricsExporter(
            distributedTracingService,
            openTelemetryService,
            tracingProperties.getMetrics().getExportIntervalSeconds(),
            meterRegistry
        );
    }
    
    /**
     * Configuration properties for tracing
     */
    @ConfigurationProperties(prefix = "waqiti.tracing")
    @lombok.Data
    public static class TracingProperties {
        private boolean enabled = true;
        private double sampleRate = 0.1;
        
        private HttpProperties http = new HttpProperties();
        private KafkaProperties kafka = new KafkaProperties();
        private OpenTelemetryProperties opentelemetry = new OpenTelemetryProperties();
        private AsyncProperties async = new AsyncProperties();
        private MetricsProperties metrics = new MetricsProperties();
        private AspectsProperties aspects = new AspectsProperties();
        
        @lombok.Data
        public static class HttpProperties {
            private int connectTimeoutSeconds = 30;
            private int readTimeoutSeconds = 30;
            private boolean propagateHeaders = true;
        }
        
        @lombok.Data
        public static class KafkaProperties {
            private boolean enabled = true;
            private String bootstrapServers = "localhost:9092";
            private String consumerGroupId = "tracing-consumer-group";
            private int consumerConcurrency = 3;
        }
        
        @lombok.Data
        public static class OpenTelemetryProperties {
            private boolean enabled = true;
            private String endpoint = "http://localhost:14250";
            private String backend = "jaeger"; // jaeger, zipkin, otlp
            private SamplingProperties sampling = new SamplingProperties();
            private ExportProperties export = new ExportProperties();
            
            @lombok.Data
            public static class SamplingProperties {
                private String strategy = "adaptive"; // always, never, probabilistic, adaptive, rate_limiting
                private double rate = 0.1;
            }
            
            @lombok.Data
            public static class ExportProperties {
                private int batchSize = 512;
                private int timeoutSeconds = 30;
            }
        }
        
        @lombok.Data
        public static class AsyncProperties {
            private boolean enabled = true;
            private int corePoolSize = 10;
            private int maxPoolSize = 50;
            private int queueCapacity = 100;
        }
        
        @lombok.Data
        public static class MetricsProperties {
            private boolean enabled = true;
            private int exportIntervalSeconds = 60;
        }
        
        @lombok.Data
        public static class AspectsProperties {
            private boolean enabled = true;
            private boolean includeParameters = false;
            private boolean includeResult = false;
        }
    }
}