/**
 * Scheduled Payment Event
 * Events published for scheduled payment lifecycle
 */
package com.waqiti.payment.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduledPaymentEvent {
    
    private EventType eventType;
    private UUID paymentId;
    private UUID userId;
    private UUID recipientId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String error;
    private LocalDateTime timestamp;
    
    public enum EventType {
        CREATED,
        UPDATED,
        PAUSED,
        RESUMED,
        CANCELLED,
        PROCESSED,
        FAILED,
        COMPLETED,
        REMINDER_SENT
    }
}