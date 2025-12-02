package com.waqiti.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TracingConfig {

    @Bean
    public io.micrometer.observation.ObservationRegistry observationRegistry() {
        return io.micrometer.observation.ObservationRegistry.create();
    }
}