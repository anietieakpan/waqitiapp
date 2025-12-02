package com.waqiti.payment.integration.stripe;

import com.waqiti.common.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeEventProcessor {
    
    private static final String EVENT_PROCESSED_PREFIX = "stripe-event-processed:";
    private static final Duration EVENT_CACHE_TTL = Duration.ofDays(7);
    
    private final CacheService cacheService;
    
    public boolean isEventProcessed(String eventId) {
        String cacheKey = EVENT_PROCESSED_PREFIX + eventId;
        Boolean processed = cacheService.get(cacheKey, Boolean.class);
        return processed != null && processed;
    }
    
    public void markEventProcessed(String eventId) {
        String cacheKey = EVENT_PROCESSED_PREFIX + eventId;
        cacheService.set(cacheKey, true, EVENT_CACHE_TTL);
        log.debug("Marked Stripe event as processed: {}", eventId);
    }
    
    public void clearProcessedEvent(String eventId) {
        String cacheKey = EVENT_PROCESSED_PREFIX + eventId;
        cacheService.delete(cacheKey);
    }
}