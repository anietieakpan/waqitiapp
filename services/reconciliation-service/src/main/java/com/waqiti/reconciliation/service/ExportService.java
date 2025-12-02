package com.waqiti.reconciliation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// Import iText PDF library classes (these need to be added to pom.xml as well)
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

/**
 * Service for exporting reconciliation data to various file formats
 */
@Slf4j
@Service
public class ExportService {
    
    private static final String[] SUPPORTED_FORMATS = {"csv", "xlsx", "json", "xml", "pdf", "txt", "zip"};
    private static final int MAX_ROWS_PER_SHEET = 1000000;
    private static final int BATCH_SIZE = 10000;
    
    /**
     * Export data to specified format
     */
    public ExportResult exportData(List<Map<String, Object>> data, ExportConfiguration config) {
        log.info("Exporting {} records to format: {}", data.size(), config.getFormat());
        
        ExportResult result = new ExportResult();
        result.setStartTime(LocalDateTime.now());
        result.setFormat(config.getFormat());
        result.setTotalRecords(data.size());
        
        try {
            ByteArrayOutputStream outputStream;
            
            switch (config.getFormat().toLowerCase()) {
                case "csv":
                    outputStream = exportToCsv(data, config);
                    break;
                case "xlsx":
                    outputStream = exportToExcel(data, config);
                    break;
                case "json":
                    outputStream = exportToJson(data, config);
                    break;
                case "xml":
                    outputStream = exportToXml(data, config);
                    break;
                case "pdf":
                    outputStream = exportToPdf(data, config);
                    break;
                case "txt":
                    outputStream = exportToText(data, config);
                    break;
                case "zip":
                    outputStream = exportToZip(data, config);
                    break;
                case "xml":
                    outputStream = exportToXML(data, config);
                    break;
                case "json":
                    outputStream = exportToJSON(data, config);
                    break;
                default:
                    log.warn("Unsupported export format requested: {}, defaulting to CSV", config.getFormat());
                    outputStream = exportToCsv(data, config);
                    break;
            }
            
            result.setData(outputStream.toByteArray());
            result.setFileSize(outputStream.size());
            result.setFileName(generateFileName(config));
            result.setSuccess(true);
            result.setExportedRecords(data.size());
            result.setEndTime(LocalDateTime.now());
            
            log.info("Export completed: {} records exported, size: {} bytes", 
                    result.getExportedRecords(), result.getFileSize());
            
        } catch (Exception e) {
            log.error("Export failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }
        
        return result;
    }
    
    /**
     * Export to CSV format
     */
    private ByteArrayOutputStream exportToCsv(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to CSV format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT
                     .withHeader(extractHeaders(data, config))
                     .withDelimiter(config.getDelimiter() != null ? config.getDelimiter().charAt(0) : ','))) {
            
            for (Map<String, Object> record : data) {
                List<Object> values = extractValues(record, config);
                printer.printRecord(values);
            }
            
            printer.flush();
        }
        
        return outputStream;
    }
    
    /**
     * Export to Excel format
     */
    private ByteArrayOutputStream exportToExcel(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to Excel format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);
            
            // Determine number of sheets needed
            int sheetsNeeded = (int) Math.ceil((double) data.size() / MAX_ROWS_PER_SHEET);
            
            for (int sheetNum = 0; sheetNum < sheetsNeeded; sheetNum++) {
                Sheet sheet = workbook.createSheet(config.getSheetName() + (sheetsNeeded > 1 ? "_" + (sheetNum + 1) : ""));
                
                // Create header row
                Row headerRow = sheet.createRow(0);
                String[] headers = extractHeaders(data, config);
                
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }
                
                // Add data rows
                int startIdx = sheetNum * MAX_ROWS_PER_SHEET;
                int endIdx = Math.min(startIdx + MAX_ROWS_PER_SHEET, data.size());
                int rowNum = 1;
                
                for (int i = startIdx; i < endIdx; i++) {
                    Map<String, Object> record = data.get(i);
                    Row row = sheet.createRow(rowNum++);
                    
                    List<Object> values = extractValues(record, config);
                    for (int j = 0; j < values.size(); j++) {
                        Cell cell = row.createCell(j);
                        setCellValue(cell, values.get(j), dateStyle, currencyStyle);
                    }
                }
                
                // Auto-size columns
                if (config.isAutoSizeColumns()) {
                    for (int i = 0; i < headers.length; i++) {
                        sheet.autoSizeColumn(i);
                    }
                }
                
                // Apply filters
                if (config.isApplyFilters()) {
                    sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, rowNum - 1, 0, headers.length - 1));
                }
                
                // Freeze panes
                if (config.isFreezePanes()) {
                    sheet.createFreezePane(0, 1);
                }
            }
            
            workbook.write(outputStream);
        }
        
        return outputStream;
    }
    
    /**
     * Export to JSON format
     */
    private ByteArrayOutputStream exportToJson(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to JSON format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            if (config.isPrettyPrint()) {
                writer.write(formatJsonPretty(data));
            } else {
                writer.write(formatJson(data));
            }
        }
        
        return outputStream;
    }
    
    /**
     * Export to XML format
     */
    private ByteArrayOutputStream exportToXml(List<Map<String, Object>> data, ExportConfiguration config) throws Exception {
        log.debug("Exporting to XML format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(outputStream, "UTF-8");
        
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement(config.getRootElement() != null ? config.getRootElement() : "data");
        
        for (Map<String, Object> record : data) {
            writer.writeStartElement(config.getRecordElement() != null ? config.getRecordElement() : "record");
            
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                writer.writeStartElement(sanitizeXmlElementName(entry.getKey()));
                writer.writeCharacters(formatValue(entry.getValue()));
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
    
    /**
     * Export to JSON format
     */
    private ByteArrayOutputStream exportToJSON(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to JSON format with {} records", data.size());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            
            Map<String, Object> exportData = new HashMap<>();
            exportData.put("exportDate", LocalDateTime.now().toString());
            exportData.put("title", config.getTitle() != null ? config.getTitle() : "Data Export");
            exportData.put("totalRecords", data.size());
            exportData.put("data", data);
            
            objectMapper.writeValue(outputStream, exportData);
        } catch (Exception e) {
            log.error("Failed to export to JSON: {}", e.getMessage(), e);
            throw new IOException("JSON export failed", e);
        }
        
        return outputStream;
    }
    
    /**
     * Export to PDF format using iText library for proper PDF generation
     */
    private ByteArrayOutputStream exportToPdf(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to PDF format with {} records", data.size());
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try {
            // Use iText for proper PDF generation
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(outputStream);
            com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            com.itextpdf.layout.Document document = new com.itextpdf.layout.Document(pdfDoc, 
                com.itextpdf.kernel.geom.PageSize.A4.rotate()); // Landscape for better table visibility
            
            // Set document metadata
            pdfDoc.getDocumentInfo().setTitle(config.getTitle() != null ? config.getTitle() : "Export Report");
            pdfDoc.getDocumentInfo().setAuthor("Waqiti Reconciliation Service");
            pdfDoc.getDocumentInfo().setCreator("Export Service");
            pdfDoc.getDocumentInfo().setSubject("Data Export");
            
            // Add header with title and metadata
            com.itextpdf.layout.element.Paragraph title = new com.itextpdf.layout.element.Paragraph(
                config.getTitle() != null ? config.getTitle() : "Data Export Report")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setFontSize(18)
                .setBold();
            document.add(title);
            
            // Add generation metadata
            com.itextpdf.layout.element.Paragraph metadata = new com.itextpdf.layout.element.Paragraph()
                .add("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .add("\nTotal Records: " + data.size())
                .add("\nFormat: PDF")
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setFontSize(10)
                .setItalic();
            document.add(metadata);
            
            // Add spacing
            document.add(new com.itextpdf.layout.element.Paragraph("\n"));
            
            // Create data table
            if (!data.isEmpty()) {
                String[] headers = extractHeaders(data, config);
                com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(
                    com.itextpdf.layout.properties.UnitValue.createPercentArray(headers.length))
                    .useAllAvailableWidth();
                
                // Add header row with styling
                for (String header : headers) {
                    com.itextpdf.layout.element.Cell headerCell = new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(header))
                        .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
                        .setBold()
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
                    table.addHeaderCell(headerCell);
                }
                
                // Add data rows with alternating colors for readability
                int rowNum = 0;
                for (Map<String, Object> record : data) {
                    List<Object> values = extractValues(record, config);
                    
                    for (Object value : values) {
                        com.itextpdf.layout.element.Cell dataCell = new com.itextpdf.layout.element.Cell()
                            .add(new com.itextpdf.layout.element.Paragraph(formatValue(value)))
                            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.LEFT);
                        
                        // Alternate row colors
                        if (rowNum % 2 == 0) {
                            dataCell.setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.WHITE);
                        } else {
                            dataCell.setBackgroundColor(new com.itextpdf.kernel.colors.DeviceRgb(240, 240, 240));
                        }
                        
                        table.addCell(dataCell);
                    }
                    rowNum++;
                    
                    // Add page break for large tables (every 30 rows)
                    if (rowNum % 30 == 0 && rowNum < data.size()) {
                        table.setKeepTogether(false);
                    }
                }
                
                document.add(table);
            } else {
                document.add(new com.itextpdf.layout.element.Paragraph("No data available")
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER));
            }
            
            // Add footer with summary
            document.add(new com.itextpdf.layout.element.Paragraph("\n"));
            com.itextpdf.layout.element.Paragraph footer = new com.itextpdf.layout.element.Paragraph()
                .add("Summary: " + data.size() + " records exported")
                .add("\nGenerated by: Waqiti Reconciliation Service")
                .add("\nTimestamp: " + LocalDateTime.now())
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setFontSize(8)
                .setItalic();
            document.add(footer);
            
            // Add page numbers
            int numberOfPages = pdfDoc.getNumberOfPages();
            for (int i = 1; i <= numberOfPages; i++) {
                document.showTextAligned(new com.itextpdf.layout.element.Paragraph(
                    String.format("Page %d of %d", i, numberOfPages)),
                    559, 20, i, 
                    com.itextpdf.layout.properties.TextAlignment.RIGHT,
                    com.itextpdf.layout.properties.VerticalAlignment.BOTTOM, 0);
            }
            
            // Close document
            document.close();
            
        } catch (Exception e) {
            log.error("Failed to generate PDF: {}", e.getMessage(), e);
            // Fallback to HTML if PDF generation fails
            log.warn("Falling back to HTML export due to PDF generation failure");
            return exportToHtml(data, config);
        }
        
        log.debug("PDF generation completed, size: {} bytes", outputStream.size());
        return outputStream;
    }
    
    /**
     * Export to HTML format (used as fallback for PDF if needed)
     */
    private ByteArrayOutputStream exportToHtml(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to HTML format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
            // Generate proper HTML document
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("<meta charset=\"UTF-8\">");
            writer.println("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            writer.println("<title>" + (config.getTitle() != null ? config.getTitle() : "Export Report") + "</title>");
            writer.println("<style>");
            writer.println("body { font-family: Arial, sans-serif; margin: 20px; }");
            writer.println(".header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; margin-bottom: 20px; }");
            writer.println("h1 { color: #333; margin: 0; }");
            writer.println(".metadata { color: #666; margin-top: 10px; }");
            writer.println("table { border-collapse: collapse; width: 100%; margin-top: 20px; }");
            writer.println("th { background-color: #4CAF50; color: white; padding: 12px; text-align: left; position: sticky; top: 0; }");
            writer.println("td { padding: 8px; border-bottom: 1px solid #ddd; }");
            writer.println("tr:hover { background-color: #f5f5f5; }");
            writer.println("tr:nth-child(even) { background-color: #f9f9f9; }");
            writer.println(".footer { margin-top: 20px; padding: 10px; text-align: center; color: #666; font-size: 0.9em; }");
            writer.println("@media print { .no-print { display: none; } }");
            writer.println("</style>");
            writer.println("</head>");
            writer.println("<body>");
            
            // Header
            writer.println("<div class=\"header\">");
            writer.println("<h1>" + (config.getTitle() != null ? config.getTitle() : "Data Export") + "</h1>");
            writer.println("<div class=\"metadata\">");
            writer.println("<p>Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "</p>");
            writer.println("<p>Total Records: " + data.size() + "</p>");
            writer.println("</div>");
            writer.println("</div>");
            
            // Data table
            writer.println("<table>");
            
            // Headers
            String[] headers = extractHeaders(data, config);
            writer.println("<thead>");
            writer.println("<tr>");
            for (String header : headers) {
                writer.println("<th>" + escapeHtml(header) + "</th>");
            }
            writer.println("</tr>");
            writer.println("</thead>");
            
            // Data rows
            writer.println("<tbody>");
            for (Map<String, Object> record : data) {
                writer.println("<tr>");
                List<Object> values = extractValues(record, config);
                for (Object value : values) {
                    writer.println("<td>" + escapeHtml(formatValue(value)) + "</td>");
                }
                writer.println("</tr>");
            }
            writer.println("</tbody>");
            writer.println("</table>");
            
            // Footer
            writer.println("<div class=\"footer\">");
            writer.println("<p>Generated by Waqiti Reconciliation Service</p>");
            writer.println("</div>");
            
            writer.println("</body>");
            writer.println("</html>");
        }
        
        return outputStream;
    }
    
    /**
     * Export to text format
     */
    private ByteArrayOutputStream exportToText(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to text format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
            String delimiter = config.getDelimiter() != null ? config.getDelimiter() : "\t";
            
            // Write headers
            String[] headers = extractHeaders(data, config);
            writer.write(String.join(delimiter, headers));
            writer.write("\n");
            
            // Write data
            for (Map<String, Object> record : data) {
                List<Object> values = extractValues(record, config);
                List<String> stringValues = values.stream()
                    .map(this::formatValue)
                    .collect(Collectors.toList());
                writer.write(String.join(delimiter, stringValues));
                writer.write("\n");
            }
        }
        
        return outputStream;
    }
    
    /**
     * Export to ZIP format (multiple files)
     */
    private ByteArrayOutputStream exportToZip(List<Map<String, Object>> data, ExportConfiguration config) throws IOException {
        log.debug("Exporting to ZIP format");
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            // Export to multiple formats and add to ZIP
            
            // Add CSV file
            ExportConfiguration csvConfig = config.clone();
            csvConfig.setFormat("csv");
            ByteArrayOutputStream csvData = exportToCsv(data, csvConfig);
            
            ZipEntry csvEntry = new ZipEntry("data.csv");
            zipOut.putNextEntry(csvEntry);
            zipOut.write(csvData.toByteArray());
            zipOut.closeEntry();
            
            // Add JSON file
            ExportConfiguration jsonConfig = config.clone();
            jsonConfig.setFormat("json");
            jsonConfig.setPrettyPrint(true);
            ByteArrayOutputStream jsonData = exportToJson(data, jsonConfig);
            
            ZipEntry jsonEntry = new ZipEntry("data.json");
            zipOut.putNextEntry(jsonEntry);
            zipOut.write(jsonData.toByteArray());
            zipOut.closeEntry();
            
            // Add metadata file
            String metadata = generateMetadata(data, config);
            ZipEntry metaEntry = new ZipEntry("metadata.txt");
            zipOut.putNextEntry(metaEntry);
            zipOut.write(metadata.getBytes(StandardCharsets.UTF_8));
            zipOut.closeEntry();
        }
        
        return outputStream;
    }
    
    /**
     * Export asynchronously
     */
    public CompletableFuture<ExportResult> exportDataAsync(List<Map<String, Object>> data, ExportConfiguration config) {
        return CompletableFuture.supplyAsync(() -> exportData(data, config));
    }
    
    /**
     * Export in batches for large datasets
     */
    public ExportResult exportDataInBatches(List<Map<String, Object>> data, ExportConfiguration config) {
        log.info("Exporting {} records in batches", data.size());
        
        ExportResult result = new ExportResult();
        result.setStartTime(LocalDateTime.now());
        result.setFormat(config.getFormat());
        result.setTotalRecords(data.size());
        
        try {
            List<ByteArrayOutputStream> batchResults = new ArrayList<>();
            
            for (int i = 0; i < data.size(); i += BATCH_SIZE) {
                int endIdx = Math.min(i + BATCH_SIZE, data.size());
                List<Map<String, Object>> batch = data.subList(i, endIdx);
                
                ExportResult batchResult = exportData(batch, config);
                if (batchResult.isSuccess()) {
                    ByteArrayOutputStream batchStream = new ByteArrayOutputStream();
                    batchStream.write(batchResult.getData());
                    batchResults.add(batchStream);
                }
            }
            
            // Merge batch results
            ByteArrayOutputStream mergedStream = mergeBatchResults(batchResults, config);
            result.setData(mergedStream.toByteArray());
            result.setFileSize(mergedStream.size());
            result.setFileName(generateFileName(config));
            result.setSuccess(true);
            result.setExportedRecords(data.size());
            
        } catch (Exception e) {
            log.error("Batch export failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        result.setEndTime(LocalDateTime.now());
        return result;
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
    
    private void setCellValue(Cell cell, Object value, CellStyle dateStyle, CellStyle currencyStyle) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            double numValue = ((Number) value).doubleValue();
            cell.setCellValue(numValue);
            
            if (value instanceof BigDecimal || value.toString().contains(".")) {
                cell.setCellStyle(currencyStyle);
            }
        } else if (value instanceof Date) {
            cell.setCellValue((Date) value);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDate) {
            cell.setCellValue(value.toString());
            cell.setCellStyle(dateStyle);
        } else if (value instanceof LocalDateTime) {
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
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
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
    
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        
        if (value instanceof LocalDate) {
            return ((LocalDate) value).format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        
        return value.toString();
    }
    
    private String formatJson(List<Map<String, Object>> data) {
        // Simplified JSON formatting - in production use Jackson or Gson
        StringBuilder json = new StringBuilder("[");
        
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) json.append(",");
            json.append(formatJsonObject(data.get(i)));
        }
        
        json.append("]");
        return json.toString();
    }
    
    private String formatJsonPretty(List<Map<String, Object>> data) {
        // Simplified pretty JSON - in production use proper JSON library
        StringBuilder json = new StringBuilder("[\n");
        
        for (int i = 0; i < data.size(); i++) {
            if (i > 0) json.append(",\n");
            json.append("  ").append(formatJsonObject(data.get(i)));
        }
        
        json.append("\n]");
        return json.toString();
    }
    
    private String formatJsonObject(Map<String, Object> obj) {
        StringBuilder json = new StringBuilder("{");
        
        int count = 0;
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            if (count++ > 0) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            } else if (value instanceof Boolean || value instanceof Number) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
    }
    
    private String sanitizeXmlElementName(String name) {
        // XML element names must start with letter or underscore
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (!sanitized.matches("^[a-zA-Z_].*")) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }
    
    private String generateFileName(ExportConfiguration config) {
        String baseName = config.getFileName() != null ? config.getFileName() : "export";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String extension = config.getFormat().toLowerCase();
        
        return String.format("%s_%s.%s", baseName, timestamp, extension);
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
        
        metadata.append("\nConfiguration:\n");
        metadata.append("- Include Headers: ").append(config.isIncludeHeaders()).append("\n");
        metadata.append("- Pretty Print: ").append(config.isPrettyPrint()).append("\n");
        
        return metadata.toString();
    }
    
    private ByteArrayOutputStream mergeBatchResults(List<ByteArrayOutputStream> batchResults, ExportConfiguration config) throws IOException {
        ByteArrayOutputStream merged = new ByteArrayOutputStream();
        
        for (ByteArrayOutputStream batch : batchResults) {
            merged.write(batch.toByteArray());
        }
        
        return merged;
    }
    
    // Inner classes
    
    public static class ExportConfiguration implements Cloneable {
        private String format = "csv";
        private String fileName;
        private String sheetName = "Data";
        private String delimiter = ",";
        private List<String> headers;
        private boolean includeHeaders = true;
        private boolean autoSizeColumns = true;
        private boolean applyFilters = true;
        private boolean freezePanes = true;
        private boolean prettyPrint = false;
        private String rootElement = "data";
        private String recordElement = "record";
        private String title = "Export Report";
        private Map<String, String> fieldMappings;
        private Map<String, String> formatters;
        
        @Override
        public ExportConfiguration clone() {
            try {
                return (ExportConfiguration) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
        
        // Getters and setters
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getSheetName() { return sheetName; }
        public void setSheetName(String sheetName) { this.sheetName = sheetName; }
        public String getDelimiter() { return delimiter; }
        public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
        public List<String> getHeaders() { return headers; }
        public void setHeaders(List<String> headers) { this.headers = headers; }
        public boolean isIncludeHeaders() { return includeHeaders; }
        public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }
        public boolean isAutoSizeColumns() { return autoSizeColumns; }
        public void setAutoSizeColumns(boolean autoSizeColumns) { this.autoSizeColumns = autoSizeColumns; }
        public boolean isApplyFilters() { return applyFilters; }
        public void setApplyFilters(boolean applyFilters) { this.applyFilters = applyFilters; }
        public boolean isFreezePanes() { return freezePanes; }
        public void setFreezePanes(boolean freezePanes) { this.freezePanes = freezePanes; }
        public boolean isPrettyPrint() { return prettyPrint; }
        public void setPrettyPrint(boolean prettyPrint) { this.prettyPrint = prettyPrint; }
        public String getRootElement() { return rootElement; }
        public void setRootElement(String rootElement) { this.rootElement = rootElement; }
        public String getRecordElement() { return recordElement; }
        public void setRecordElement(String recordElement) { this.recordElement = recordElement; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Map<String, String> getFieldMappings() { return fieldMappings; }
        public void setFieldMappings(Map<String, String> fieldMappings) { this.fieldMappings = fieldMappings; }
        public Map<String, String> getFormatters() { return formatters; }
        public void setFormatters(Map<String, String> formatters) { this.formatters = formatters; }
    }
    
    public static class ExportResult {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String format;
        private String fileName;
        private byte[] data;
        private long fileSize;
        private int totalRecords;
        private int exportedRecords;
        private boolean success;
        private String errorMessage;
        
        public long getDurationMillis() {
            if (startTime != null && endTime != null) {
                return java.time.Duration.between(startTime, endTime).toMillis();
            }
            return 0;
        }
        
        // Getters and setters
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public byte[] getData() { return data; }
        public void setData(byte[] data) { this.data = data; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public int getTotalRecords() { return totalRecords; }
        public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
        public int getExportedRecords() { return exportedRecords; }
        public void setExportedRecords(int exportedRecords) { this.exportedRecords = exportedRecords; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}