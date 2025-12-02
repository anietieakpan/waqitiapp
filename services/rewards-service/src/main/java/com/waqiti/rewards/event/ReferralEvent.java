package com.waqiti.rewards.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all referral-related events
 *
 * @author Waqiti Platform Team
 * @version 2.0
 * @since 2025-11-09
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralEvent {

    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;
    private String source;
    private Map<String, Object> metadata;

    public static ReferralEventBuilder baseBuilder(String eventType) {
        return ReferralEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .timestamp(Instant.now())
                .source("rewards-service");
    }
}
