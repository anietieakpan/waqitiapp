package com.waqiti.analytics.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PagerDuty Feign Client Configuration
 *
 * Configures authentication and headers for PagerDuty API calls.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Configuration
public class PagerDutyClientConfiguration {

    @Value("${pagerduty.routing-key:}")
    private String routingKey;

    @Bean
    public RequestInterceptor pagerDutyRequestInterceptor() {
        return new PagerDutyRequestInterceptor();
    }

    /**
     * Adds required headers for PagerDuty API requests
     */
    private class PagerDutyRequestInterceptor implements RequestInterceptor {
        @Override
        public void apply(RequestTemplate template) {
            template.header("Content-Type", "application/json");
            template.header("Accept", "application/vnd.pagerduty+json;version=2");
        }
    }
}
