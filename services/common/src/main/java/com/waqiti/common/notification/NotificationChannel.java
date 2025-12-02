package com.waqiti.common.notification;

/**
 * Notification Channel Enumeration
 * 
 * Defines all supported notification delivery channels for the Waqiti platform.
 * Used throughout the system for user preference management and notification routing.
 */
public enum NotificationChannel {
    /**
     * Push notifications to mobile apps and web browsers
     */
    PUSH,
    
    /**
     * Email notifications
     */
    EMAIL,
    
    /**
     * SMS text messages
     */
    SMS,
    
    /**
     * In-app notifications visible within the application
     */
    IN_APP,
    
    /**
     * Webhook callbacks to external systems
     */
    WEBHOOK,
    
    /**
     * WhatsApp messages
     */
    WHATSAPP,
    
    /**
     * Slack notifications
     */
    SLACK,
    
    /**
     * Discord notifications
     */
    DISCORD,
    
    /**
     * Telegram bot messages
     */
    TELEGRAM
}