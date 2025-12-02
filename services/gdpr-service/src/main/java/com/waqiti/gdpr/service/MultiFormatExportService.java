package com.waqiti.gdpr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Multi-Format Export Service
 *
 * Converts GDPR data exports to multiple formats:
 * - JSON (machine-readable, default)
 * - CSV (spreadsheet-compatible)
 * - XML (structured data exchange)
 * - EXCEL (human-readable spreadsheet)
 * - PDF (human-readable document) - future
 *
 * Production-ready with proper encoding, formatting, and error handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiFormatExportService {

    private final ObjectMapper objectMapper;

    /**
     * Export data as JSON
     *
     * @param data export data
     * @return JSON bytes
     */
    public byte[] exportAsJson(Map<String, Object> data) {
        try {
            long startTime = System.currentTimeMillis();

            // Pretty print JSON for readability
            ObjectMapper prettyMapper = objectMapper.copy();
            prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);

            byte[] jsonBytes = prettyMapper.writeValueAsBytes(data);

            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Exported as JSON: size={} bytes, time={}ms",
                    jsonBytes.length, processingTime);

            return jsonBytes;

        } catch (Exception e) {
            log.error("JSON export failed: {}", e.getMessage(), e);
            throw new ExportException("Failed to export as JSON", e);
        }
    }

    /**
     * Export data as CSV
     *
     * @param data export data
     * @return CSV bytes
     */
    public byte[] exportAsCsv(Map<String, Object> data) {
        try {
            long startTime = System.currentTimeMillis();

            StringBuilder csv = new StringBuilder();

            // Process each category
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String category = entry.getKey();
                Object value = entry.getValue();

                csv.append("# Category: ").append(category).append("\n");

                if (value instanceof List) {
                    List<?> items = (List<?>) value;
                    if (!items.isEmpty()) {
                        // Write headers from first item
                        Object firstItem = items.get(0);
                        if (firstItem instanceof Map) {
                            Map<?, ?> firstMap = (Map<?, ?>) firstItem;
                            csv.append(String.join(",", firstMap.keySet().stream()
                                    .map(Object::toString)
                                    .toArray(String[]::new))).append("\n");

                            // Write data rows
                            for (Object item : items) {
                                if (item instanceof Map) {
                                    Map<?, ?> itemMap = (Map<?, ?>) item;
                                    csv.append(String.join(",", itemMap.values().stream()
                                            .map(v -> escapeCsvValue(String.valueOf(v)))
                                            .toArray(String[]::new))).append("\n");
                                }
                            }
                        }
                    }
                }
                csv.append("\n");
            }

            byte[] csvBytes = csv.toString().getBytes(StandardCharsets.UTF_8);

            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Exported as CSV: size={} bytes, time={}ms",
                    csvBytes.length, processingTime);

            return csvBytes;

        } catch (Exception e) {
            log.error("CSV export failed: {}", e.getMessage(), e);
            throw new ExportException("Failed to export as CSV", e);
        }
    }

    /**
     * Export data as XML
     *
     * @param data export data
     * @return XML bytes
     */
    public byte[] exportAsXml(Map<String, Object> data) {
        try {
            long startTime = System.currentTimeMillis();

            StringBuilder xml = new StringBuilder();
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<gdpr-data-export>\n");

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String category = entry.getKey();
                Object value = entry.getValue();

                xml.append("  <category name=\"").append(escapeXml(category)).append("\">\n");

                if (value instanceof List) {
                    List<?> items = (List<?>) value;
                    for (Object item : items) {
                        xml.append("    <item>\n");
                        if (item instanceof Map) {
                            Map<?, ?> itemMap = (Map<?, ?>) item;
                            for (Map.Entry<?, ?> field : itemMap.entrySet()) {
                                String fieldName = String.valueOf(field.getKey());
                                String fieldValue = field.getValue() != null ?
                                        String.valueOf(field.getValue()) : "";

                                xml.append("      <").append(sanitizeXmlTag(fieldName)).append(">")
                                        .append(escapeXml(fieldValue))
                                        .append("</").append(sanitizeXmlTag(fieldName)).append(">\n");
                            }
                        }
                        xml.append("    </item>\n");
                    }
                }

                xml.append("  </category>\n");
            }

            xml.append("</gdpr-data-export>\n");

            byte[] xmlBytes = xml.toString().getBytes(StandardCharsets.UTF_8);

            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Exported as XML: size={} bytes, time={}ms",
                    xmlBytes.length, processingTime);

            return xmlBytes;

        } catch (Exception e) {
            log.error("XML export failed: {}", e.getMessage(), e);
            throw new ExportException("Failed to export as XML", e);
        }
    }

    /**
     * Export data as Excel spreadsheet
     *
     * @param data export data
     * @return Excel file bytes
     */
    public byte[] exportAsExcel(Map<String, Object> data) {
        try {
            long startTime = System.currentTimeMillis();

            Workbook workbook = new XSSFWorkbook();

            // Create a sheet for each category
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String category = entry.getKey();
                Object value = entry.getValue();

                // Sanitize sheet name (max 31 chars, no special chars)
                String sheetName = sanitizeSheetName(category);
                Sheet sheet = workbook.createSheet(sheetName);

                if (value instanceof List) {
                    List<?> items = (List<?>) value;
                    if (!items.isEmpty()) {
                        Object firstItem = items.get(0);
                        if (firstItem instanceof Map) {
                            Map<?, ?> firstMap = (Map<?, ?>) firstItem;

                            // Create header row
                            Row headerRow = sheet.createRow(0);
                            CellStyle headerStyle = createHeaderStyle(workbook);

                            int colIndex = 0;
                            String[] headers = firstMap.keySet().stream()
                                    .map(Object::toString)
                                    .toArray(String[]::new);

                            for (String header : headers) {
                                Cell cell = headerRow.createCell(colIndex++);
                                cell.setCellValue(header);
                                cell.setCellStyle(headerStyle);
                            }

                            // Create data rows
                            int rowIndex = 1;
                            for (Object item : items) {
                                if (item instanceof Map) {
                                    Map<?, ?> itemMap = (Map<?, ?>) item;
                                    Row dataRow = sheet.createRow(rowIndex++);

                                    colIndex = 0;
                                    for (String header : headers) {
                                        Cell cell = dataRow.createCell(colIndex++);
                                        Object cellValue = itemMap.get(header);
                                        setCellValue(cell, cellValue);
                                    }
                                }
                            }

                            // Auto-size columns
                            for (int i = 0; i < headers.length; i++) {
                                sheet.autoSizeColumn(i);
                            }
                        }
                    }
                }
            }

            // Write to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            workbook.close();

            byte[] excelBytes = baos.toByteArray();

            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Exported as Excel: size={} bytes, sheets={}, time={}ms",
                    excelBytes.length, data.size(), processingTime);

            return excelBytes;

        } catch (Exception e) {
            log.error("Excel export failed: {}", e.getMessage(), e);
            throw new ExportException("Failed to export as Excel", e);
        }
    }

    /**
     * Export data in structured bundle (ZIP with multiple formats)
     * Future implementation
     *
     * @param data export data
     * @return ZIP bytes containing JSON, CSV, XML, and Excel
     */
    public byte[] exportAsStructuredBundle(Map<String, Object> data) {
        // Future implementation: create ZIP file with multiple formats
        throw new UnsupportedOperationException("Structured bundle export not yet implemented");
    }

    // Helper methods

    private String escapeCsvValue(String value) {
        if (value == null) return "";

        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String escapeXml(String value) {
        if (value == null) return "";

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String sanitizeXmlTag(String tag) {
        // Remove invalid XML tag characters
        return tag.replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("^[^a-zA-Z_]", "_"); // Tag must start with letter or underscore
    }

    private String sanitizeSheetName(String name) {
        // Excel sheet name max 31 chars, no special chars: \ / ? * [ ]
        String sanitized = name.replaceAll("[\\\\/:?*\\[\\]]", "_");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }

    // Exception class

    public static class ExportException extends RuntimeException {
        public ExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
