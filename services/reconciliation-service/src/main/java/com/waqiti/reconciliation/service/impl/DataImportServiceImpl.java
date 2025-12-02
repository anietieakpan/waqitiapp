package com.waqiti.reconciliation.service.impl;

import com.waqiti.reconciliation.domain.ImportConfiguration;
import com.waqiti.reconciliation.domain.ImportError;
import com.waqiti.reconciliation.domain.ImportResult;
import com.waqiti.reconciliation.service.DataImportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DataImportServiceImpl implements DataImportService {

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    
    // Metrics
    private Counter importCounter;
    private Counter importErrorCounter;
    private Timer importTimer;
    
    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // Constants
    private static final List<String> SUPPORTED_FORMATS = 
        Arrays.asList("csv", "xlsx", "xls", "json", "xml", "txt", "tsv");
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024; // 50MB default
    private static final int DEFAULT_PREVIEW_ROWS = 10;
    
    @PostConstruct
    public void initialize() {
        importCounter = Counter.builder("reconciliation.import.requests")
            .description("Number of import requests")
            .register(meterRegistry);
            
        importErrorCounter = Counter.builder("reconciliation.import.errors")
            .description("Number of import errors")
            .register(meterRegistry);
            
        importTimer = Timer.builder("reconciliation.import.time")
            .description("Import processing time")
            .register(meterRegistry);
            
        log.info("DataImportServiceImpl initialized");
    }

    @Override
    @CircuitBreaker(name = "import-service", fallbackMethod = "importFileFallback")
    @Retry(name = "import-service")
    public ImportResult importFile(MultipartFile file, ImportConfiguration config) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Importing file: {} size: {} bytes", file.getOriginalFilename(), file.getSize());
        
        try {
            importCounter.increment();
            
            // Validate file
            validateFile(file, config);
            
            String importId = UUID.randomUUID().toString();
            LocalDateTime startTime = LocalDateTime.now();
            
            // Detect file type
            String fileType = detectFileType(file.getOriginalFilename());
            
            // Import data based on file type
            List<Map<String, Object>> rawData = importByType(file, fileType, config);
            
            // Process and validate imported data
            ProcessedData processedData = processData(rawData, config);
            
            // Build result
            ImportResult result = ImportResult.builder()
                .importId(importId)
                .filename(file.getOriginalFilename())
                .fileType(fileType)
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .totalRecords(processedData.getTotalRecords())
                .successfulRecords(processedData.getSuccessfulRecords())
                .failedRecords(processedData.getFailedRecords())
                .success(processedData.getFailedRecords() == 0)
                .data(processedData.getData())
                .errors(processedData.getErrors())
                .warnings(processedData.getWarnings())
                .fileSizeBytes(file.getSize())
                .metadata(extractMetadata(file, config))
                .build();
            
            sample.stop(importTimer);
            log.info("Import completed: {} successful, {} failed records", 
                result.getSuccessfulRecords(), result.getFailedRecords());
                
            return result;
            
        } catch (Exception e) {
            sample.stop(importTimer);
            importErrorCounter.increment();
            log.error("Import failed: {}", e.getMessage(), e);
            
            return ImportResult.builder()
                .filename(file.getOriginalFilename())
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .success(false)
                .errorMessage(e.getMessage())
                .totalRecords(0)
                .successfulRecords(0)
                .failedRecords(0)
                .build();
        }
    }

    @Override
    public ImportResult importFromStream(InputStream inputStream, String filename, 
                                        ImportConfiguration config) {
        try {
            // Create temporary file from stream
            File tempFile = File.createTempFile("import_", "_" + filename);
            tempFile.deleteOnExit();
            
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                inputStream.transferTo(fos);
            }
            
            // Create MultipartFile wrapper
            MultipartFile multipartFile = new StreamMultipartFile(tempFile, filename);
            
            return importFile(multipartFile, config);
            
        } catch (Exception e) {
            log.error("Failed to import from stream", e);
            return ImportResult.builder()
                .filename(filename)
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    @Override
    public CompletableFuture<ImportResult> importFileAsync(MultipartFile file, 
                                                          ImportConfiguration config) {
        return CompletableFuture.supplyAsync(() -> importFile(file, config), executorService);
    }

    @Override
    public ImportResult validateFile(MultipartFile file, ImportConfiguration config) {
        try {
            // Basic file validation
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("File is empty");
            }
            
            // Check file size
            double maxSize = config.getMaxFileSize() != null ? 
                config.getMaxFileSize() : MAX_FILE_SIZE_BYTES;
            if (file.getSize() > maxSize) {
                throw new IllegalArgumentException(
                    String.format("File size %.2f MB exceeds maximum allowed size of %.2f MB", 
                        file.getSize() / (1024.0 * 1024.0), 
                        maxSize / (1024.0 * 1024.0)));
            }
            
            // Check file type
            String fileType = detectFileType(file.getOriginalFilename());
            if (!SUPPORTED_FORMATS.contains(fileType)) {
                throw new IllegalArgumentException("Unsupported file format: " + fileType);
            }
            
            // Try to parse first few rows
            List<Map<String, Object>> preview = previewFile(file, config, 5);
            
            return ImportResult.builder()
                .filename(file.getOriginalFilename())
                .fileType(fileType)
                .success(true)
                .totalRecords(preview.size())
                .successfulRecords(preview.size())
                .failedRecords(0)
                .build();
                
        } catch (Exception e) {
            return ImportResult.builder()
                .filename(file.getOriginalFilename())
                .success(false)
                .errorMessage("Validation failed: " + e.getMessage())
                .build();
        }
    }

    @Override
    public List<String> getSupportedFormats() {
        return new ArrayList<>(SUPPORTED_FORMATS);
    }

    @Override
    public List<Map<String, Object>> previewFile(MultipartFile file, ImportConfiguration config, 
                                                int maxRows) {
        try {
            String fileType = detectFileType(file.getOriginalFilename());
            
            // Create preview configuration
            ImportConfiguration previewConfig = ImportConfiguration.builder()
                .firstRowAsHeader(config.isFirstRowAsHeader())
                .delimiter(config.getDelimiter())
                .maxRecords(maxRows)
                .skipEmptyRows(config.isSkipEmptyRows())
                .trimValues(config.isTrimValues())
                .encoding(config.getEncoding())
                .build();
            
            return importByType(file, fileType, previewConfig);
            
        } catch (Exception e) {
            log.error("Failed to preview file", e);
            return Collections.emptyList();
        }
    }

    @Override
    public ImportConfiguration detectConfiguration(MultipartFile file) {
        try {
            String fileType = detectFileType(file.getOriginalFilename());
            ImportConfiguration.ImportConfigurationBuilder builder = 
                ImportConfiguration.defaultBuilder();
            
            // Auto-detect based on file type
            switch (fileType) {
                case "csv":
                    String delimiter = detectCsvDelimiter(file);
                    builder.delimiter(delimiter);
                    break;
                    
                case "tsv":
                    builder.delimiter("\t");
                    break;
                    
                case "xlsx":
                case "xls":
                    builder.sheetIndex(0);
                    break;
            }
            
            // Detect if first row contains headers
            boolean hasHeaders = detectHeaders(file, fileType);
            builder.firstRowAsHeader(hasHeaders);
            
            // Detect encoding
            String encoding = detectEncoding(file);
            builder.encoding(encoding);
            
            return builder.build();
            
        } catch (Exception e) {
            log.error("Failed to detect configuration", e);
            return ImportConfiguration.defaultBuilder().build();
        }
    }

    private List<Map<String, Object>> importByType(MultipartFile file, String fileType, 
                                                  ImportConfiguration config) throws Exception {
        return switch (fileType.toLowerCase()) {
            case "csv" -> importCsv(file, config);
            case "tsv" -> importTsv(file, config);
            case "xlsx", "xls" -> importExcel(file, config);
            case "json" -> importJson(file, config);
            case "xml" -> importXml(file, config);
            case "txt" -> importText(file, config);
            default -> {
                log.warn("Unknown file type '{}', attempting to import as CSV format", fileType);
                yield importCsv(file, config);
            }
        };
    }

    private List<Map<String, Object>> importCsv(MultipartFile file, 
                                               ImportConfiguration config) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        
        Charset charset = Charset.forName(config.getEffectiveEncoding());
        
        try (Reader reader = new InputStreamReader(file.getInputStream(), charset);
             CSVParser parser = CSVFormat.DEFAULT
                     .withFirstRecordAsHeader()
                     .withIgnoreEmptyLines()
                     .withTrim()
                     .withDelimiter(config.getEffectiveDelimiter().charAt(0))
                     .parse(reader)) {
            
            List<String> headers = parser.getHeaderNames();
            int recordCount = 0;
            
            for (CSVRecord record : parser) {
                if (config.isSkipEmptyRows() && isEmptyRecord(record)) {
                    continue;
                }
                
                Map<String, Object> row = new HashMap<>();
                for (String header : headers) {
                    String value = record.get(header);
                    row.put(header, parseValue(value, config));
                }
                
                data.add(row);
                recordCount++;
                
                if (config.getMaxRecords() != null && recordCount >= config.getMaxRecords()) {
                    break;
                }
            }
        }
        
        return data;
    }

    private List<Map<String, Object>> importTsv(MultipartFile file, 
                                               ImportConfiguration config) throws IOException {
        ImportConfiguration tsvConfig = ImportConfiguration.builder()
            .delimiter("\t")
            .firstRowAsHeader(config.isFirstRowAsHeader())
            .maxRecords(config.getMaxRecords())
            .skipEmptyRows(config.isSkipEmptyRows())
            .trimValues(config.isTrimValues())
            .encoding(config.getEncoding())
            .build();
            
        return importCsv(file, tsvConfig);
    }

    private List<Map<String, Object>> importExcel(MultipartFile file, 
                                                 ImportConfiguration config) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        
        try (InputStream is = file.getInputStream()) {
            Workbook workbook = null;
            try {
                if (file.getOriginalFilename().endsWith(".xlsx")) {
                    workbook = new XSSFWorkbook(is);
                } else {
                    workbook = new HSSFWorkbook(is);
                }
                
                Sheet sheet = workbook.getSheetAt(
                    config.getSheetIndex() != null ? config.getSheetIndex() : 0);
                
                Iterator<Row> rowIterator = sheet.iterator();
                
                // Skip rows if configured
                if (config.getSkipRows() != null) {
                    for (int i = 0; i < config.getSkipRows() && rowIterator.hasNext(); i++) {
                        rowIterator.next();
                    }
                }
                
                // Get headers
                List<String> headers = new ArrayList<>();
                if (rowIterator.hasNext() && config.isFirstRowAsHeader()) {
                    Row headerRow = rowIterator.next();
                    for (Cell cell : headerRow) {
                        headers.add(getCellValueAsString(cell));
                    }
                }
                
                // Process data rows
                int recordCount = 0;
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    
                    if (config.isSkipEmptyRows() && isEmptyRow(row)) {
                        continue;
                    }
                    
                    Map<String, Object> rowData = new HashMap<>();
                    
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i);
                        String header = headers.size() > i ? headers.get(i) : "column_" + i;
                        Object value = getCellValue(cell);
                        rowData.put(header, value);
                    }
                    
                    data.add(rowData);
                    recordCount++;
                    
                    if (config.getMaxRecords() != null && recordCount >= config.getMaxRecords()) {
                        break;
                    }
                }
            } finally {
                if (workbook != null) {
                    workbook.close();
                }
            }
        }
        
        return data;
    }

    private List<Map<String, Object>> importJson(MultipartFile file, 
                                                ImportConfiguration config) throws IOException {
        try (InputStream is = file.getInputStream()) {
            List<Map<String, Object>> data = objectMapper.readValue(
                is, new TypeReference<List<Map<String, Object>>>() {});
            
            if (config.getMaxRecords() != null && data.size() > config.getMaxRecords()) {
                return data.subList(0, config.getMaxRecords());
            }
            
            return data;
        }
    }

    private List<Map<String, Object>> importXml(MultipartFile file,
                                               ImportConfiguration config) throws Exception {
        List<Map<String, Object>> data = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        // XXE Protection: Disable external entity processing to prevent XXE attacks
        // These settings protect against XML External Entity (XXE) injection vulnerabilities
        // which could allow attackers to access sensitive files or cause DoS attacks
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            log.debug("XXE protection features enabled successfully for XML import");
        } catch (Exception e) {
            log.error("Failed to enable XXE protection features - this is a security risk", e);
            throw new SecurityException("Failed to configure secure XML parser", e);
        }

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(file.getInputStream());
        
        doc.getDocumentElement().normalize();
        
        // Get root element
        Element root = doc.getDocumentElement();
        
        // Get all record elements (assuming they're direct children of root)
        NodeList recordNodes = root.getChildNodes();
        
        int recordCount = 0;
        for (int i = 0; i < recordNodes.getLength(); i++) {
            Node node = recordNodes.item(i);
            
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                Map<String, Object> record = parseXmlElement(element);
                
                if (!record.isEmpty()) {
                    data.add(record);
                    recordCount++;
                    
                    if (config.getMaxRecords() != null && recordCount >= config.getMaxRecords()) {
                        break;
                    }
                }
            }
        }
        
        return data;
    }

    private List<Map<String, Object>> importText(MultipartFile file, 
                                                ImportConfiguration config) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();
        
        Charset charset = Charset.forName(config.getEffectiveEncoding());
        String delimiter = config.getEffectiveDelimiter();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), charset))) {
            
            String line;
            List<String> headers = null;
            int lineNumber = 0;
            int recordCount = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip rows if configured
                if (config.getSkipRows() != null && lineNumber <= config.getSkipRows()) {
                    continue;
                }
                
                if (config.isSkipEmptyRows() && line.trim().isEmpty()) {
                    continue;
                }
                
                String[] values = line.split(delimiter);
                
                if (headers == null && config.isFirstRowAsHeader()) {
                    headers = Arrays.asList(values);
                    continue;
                }
                
                Map<String, Object> row = new HashMap<>();
                
                if (headers != null) {
                    for (int i = 0; i < Math.min(headers.size(), values.length); i++) {
                        String value = config.isTrimValues() ? values[i].trim() : values[i];
                        row.put(headers.get(i), parseValue(value, config));
                    }
                } else {
                    for (int i = 0; i < values.length; i++) {
                        String value = config.isTrimValues() ? values[i].trim() : values[i];
                        row.put("column_" + i, parseValue(value, config));
                    }
                }
                
                data.add(row);
                recordCount++;
                
                if (config.getMaxRecords() != null && recordCount >= config.getMaxRecords()) {
                    break;
                }
            }
        }
        
        return data;
    }

    private ProcessedData processData(List<Map<String, Object>> rawData, 
                                     ImportConfiguration config) {
        ProcessedData processed = new ProcessedData();
        processed.setTotalRecords(rawData.size());
        
        List<Map<String, Object>> validData = new ArrayList<>();
        List<ImportError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AtomicInteger rowNumber = new AtomicInteger(1);
        
        for (Map<String, Object> record : rawData) {
            int currentRow = rowNumber.getAndIncrement();
            
            try {
                // Apply field mappings
                Map<String, Object> mapped = applyFieldMappings(record, config);
                
                // Validate record
                validateRecord(mapped, config, currentRow);
                
                // Apply transformations
                Map<String, Object> transformed = applyTransformations(mapped, config);
                
                // Apply default values
                Map<String, Object> withDefaults = applyDefaultValues(transformed, config);
                
                validData.add(withDefaults);
                processed.incrementSuccessful();
                
            } catch (ValidationException e) {
                errors.add(ImportError.builder()
                    .rowNumber(currentRow)
                    .fieldName(e.getFieldName())
                    .errorType(e.getErrorType())
                    .errorMessage(e.getMessage())
                    .record(record)
                    .timestamp(LocalDateTime.now())
                    .severity(e.getSeverity())
                    .build());
                processed.incrementFailed();
                
            } catch (Exception e) {
                errors.add(ImportError.builder()
                    .rowNumber(currentRow)
                    .errorType(ImportError.ErrorType.PARSING_ERROR.name())
                    .errorMessage(e.getMessage())
                    .record(record)
                    .timestamp(LocalDateTime.now())
                    .severity(ImportError.Severity.HIGH.name())
                    .build());
                processed.incrementFailed();
            }
        }
        
        // Add warnings if needed
        if (processed.getFailedRecords() > 0) {
            warnings.add(String.format("%d records failed validation", 
                processed.getFailedRecords()));
        }
        
        if (rawData.size() > 10000) {
            warnings.add("Large dataset imported. Consider using batch import for better performance.");
        }
        
        processed.setData(validData);
        processed.setErrors(errors);
        processed.setWarnings(warnings);
        
        return processed;
    }

    // Helper methods

    private String detectFileType(String filename) {
        if (filename == null) {
            return "";
        }
        
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase();
        }
        
        return "";
    }

    private String detectCsvDelimiter(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {
            
            String firstLine = reader.readLine();
            if (firstLine == null) {
                return ",";
            }
            
            // Count occurrences of common delimiters
            int commaCount = countOccurrences(firstLine, ',');
            int semicolonCount = countOccurrences(firstLine, ';');
            int tabCount = countOccurrences(firstLine, '\t');
            int pipeCount = countOccurrences(firstLine, '|');
            
            // Return the delimiter with most occurrences
            if (tabCount > Math.max(Math.max(commaCount, semicolonCount), pipeCount)) {
                return "\t";
            } else if (semicolonCount > Math.max(commaCount, pipeCount)) {
                return ";";
            } else if (pipeCount > commaCount) {
                return "|";
            } else {
                return ",";
            }
            
        } catch (Exception e) {
            return ",";
        }
    }

    private boolean detectHeaders(MultipartFile file, String fileType) {
        try {
            List<Map<String, Object>> preview = previewFile(file, 
                ImportConfiguration.defaultBuilder().build(), 2);
            
            if (preview.size() < 2) {
                return true; // Assume headers if only one row
            }
            
            // Check if first row has different data types than second row
            Map<String, Object> firstRow = preview.get(0);
            Map<String, Object> secondRow = preview.get(1);
            
            for (String key : firstRow.keySet()) {
                Object firstValue = firstRow.get(key);
                Object secondValue = secondRow.get(key);
                
                // If first row has strings where second has numbers, likely headers
                if (firstValue instanceof String && secondValue instanceof Number) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            return true; // Default to true
        }
    }

    private String detectEncoding(MultipartFile file) {
        // Simple encoding detection - in production use a proper library
        try {
            byte[] bytes = file.getBytes();
            
            // Check for BOM
            if (bytes.length >= 3 && 
                bytes[0] == (byte) 0xEF && 
                bytes[1] == (byte) 0xBB && 
                bytes[2] == (byte) 0xBF) {
                return "UTF-8";
            }
            
            if (bytes.length >= 2) {
                if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
                    return "UTF-16LE";
                }
                if (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
                    return "UTF-16BE";
                }
            }
            
            return "UTF-8"; // Default
            
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    private Object parseValue(String value, ImportConfiguration config) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        if (config.isTrimValues()) {
            value = value.trim();
        }
        
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
            } catch (DateTimeParseException e) {
                // Not a date
            }
        }
        
        return value;
    }

    private Object getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue();
                }
                yield cell.getNumericCellValue();
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private String getCellValueAsString(Cell cell) {
        Object value = getCellValue(cell);
        return value != null ? value.toString() : "";
    }

    private boolean isEmptyRecord(CSVRecord record) {
        for (String value : record) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        
        for (Cell cell : row) {
            if (cell != null && getCellValue(cell) != null) {
                String value = getCellValueAsString(cell);
                if (!value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        
        return true;
    }

    private Map<String, Object> parseXmlElement(Element element) {
        Map<String, Object> record = new HashMap<>();
        
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                record.put(childElement.getNodeName(), childElement.getTextContent());
            }
        }
        
        return record;
    }

    private Map<String, Object> applyFieldMappings(Map<String, Object> record, 
                                                  ImportConfiguration config) {
        if (!config.hasFieldMappings()) {
            return record;
        }
        
        Map<String, Object> mapped = new HashMap<>();
        for (Map.Entry<String, String> mapping : config.getFieldMappings().entrySet()) {
            String sourceField = mapping.getKey();
            String targetField = mapping.getValue();
            
            if (record.containsKey(sourceField)) {
                mapped.put(targetField, record.get(sourceField));
            }
        }
        
        return mapped;
    }

    private void validateRecord(Map<String, Object> record, ImportConfiguration config, 
                               int rowNumber) throws ValidationException {
        // Validate required fields
        if (config.getRequiredFields() != null) {
            for (String field : config.getRequiredFields()) {
                if (!record.containsKey(field) || record.get(field) == null) {
                    throw new ValidationException(
                        field,
                        ImportError.ErrorType.REQUIRED_FIELD_MISSING.name(),
                        String.format("Required field '%s' is missing at row %d", field, rowNumber),
                        ImportError.Severity.HIGH.name()
                    );
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
                    throw new ValidationException(
                        field,
                        ImportError.ErrorType.TYPE_MISMATCH.name(),
                        String.format("Invalid type for field '%s' at row %d: expected %s, got %s",
                            field, rowNumber, expectedType, value.getClass().getSimpleName()),
                        ImportError.Severity.MEDIUM.name()
                    );
                }
            }
        }
        
        // Validate allowed values
        if (config.getAllowedValues() != null) {
            for (String field : config.getAllowedValues()) {
                Object value = record.get(field);
                if (value != null && !config.getAllowedValues().contains(value.toString())) {
                    throw new ValidationException(
                        field,
                        ImportError.ErrorType.CONSTRAINT_VIOLATION.name(),
                        String.format("Invalid value for field '%s' at row %d: '%s' is not allowed",
                            field, rowNumber, value),
                        ImportError.Severity.MEDIUM.name()
                    );
                }
            }
        }
    }

    private boolean isValidType(Object value, String expectedType) {
        return switch (expectedType.toLowerCase()) {
            case "string", "text" -> value instanceof String;
            case "number", "numeric", "integer", "long" -> value instanceof Number;
            case "decimal", "double", "float" -> value instanceof BigDecimal || value instanceof Double;
            case "boolean", "bool" -> value instanceof Boolean;
            case "date" -> value instanceof Date || value instanceof LocalDate || value instanceof LocalDateTime;
            default -> true;
        };
    }

    private Map<String, Object> applyTransformations(Map<String, Object> record, 
                                                    ImportConfiguration config) {
        if (!config.hasTransformations()) {
            return record;
        }
        
        Map<String, Object> transformed = new HashMap<>(record);
        
        for (Map.Entry<String, String> transformation : config.getTransformations().entrySet()) {
            String field = transformation.getKey();
            String transformType = transformation.getValue();
            
            if (transformed.containsKey(field)) {
                Object value = transformed.get(field);
                transformed.put(field, applyTransformation(value, transformType));
            }
        }
        
        return transformed;
    }

    private Object applyTransformation(Object value, String transformType) {
        if (value == null) {
            return null;
        }
        
        return switch (transformType.toLowerCase()) {
            case "uppercase" -> value.toString().toUpperCase();
            case "lowercase" -> value.toString().toLowerCase();
            case "trim" -> value.toString().trim();
            case "remove_spaces" -> value.toString().replaceAll("\\s+", "");
            case "to_number" -> {
                try {
                    yield new BigDecimal(value.toString());
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "to_boolean" -> Boolean.parseBoolean(value.toString());
            default -> value;
        };
    }

    private Map<String, Object> applyDefaultValues(Map<String, Object> record, 
                                                  ImportConfiguration config) {
        if (config.getDefaultValues() == null) {
            return record;
        }
        
        Map<String, Object> withDefaults = new HashMap<>(record);
        
        for (Map.Entry<String, Object> defaultEntry : config.getDefaultValues().entrySet()) {
            String field = defaultEntry.getKey();
            if (!withDefaults.containsKey(field) || withDefaults.get(field) == null) {
                withDefaults.put(field, defaultEntry.getValue());
            }
        }
        
        return withDefaults;
    }

    private int countOccurrences(String str, char ch) {
        return (int) str.chars().filter(c -> c == ch).count();
    }

    private Map<String, Object> extractMetadata(MultipartFile file, ImportConfiguration config) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filename", file.getOriginalFilename());
        metadata.put("size", file.getSize());
        metadata.put("contentType", file.getContentType());
        metadata.put("encoding", config.getEffectiveEncoding());
        metadata.put("delimiter", config.getEffectiveDelimiter());
        return metadata;
    }

    // Fallback method
    public ImportResult importFileFallback(MultipartFile file, ImportConfiguration config, 
                                          Exception ex) {
        log.warn("Import fallback triggered: {}", ex.getMessage());
        
        return ImportResult.builder()
            .filename(file.getOriginalFilename())
            .success(false)
            .errorMessage("Import service temporarily unavailable: " + ex.getMessage())
            .totalRecords(0)
            .successfulRecords(0)
            .failedRecords(0)
            .build();
    }

    // Inner classes
    
    private static class ProcessedData {
        private int totalRecords;
        private int successfulRecords;
        private int failedRecords;
        private List<Map<String, Object>> data;
        private List<ImportError> errors;
        private List<String> warnings;
        
        public void incrementSuccessful() { successfulRecords++; }
        public void incrementFailed() { failedRecords++; }
        
        // Getters and setters
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public int getSuccessfulRecords() { return successfulRecords; }
        public int getFailedRecords() { return failedRecords; }
        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }
        public List<ImportError> getErrors() { return errors; }
        public void setErrors(List<ImportError> errors) { this.errors = errors; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }
    
    private static class ValidationException extends Exception {
        private final String fieldName;
        private final String errorType;
        private final String severity;
        
        public ValidationException(String fieldName, String errorType, String message, String severity) {
            super(message);
            this.fieldName = fieldName;
            this.errorType = errorType;
            this.severity = severity;
        }
        
        public String getFieldName() { return fieldName; }
        public String getErrorType() { return errorType; }
        public String getSeverity() { return severity; }
    }
    
    private static class StreamMultipartFile implements MultipartFile {
        private final File file;
        private final String name;
        
        public StreamMultipartFile(File file, String name) {
            this.file = file;
            this.name = name;
        }
        
        @Override
        public String getName() { return name; }
        
        @Override
        public String getOriginalFilename() { return name; }
        
        @Override
        public String getContentType() { return "application/octet-stream"; }
        
        @Override
        public boolean isEmpty() { return file.length() == 0; }
        
        @Override
        public long getSize() { return file.length(); }
        
        @Override
        public byte[] getBytes() throws IOException {
            return java.nio.file.Files.readAllBytes(file.toPath());
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }
        
        @Override
        public void transferTo(File dest) throws IOException {
            java.nio.file.Files.copy(file.toPath(), dest.toPath());
        }
    }
}