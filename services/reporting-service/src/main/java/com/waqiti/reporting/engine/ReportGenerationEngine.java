package com.waqiti.reporting.engine;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.waqiti.reporting.dto.StatementDocument;
import com.waqiti.reporting.dto.MISDocument;
import com.waqiti.reporting.dto.RiskReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationEngine {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final OptimizedPDFGenerator optimizedPDFGenerator;
    
    public byte[] generatePDF(Object reportData, String reportType) {
        try {
            // Use optimized PDF generator for better performance
            return optimizedPDFGenerator.generateOptimizedPDF(reportData, reportType);
        } catch (Exception e) {
            log.error("Error generating PDF report", e);
            throw new ReportGenerationException("Failed to generate PDF report", e);
        }
    }
    
    public byte[] generateExcel(Object reportData, String reportType) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // Generate content based on report type
            if (reportData instanceof StatementDocument) {
                generateStatementExcel(workbook, (StatementDocument) reportData, headerStyle, dataStyle);
            } else if (reportData instanceof MISDocument) {
                generateMISExcel(workbook, (MISDocument) reportData, headerStyle, dataStyle);
            } else if (reportData instanceof RiskReport) {
                generateRiskReportExcel(workbook, (RiskReport) reportData, headerStyle, dataStyle);
            } else if (reportData instanceof List) {
                generateListExcel(workbook, (List<?>) reportData, headerStyle, dataStyle);
            } else {
                generateGenericExcel(workbook, reportData, headerStyle, dataStyle);
            }
            
            workbook.write(baos);
            return baos.toByteArray();
            
        } catch (Exception e) {
            log.error("Error generating Excel report", e);
            throw new ReportGenerationException("Failed to generate Excel report", e);
        }
    }
    
    public byte[] generateCSV(Object reportData) {
        try (StringWriter sw = new StringWriter();
             CSVPrinter csvPrinter = new CSVPrinter(sw, CSVFormat.DEFAULT.withHeader())) {
            
            if (reportData instanceof List) {
                generateListCSV(csvPrinter, (List<?>) reportData);
            } else if (reportData instanceof Map) {
                generateMapCSV(csvPrinter, (Map<?, ?>) reportData);
            } else {
                generateGenericCSV(csvPrinter, reportData);
            }
            
            csvPrinter.flush();
            return sw.toString().getBytes();
            
        } catch (Exception e) {
            log.error("Error generating CSV report", e);
            throw new ReportGenerationException("Failed to generate CSV report", e);
        }
    }
    
    private void addPDFHeader(Document document, String reportType) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.GRAY);
        
        Paragraph title = new Paragraph("Waqiti Financial Report", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        
        Paragraph subtitle = new Paragraph(reportType + " - Generated on " + 
            LocalDateTime.now().format(DATETIME_FORMATTER), subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(20);
        document.add(subtitle);
    }
    
    private void generateStatementPDF(Document document, StatementDocument statement) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        
        // Account Information
        document.add(new Paragraph("Account Information", sectionFont));
        document.add(new Paragraph("Account Number: " + statement.getAccountNumber(), normalFont));
        document.add(new Paragraph("Account Type: " + statement.getAccountType(), normalFont));
        document.add(new Paragraph("Statement Period: " + statement.getPeriod().getStartDate() + 
            " to " + statement.getPeriod().getEndDate(), normalFont));
        document.add(new Paragraph(" "));
        
        // Balance Summary
        document.add(new Paragraph("Balance Summary", sectionFont));
        PdfPTable balanceTable = new PdfPTable(2);
        balanceTable.setWidthPercentage(100);
        
        addTableRow(balanceTable, "Opening Balance", formatCurrency(statement.getBalanceSummary().getOpeningBalance()));
        addTableRow(balanceTable, "Closing Balance", formatCurrency(statement.getBalanceSummary().getClosingBalance()));
        addTableRow(balanceTable, "Average Balance", formatCurrency(statement.getBalanceSummary().getAverageBalance()));
        
        document.add(balanceTable);
        document.add(new Paragraph(" "));
        
        // Transaction History
        document.add(new Paragraph("Transaction History", sectionFont));
        PdfPTable transactionTable = new PdfPTable(5);
        transactionTable.setWidthPercentage(100);
        transactionTable.setWidths(new float[]{2, 3, 1.5f, 1.5f, 2});
        
        // Headers
        addTableHeader(transactionTable, "Date");
        addTableHeader(transactionTable, "Description");
        addTableHeader(transactionTable, "Debit");
        addTableHeader(transactionTable, "Credit");
        addTableHeader(transactionTable, "Balance");
        
        // Transactions
        for (StatementDocument.Transaction tx : statement.getTransactions()) {
            addTableCell(transactionTable, tx.getDate().format(DATE_FORMATTER));
            addTableCell(transactionTable, tx.getDescription());
            addTableCell(transactionTable, tx.getDebit() != null ? formatCurrency(tx.getDebit()) : "");
            addTableCell(transactionTable, tx.getCredit() != null ? formatCurrency(tx.getCredit()) : "");
            addTableCell(transactionTable, formatCurrency(tx.getBalance()));
        }
        
        document.add(transactionTable);
    }
    
    private void generateMISPDF(Document document, MISDocument mis) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        
        // Executive Summary
        document.add(new Paragraph("Executive Summary", sectionFont));
        document.add(new Paragraph(mis.getExecutiveSummary().getSummary(), normalFont));
        document.add(new Paragraph(" "));
        
        // Key Metrics
        document.add(new Paragraph("Key Performance Indicators", sectionFont));
        PdfPTable kpiTable = new PdfPTable(4);
        kpiTable.setWidthPercentage(100);
        
        addTableHeader(kpiTable, "Metric");
        addTableHeader(kpiTable, "Actual");
        addTableHeader(kpiTable, "Target");
        addTableHeader(kpiTable, "Status");
        
        for (MISDocument.KPIMetric kpi : mis.getKeyMetrics()) {
            addTableCell(kpiTable, kpi.getName());
            addTableCell(kpiTable, kpi.getActualValue().toString() + " " + kpi.getUnit());
            addTableCell(kpiTable, kpi.getTargetValue().toString() + " " + kpi.getUnit());
            addTableCell(kpiTable, kpi.getStatus());
        }
        
        document.add(kpiTable);
    }
    
    private void generateRiskReportPDF(Document document, RiskReport report) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        
        // Risk Summary
        document.add(new Paragraph("Risk Summary", sectionFont));
        document.add(new Paragraph("Overall Risk Level: " + report.getSummary().getOverallRiskLevel(), normalFont));
        document.add(new Paragraph("Risk Score: " + report.getSummary().getRiskScore(), normalFont));
        document.add(new Paragraph("Total Alerts: " + report.getSummary().getTotalAlerts(), normalFont));
        document.add(new Paragraph(" "));
        
        // Risk Alerts
        if (!report.getAlerts().isEmpty()) {
            document.add(new Paragraph("Risk Alerts", sectionFont));
            PdfPTable alertTable = new PdfPTable(3);
            alertTable.setWidthPercentage(100);
            
            addTableHeader(alertTable, "Severity");
            addTableHeader(alertTable, "Category");
            addTableHeader(alertTable, "Description");
            
            for (RiskReport.RiskAlert alert : report.getAlerts()) {
                addTableCell(alertTable, alert.getSeverity());
                addTableCell(alertTable, alert.getCategory());
                addTableCell(alertTable, alert.getDescription());
            }
            
            document.add(alertTable);
        }
    }
    
    private void generateGenericPDF(Document document, Object data) throws DocumentException {
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        document.add(new Paragraph(data.toString(), normalFont));
    }
    
    private void generateStatementExcel(Workbook workbook, StatementDocument statement, 
                                       CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("Account Statement");
        int rowNum = 0;
        
        // Title
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Account Statement - " + statement.getAccountNumber());
        
        rowNum++; // Empty row
        
        // Account Info
        Row accountRow = sheet.createRow(rowNum++);
        accountRow.createCell(0).setCellValue("Account Type:");
        accountRow.createCell(1).setCellValue(statement.getAccountType());
        
        Row periodRow = sheet.createRow(rowNum++);
        periodRow.createCell(0).setCellValue("Statement Period:");
        periodRow.createCell(1).setCellValue(statement.getPeriod().getStartDate() + " to " + 
            statement.getPeriod().getEndDate());
        
        rowNum++; // Empty row
        
        // Balance Summary
        Row balanceHeaderRow = sheet.createRow(rowNum++);
        balanceHeaderRow.createCell(0).setCellValue("Balance Summary");
        
        Row openingRow = sheet.createRow(rowNum++);
        openingRow.createCell(0).setCellValue("Opening Balance:");
        openingRow.createCell(1).setCellValue(statement.getBalanceSummary().getOpeningBalance().doubleValue());
        
        Row closingRow = sheet.createRow(rowNum++);
        closingRow.createCell(0).setCellValue("Closing Balance:");
        closingRow.createCell(1).setCellValue(statement.getBalanceSummary().getClosingBalance().doubleValue());
        
        rowNum++; // Empty row
        
        // Transaction Headers
        Row headerRow = sheet.createRow(rowNum++);
        String[] headers = {"Date", "Description", "Reference", "Debit", "Credit", "Balance"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Transaction Data
        for (StatementDocument.Transaction tx : statement.getTransactions()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(tx.getDate().toString());
            row.createCell(1).setCellValue(tx.getDescription());
            row.createCell(2).setCellValue(tx.getReference());
            if (tx.getDebit() != null) {
                row.createCell(3).setCellValue(tx.getDebit().doubleValue());
            }
            if (tx.getCredit() != null) {
                row.createCell(4).setCellValue(tx.getCredit().doubleValue());
            }
            row.createCell(5).setCellValue(tx.getBalance().doubleValue());
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void generateMISExcel(Workbook workbook, MISDocument mis, 
                                 CellStyle headerStyle, CellStyle dataStyle) {
        // KPI Sheet
        Sheet kpiSheet = workbook.createSheet("KPIs");
        int rowNum = 0;
        
        Row headerRow = kpiSheet.createRow(rowNum++);
        String[] headers = {"Metric", "Category", "Actual", "Target", "Variance", "Status"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        for (MISDocument.KPIMetric kpi : mis.getKeyMetrics()) {
            Row row = kpiSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(kpi.getName());
            row.createCell(1).setCellValue(kpi.getCategory());
            row.createCell(2).setCellValue(kpi.getActualValue().doubleValue());
            row.createCell(3).setCellValue(kpi.getTargetValue().doubleValue());
            row.createCell(4).setCellValue(kpi.getVariance().doubleValue());
            row.createCell(5).setCellValue(kpi.getStatus());
        }
        
        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            kpiSheet.autoSizeColumn(i);
        }
        
        // Segment Analysis Sheet
        if (!mis.getSegmentAnalysis().isEmpty()) {
            Sheet segmentSheet = workbook.createSheet("Segment Analysis");
            rowNum = 0;
            
            Row segmentHeaderRow = segmentSheet.createRow(rowNum++);
            String[] segmentHeaders = {"Segment", "Revenue", "Cost", "Profit", "Margin %", "Volume"};
            for (int i = 0; i < segmentHeaders.length; i++) {
                Cell cell = segmentHeaderRow.createCell(i);
                cell.setCellValue(segmentHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            
            for (MISDocument.BusinessSegment segment : mis.getSegmentAnalysis()) {
                Row row = segmentSheet.createRow(rowNum++);
                row.createCell(0).setCellValue(segment.getSegmentName());
                row.createCell(1).setCellValue(segment.getRevenue().doubleValue());
                row.createCell(2).setCellValue(segment.getCost().doubleValue());
                row.createCell(3).setCellValue(segment.getProfit().doubleValue());
                row.createCell(4).setCellValue(segment.getMargin().doubleValue());
                row.createCell(5).setCellValue(segment.getTransactionVolume());
            }
            
            for (int i = 0; i < segmentHeaders.length; i++) {
                segmentSheet.autoSizeColumn(i);
            }
        }
    }
    
    private void generateRiskReportExcel(Workbook workbook, RiskReport report, 
                                        CellStyle headerStyle, CellStyle dataStyle) {
        // Summary Sheet
        Sheet summarySheet = workbook.createSheet("Risk Summary");
        int rowNum = 0;
        
        Row titleRow = summarySheet.createRow(rowNum++);
        titleRow.createCell(0).setCellValue("Risk Report - " + report.getReportType());
        
        rowNum++; // Empty row
        
        Row riskLevelRow = summarySheet.createRow(rowNum++);
        riskLevelRow.createCell(0).setCellValue("Overall Risk Level:");
        riskLevelRow.createCell(1).setCellValue(report.getSummary().getOverallRiskLevel());
        
        Row riskScoreRow = summarySheet.createRow(rowNum++);
        riskScoreRow.createCell(0).setCellValue("Risk Score:");
        riskScoreRow.createCell(1).setCellValue(report.getSummary().getRiskScore().doubleValue());
        
        rowNum++; // Empty row
        
        // Metrics Sheet
        Sheet metricsSheet = workbook.createSheet("Risk Metrics");
        rowNum = 0;
        
        Row metricsHeaderRow = metricsSheet.createRow(rowNum++);
        String[] metricHeaders = {"Metric", "Category", "Value", "Threshold", "Status"};
        for (int i = 0; i < metricHeaders.length; i++) {
            Cell cell = metricsHeaderRow.createCell(i);
            cell.setCellValue(metricHeaders[i]);
            cell.setCellStyle(headerStyle);
        }
        
        for (RiskReport.RiskMetric metric : report.getMetrics()) {
            Row row = metricsSheet.createRow(rowNum++);
            row.createCell(0).setCellValue(metric.getName());
            row.createCell(1).setCellValue(metric.getCategory());
            row.createCell(2).setCellValue(metric.getValue().doubleValue());
            row.createCell(3).setCellValue(metric.getThreshold().doubleValue());
            row.createCell(4).setCellValue(metric.getStatus());
        }
        
        for (int i = 0; i < metricHeaders.length; i++) {
            metricsSheet.autoSizeColumn(i);
        }
    }
    
    private void generateListExcel(Workbook workbook, List<?> list, 
                                  CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("Data");
        
        if (list.isEmpty()) {
            return;
        }
        
        // Implement generic list to Excel conversion using reflection
        Object firstObject = list.get(0);
        Class<?> clazz = firstObject.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < fields.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(fields[i].getName());
            cell.setCellStyle(headerStyle);
        }
        
        // Create data rows
        for (int rowIdx = 0; rowIdx < list.size(); rowIdx++) {
            Row dataRow = sheet.createRow(rowIdx + 1);
            Object obj = list.get(rowIdx);
            
            for (int colIdx = 0; colIdx < fields.length; colIdx++) {
                Cell cell = dataRow.createCell(colIdx);
                try {
                    fields[colIdx].setAccessible(true);
                    Object value = fields[colIdx].get(obj);
                    if (value != null) {
                        if (value instanceof Number) {
                            cell.setCellValue(((Number) value).doubleValue());
                        } else if (value instanceof Boolean) {
                            cell.setCellValue((Boolean) value);
                        } else if (value instanceof LocalDateTime) {
                            cell.setCellValue(((LocalDateTime) value).toString());
                        } else {
                            cell.setCellValue(value.toString());
                        }
                    }
                    cell.setCellStyle(dataStyle);
                } catch (IllegalAccessException e) {
                    log.warn("Could not access field {} on object {}", fields[colIdx].getName(), obj.getClass());
                    cell.setCellValue("N/A");
                }
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < fields.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }
    
    private void generateGenericExcel(Workbook workbook, Object data, 
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("Report");
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue(data.toString());
    }
    
    private void generateListCSV(CSVPrinter csvPrinter, List<?> list) throws IOException {
        if (list.isEmpty()) {
            return;
        }
        
        // Implement generic list to CSV conversion using reflection
        Object firstObject = list.get(0);
        Class<?> clazz = firstObject.getClass();
        Field[] fields = clazz.getDeclaredFields();
        
        // Write header
        List<String> headers = Arrays.stream(fields)
            .map(Field::getName)
            .collect(Collectors.toList());
        csvPrinter.printRecord(headers);
        
        // Write data rows
        for (Object obj : list) {
            List<Object> row = new ArrayList<>();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    row.add(value != null ? value.toString() : "");
                } catch (IllegalAccessException e) {
                    log.warn("Could not access field {} on object {}", field.getName(), obj.getClass());
                    row.add("N/A");
                }
            }
            csvPrinter.printRecord(row);
        }
    }
    
    private void generateMapCSV(CSVPrinter csvPrinter, Map<?, ?> map) throws IOException {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            csvPrinter.printRecord(entry.getKey(), entry.getValue());
        }
    }
    
    private void generateGenericCSV(CSVPrinter csvPrinter, Object data) throws IOException {
        csvPrinter.printRecord(data.toString());
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
    
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }
    
    private void addTableHeader(PdfPTable table, String header) {
        PdfPCell cell = new PdfPCell(new Phrase(header, 
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE)));
        cell.setBackgroundColor(BaseColor.GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }
    
    private void addTableCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, 
            FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.BLACK)));
        table.addCell(cell);
    }
    
    private void addTableRow(PdfPTable table, String label, String value) {
        table.addCell(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        table.addCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 10)));
    }
    
    private String formatCurrency(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }
}