package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReportData {

    private ReconciliationReportRequest.ReportType reportType;
    
    private LocalDate fromDate;
    
    private LocalDate toDate;
    
    private LocalDateTime generatedAt;
    
    private DailyReconciliationSummaryData summaryData;
    
    private BreakAnalysisReportData breakAnalysisData;
    
    private NostroReconciliationReportData nostroReconciliationData;
    
    private VarianceTrendAnalysisData varianceTrendData;
    
    private Map<String, Object> customData;
    
    private ReportStatistics statistics;
    
    private List<ReportChart> charts;
    
    private List<ReportTable> tables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportStatistics {
        private int totalReconciliations;
        private int successfulReconciliations;
        private int failedReconciliations;
        private double successRate;
        private BigDecimal totalVariance;
        private int totalBreaks;
        private int resolvedBreaks;
        private double breakResolutionRate;
        private Long averageProcessingTimeMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportChart {
        private String chartType;
        private String title;
        private String xAxisLabel;
        private String yAxisLabel;
        private List<ChartDataPoint> dataPoints;
        private Map<String, Object> chartOptions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartDataPoint {
        private String label;
        private Number value;
        private String category;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportTable {
        private String tableName;
        private List<String> headers;
        private List<List<Object>> rows;
        private Map<String, String> columnFormats;
        private String summary;
    }

    public boolean hasData() {
        return summaryData != null || 
               breakAnalysisData != null || 
               nostroReconciliationData != null || 
               varianceTrendData != null ||
               (customData != null && !customData.isEmpty());
    }

    public boolean hasCharts() {
        return charts != null && !charts.isEmpty();
    }

    public boolean hasTables() {
        return tables != null && !tables.isEmpty();
    }

    public boolean hasStatistics() {
        return statistics != null;
    }
}