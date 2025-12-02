package com.waqiti.analytics.client;

import com.waqiti.analytics.dto.slack.SlackMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for Slack Web API
 *
 * Sends messages to Slack channels for operational notifications.
 * Supports rich message formatting with blocks and attachments.
 *
 * Circuit Breaker: Protects against Slack API failures
 * Retry: Attempts 3 times with exponential backoff
 * Rate Limiting: Handled by Resilience4j RateLimiter
 *
 * API Documentation: https://api.slack.com/web
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@FeignClient(
    name = "slack",
    url = "${slack.api.url:https://slack.com/api}",
    configuration = SlackClientConfiguration.class
)
public interface SlackClient {

    /**
     * Post message to Slack channel
     *
     * @param message Slack message with channel and content
     */
    @PostMapping("/chat.postMessage")
    @CircuitBreaker(name = "slackService")
    @Retry(name = "slackService")
    void postMessage(@RequestBody SlackMessage message);
}
