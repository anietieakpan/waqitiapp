package com.waqiti.payment.ratelimit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private int remainingRequests;
    private int maxRequests;
    private int currentRequests;
    private Duration windowSize;
    private RateLimitAlgorithm algorithm;
    private Instant resetTime;
}