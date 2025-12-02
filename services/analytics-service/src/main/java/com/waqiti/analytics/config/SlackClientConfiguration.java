package com.waqiti.analytics.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Slack Feign Client Configuration
 *
 * Configures authentication and headers for Slack Web API calls.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@Configuration
public class SlackClientConfiguration {

    @Value("${slack.token:}")
    private String botToken;

    @Bean
    public RequestInterceptor slackRequestInterceptor() {
        return new SlackRequestInterceptor();
    }

    /**
     * Adds authentication token and headers for Slack API requests
     */
    private class SlackRequestInterceptor implements RequestInterceptor {
        @Override
        public void apply(RequestTemplate template) {
            template.header("Content-Type", "application/json");
            template.header("Authorization", "Bearer " + botToken);
        }
    }
}
