package com.waqiti.notification.service;

import com.waqiti.notification.domain.Alert;
import com.waqiti.notification.domain.AlertSeverity;
import com.waqiti.notification.domain.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for determining appropriate notification channels based on alert characteristics
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationRoutingService {

    /**
     * Determine notification channels based on alert severity, type, and requirements
     */
    public List<NotificationChannel> determineChannels(Alert alert) {
        List<NotificationChannel> channels = new ArrayList<>();

        log.debug("Determining notification channels for alert: {} with severity: {}",
            alert.getId(), alert.getSeverity());

        // Base channels for all alerts
        channels.add(NotificationChannel.DASHBOARD);

        // Severity-based routing
        switch (alert.getSeverity()) {
            case CRITICAL:
                channels.addAll(getCriticalAlertChannels(alert));
                break;
            case HIGH:
                channels.addAll(getHighPriorityChannels(alert));
                break;
            case MEDIUM:
                channels.addAll(getMediumPriorityChannels(alert));
                break;
            case LOW:
            case INFO:
                channels.addAll(getLowPriorityChannels(alert));
                break;
        }

        // Type-specific routing
        channels.addAll(getTypeSpecificChannels(alert));

        // Category-specific routing
        channels.addAll(getCategorySpecificChannels(alert));

        // Remove duplicates and return
        return channels.stream().distinct().toList();
    }

    /**
     * Get channels for critical alerts - use all available channels
     */
    private List<NotificationChannel> getCriticalAlertChannels(Alert alert) {
        return List.of(
            NotificationChannel.EMAIL,
            NotificationChannel.SLACK,
            NotificationChannel.TEAMS,
            NotificationChannel.IN_APP,
            NotificationChannel.SMS,
            NotificationChannel.WEBHOOK
        );
    }

    /**
     * Get channels for high priority alerts
     */
    private List<NotificationChannel> getHighPriorityChannels(Alert alert) {
        return List.of(
            NotificationChannel.EMAIL,
            NotificationChannel.SLACK,
            NotificationChannel.IN_APP,
            NotificationChannel.WEBHOOK
        );
    }

    /**
     * Get channels for medium priority alerts
     */
    private List<NotificationChannel> getMediumPriorityChannels(Alert alert) {
        return List.of(
            NotificationChannel.EMAIL,
            NotificationChannel.IN_APP
        );
    }

    /**
     * Get channels for low priority alerts
     */
    private List<NotificationChannel> getLowPriorityChannels(Alert alert) {
        return List.of(
            NotificationChannel.IN_APP
        );
    }

    /**
     * Get additional channels based on alert type
     */
    private List<NotificationChannel> getTypeSpecificChannels(Alert alert) {
        List<NotificationChannel> channels = new ArrayList<>();

        if (alert.getType() == null) {
            return channels;
        }

        switch (alert.getType().toUpperCase()) {
            case "SECURITY":
            case "FRAUD":
                // Security alerts should go through multiple channels
                channels.add(NotificationChannel.WHATSAPP);
                if (alert.getSeverity().isCriticalOrHigh()) {
                    channels.add(NotificationChannel.SMS);
                }
                break;

            case "FINANCIAL":
            case "PAYMENT":
                // Financial alerts need audit trail
                channels.add(NotificationChannel.EMAIL);
                channels.add(NotificationChannel.WEBHOOK);
                break;

            case "SYSTEM":
            case "INFRASTRUCTURE":
                // Technical alerts for ops team
                channels.add(NotificationChannel.SLACK);
                channels.add(NotificationChannel.TEAMS);
                break;

            case "COMPLIANCE":
                // Compliance alerts need formal notification
                channels.add(NotificationChannel.EMAIL);
                break;

            case "CUSTOMER":
                // Customer-related alerts
                channels.add(NotificationChannel.IN_APP);
                break;
        }

        return channels;
    }

    /**
     * Get additional channels based on alert category
     */
    private List<NotificationChannel> getCategorySpecificChannels(Alert alert) {
        List<NotificationChannel> channels = new ArrayList<>();

        if (alert.getCategory() == null) {
            return channels;
        }

        switch (alert.getCategory().toUpperCase()) {
            case "DATABASE":
            case "NETWORK":
                // Infrastructure categories
                channels.add(NotificationChannel.SLACK);
                break;

            case "BUSINESS":
                // Business alerts need management visibility
                channels.add(NotificationChannel.EMAIL);
                channels.add(NotificationChannel.TEAMS);
                break;

            case "EXTERNAL_SERVICE":
                // External service issues
                channels.add(NotificationChannel.WEBHOOK);
                break;
        }

        return channels;
    }

    /**
     * Determine channels for acknowledgment-required alerts
     */
    public List<NotificationChannel> determineAcknowledgmentChannels(Alert alert) {
        List<NotificationChannel> channels = new ArrayList<>();

        if (Boolean.TRUE.equals(alert.getRequiresAcknowledgment())) {
            // Acknowledgment required alerts should use persistent channels
            channels.add(NotificationChannel.EMAIL);
            channels.add(NotificationChannel.IN_APP);

            if (alert.getSeverity().isCriticalOrHigh()) {
                channels.add(NotificationChannel.SLACK);
                channels.add(NotificationChannel.SMS);
            }
        }

        return channels;
    }

    /**
     * Determine escalation channels when primary channels fail or alerts are not acknowledged
     */
    public List<NotificationChannel> determineEscalationChannels(Alert alert) {
        List<NotificationChannel> channels = new ArrayList<>();

        // Escalation always uses more intrusive channels
        channels.add(NotificationChannel.SMS);
        channels.add(NotificationChannel.WHATSAPP);

        if (alert.getSeverity() == AlertSeverity.CRITICAL) {
            // For critical alerts, use all available channels
            channels.addAll(Set.of(NotificationChannel.values()));
        }

        return channels.stream().distinct().toList();
    }

    /**
     * Filter channels based on time of day and business hours
     */
    public List<NotificationChannel> filterChannelsByTime(List<NotificationChannel> channels, Alert alert) {
        // During business hours, prefer less intrusive channels
        // Outside business hours, for critical alerts, use all channels

        java.time.LocalTime now = java.time.LocalTime.now();
        boolean isBusinessHours = now.isAfter(java.time.LocalTime.of(8, 0)) &&
                                 now.isBefore(java.time.LocalTime.of(18, 0));

        if (isBusinessHours && !alert.getSeverity().isCriticalOrHigh()) {
            // During business hours, filter out SMS/WhatsApp for non-critical alerts
            return channels.stream()
                .filter(channel -> !channel.equals(NotificationChannel.SMS) &&
                                 !channel.equals(NotificationChannel.WHATSAPP))
                .toList();
        }

        return channels;
    }
}