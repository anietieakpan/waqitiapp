package com.waqiti.expense.config;

import brave.sampler.Sampler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Distributed tracing configuration with Spring Cloud Sleuth/Micrometer Tracing
 */
@Configuration
@Slf4j
public class TracingConfig {

    @Value("${spring.application.name:expense-service}")
    private String applicationName;

    @Value("${tracing.sampling.probability:0.1}")
    private float samplingProbability;

    /**
     * Configure trace sampling strategy
     */
    @Bean
    public Sampler defaultSampler() {
        log.info("Configuring distributed tracing with sampling probability: {}", samplingProbability);
        return Sampler.create(samplingProbability);
    }
}
