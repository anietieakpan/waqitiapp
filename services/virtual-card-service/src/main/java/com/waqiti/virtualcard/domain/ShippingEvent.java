package com.waqiti.virtualcard.domain;

import com.waqiti.virtualcard.domain.enums.ShippingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Shipping Event embeddable entity for tracking shipment progress
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingEvent {
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ShippingStatus status;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "location")
    private String location;
    
    @Column(name = "timestamp")
    private Instant timestamp;
    
    @Column(name = "event_code")
    private String eventCode;
    
    @Column(name = "carrier_message")
    private String carrierMessage;
    
    @Column(name = "exception_code")
    private String exceptionCode;
    
    @Column(name = "exception_description")
    private String exceptionDescription;
    
    @Column(name = "signed_by")
    private String signedBy;
    
    @Column(name = "delivery_attempt_count")
    private Integer deliveryAttemptCount;
    
    public static ShippingEvent created() {
        return ShippingEvent.builder()
            .status(ShippingStatus.PREPARING)
            .description("Shipment created")
            .timestamp(Instant.now())
            .build();
    }
    
    public static ShippingEvent shipped(String trackingNumber, String carrier) {
        return ShippingEvent.builder()
            .status(ShippingStatus.SHIPPED)
            .description("Package shipped with " + carrier)
            .timestamp(Instant.now())
            .build();
    }
    
    public static ShippingEvent inTransit(String location) {
        return ShippingEvent.builder()
            .status(ShippingStatus.IN_TRANSIT)
            .description("In transit")
            .location(location)
            .timestamp(Instant.now())
            .build();
    }
    
    public static ShippingEvent outForDelivery(String location) {
        return ShippingEvent.builder()
            .status(ShippingStatus.OUT_FOR_DELIVERY)
            .description("Out for delivery")
            .location(location)
            .timestamp(Instant.now())
            .build();
    }
    
    public static ShippingEvent delivered(String signedBy) {
        return ShippingEvent.builder()
            .status(ShippingStatus.DELIVERED)
            .description("Delivered")
            .signedBy(signedBy)
            .timestamp(Instant.now())
            .build();
    }
    
    public static ShippingEvent deliveryAttempted(int attemptNumber, String reason) {
        return ShippingEvent.builder()
            .status(ShippingStatus.DELIVERY_ATTEMPTED)
            .description("Delivery attempted - " + reason)
            .deliveryAttemptCount(attemptNumber)
            .timestamp(Instant.now())
            .build();
    }
    
    public static ShippingEvent exception(String exceptionCode, String description) {
        return ShippingEvent.builder()
            .status(ShippingStatus.EXCEPTION)
            .description("Delivery exception")
            .exceptionCode(exceptionCode)
            .exceptionDescription(description)
            .timestamp(Instant.now())
            .build();
    }
}