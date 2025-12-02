package com.waqiti.reporting.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "report_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ReportSchedule {

    @Id
    private UUID scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private ReportDefinition reportDefinition;

    @Column(nullable = false)
    private String scheduleName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduleFrequency frequency;

    @Column(nullable = false)
    private LocalTime executionTime;

    @ElementCollection
    @CollectionTable(name = "schedule_recipients", joinColumns = @JoinColumn(name = "schedule_id"))
    @Column(name = "recipient_email")
    private List<String> recipients;

    @ElementCollection
    @CollectionTable(name = "schedule_parameters", joinColumns = @JoinColumn(name = "schedule_id"))
    @MapKeyColumn(name = "parameter_name")
    @Column(name = "parameter_value")
    private Map<String, String> parameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutputFormat outputFormat;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private Boolean emailNotification;

    private LocalDateTime lastExecuted;

    private LocalDateTime nextExecution;

    @Enumerated(EnumType.STRING)
    private ExecutionStatus lastExecutionStatus;

    private String lastExecutionError;

    @Column(nullable = false)
    private String createdBy;

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private String updatedBy;

    // Weekly schedule specific fields
    @ElementCollection
    @CollectionTable(name = "schedule_days", joinColumns = @JoinColumn(name = "schedule_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private List<DayOfWeek> daysOfWeek;

    // Monthly schedule specific fields
    private Integer dayOfMonth;

    @PrePersist
    protected void onCreate() {
        if (scheduleId == null) {
            scheduleId = UUID.randomUUID();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (emailNotification == null) {
            emailNotification = true;
        }
        if (outputFormat == null) {
            outputFormat = OutputFormat.PDF;
        }
        calculateNextExecution();
    }

    @PreUpdate
    protected void onUpdate() {
        calculateNextExecution();
    }

    private void calculateNextExecution() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = switch (frequency) {
            case DAILY -> now.plusDays(1).with(executionTime);
            case WEEKLY -> calculateNextWeeklyExecution(now);
            case MONTHLY -> calculateNextMonthlyExecution(now);
            case QUARTERLY -> now.plusMonths(3).with(executionTime);
            case YEARLY -> now.plusYears(1).with(executionTime);
        };
        
        // Ensure next execution is in the future
        if (next.isBefore(now) || next.equals(now)) {
            next = switch (frequency) {
                case DAILY -> next.plusDays(1);
                case WEEKLY -> next.plusWeeks(1);
                case MONTHLY -> next.plusMonths(1);
                case QUARTERLY -> next.plusMonths(3);
                case YEARLY -> next.plusYears(1);
            };
        }
        
        this.nextExecution = next;
    }

    private LocalDateTime calculateNextWeeklyExecution(LocalDateTime now) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return now.plusWeeks(1).with(executionTime);
        }
        
        // Find next occurrence of scheduled day
        for (int i = 0; i < 7; i++) {
            LocalDateTime candidate = now.plusDays(i).with(executionTime);
            if (daysOfWeek.contains(DayOfWeek.valueOf(candidate.getDayOfWeek().name()))) {
                if (candidate.isAfter(now)) {
                    return candidate;
                }
            }
        }
        
        return now.plusWeeks(1).with(executionTime);
    }

    private LocalDateTime calculateNextMonthlyExecution(LocalDateTime now) {
        if (dayOfMonth == null) {
            return now.plusMonths(1).with(executionTime);
        }
        
        LocalDateTime candidate = now.withDayOfMonth(Math.min(dayOfMonth, now.toLocalDate().lengthOfMonth()))
                                     .with(executionTime);
        
        if (candidate.isBefore(now) || candidate.equals(now)) {
            candidate = candidate.plusMonths(1);
            candidate = candidate.withDayOfMonth(Math.min(dayOfMonth, candidate.toLocalDate().lengthOfMonth()));
        }
        
        return candidate;
    }

    public enum ScheduleFrequency {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }

    public enum DayOfWeek {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY
    }

    public enum OutputFormat {
        PDF,
        EXCEL,
        CSV,
        JSON,
        XML,
        HTML
    }

    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        CANCELLED,
        RUNNING
    }
}