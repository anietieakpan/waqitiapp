package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.ExportConfiguration;
import com.waqiti.reconciliation.domain.ExportResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DataExportService {
    
    /**
     * Export data to specified format
     */
    ExportResult exportData(List<Map<String, Object>> data, ExportConfiguration config);
    
    /**
     * Export data asynchronously
     */
    CompletableFuture<ExportResult> exportDataAsync(List<Map<String, Object>> data, ExportConfiguration config);
    
    /**
     * Export large datasets in batches
     */
    ExportResult exportDataInBatches(List<Map<String, Object>> data, ExportConfiguration config);
    
    /**
     * Get supported export formats
     */
    List<String> getSupportedFormats();
    
    /**
     * Validate export configuration
     */
    boolean validateConfiguration(ExportConfiguration config);
    
    /**
     * Get export template for format
     */
    byte[] getExportTemplate(String format, List<String> headers);
}