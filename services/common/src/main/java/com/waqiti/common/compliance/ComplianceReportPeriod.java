package com.waqiti.common.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Compliance report period definition
 * 
 * Defines time periods for compliance reporting with various preset
 * configurations for common regulatory reporting cycles.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceReportPeriod {

    /**
     * Period start date and time
     */
    private LocalDateTime startDate;

    /**
     * Period end date and time
     */
    private LocalDateTime endDate;

    /**
     * Period type identifier
     */
    private PeriodType periodType;

    /**
     * Human-readable period name
     */
    private String periodName;

    /**
     * Period description
     */
    private String description;

    /**
     * Number of business days in period
     */
    private Integer businessDays;

    /**
     * Number of calendar days in period
     */
    private Integer calendarDays;

    /**
     * Period configuration metadata
     */
    private java.util.Map<String, Object> metadata;

    // Static constants for backward compatibility
    public static final PeriodType DAILY = PeriodType.DAILY;
    public static final PeriodType WEEKLY = PeriodType.WEEKLY;
    public static final PeriodType MONTHLY = PeriodType.MONTHLY;
    public static final PeriodType QUARTERLY = PeriodType.QUARTERLY;
    public static final PeriodType SEMI_ANNUALLY = PeriodType.SEMI_ANNUALLY;
    public static final PeriodType ANNUALLY = PeriodType.ANNUALLY;
    public static final PeriodType CUSTOM = PeriodType.CUSTOM;

    /**
     * Predefined period types for compliance reporting
     */
    public enum PeriodType {
        DAILY("Daily", "Daily reporting period"),
        WEEKLY("Weekly", "Weekly reporting period"),
        MONTHLY("Monthly", "Monthly reporting period"),
        QUARTERLY("Quarterly", "Quarterly reporting period"),
        SEMI_ANNUALLY("Semi-Annual", "Semi-annual reporting period"),
        ANNUALLY("Annual", "Annual reporting period"),
        CUSTOM("Custom", "Custom date range"),
        INCIDENT_BASED("Incident", "Incident-based period"),
        ROLLING_30_DAYS("Rolling 30 Days", "Rolling 30-day period"),
        ROLLING_90_DAYS("Rolling 90 Days", "Rolling 90-day period"),
        FISCAL_QUARTER("Fiscal Quarter", "Fiscal quarter period"),
        FISCAL_YEAR("Fiscal Year", "Fiscal year period"),
        CALENDAR_QUARTER("Calendar Quarter", "Calendar quarter period"),
        CALENDAR_YEAR("Calendar Year", "Calendar year period"),
        ON_DEMAND("On-Demand", "On-demand or ad-hoc reporting period");

        private final String displayName;
        private final String description;

        PeriodType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * Calculate the duration in days
     */
    public long getDurationInDays() {
        if (startDate == null || endDate == null) return 0;
        return ChronoUnit.DAYS.between(startDate, endDate) + 1; // Include both start and end dates
    }

    /**
     * Calculate the duration in hours
     */
    public long getDurationInHours() {
        if (startDate == null || endDate == null) return 0;
        return ChronoUnit.HOURS.between(startDate, endDate);
    }

    /**
     * Check if the period is valid
     */
    public boolean isValid() {
        return startDate != null && endDate != null && !startDate.isAfter(endDate);
    }

    /**
     * Check if the period contains a specific date
     */
    public boolean contains(LocalDateTime date) {
        if (!isValid() || date == null) return false;
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Check if this period overlaps with another period
     */
    public boolean overlaps(ComplianceReportPeriod other) {
        if (!isValid() || !other.isValid()) return false;
        
        return !endDate.isBefore(other.startDate) && !startDate.isAfter(other.endDate);
    }

    /**
     * Get period summary string
     */
    public String getPeriodSummary() {
        if (!isValid()) return "Invalid period";
        
        if (periodName != null && !periodName.trim().isEmpty()) {
            return periodName;
        }
        
        return String.format("%s to %s (%d days)", 
            startDate.toLocalDate(), 
            endDate.toLocalDate(), 
            getDurationInDays());
    }

    // Static factory methods for common periods

    /**
     * Create current month period
     */
    public static ComplianceReportPeriod currentMonth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfMonth = startOfMonth.plusMonths(1).minusNanos(1);
        
        return ComplianceReportPeriod.builder()
            .startDate(startOfMonth)
            .endDate(endOfMonth)
            .periodType(PeriodType.MONTHLY)
            .periodName("Current Month")
            .description("Current calendar month")
            .build();
    }

    /**
     * Create previous month period
     */
    public static ComplianceReportPeriod previousMonth() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfPrevMonth = now.minusMonths(1).withDayOfMonth(1)
            .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfPrevMonth = now.withDayOfMonth(1).minusNanos(1);
        
        return ComplianceReportPeriod.builder()
            .startDate(startOfPrevMonth)
            .endDate(endOfPrevMonth)
            .periodType(PeriodType.MONTHLY)
            .periodName("Previous Month")
            .description("Previous calendar month")
            .build();
    }

    /**
     * Create current quarter period
     */
    public static ComplianceReportPeriod currentQuarter() {
        LocalDateTime now = LocalDateTime.now();
        int currentQuarter = ((now.getMonthValue() - 1) / 3) + 1;
        int quarterStartMonth = (currentQuarter - 1) * 3 + 1;
        
        LocalDateTime startOfQuarter = now.withMonth(quarterStartMonth).withDayOfMonth(1)
            .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfQuarter = startOfQuarter.plusMonths(3).minusNanos(1);
        
        return ComplianceReportPeriod.builder()
            .startDate(startOfQuarter)
            .endDate(endOfQuarter)
            .periodType(PeriodType.QUARTERLY)
            .periodName("Q" + currentQuarter + " " + now.getYear())
            .description("Current quarter")
            .build();
    }

    /**
     * Create current year period
     */
    public static ComplianceReportPeriod currentYear() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfYear = now.withMonth(1).withDayOfMonth(1)
            .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfYear = startOfYear.plusYears(1).minusNanos(1);
        
        return ComplianceReportPeriod.builder()
            .startDate(startOfYear)
            .endDate(endOfYear)
            .periodType(PeriodType.ANNUALLY)
            .periodName("Year " + now.getYear())
            .description("Current calendar year")
            .build();
    }

    /**
     * Create rolling 30-day period
     */
    public static ComplianceReportPeriod rolling30Days() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        
        return ComplianceReportPeriod.builder()
            .startDate(start)
            .endDate(end)
            .periodType(PeriodType.ROLLING_30_DAYS)
            .periodName("Last 30 Days")
            .description("Rolling 30-day period")
            .build();
    }

    /**
     * Create rolling 90-day period
     */
    public static ComplianceReportPeriod rolling90Days() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusDays(90).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        
        return ComplianceReportPeriod.builder()
            .startDate(start)
            .endDate(end)
            .periodType(PeriodType.ROLLING_90_DAYS)
            .periodName("Last 90 Days")
            .description("Rolling 90-day period")
            .build();
    }

    /**
     * Create custom period
     */
    public static ComplianceReportPeriod custom(LocalDateTime start, LocalDateTime end) {
        return ComplianceReportPeriod.builder()
            .startDate(start)
            .endDate(end)
            .periodType(PeriodType.CUSTOM)
            .periodName("Custom Period")
            .description("Custom date range")
            .build();
    }

    /**
     * Create custom period with name
     */
    public static ComplianceReportPeriod custom(LocalDateTime start, LocalDateTime end, String name) {
        return ComplianceReportPeriod.builder()
            .startDate(start)
            .endDate(end)
            .periodType(PeriodType.CUSTOM)
            .periodName(name)
            .description("Custom date range: " + name)
            .build();
    }

    /**
     * Create incident-based period
     */
    public static ComplianceReportPeriod incidentPeriod(LocalDateTime incidentStart, LocalDateTime incidentEnd) {
        return ComplianceReportPeriod.builder()
            .startDate(incidentStart)
            .endDate(incidentEnd)
            .periodType(PeriodType.INCIDENT_BASED)
            .periodName("Incident Period")
            .description("Period based on specific incident timeline")
            .build();
    }
}