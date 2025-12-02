package com.waqiti.recurringpayment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Set; /**
 * Schedule configuration for payments.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleConfiguration {
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private PaymentFrequency frequency;
    
    @Column(name = "interval_value")
    @Builder.Default
    private Integer interval = 1;
    
    @Column(name = "custom_interval_seconds")
    private Long customIntervalSeconds;
    
    @Column(name = "day_of_month")
    private Integer dayOfMonth;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "scheduled_payment_days_of_week",
                    joinColumns = @JoinColumn(name = "payment_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek;
    
    @Column(name = "time_of_day")
    private String timeOfDay; // HH:mm format
    
    @Column(name = "skip_weekends")
    private Boolean skipWeekends;
    
    @Column(name = "skip_holidays")
    private Boolean skipHolidays;
    
    @Column(name = "retry_on_failure")
    private Boolean retryOnFailure;
    
    public Duration getCustomInterval() {
        return customIntervalSeconds != null ? Duration.ofSeconds(customIntervalSeconds) : null;
    }
    
    public void setCustomInterval(Duration interval) {
        this.customIntervalSeconds = interval != null ? interval.getSeconds() : null;
    }
}
