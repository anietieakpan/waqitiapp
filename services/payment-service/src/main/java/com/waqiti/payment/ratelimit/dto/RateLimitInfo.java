package com.waqiti.payment.ratelimit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitInfo {
    private String key;
    private int currentRequests;
    private long ttlSeconds;
}