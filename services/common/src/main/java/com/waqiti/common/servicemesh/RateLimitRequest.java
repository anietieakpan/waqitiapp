package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for creating rate limiting policies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRequest {
    private String policyName;
    private String serviceName;
    private int requests;
    private long periodSeconds;
    private int burstCapacity;
    private String keyExtractor;
    private int responseStatusCode;
    private String responseMessage;
}