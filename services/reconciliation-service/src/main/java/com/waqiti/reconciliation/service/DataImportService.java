package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.domain.ImportConfiguration;
import com.waqiti.reconciliation.domain.ImportResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface DataImportService {
    
    /**
     * Import data from file
     */
    ImportResult importFile(MultipartFile file, ImportConfiguration config);
    
    /**
     * Import data from input stream
     */
    ImportResult importFromStream(InputStream inputStream, String filename, ImportConfiguration config);
    
    /**
     * Import data asynchronously
     */
    CompletableFuture<ImportResult> importFileAsync(MultipartFile file, ImportConfiguration config);
    
    /**
     * Validate import file without processing
     */
    ImportResult validateFile(MultipartFile file, ImportConfiguration config);
    
    /**
     * Get supported import formats
     */
    List<String> getSupportedFormats();
    
    /**
     * Preview file data (first N rows)
     */
    List<Map<String, Object>> previewFile(MultipartFile file, ImportConfiguration config, int maxRows);
    
    /**
     * Auto-detect file format and configuration
     */
    ImportConfiguration detectConfiguration(MultipartFile file);
}