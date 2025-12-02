package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.ShippingEvent;
import com.waqiti.virtualcard.domain.enums.ShippingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for shipping tracking information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingTrackingDto {
    
    private String cardId;
    private String orderId;
    private String trackingNumber;
    private ShippingStatus status;
    
    private Instant estimatedDelivery;
    private Instant actualDelivery;
    private String carrier;
    private String currentLocation;
    
    private List<ShippingEvent> trackingEvents;
    
    // Computed fields
    private String statusDescription;
    private String estimatedDeliveryFormatted;
    private int daysInTransit;
    private boolean isDelivered;
    private boolean isOverdue;
    private boolean hasIssues;
    private String nextExpectedUpdate;
    
    public String getStatusDescription() {
        return status != null ? status.getDescription() : "Unknown";
    }
    
    public String getEstimatedDeliveryFormatted() {
        if (estimatedDelivery == null) {
            return "Not available";
        }
        
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(
            estimatedDelivery, java.time.ZoneId.systemDefault());
        
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }
    
    public int getDaysInTransit() {
        if (trackingEvents == null || trackingEvents.isEmpty()) {
            return 0;
        }
        
        // Find the shipped event
        java.util.Optional<ShippingEvent> shippedEvent = trackingEvents.stream()
            .filter(event -> event.getStatus() == ShippingStatus.SHIPPED)
            .findFirst();
        
        if (shippedEvent.isEmpty()) {
            return 0;
        }
        
        Instant endTime = actualDelivery != null ? actualDelivery : Instant.now();
        return (int) java.time.Duration.between(shippedEvent.get().getTimestamp(), endTime).toDays();
    }
    
    public boolean isDelivered() {
        return status == ShippingStatus.DELIVERED;
    }
    
    public boolean isOverdue() {
        return estimatedDelivery != null && 
               Instant.now().isAfter(estimatedDelivery) && 
               !isDelivered();
    }
    
    public boolean hasIssues() {
        return status != null && status.hasIssue();
    }
    
    public String getNextExpectedUpdate() {
        if (isDelivered()) {
            return "Delivered";
        }
        
        switch (status) {
            case SHIPPED:
                return "In transit update expected within 24 hours";
            case IN_TRANSIT:
                return "Delivery status update expected within 24 hours";
            case OUT_FOR_DELIVERY:
                return "Delivery expected today";
            case DELIVERY_ATTEMPTED:
                return "Next delivery attempt scheduled";
            case EXCEPTION:
                return "Contact carrier for resolution";
            default:
                return "Status update pending";
        }
    }
    
    /**
     * Gets the latest tracking event
     */
    public ShippingEvent getLatestEvent() {
        if (trackingEvents == null || trackingEvents.isEmpty()) {
            return null;
        }
        
        return trackingEvents.stream()
            .max(java.util.Comparator.comparing(ShippingEvent::getTimestamp))
            .orElse(null);
    }
    
    /**
     * Gets formatted tracking summary
     */
    public String getTrackingSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Status: ").append(getStatusDescription());
        
        if (currentLocation != null && !currentLocation.trim().isEmpty()) {
            summary.append(" - Last seen: ").append(currentLocation);
        }
        
        if (estimatedDelivery != null && !isDelivered()) {
            summary.append(" - Expected: ").append(getEstimatedDeliveryFormatted());
        }
        
        if (actualDelivery != null) {
            String deliveredDate = java.time.LocalDateTime.ofInstant(
                actualDelivery, java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a"));
            summary.append(" - Delivered: ").append(deliveredDate);
        }
        
        return summary.toString();
    }
}