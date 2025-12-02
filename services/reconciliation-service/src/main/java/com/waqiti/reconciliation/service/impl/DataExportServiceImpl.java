package com.waqiti.reconciliation.service.impl;

import com.waqiti.reconciliation.domain.ExportConfiguration;
import com.waqiti.reconciliation.domain.ExportResult;
import com.waqiti.reconciliation.service.DataExportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class DataExportServiceImpl implements DataExportService {

    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    
    public DataExportServiceImpl(MeterRegistry meterRegistry, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }
    
    // Metrics
    private Counter exportCounter;
    private Counter exportErrorCounter;
    private Timer exportTimer;
    
    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // Constants
    private static final List<String> SUPPORTED_FORMATS = 
        Arrays.asList("csv", "xlsx", "json", "xml", "pdf", "txt", "zip");
    private static final int MAX_ROWS_PER_SHEET = 1000000;
    private static final int DEFAULT_BATCH_SIZE = 10000;
    
    @PostConstruct
    public void initialize() {
        exportCounter = Counter.builder("reconciliation.export.requests")
            .description("Number of export requests")
            .register(meterRegistry);
            
        exportErrorCounter = Counter.builder("reconciliation.export.errors")
            .description("Number of export errors")
            .register(meterRegistry);
            
        exportTimer = Timer.builder("reconciliation.export.time")
            .description("Export processing time")
            .register(meterRegistry);
            
        // Configure ObjectMapper
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.findAndRegisterModules();
        
        log.info("DataExportServiceImpl initialized");
    }

    @Override
    @CircuitBreaker(name = "export-service", fallbackMethod = "exportDataFallback")
    @Retry(name = "export-service")
    public ExportResult exportData(List<Map<String, Object>> data, ExportConfiguration config) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Exporting {} records to format: {}", data.size(), config.getFormat());
        
        try {
            exportCounter.increment();
            
            // Validate configuration
            if (!validateConfiguration(config)) {
                throw new IllegalArgumentException("Invalid export configuration");
            }
            
            String exportId = UUID.randomUUID().toString();
            LocalDateTime startTime = LocalDateTime.now();
            
            // Perform export based on format
            ByteArrayOutputStream outputStream = performExport(data, config);
            
            // Calculate checksum
            String checksum = calculateChecksum(outputStream.toByteArray());
            
            // Build result
            ExportResult result = ExportResult.builder()
                .exportId(exportId)
                .startTime(startTime)
                .endTime(LocalDateTime.now())
                .format(config.getFormat())
                .fileName(generateFileName(config))
                .data(outputStream.toByteArray())
                .fileSize((long) outputStream.size())
                .totalRecords(data.size())
                .exportedRecords(data.size())
                .success(true)
                .checksumMd5(checksum)
                .warnings(collectWarnings(data, config))
                .build();
            
            sample.stop(exportTimer);
            log.info("Export completed successfully: {} records, {} bytes", 
                result.getExportedRecords(), result.getFileSize());
                
            return result;
            
        } catch (Exception e) {
            sample.stop(exportTimer);
            exportErrorCounter.increment();
            log.error("Export failed: {}", e.getMessage(), e);
            
            return ExportResult.builder()
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .format(config.getFormat())
                .success(false)
                .errorMessage(e.getMessage())
                .totalRecords(data.size())
                .exportedRecords(0)
                .build();
        }
    }

    @Override
    public CompletableFuture<ExportResult> exportDataAsync(List<Map<String, Object>> data, 
                                                          ExportConfiguration config) {
        return CompletableFuture.supplyAsync(() -> exportData(data, config), executorService);
    }

    @Override
    public ExportResult exportDataInBatches(List<Map<String, Object>> data, 
                                           ExportConfiguration config) {
        log.info("Exporting {} records in batches", data.size());
        
        try {
            int batchSize = config.getBatchSize() != null ? config.getBatchSize() : DEFAULT_BATCH_SIZE;
            List<ByteArrayOutputStream> batchResults = new ArrayList<>();
            
            for (int i = 0; i < data.size(); i += batchSize) {
                int endIdx = Math.min(i + batchSize, data.size());
                List<Map<String, Object>> batch = data.subList(i, endIdx);
                
                ByteArrayOutputStream batchStream = performExport(batch, config);
                batchResults.add(batchStream);
                
                log.debug("Processed batch {}/{}", (i/batchSize) + 1, 
                    (data.size() + batchSize - 1) / batchSize);
            }
            
            // Merge batch results
            ByteArrayOutputStream mergedStream = mergeBatchResults(batchResults, config);
            
            return ExportResult.builder()
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .format(config.getFormat())
                .fileName(generateFileName(config))
                .data(mergedStream.toByteArray())
                .fileSize((long) mergedStream.size())
                .totalRecords(data.size())
                .exportedRecords(data.size())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Batch export failed: {}", e.getMessage(), e);
            return ExportResult.builder()
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    @Override
    public List<String> getSupportedFormats() {
        return new ArrayList<>(SUPPORTED_FORMATS);
    }

    @Override
    public boolean validateConfiguration(ExportConfiguration config) {
        if (config == null || !config.isValid()) {
            return false;
        }
        
        return SUPPORTED_FORMATS.contains(config.getFormat().toLowerCase());
    }

    @Override
    @Cacheable(value = "exportTemplates", key = "#format")
    public byte[] getExportTemplate(String format, List<String> headers) {
        log.debug("Generating export template for format: {}", format);
        
        try {
            ExportConfiguration config = ExportConfiguration.defaultBuilder()
                .format(format)
                .headers(headers)
                .build();
                
            List<Map<String, Object>> emptyData = new ArrayList<>();
            ByteArrayOutputStream template = performExport(emptyData, config);
            
            return template.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to generate export template", e);
            return new byte[0];
        }
    }

    private ByteArrayOutputStream performExport(List<Map<String, Object>> data, 
                                               ExportConfiguration config) throws Exception {
        return switch (config.getFormat().toLowerCase()) {
            case "csv" -> exportToCsv(data, config);
            case "xlsx" -> exportToExcel(data, config);
            case "json" -> exportToJson(data, config);
            case "xml" -> exportToXml(data, config);
            case "pdf" -> exportToPdf(data, config);
            case "txt" -> exportToText(data, config);
            case "zip" -> exportToZip(data, config);
            default -> {
                log.warn("Unknown export format '{}', defaulting to CSV format", config.getFormat());
                yield exportToCsv(data, config);
            }
        };
    }

    private ByteArrayOutputStream exportToCsv(List<Map<String, Object>> data, 
                                             ExportConfiguration config) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .withHeader(extractHeaders(data, config))
                     .withDelimiter(config.getDelimiter().charAt(0)))) {
            
            for (Map<String, Object> record : data) {
                List<Object> values = extractValues(record, config);
                printer.printRecord(values);
            }
            
            printer.flush();
        }
        
        return outputStream;
    }

    private ByteArrayOutputStream exportToExcel(List<Map<String, Object>> data, 
                                               ExportConfiguration config) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            // Determine sheets needed
            int maxRows = config.getMaxRowsPerSheet() != null ? 
                config.getMaxRowsPerSheet() : MAX_ROWS_PER_SHEET;
            int sheetsNeeded = (int) Math.ceil((double) data.size() / maxRows);
            
            for (int sheetNum = 0; sheetNum < sheetsNeeded; sheetNum++) {
                Sheet sheet = workbook.createSheet(
                    config.getSheetName() + (sheetsNeeded > 1 ? "_" + (sheetNum + 1) : ""));
                
                // Create header row
                createHeaderRow(sheet, config, headerStyle, data);
                
                // Add data rows
                int startIdx = sheetNum * maxRows;
                int endIdx = Math.min(startIdx + maxRows, data.size());
                
                addDataRows(sheet, data.subList(startIdx, endIdx), config, 
                    dateStyle, currencyStyle);
                
                // Apply formatting
                if (config.isAutoSizeColumns()) {
                    autoSizeColumns(sheet, extractHeaders(data, config).length);
                }
                
                if (config.isApplyFilters()) {
                    applyFilters(sheet);
                }
                
                if (config.isFreezePanes()) {
                    sheet.createFreezePane(0, 1);
                }
            }
            
            workbook.write(outputStream);
        }
        
        return outputStream;
    }

    private ByteArrayOutputStream exportToJson(List<Map<String, Object>> data, 
                                              ExportConfiguration config) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        if (config.isPrettyPrint()) {
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(outputStream, data);
        } else {
            objectMapper.writeValue(outputStream, data);
        }
        
        return outputStream;
    }

    private ByteArrayOutputStream exportToXml(List<Map<String, Object>> data, 
                                             ExportConfiguration config) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(outputStream, "UTF-8");
        
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement(config.getRootElement());
        
        for (Map<String, Object> record : data) {
            writer.writeStartElement(config.getRecordElement());
            
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                writer.writeStartElement(sanitizeXmlElementName(entry.getKey()));
                writer.writeCharacters(formatValue(entry.getValue(), config));
                writer.writeEndElement();
            }
            
            writer.writeEndElement();
        }
        
        writer.writeEndElement();
        writer.writeEndDocument();
        writer.flush();
        writer.close();
        
        return outputStream;
    }

    private ByteArrayOutputStream exportToPdf(List<Map<String, Object>> data, 
                                             ExportConfiguration config) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        Document document = new Document(PageSize.A4.rotate());
        PdfWriter.getInstance(document, outputStream);
        document.open();
        
        // Add title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Paragraph title = new Paragraph(config.getTitle(), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        
        // Add metadata
        document.add(new Paragraph("\n"));
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        document.add(new Paragraph("Generated: " + LocalDateTime.now(), metaFont));
        document.add(new Paragraph("Total Records: " + data.size(), metaFont));
        document.add(new Paragraph("\n"));
        
        // Create table
        String[] headers = extractHeaders(data, config);
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        
        // Add headers
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
            cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
            table.addCell(cell);
        }
        
        // Add data
        Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        for (Map<String, Object> record : data) {
            List<Object> values = extractValues(record, config);
            for (Object value : values) {
                table.addCell(new Phrase(formatValue(value, config), dataFont));
            }
        }
        
        document.add(table);
        document.close();
        
        return outputStream;
    }

    private ByteArrayOutputStream exportToText(List<Map<String, Object>> data, 
                                              ExportConfiguration config) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            String delimiter = config.getDelimiter();
            
            // Write headers
            String[] headers = extractHeaders(data, config);
            writer.write(String.join(delimiter, headers));
            writer.write("\n");
            
            // Write data
            for (Map<String, Object> record : data) {
                List<Object> values = extractValues(record, config);
                List<String> stringValues = values.stream()
                    .map(v -> formatValue(v, config))
                    .collect(Collectors.toList());
                writer.write(String.join(delimiter, stringValues));
                writer.write("\n");
            }
        }
        
        return outputStream;
    }

    private ByteArrayOutputStream exportToZip(List<Map<String, Object>> data, 
                                             ExportConfiguration config) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            // Export to multiple formats and add to ZIP
            
            // CSV file
            ExportConfiguration csvConfig = config.clone();
            csvConfig = ExportConfiguration.defaultBuilder()
                .format("csv")
                .headers(config.getHeaders())
                .build();
            ByteArrayOutputStream csvData = exportToCsv(data, csvConfig);
            
            ZipEntry csvEntry = new ZipEntry("data.csv");
            zipOut.putNextEntry(csvEntry);
            zipOut.write(csvData.toByteArray());
            zipOut.closeEntry();
            
            // JSON file
            ExportConfiguration jsonConfig = ExportConfiguration.defaultBuilder()
                .format("json")
                .prettyPrint(true)
                .build();
            ByteArrayOutputStream jsonData = exportToJson(data, jsonConfig);
            
            ZipEntry jsonEntry = new ZipEntry("data.json");
            zipOut.putNextEntry(jsonEntry);
            zipOut.write(jsonData.toByteArray());
            zipOut.closeEntry();
            
            // Metadata file
            String metadata = generateMetadata(data, config);
            ZipEntry metaEntry = new ZipEntry("metadata.txt");
            zipOut.putNextEntry(metaEntry);
            zipOut.write(metadata.getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
        
        return outputStream;
    }

    // Helper methods

    private String[] extractHeaders(List<Map<String, Object>> data, ExportConfiguration config) {
        if (config.getHeaders() != null && !config.getHeaders().isEmpty()) {
            return config.getHeaders().toArray(new String[0]);
        }
        
        if (!data.isEmpty()) {
            Set<String> headerSet = new LinkedHashSet<>();
            for (Map<String, Object> record : data) {
                headerSet.addAll(record.keySet());
            }
            return headerSet.toArray(new String[0]);
        }
        
        return new String[0];
    }

    private List<Object> extractValues(Map<String, Object> record, ExportConfiguration config) {
        List<Object> values = new ArrayList<>();
        String[] headers = extractHeaders(Collections.singletonList(record), config);
        
        for (String header : headers) {
            values.add(record.get(header));
        }
        
        return values;
    }

    private String formatValue(Object value, ExportConfiguration config) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(
                DateTimeFormatter.ofPattern(config.getDateTimeFormat()));
        }
        
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(
                DateTimeFormatter.ofPattern(config.getDateTimeFormat()));
        }
        
        if (value instanceof BigDecimal && config.getCurrencyFormat() != null) {
            // Format as currency
            return String.format(config.getCurrencyFormat(), value);
        }
        
        return value.toString();
    }

    private String generateFileName(ExportConfiguration config) {
        String baseName = config.getFileName() != null ? config.getFileName() : "export";
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String extension = config.getFileExtension();
        
        return String.format("%s_%s.%s", baseName, timestamp, extension);
    }

    private String calculateChecksum(byte[] data) {
        try {
            // SECURITY FIX: Use SHA-256 instead of weak MD5 algorithm
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to calculate checksum", e);
            return null;
        }
    }

    private List<String> collectWarnings(List<Map<String, Object>> data, ExportConfiguration config) {
        List<String> warnings = new ArrayList<>();
        
        if (data.size() > 100000) {
            warnings.add("Large dataset exported. Consider using batch export for better performance.");
        }
        
        if (config.getFormat().equals("xlsx") && data.size() > MAX_ROWS_PER_SHEET) {
            warnings.add("Data split across multiple sheets due to Excel row limits.");
        }
        
        return warnings;
    }

    private ByteArrayOutputStream mergeBatchResults(List<ByteArrayOutputStream> batchResults, 
                                                   ExportConfiguration config) throws IOException {
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        
        for (ByteArrayOutputStream batch : batchResults) {
            merged.write(batch.toByteArray());
        }
        
        return merged;
    }

    private String sanitizeXmlElementName(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private String generateMetadata(List<Map<String, Object>> data, ExportConfiguration config) {
        StringBuilder metadata = new StringBuilder();
        metadata.append("Export Metadata\n");
        metadata.append("===============\n\n");
        metadata.append("Generated: ").append(LocalDateTime.now()).append("\n");
        metadata.append("Format: ").append(config.getFormat()).append("\n");
        metadata.append("Total Records: ").append(data.size()).append("\n");
        
        if (!data.isEmpty()) {
            metadata.append("Fields: ").append(String.join(", ", data.get(0).keySet())).append("\n");
        }
        
        return metadata.toString();
    }

    // Excel helper methods

    private void createHeaderRow(Sheet sheet, ExportConfiguration config, 
                                CellStyle headerStyle, List<Map<String, Object>> data) {
        Row headerRow = sheet.createRow(0);
        String[] headers = extractHeaders(data, config);
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void addDataRows(Sheet sheet, List<Map<String, Object>> data, 
                           ExportConfiguration config, CellStyle dateStyle, 
                           CellStyle currencyStyle) {
        int rowNum = 1;
        for (Map<String, Object> record : data) {
            Row row = sheet.createRow(rowNum++);
            List<Object> values = extractValues(record, config);
            
            for (int j = 0; j < values.size(); j++) {
                Cell cell = row.createCell(j);
                setCellValue(cell, values.get(j), dateStyle, currencyStyle);
            }
        }
    }

    private void setCellValue(Cell cell, Object value, CellStyle dateStyle, 
                            CellStyle currencyStyle) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            cell.setCellValue(numValue);
            
            if (value instanceof BigDecimal) {
                cell.setCellStyle(currencyStyle);
            }
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDate || value instanceof LocalDateTime) {
            cell.setCellValue(value.toString());
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createDateStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        CreationHelper createHelper = workbook.getCreationHelper();
        style.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$#,##0.00"));
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void applyFilters(Sheet sheet) {
        int lastRow = sheet.getLastRowNum();
        int lastCol = sheet.getRow(0).getLastCellNum() - 1;
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, lastRow, 0, lastCol));
    }

    // Fallback method

    public ExportResult exportDataFallback(List<Map<String, Object>> data, 
                                          ExportConfiguration config, Exception ex) {
        log.warn("Export fallback triggered: {}", ex.getMessage());
        
        return ExportResult.builder()
            .success(false)
            .errorMessage("Export service temporarily unavailable: " + ex.getMessage())
            .totalRecords(data.size())
            .exportedRecords(0)
            .build();
    }
}