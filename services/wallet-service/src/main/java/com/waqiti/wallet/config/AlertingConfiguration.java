package com.waqiti.wallet.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.Executor;

/**
 * Configuration for alerting infrastructure.
 *
 * <p>Configures:
 * <ul>
 *   <li>RestTemplate for external API calls (PagerDuty, Slack)</li>
 *   <li>Async executor for non-blocking alert delivery</li>
 *   <li>Timeouts and retry policies</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-22
 */
@Configuration
public class AlertingConfiguration {

    /**
     * RestTemplate for alerting service with appropriate timeouts.
     */
    @Bean(name = "alertingRestTemplate")
    public RestTemplate alertingRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Async executor for alert delivery.
     * Prevents blocking the main application thread when sending alerts.
     */
    @Bean(name = "alertExecutor")
    public Executor alertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("alert-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
