package com.waqiti.payment.ratelimit.dto;

public enum RateLimitAlgorithm {
    TOKEN_BUCKET,
    SLIDING_WINDOW,
    FIXED_WINDOW,
    LEAKY_BUCKET
}