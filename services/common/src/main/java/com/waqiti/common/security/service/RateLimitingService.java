package com.waqiti.common.security.service;

import com.waqiti.common.security.config.RateLimitConfiguration;
import com.waqiti.common.security.model.RateLimitResult;
import com.waqiti.common.security.model.RateLimitTier;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Rate limiting service with Redis backend
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {
    
    private final ProxyManager<byte[]> buckets;
    private final RateLimitConfiguration rateLimitConfig;
    
    public RateLimitResult checkRateLimit(String clientId, String endpoint) {
        RateLimitTier tier = rateLimitConfig.getTierForEndpoint(endpoint);
        BucketConfiguration bucketConfig = createBucketConfiguration(tier);
        
        String bucketKey = "rate-limit:" + clientId + ":" + endpoint;
        Bucket bucket = buckets.builder().build(bucketKey.getBytes(), bucketConfig);
        
        if (bucket.tryConsume(1)) {
            long remainingTokens = bucket.getAvailableTokens();
            return RateLimitResult.allowed(tier.getRequestsPerMinute(), remainingTokens);
        } else {
            long waitForRefill = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
            long retryAfter = Duration.ofNanos(waitForRefill).getSeconds();
            return RateLimitResult.denied(tier.getRequestsPerMinute(), retryAfter);
        }
    }
    
    private BucketConfiguration createBucketConfiguration(RateLimitTier tier) {
        Bandwidth bandwidth = Bandwidth.simple(
            tier.getRequestsPerMinute(), 
            Duration.ofMinutes(1)
        );
        
        return BucketConfiguration.builder()
            .addLimit(bandwidth)
            .build();
    }
}