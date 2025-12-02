package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReportRequest {

    @NotNull(message = "Report type is required")
    private ReportType reportType;
    
    @NotNull(message = "From date is required")
    private LocalDate fromDate;
    
    @NotNull(message = "To date is required")
    private LocalDate toDate;
    
    private String generatedBy;
    
    private List<String> includeAccounts;
    
    private List<String> excludeAccounts;
    
    private List<String> currencies;
    
    @Builder.Default
    private boolean includeDetails = true;
    
    @Builder.Default
    private boolean includeSummary = true;
    
    @Builder.Default
    private boolean includeCharts = false;
    
    private ReportFormat format;
    
    private Map<String, Object> additionalFilters;
    
    private String emailRecipients;
    
    @Builder.Default
    private boolean scheduleReport = false;
    
    private String scheduleFrequency;

    public enum ReportType {
        DAILY_RECONCILIATION_SUMMARY("Daily Reconciliation Summary"),
        BREAK_ANALYSIS_REPORT("Break Analysis Report"),
        NOSTRO_RECONCILIATION_REPORT("Nostro Reconciliation Report"),
        VARIANCE_TREND_ANALYSIS("Variance Trend Analysis"),
        SETTLEMENT_RECONCILIATION_REPORT("Settlement Reconciliation Report"),
        EXCEPTION_REPORT("Exception Report"),
        REGULATORY_RECONCILIATION_REPORT("Regulatory Reconciliation Report"),
        MANAGEMENT_DASHBOARD("Management Dashboard"),
        AUDIT_TRAIL_REPORT("Audit Trail Report"),
        PERFORMANCE_METRICS_REPORT("Performance Metrics Report");

        private final String description;

        ReportType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum ReportFormat {
        PDF("PDF Document"),
        EXCEL("Excel Spreadsheet"),
        CSV("CSV File"),
        HTML("HTML Report"),
        JSON("JSON Data"),
        XML("XML Data");

        private final String description;

        ReportFormat(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public boolean isDateRangeValid() {
        return fromDate != null && toDate != null && !fromDate.isAfter(toDate);
    }

    public long getReportingDays() {
        if (!isDateRangeValid()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    }

    public boolean isScheduled() {
        return scheduleReport && scheduleFrequency != null;
    }

    public boolean hasFilters() {
        return (includeAccounts != null && !includeAccounts.isEmpty()) ||
               (excludeAccounts != null && !excludeAccounts.isEmpty()) ||
               (currencies != null && !currencies.isEmpty()) ||
               (additionalFilters != null && !additionalFilters.isEmpty());
    }
}