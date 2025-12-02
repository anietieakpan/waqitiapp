package com.waqiti.notification.domain;

/**
 * Notification channels for alert delivery
 */
public enum NotificationChannel {
    /**
     * Email notifications
     */
    EMAIL("EMAIL"),

    /**
     * SMS notifications
     */
    SMS("SMS"),

    /**
     * Push notifications
     */
    PUSH("PUSH"),

    /**
     * In-app notifications
     */
    IN_APP("IN_APP"),

    /**
     * WhatsApp notifications
     */
    WHATSAPP("WHATSAPP"),

    /**
     * Webhook notifications
     */
    WEBHOOK("WEBHOOK"),

    /**
     * Slack notifications
     */
    SLACK("SLACK"),

    /**
     * Microsoft Teams notifications
     */
    TEAMS("TEAMS"),

    /**
     * Operational dashboard alerts
     */
    DASHBOARD("DASHBOARD");

    private final String channel;

    NotificationChannel(String channel) {
        this.channel = channel;
    }

    public String getChannel() {
        return channel;
    }

    /**
     * Get channel from string value
     */
    public static NotificationChannel fromString(String channel) {
        for (NotificationChannel nc : values()) {
            if (nc.channel.equalsIgnoreCase(channel)) {
                return nc;
            }
        }
        throw new IllegalArgumentException("Unknown notification channel: " + channel);
    }

    /**
     * Check if this is a real-time channel
     */
    public boolean isRealTime() {
        return this == PUSH || this == IN_APP || this == WEBHOOK || this == DASHBOARD;
    }

    /**
     * Check if this is an external channel
     */
    public boolean isExternal() {
        return this == EMAIL || this == SMS || this == WHATSAPP || this == SLACK || this == TEAMS;
    }
}