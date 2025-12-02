package com.waqiti.common.tracing;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveBaggageManager;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import brave.Tracing;
import brave.sampler.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Auto-configuration for the tracing framework.
 * This configuration is automatically loaded when the tracing library is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass({Tracer.class})
@ConditionalOnProperty(value = "tracing.enabled", havingValue = "true", matchIfMissing = true)
@EnableAspectJAutoProxy
@ComponentScan(basePackages = "com.waqiti.common.tracing")
@Slf4j
public class TracingAutoConfiguration {
    
    /**
     * Provides a fallback MeterRegistry if none is configured.
     */
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        log.info("Creating SimpleMeterRegistry as fallback");
        return new SimpleMeterRegistry();
    }
    
    /**
     * Provides a fallback Tracing instance if none is configured.
     */
    @Bean
    @ConditionalOnMissingBean(Tracing.class)
    public Tracing tracing() {
        log.info("Creating default Tracing instance");
        return Tracing.newBuilder()
                .localServiceName("waqiti-service")
                .sampler(Sampler.ALWAYS_SAMPLE)
                .build();
    }
    
    /**
     * Provides a fallback Tracer if none is configured.
     */
    @Bean
    @ConditionalOnMissingBean(Tracer.class)
    public Tracer tracer(Tracing tracing) {
        log.info("Creating BraveTracer from Tracing instance");
        return new BraveTracer(
                tracing.tracer(),
                new BraveCurrentTraceContext(tracing.currentTraceContext()),
                new BraveBaggageManager()
        );
    }
    
    /**
     * Creates the TracingAspect bean.
     */
    @Bean
    @ConditionalOnMissingBean(TracingAspect.class)
    public TracingAspect tracingAspect(Tracer tracer, TracingMetrics tracingMetrics) {
        log.info("Enabling @Traced annotation support");
        return new TracingAspect(tracer, tracingMetrics);
    }
    
    /**
     * Creates the TracingMetrics bean.
     */
    @Bean
    @ConditionalOnMissingBean(TracingMetrics.class)
    public TracingMetrics tracingMetrics(MeterRegistry meterRegistry) {
        return new TracingMetrics(meterRegistry);
    }
    
    /**
     * Creates the SpanCustomizer bean.
     */
    @Bean
    @ConditionalOnMissingBean(SpanCustomizer.class)
    public SpanCustomizer spanCustomizer() {
        return new SpanCustomizer();
    }
    
    /**
     * Creates the TracingContextProvider bean.
     */
    @Bean
    @ConditionalOnMissingBean(TracingContextProvider.class)
    public TracingContextProvider tracingContextProvider(Tracer tracer) {
        return new TracingContextProvider(tracer);
    }
}