package com.waqiti.recurringpayment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.Set; /**
 * Reminder settings for scheduled payments.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReminderSettings {
    
    @Column(name = "reminder_enabled")
    @Builder.Default
    private boolean enabled = false;
    
    @Column(name = "reminder_advance_hours")
    @Builder.Default
    private Integer advanceHours = 24;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "scheduled_payment_reminder_channels",
                    joinColumns = @JoinColumn(name = "payment_id"))
    @Column(name = "channel")
    @Enumerated(EnumType.STRING)
    private Set<NotificationChannel> channels;
    
    @Column(name = "reminder_message_template")
    private String messageTemplate;
    
    public Duration getAdvanceNotice() {
        return Duration.ofHours(advanceHours != null ? advanceHours : 24);
    }
}
