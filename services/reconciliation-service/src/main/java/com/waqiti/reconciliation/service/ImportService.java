package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.security.SecureFileUploadValidator;
import com.waqiti.reconciliation.security.SecureFileUploadValidator.FileValidationResult;
import com.waqiti.reconciliation.security.SecureFileUploadValidator.FileUploadContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for importing reconciliation data from various file formats
 * Enhanced with comprehensive security validation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {
    
    private static final String[] SUPPORTED_FORMATS = {"csv", "xlsx", "xls", "json", "xml", "txt"};
    private static final int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int BATCH_SIZE = 1000;
    
    private final SecureFileUploadValidator fileUploadValidator;
    
    /**
     * Import data from file with comprehensive security validation
     */
    public ImportResult importFile(MultipartFile file, ImportConfiguration config) {
        log.info("Importing file: {} size: {} bytes", file.getOriginalFilename(), file.getSize());
        
        ImportResult result = new ImportResult();
        result.setFilename(file.getOriginalFilename());
        result.setStartTime(LocalDateTime.now());
        
        try {
            // Get current user for audit
            String userId = getCurrentUserId();
            
            // Perform comprehensive security validation
            FileUploadContext uploadContext = new FileUploadContext();
            uploadContext.setPurpose("reconciliation_import");
            uploadContext.setCategory(config.getImportType() != null ? config.getImportType() : "general");
            
            FileValidationResult validationResult = fileUploadValidator.validateFileUpload(
                    file, userId, uploadContext);
            
            if (!validationResult.isValid()) {
                throw new SecurityException("File validation failed: " + validationResult.getError());
            }
            
            // Store validation metadata
            result.setUploadId(validationResult.getUploadId());
            result.setFileHash(validationResult.getFileHash());
            
            // Additional application-specific validation
            validateFile(file);
            
            // Determine file type
            String fileType = determineFileType(file.getOriginalFilename());
            result.setFileType(fileType);
            
            // Import based on file type
            List<Map<String, Object>> data;
            switch (fileType.toLowerCase()) {
                case "csv":
                    data = importCsv(file, config);
                    break;
                case "xlsx":
                case "xls":
                    data = importExcel(file, config);
                    break;
                case "json":
                    data = importJson(file, config);
                    break;
                case "xml":
                    data = importXml(file, config);
                    break;
                case "txt":
                    data = importText(file, config);
                    break;
                default:
                    log.warn("Unknown file type '{}', attempting to import as CSV format", fileType);
                    data = importCsv(file, config);
            }
            
            // Process imported data
            ProcessedData processed = processImportedData(data, config);
            
            result.setTotalRecords(processed.getTotalRecords());
            result.setSuccessfulRecords(processed.getSuccessfulRecords());
            result.setFailedRecords(processed.getFailedRecords());
            result.setErrors(processed.getErrors());
            result.setData(processed.getData());
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(true);
            
            log.info("Import completed: {} records imported, {} failed", 
                    result.getSuccessfulRecords(), result.getFailedRecords());
            
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Import CSV file
     */
    private List<Map<String, Object>> importCsv(MultipartFile file, ImportConfiguration config) throws IOException {
        log.debug("Importing CSV file");
        
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreEmptyLines()
                     .withTrim()
                     .parse(reader)) {
            
            List<String> headers = parser.getHeaderNames();
            
            for (CSVRecord record : parser) {
                Map<String, Object> row = new HashMap<>();
                
                for (String header : headers) {
                    String value = record.get(header);
                    row.put(header, parseValue(value, config));
                }
                
                data.add(row);
                
                if (data.size() >= config.getMaxRecords()) {
                    break;
                }
            }
        }
        
        return data;
    }
    
    /**
     * Import Excel file
     */
    private List<Map<String, Object>> importExcel(MultipartFile file, ImportConfiguration config) throws IOException {
        log.debug("Importing Excel file");
        
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(config.getSheetIndex());
            
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found at index: " + config.getSheetIndex());
            }
            
            Iterator<Row> rowIterator = sheet.iterator();
            
            // Get headers from first row
            List<String> headers = new ArrayList<>();
            if (rowIterator.hasNext() && config.isFirstRowAsHeader()) {
                Row headerRow = rowIterator.next();
                for (Cell cell : headerRow) {
                    headers.add(getCellValue(cell).toString());
                }
            }
            
            // Process data rows
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> rowData = new HashMap<>();
                
                for (int i = 0; i < headers.size() && i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    Object value = getCellValue(cell);
                    rowData.put(headers.get(i), value);
                }
                
                data.add(rowData);
                
                if (data.size() >= config.getMaxRecords()) {
                    break;
                }
            }
        }
        
        return data;
    }
    
    /**
     * Import JSON file
     */
    private List<Map<String, Object>> importJson(MultipartFile file, ImportConfiguration config) throws IOException {
        log.debug("Importing JSON file");
        
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            // Parse JSON (simplified - in production use Jackson or Gson)
            String jsonStr = json.toString().trim();
            if (jsonStr.startsWith("[")) {
                // Array of objects
                jsonStr = jsonStr.substring(1, jsonStr.length() - 1);
                String[] objects = jsonStr.split("},\\s*\\{");
                
                for (String obj : objects) {
                    Map<String, Object> map = parseJsonObject(obj);
                    data.add(map);
                    
                    if (data.size() >= config.getMaxRecords()) {
                        break;
                    }
                }
            } else if (jsonStr.startsWith("{")) {
                // Single object
                Map<String, Object> map = parseJsonObject(jsonStr);
                data.add(map);
            }
        }
        
        return data;
    }
    
    /**
     * Import XML file
     */
    private List<Map<String, Object>> importXml(MultipartFile file, ImportConfiguration config) throws IOException {
        log.debug("Importing XML file");
        
        // Simplified XML parsing - in production use proper XML parser
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            StringBuilder xml = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                xml.append(line);
            }
            
            // Simple XML to Map conversion (placeholder)
            Map<String, Object> map = new HashMap<>();
            map.put("xml_content", xml.toString());
            data.add(map);
        }
        
        return data;
    }
    
    /**
     * Import text file
     */
    private List<Map<String, Object>> importText(MultipartFile file, ImportConfiguration config) throws IOException {
        log.debug("Importing text file");
        
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            
            String delimiter = config.getDelimiter() != null ? config.getDelimiter() : "\\t";
            String line;
            List<String> headers = null;
            
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(delimiter);
                
                if (headers == null && config.isFirstRowAsHeader()) {
                    headers = Arrays.asList(values);
                    continue;
                }
                
                Map<String, Object> row = new HashMap<>();
                
                if (headers != null) {
                    for (int i = 0; i < Math.min(headers.size(), values.length); i++) {
                        row.put(headers.get(i), parseValue(values[i], config));
                    }
                } else {
                    for (int i = 0; i < values.length; i++) {
                        row.put("column_" + i, parseValue(values[i], config));
                    }
                }
                
                data.add(row);
                
                if (data.size() >= config.getMaxRecords()) {
                    break;
                }
            }
        }
        
        return data;
    }
    
    /**
     * Process imported data
     */
    private ProcessedData processImportedData(List<Map<String, Object>> data, ImportConfiguration config) {
        ProcessedData processed = new ProcessedData();
        processed.setTotalRecords(data.size());
        
        List<Map<String, Object>> validData = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> record = data.get(i);
            
            try {
                // Validate record
                validateRecord(record, config);
                
                // Transform record
                Map<String, Object> transformed = transformRecord(record, config);
                
                // Add to valid data
                validData.add(transformed);
                processed.incrementSuccessful();
                
            } catch (Exception e) {
                errors.add(new ImportError(i + 1, e.getMessage(), record));
                processed.incrementFailed();
            }
        }
        
        processed.setData(validData);
        processed.setErrors(errors);
        
        return processed;
    }
    
    /**
     * Validate file
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of " + MAX_FILE_SIZE + " bytes");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }
        
        String fileType = determineFileType(filename);
        if (!isSupportedFormat(fileType)) {
            throw new IllegalArgumentException("Unsupported file format: " + fileType);
        }
    }
    
    /**
     * Validate record
     */
    private void validateRecord(Map<String, Object> record, ImportConfiguration config) {
        if (record == null || record.isEmpty()) {
            throw new IllegalArgumentException("Empty record");
        }
        
        // Validate required fields
        if (config.getRequiredFields() != null) {
            for (String field : config.getRequiredFields()) {
                if (!record.containsKey(field) || record.get(field) == null) {
                    throw new IllegalArgumentException("Missing required field: " + field);
                }
            }
        }
        
        // Validate field types
        if (config.getFieldTypes() != null) {
            for (Map.Entry<String, String> entry : config.getFieldTypes().entrySet()) {
                String field = entry.getKey();
                String expectedType = entry.getValue();
                Object value = record.get(field);
                
                if (value != null && !isValidType(value, expectedType)) {
                    throw new IllegalArgumentException(
                        String.format("Invalid type for field %s: expected %s, got %s", 
                                    field, expectedType, value.getClass().getSimpleName()));
                }
            }
        }
    }
    
    /**
     * Transform record
     */
    private Map<String, Object> transformRecord(Map<String, Object> record, ImportConfiguration config) {
        Map<String, Object> transformed = new HashMap<>(record);
        
        // Apply field mappings
        if (config.getFieldMappings() != null) {
            Map<String, Object> mapped = new HashMap<>();
            
            for (Map.Entry<String, String> mapping : config.getFieldMappings().entrySet()) {
                String sourceField = mapping.getKey();
                String targetField = mapping.getValue();
                
                if (transformed.containsKey(sourceField)) {
                    mapped.put(targetField, transformed.get(sourceField));
                }
            }
            
            transformed = mapped;
        }
        
        // Apply transformations
        if (config.getTransformations() != null) {
            for (Map.Entry<String, String> transformation : config.getTransformations().entrySet()) {
                String field = transformation.getKey();
                String transformType = transformation.getValue();
                
                if (transformed.containsKey(field)) {
                    Object value = transformed.get(field);
                    transformed.put(field, applyTransformation(value, transformType));
                }
            }
        }
        
        return transformed;
    }
    
    // Helper methods
    
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }
    
    private String determineFileType(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }
    
    private boolean isSupportedFormat(String format) {
        return Arrays.asList(SUPPORTED_FORMATS).contains(format.toLowerCase());
    }
    
    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    private Object parseValue(String value, ImportConfiguration config) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        value = value.trim();
        
        // Try to parse as number
        try {
            if (value.contains(".")) {
                return new BigDecimal(value);
            } else {
                return Long.parseLong(value);
            }
        } catch (NumberFormatException e) {
            // Not a number
        }
        
        // Try to parse as boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        
        // Try to parse as date
        if (config.getDateFormat() != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(config.getDateFormat());
                return LocalDate.parse(value, formatter);
            } catch (Exception e) {
                // Not a date
            }
        }
        
        // Return as string
        return value;
    }
    
    private Map<String, Object> parseJsonObject(String json) {
        // Simplified JSON parsing - in production use proper JSON library
        Map<String, Object> map = new HashMap<>();
        
        json = json.replaceAll("[{}]", "").trim();
        String[] pairs = json.split(",");
        
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].replaceAll("\"", "").trim();
                String value = keyValue[1].replaceAll("\"", "").trim();
                map.put(key, value);
            }
        }
        
        return map;
    }
    
    private boolean isValidType(Object value, String expectedType) {
        switch (expectedType.toLowerCase()) {
            case "string":
                return value instanceof String;
            case "number":
            case "numeric":
                return value instanceof Number;
            case "boolean":
                return value instanceof Boolean;
            case "date":
                return value instanceof Date || value instanceof LocalDate || value instanceof LocalDateTime;
            default:
                return true;
        }
    }
    
    private Object applyTransformation(Object value, String transformType) {
        if (value == null) {
            return null;
        }
        
        switch (transformType.toLowerCase()) {
            case "uppercase":
                return value.toString().toUpperCase();
            case "lowercase":
                return value.toString().toLowerCase();
            case "trim":
                return value.toString().trim();
            case "remove_spaces":
                return value.toString().replaceAll("\\s+", "");
            default:
                return value;
        }
    }
    
    // Inner classes
    
    public static class ImportConfiguration {
        private boolean firstRowAsHeader = true;
        private int sheetIndex = 0;
        private String delimiter;
        private String dateFormat;
        private int maxRecords = Integer.MAX_VALUE;
        private List<String> requiredFields;
        private Map<String, String> fieldTypes;
        private Map<String, String> fieldMappings;
        private Map<String, String> transformations;
        private String importType;
        
        // Getters and setters
        public boolean isFirstRowAsHeader() { return firstRowAsHeader; }
        public void setFirstRowAsHeader(boolean firstRowAsHeader) { this.firstRowAsHeader = firstRowAsHeader; }
        public int getSheetIndex() { return sheetIndex; }
        public void setSheetIndex(int sheetIndex) { this.sheetIndex = sheetIndex; }
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        public String getDateFormat() { return dateFormat; }
        public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
        public int getMaxRecords() { return maxRecords; }
        public void setMaxRecords(int maxRecords) { this.maxRecords = maxRecords; }
        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> requiredFields) { this.requiredFields = requiredFields; }
        public Map<String, String> getFieldTypes() { return fieldTypes; }
        public void setFieldTypes(Map<String, String> fieldTypes) { this.fieldTypes = fieldTypes; }
        public Map<String, String> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }
        public Map<String, String> getTransformations() { return transformations; }
        public void setTransformations(Map<String, String> transformations) { this.transformations = transformations; }
        public String getImportType() { return importType; }
        public void setImportType(String importType) { this.importType = importType; }
    }
    
    public static class ImportResult {
        private String filename;
        private String fileType;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalRecords;
        private int successfulRecords;
        private int failedRecords;
        private boolean success;
        private String errorMessage;
        private List<Map<String, Object>> data;
        private List<ImportError> errors;
        private String uploadId;
        private String fileHash;
        
        // Getters and setters
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public int getSuccessfulRecords() { return successfulRecords; }
        public void setSuccessfulRecords(int successfulRecords) { this.successfulRecords = successfulRecords; }
        public int getFailedRecords() { return failedRecords; }
        public void setFailedRecords(int failedRecords) { this.failedRecords = failedRecords; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }
        public List<ImportError> getErrors() { return errors; }
        public void setErrors(List<ImportError> errors) { this.errors = errors; }
        public String getUploadId() { return uploadId; }
        public void setUploadId(String uploadId) { this.uploadId = uploadId; }
        public String getFileHash() { return fileHash; }
        public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    }
    
    private static class ProcessedData {
        private int totalRecords;
        private int successfulRecords;
        private int failedRecords;
        private List<Map<String, Object>> data;
        private List<ImportError> errors;
        
        public void incrementSuccessful() { successfulRecords++; }
        public void incrementFailed() { failedRecords++; }
        
        // Getters and setters
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public int getSuccessfulRecords() { return successfulRecords; }
        public void setSuccessfulRecords(int successfulRecords) { this.successfulRecords = successfulRecords; }
        public int getFailedRecords() { return failedRecords; }
        public void setFailedRecords(int failedRecords) { this.failedRecords = failedRecords; }
        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }
        public List<ImportError> getErrors() { return errors; }
        public void setErrors(List<ImportError> errors) { this.errors = errors; }
    }
    
    public static class ImportError {
        private int rowNumber;
        private String error;
        private Map<String, Object> record;
        
        public ImportError(int rowNumber, String error, Map<String, Object> record) {
            this.rowNumber = rowNumber;
            this.error = error;
            this.record = record;
        }
        
        // Getters
        public int getRowNumber() { return rowNumber; }
        public String getError() { return error; }
        public Map<String, Object> getRecord() { return record; }
    }
}