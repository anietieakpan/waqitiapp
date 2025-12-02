package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Request to send notifications across multiple channels
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class MultiChannelNotificationRequest extends NotificationRequest {
    
    /**
     * Channels to send notifications to
     */
    private List<NotificationChannel> channels;
    
    /**
     * Channel-specific requests
     */
    private Map<NotificationChannel, NotificationRequest> channelRequests;
    
    /**
     * Fallback strategy
     */
    @Builder.Default
    private FallbackStrategy fallbackStrategy = FallbackStrategy.NONE;
    
    /**
     * Channel priority order
     */
    private List<NotificationChannel> priorityOrder;
    
    /**
     * Whether to send to all channels or stop on first success
     */
    @Builder.Default
    private boolean sendToAllChannels = true;
    
    /**
     * Delay between channel attempts (ms)
     */
    @Builder.Default
    private long channelDelayMs = 0;
    
    /**
     * Unified message content
     */
    private Map<String, Object> unifiedContent;
    
    /**
     * Channel coordination settings
     */
    private Map<String, Object> coordination;
    
    
    public enum FallbackStrategy {
        NONE,
        NEXT_CHANNEL,
        ALL_REMAINING,
        SPECIFIC_CHANNEL
    }
}