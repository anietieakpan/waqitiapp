package com.waqiti.common.servicemesh;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

/**
 * Rate limiting policy configuration
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RateLimitPolicy extends Policy {
    private int requests;
    private Duration period;
    private int burstCapacity;
    private String keyExtractor;
    private int responseStatusCode;
    private String responseMessage;
}