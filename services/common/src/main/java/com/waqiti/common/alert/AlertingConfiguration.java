package com.waqiti.common.alert;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for alerting infrastructure
 *
 * @author Waqiti Engineering
 * @version 1.0
 * @since 2025-10-10
 */
@Configuration
@EnableAsync
public class AlertingConfiguration {

    /**
     * RestTemplate for alerting HTTP calls
     * Configured with timeouts to prevent hanging
     */
    @Bean(name = "alertingRestTemplate")
    public RestTemplate alertingRestTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Dedicated thread pool for async alert processing
     * Prevents alerting from blocking application threads
     */
    @Bean(name = "alertExecutor")
    public Executor alertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("alert-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
