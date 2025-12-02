package com.waqiti.analytics.client;

import com.waqiti.analytics.dto.pagerduty.PagerDutyEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for PagerDuty Events API v2
 *
 * Creates incidents for critical alerts requiring immediate attention.
 * Maps alert severity to PagerDuty urgency levels.
 *
 * Circuit Breaker: Protects against PagerDuty API failures
 * Retry: Attempts 3 times with exponential backoff
 * Fallback: Logs error but continues (PagerDuty is supplementary alerting)
 *
 * API Documentation: https://developer.pagerduty.com/api-reference/
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@FeignClient(
    name = "pagerduty",
    url = "${pagerduty.api.url:https://events.pagerduty.com}",
    configuration = PagerDutyClientConfiguration.class
)
public interface PagerDutyClient {

    /**
     * Trigger PagerDuty incident
     *
     * @param event PagerDuty event with alert details
     */
    @PostMapping("/v2/enqueue")
    @CircuitBreaker(name = "pagerDutyService")
    @Retry(name = "pagerDutyService")
    void triggerEvent(@RequestBody PagerDutyEvent event);
}
