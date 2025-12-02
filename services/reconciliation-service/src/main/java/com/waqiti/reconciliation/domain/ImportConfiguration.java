package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ImportConfiguration {
    private final boolean firstRowAsHeader;
    private final Integer sheetIndex;
    private final String delimiter;
    private final String dateFormat;
    private final Integer maxRecords;
    private final List<String> requiredFields;
    private final Map<String, String> fieldTypes;
    private final Map<String, String> fieldMappings;
    private final Map<String, String> transformations;
    private final boolean skipEmptyRows;
    private final boolean trimValues;
    private final boolean validateData;
    private final String encoding;
    private final Integer skipRows;
    private final List<String> allowedValues;
    private final Map<String, Object> defaultValues;
    private final Double maxFileSize;
    
    // Default configuration
    public static ImportConfigurationBuilder defaultBuilder() {
        return ImportConfiguration.builder()
            .firstRowAsHeader(true)
            .sheetIndex(0)
            .delimiter(",")
            .dateFormat("yyyy-MM-dd")
            .maxRecords(Integer.MAX_VALUE)
            .skipEmptyRows(true)
            .trimValues(true)
            .validateData(true)
            .encoding("UTF-8")
            .skipRows(0)
            .maxFileSize(50.0 * 1024 * 1024); // 50MB
    }
    
    public boolean isValid() {
        return maxRecords != null && maxRecords > 0 
            && maxFileSize != null && maxFileSize > 0;
    }
    
    public boolean hasFieldMappings() {
        return fieldMappings != null && !fieldMappings.isEmpty();
    }
    
    public boolean hasTransformations() {
        return transformations != null && !transformations.isEmpty();
    }
    
    public boolean hasValidation() {
        return validateData && 
            ((requiredFields != null && !requiredFields.isEmpty()) ||
             (fieldTypes != null && !fieldTypes.isEmpty()) ||
             (allowedValues != null && !allowedValues.isEmpty()));
    }
    
    public String getEffectiveDelimiter() {
        return delimiter != null ? delimiter : ",";
    }
    
    public String getEffectiveEncoding() {
        return encoding != null ? encoding : "UTF-8";
    }
}