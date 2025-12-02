package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ExportConfiguration implements Cloneable {
    private final String format;
    private final String fileName;
    private final String sheetName;
    private final String delimiter;
    private final List<String> headers;
    private final boolean includeHeaders;
    private final boolean autoSizeColumns;
    private final boolean applyFilters;
    private final boolean freezePanes;
    private final boolean prettyPrint;
    private final String rootElement;
    private final String recordElement;
    private final String title;
    private final Map<String, String> fieldMappings;
    private final Map<String, String> formatters;
    private final Integer maxRowsPerSheet;
    private final Integer batchSize;
    private final String dateTimeFormat;
    private final String currencyFormat;
    private final String compressionLevel;
    
    // Default values
    public static ExportConfigurationBuilder defaultBuilder() {
        return ExportConfiguration.builder()
            .format("csv")
            .sheetName("Data")
            .delimiter(",")
            .includeHeaders(true)
            .autoSizeColumns(true)
            .applyFilters(true)
            .freezePanes(true)
            .prettyPrint(false)
            .rootElement("data")
            .recordElement("record")
            .title("Export Report")
            .maxRowsPerSheet(1000000)
            .batchSize(10000)
            .dateTimeFormat("yyyy-MM-dd HH:mm:ss")
            .currencyFormat("$#,##0.00")
            .compressionLevel("NORMAL");
    }
    
    @Override
    public ExportConfiguration clone() {
        try {
            return (ExportConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Clone not supported", e);
        }
    }
    
    public boolean isValid() {
        return format != null && !format.trim().isEmpty();
    }
    
    public String getFileExtension() {
        return switch (format.toLowerCase()) {
            case "xlsx", "excel" -> "xlsx";
            case "csv" -> "csv";
            case "json" -> "json";
            case "xml" -> "xml";
            case "pdf" -> "pdf";
            case "txt", "text" -> "txt";
            case "zip" -> "zip";
            default -> format.toLowerCase();
        };
    }
}