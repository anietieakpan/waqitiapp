package com.waqiti.reporting.engine;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.waqiti.reporting.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Optimized PDF generation with streaming, batching, and memory-efficient processing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptimizedPDFGenerator {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Optimization parameters
    private static final int BATCH_SIZE = 100;
    private static final int MAX_MEMORY_BUFFER = 10 * 1024 * 1024; // 10MB
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    
    // Font cache to avoid repeated font creation
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
    private static final Font SUBTITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 12, BaseColor.GRAY);
    private static final Font SECTION_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
    private static final Font NORMAL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
    private static final Font SMALL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.BLACK);
    private static final Font HEADER_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.WHITE);
    
    /**
     * Generate PDF with streaming and optimization
     */
    public byte[] generateOptimizedPDF(Object reportData, String reportType) throws ReportGenerationException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            
            // Enable compression
            writer.setCompressionLevel(PdfWriter.BEST_COMPRESSION);
            writer.setFullCompression();
            
            // Set PDF version for better compatibility
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            
            // Add metadata
            addMetadata(document, reportType);
            
            document.open();
            
            // Add watermark if needed
            addWatermark(writer);
            
            // Add header
            addOptimizedHeader(document, reportType);
            
            // Generate content based on report type
            generateOptimizedContent(document, writer, reportData, reportType);
            
            document.close();
            
            byte[] pdfBytes = baos.toByteArray();
            log.info("Generated PDF report: type={}, size={} bytes", reportType, pdfBytes.length);
            
            return pdfBytes;
            
        } catch (Exception e) {
            log.error("Error generating optimized PDF report", e);
            throw new ReportGenerationException("Failed to generate PDF report", e);
        }
    }
    
    /**
     * Generate large statement PDF with streaming
     */
    public CompletableFuture<byte[]> generateLargeStatementPDFAsync(StatementDocument statement) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return generateStreamingStatementPDF(statement);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate statement PDF", e);
            }
        }, executorService);
    }
    
    private byte[] generateStreamingStatementPDF(StatementDocument statement) throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        
        // Enable streaming mode for large documents
        writer.setStrictImageSequence(true);
        writer.setCompressionLevel(PdfWriter.BEST_COMPRESSION);
        
        document.open();
        
        // Add header
        addStatementHeader(document, statement);
        
        // Process transactions in batches
        List<StatementDocument.Transaction> transactions = statement.getTransactions();
        int totalTransactions = transactions.size();
        
        PdfPTable transactionTable = createTransactionTable();
        
        for (int i = 0; i < totalTransactions; i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, totalTransactions);
            List<StatementDocument.Transaction> batch = transactions.subList(i, endIndex);
            
            processBatchTransactions(transactionTable, batch);
            
            // Flush to document periodically to manage memory
            if (i + BATCH_SIZE < totalTransactions && transactionTable.size() > 50) {
                document.add(transactionTable);
                transactionTable = createTransactionTable(); // Start new table
            }
        }
        
        // Add remaining transactions
        if (transactionTable.size() > 0) {
            document.add(transactionTable);
        }
        
        // Add footer
        addStatementFooter(document, statement);
        
        document.close();
        
        return baos.toByteArray();
    }
    
    private void generateOptimizedContent(Document document, PdfWriter writer, 
                                         Object reportData, String reportType) throws DocumentException {
        if (reportData instanceof StatementDocument) {
            generateOptimizedStatementPDF(document, (StatementDocument) reportData);
        } else if (reportData instanceof MISDocument) {
            generateOptimizedMISPDF(document, (MISDocument) reportData);
        } else if (reportData instanceof RiskReport) {
            generateOptimizedRiskReportPDF(document, (RiskReport) reportData);
        } else if (reportData instanceof ComplianceReport) {
            generateOptimizedCompliancePDF(document, (ComplianceReport) reportData);
        } else {
            generateGenericPDF(document, reportData);
        }
    }
    
    private void generateOptimizedStatementPDF(Document document, StatementDocument statement) throws DocumentException {
        // Account Information Section
        PdfPTable accountTable = new PdfPTable(2);
        accountTable.setWidthPercentage(100);
        accountTable.setSpacingAfter(10);
        
        addStyledCell(accountTable, "Account Information", SECTION_FONT, 2, BaseColor.LIGHT_GRAY);
        addKeyValueRow(accountTable, "Account Number:", statement.getAccountNumber());
        addKeyValueRow(accountTable, "Account Type:", statement.getAccountType());
        addKeyValueRow(accountTable, "Statement Period:", 
            statement.getPeriod().getStartDate() + " to " + statement.getPeriod().getEndDate());
        
        document.add(accountTable);
        
        // Balance Summary Section
        PdfPTable balanceTable = new PdfPTable(2);
        balanceTable.setWidthPercentage(100);
        balanceTable.setSpacingAfter(10);
        
        addStyledCell(balanceTable, "Balance Summary", SECTION_FONT, 2, BaseColor.LIGHT_GRAY);
        addKeyValueRow(balanceTable, "Opening Balance:", formatCurrency(statement.getBalanceSummary().getOpeningBalance()));
        addKeyValueRow(balanceTable, "Total Credits:", formatCurrency(statement.getBalanceSummary().getTotalCredits()));
        addKeyValueRow(balanceTable, "Total Debits:", formatCurrency(statement.getBalanceSummary().getTotalDebits()));
        addKeyValueRow(balanceTable, "Closing Balance:", formatCurrency(statement.getBalanceSummary().getClosingBalance()));
        
        document.add(balanceTable);
        
        // Transaction History Section
        Paragraph txHeader = new Paragraph("Transaction History", SECTION_FONT);
        txHeader.setSpacingAfter(5);
        document.add(txHeader);
        
        PdfPTable transactionTable = createTransactionTable();
        
        // Process transactions efficiently
        for (StatementDocument.Transaction tx : statement.getTransactions()) {
            addTransactionRow(transactionTable, tx);
        }
        
        document.add(transactionTable);
    }
    
    private void generateOptimizedMISPDF(Document document, MISDocument mis) throws DocumentException {
        // Executive Summary
        Paragraph execSummary = new Paragraph("Executive Summary", SECTION_FONT);
        execSummary.setSpacingAfter(5);
        document.add(execSummary);
        
        Paragraph summaryText = new Paragraph(mis.getExecutiveSummary().getSummary(), NORMAL_FONT);
        summaryText.setSpacingAfter(10);
        document.add(summaryText);
        
        // KPI Dashboard
        PdfPTable kpiTable = new PdfPTable(5);
        kpiTable.setWidthPercentage(100);
        kpiTable.setSpacingAfter(10);
        
        // Headers
        addTableHeader(kpiTable, "KPI");
        addTableHeader(kpiTable, "Actual");
        addTableHeader(kpiTable, "Target");
        addTableHeader(kpiTable, "Variance");
        addTableHeader(kpiTable, "Status");
        
        // KPI Data
        for (MISDocument.KPIMetric kpi : mis.getKeyMetrics()) {
            addKPIRow(kpiTable, kpi);
        }
        
        document.add(kpiTable);
        
        // Segment Analysis
        if (!mis.getSegmentAnalysis().isEmpty()) {
            Paragraph segmentHeader = new Paragraph("Business Segment Analysis", SECTION_FONT);
            segmentHeader.setSpacingBefore(10);
            segmentHeader.setSpacingAfter(5);
            document.add(segmentHeader);
            
            PdfPTable segmentTable = createSegmentTable();
            
            for (MISDocument.BusinessSegment segment : mis.getSegmentAnalysis()) {
                addSegmentRow(segmentTable, segment);
            }
            
            document.add(segmentTable);
        }
    }
    
    private void generateOptimizedRiskReportPDF(Document document, RiskReport report) throws DocumentException {
        // Risk Summary Dashboard
        PdfPTable summaryTable = new PdfPTable(2);
        summaryTable.setWidthPercentage(100);
        summaryTable.setSpacingAfter(10);
        
        addStyledCell(summaryTable, "Risk Assessment Summary", SECTION_FONT, 2, BaseColor.LIGHT_GRAY);
        addKeyValueRow(summaryTable, "Overall Risk Level:", report.getSummary().getOverallRiskLevel());
        addKeyValueRow(summaryTable, "Risk Score:", report.getSummary().getRiskScore().toString());
        addKeyValueRow(summaryTable, "Total Alerts:", String.valueOf(report.getSummary().getTotalAlerts()));
        addKeyValueRow(summaryTable, "High Risk Items:", String.valueOf(report.getSummary().getHighRiskCount()));
        
        document.add(summaryTable);
        
        // Risk Alerts
        if (!report.getAlerts().isEmpty()) {
            Paragraph alertHeader = new Paragraph("Risk Alerts", SECTION_FONT);
            alertHeader.setSpacingAfter(5);
            document.add(alertHeader);
            
            PdfPTable alertTable = createAlertTable();
            
            for (RiskReport.RiskAlert alert : report.getAlerts()) {
                addAlertRow(alertTable, alert);
            }
            
            document.add(alertTable);
        }
        
        // Risk Metrics
        if (!report.getMetrics().isEmpty()) {
            Paragraph metricsHeader = new Paragraph("Risk Metrics", SECTION_FONT);
            metricsHeader.setSpacingBefore(10);
            metricsHeader.setSpacingAfter(5);
            document.add(metricsHeader);
            
            PdfPTable metricsTable = createMetricsTable();
            
            for (RiskReport.RiskMetric metric : report.getMetrics()) {
                addMetricRow(metricsTable, metric);
            }
            
            document.add(metricsTable);
        }
    }
    
    private void generateOptimizedCompliancePDF(Document document, ComplianceReport report) throws DocumentException {
        // Compliance Summary
        Paragraph header = new Paragraph("Compliance Report", TITLE_FONT);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(10);
        document.add(header);
        
        // Compliance Status
        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(100);
        statusTable.setSpacingAfter(10);
        
        addStyledCell(statusTable, "Compliance Status", SECTION_FONT, 2, BaseColor.LIGHT_GRAY);
        addKeyValueRow(statusTable, "Overall Status:", report.getOverallStatus());
        addKeyValueRow(statusTable, "Compliance Score:", report.getComplianceScore() + "%");
        addKeyValueRow(statusTable, "Last Audit Date:", report.getLastAuditDate().format(DATE_FORMATTER));
        
        document.add(statusTable);
        
        // Compliance Items
        if (!report.getComplianceItems().isEmpty()) {
            PdfPTable itemsTable = createComplianceItemsTable();
            
            for (ComplianceReport.ComplianceItem item : report.getComplianceItems()) {
                addComplianceItemRow(itemsTable, item);
            }
            
            document.add(itemsTable);
        }
    }
    
    // Helper methods
    private void addMetadata(Document document, String reportType) {
        document.addTitle("Waqiti " + reportType);
        document.addAuthor("Waqiti Reporting System");
        document.addSubject(reportType + " Report");
        document.addCreator("Waqiti Platform");
        document.addCreationDate();
    }
    
    private void addWatermark(PdfWriter writer) {
        try {
            PdfContentByte canvas = writer.getDirectContentUnder();
            canvas.saveState();
            canvas.setGState(new PdfGState(0.1f)); // 10% opacity
            canvas.beginText();
            canvas.setFontAndSize(BaseFont.createFont(), 50);
            canvas.showTextAligned(Element.ALIGN_CENTER, "CONFIDENTIAL", 
                PageSize.A4.getWidth() / 2, PageSize.A4.getHeight() / 2, 45);
            canvas.endText();
            canvas.restoreState();
        } catch (Exception e) {
            log.warn("Failed to add watermark", e);
        }
    }
    
    private void addOptimizedHeader(Document document, String reportType) throws DocumentException {
        // Logo placeholder
        Paragraph logo = new Paragraph("WAQITI", TITLE_FONT);
        logo.setAlignment(Element.ALIGN_CENTER);
        document.add(logo);
        
        // Report title
        Paragraph title = new Paragraph(reportType, SUBTITLE_FONT);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);
        
        // Generation date
        Paragraph date = new Paragraph("Generated on " + LocalDateTime.now().format(DATETIME_FORMATTER), SMALL_FONT);
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingAfter(20);
        document.add(date);
    }
    
    private PdfPTable createTransactionTable() throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2, 1.5f, 1.5f, 2});
        
        // Headers
        addTableHeader(table, "Date");
        addTableHeader(table, "Description");
        addTableHeader(table, "Reference");
        addTableHeader(table, "Debit");
        addTableHeader(table, "Credit");
        addTableHeader(table, "Balance");
        
        return table;
    }
    
    private void addTransactionRow(PdfPTable table, StatementDocument.Transaction tx) {
        addTableCell(table, tx.getDate().format(DATE_FORMATTER), SMALL_FONT);
        addTableCell(table, tx.getDescription(), SMALL_FONT);
        addTableCell(table, tx.getReference(), SMALL_FONT);
        addTableCell(table, tx.getDebit() != null ? formatCurrency(tx.getDebit()) : "", SMALL_FONT);
        addTableCell(table, tx.getCredit() != null ? formatCurrency(tx.getCredit()) : "", SMALL_FONT);
        addTableCell(table, formatCurrency(tx.getBalance()), SMALL_FONT);
    }
    
    private void processBatchTransactions(PdfPTable table, List<StatementDocument.Transaction> batch) {
        for (StatementDocument.Transaction tx : batch) {
            addTransactionRow(table, tx);
        }
    }
    
    private void addStatementHeader(Document document, StatementDocument statement) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(10);
        
        addKeyValueRow(headerTable, "Account:", statement.getAccountNumber());
        addKeyValueRow(headerTable, "Period:", statement.getPeriod().getStartDate() + " to " + 
            statement.getPeriod().getEndDate());
        
        document.add(headerTable);
    }
    
    private void addStatementFooter(Document document, StatementDocument statement) throws DocumentException {
        Paragraph footer = new Paragraph();
        footer.setSpacingBefore(20);
        footer.add(new Chunk("This is an electronic statement. ", SMALL_FONT));
        footer.add(new Chunk("For queries, contact support@example.com", SMALL_FONT));
        document.add(footer);
    }
    
    // Table creation methods
    private PdfPTable createSegmentTable() throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 2, 2, 2, 1.5f, 1.5f});
        
        addTableHeader(table, "Segment");
        addTableHeader(table, "Revenue");
        addTableHeader(table, "Cost");
        addTableHeader(table, "Profit");
        addTableHeader(table, "Margin %");
        addTableHeader(table, "Volume");
        
        return table;
    }
    
    private PdfPTable createAlertTable() throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 2, 4, 2});
        
        addTableHeader(table, "Severity");
        addTableHeader(table, "Category");
        addTableHeader(table, "Description");
        addTableHeader(table, "Date");
        
        return table;
    }
    
    private PdfPTable createMetricsTable() throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        
        addTableHeader(table, "Metric");
        addTableHeader(table, "Value");
        addTableHeader(table, "Threshold");
        addTableHeader(table, "Status");
        addTableHeader(table, "Trend");
        
        return table;
    }
    
    private PdfPTable createComplianceItemsTable() throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        
        addTableHeader(table, "Requirement");
        addTableHeader(table, "Status");
        addTableHeader(table, "Last Checked");
        addTableHeader(table, "Notes");
        
        return table;
    }
    
    // Row addition methods
    private void addKPIRow(PdfPTable table, MISDocument.KPIMetric kpi) {
        addTableCell(table, kpi.getName(), SMALL_FONT);
        addTableCell(table, formatNumber(kpi.getActualValue()) + " " + kpi.getUnit(), SMALL_FONT);
        addTableCell(table, formatNumber(kpi.getTargetValue()) + " " + kpi.getUnit(), SMALL_FONT);
        addTableCell(table, formatNumber(kpi.getVariance()) + "%", SMALL_FONT);
        
        PdfPCell statusCell = new PdfPCell(new Phrase(kpi.getStatus(), SMALL_FONT));
        statusCell.setBackgroundColor(getStatusColor(kpi.getStatus()));
        table.addCell(statusCell);
    }
    
    private void addSegmentRow(PdfPTable table, MISDocument.BusinessSegment segment) {
        addTableCell(table, segment.getSegmentName(), SMALL_FONT);
        addTableCell(table, formatCurrency(segment.getRevenue()), SMALL_FONT);
        addTableCell(table, formatCurrency(segment.getCost()), SMALL_FONT);
        addTableCell(table, formatCurrency(segment.getProfit()), SMALL_FONT);
        addTableCell(table, formatNumber(segment.getMargin()) + "%", SMALL_FONT);
        addTableCell(table, String.valueOf(segment.getTransactionVolume()), SMALL_FONT);
    }
    
    private void addAlertRow(PdfPTable table, RiskReport.RiskAlert alert) {
        PdfPCell severityCell = new PdfPCell(new Phrase(alert.getSeverity(), SMALL_FONT));
        severityCell.setBackgroundColor(getSeverityColor(alert.getSeverity()));
        table.addCell(severityCell);
        
        addTableCell(table, alert.getCategory(), SMALL_FONT);
        addTableCell(table, alert.getDescription(), SMALL_FONT);
        addTableCell(table, alert.getDetectedDate().format(DATE_FORMATTER), SMALL_FONT);
    }
    
    private void addMetricRow(PdfPTable table, RiskReport.RiskMetric metric) {
        addTableCell(table, metric.getName(), SMALL_FONT);
        addTableCell(table, formatNumber(metric.getValue()), SMALL_FONT);
        addTableCell(table, formatNumber(metric.getThreshold()), SMALL_FONT);
        
        PdfPCell statusCell = new PdfPCell(new Phrase(metric.getStatus(), SMALL_FONT));
        statusCell.setBackgroundColor(getStatusColor(metric.getStatus()));
        table.addCell(statusCell);
        
        addTableCell(table, metric.getTrend(), SMALL_FONT);
    }
    
    private void addComplianceItemRow(PdfPTable table, ComplianceReport.ComplianceItem item) {
        addTableCell(table, item.getRequirement(), SMALL_FONT);
        
        PdfPCell statusCell = new PdfPCell(new Phrase(item.getStatus(), SMALL_FONT));
        statusCell.setBackgroundColor(getComplianceStatusColor(item.getStatus()));
        table.addCell(statusCell);
        
        addTableCell(table, item.getLastChecked().format(DATE_FORMATTER), SMALL_FONT);
        addTableCell(table, item.getNotes(), SMALL_FONT);
    }
    
    // Utility methods
    private void addTableHeader(PdfPTable table, String header) {
        PdfPCell cell = new PdfPCell(new Phrase(header, HEADER_FONT));
        cell.setBackgroundColor(BaseColor.DARK_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }
    
    private void addTableCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(3);
        table.addCell(cell);
    }
    
    private void addStyledCell(PdfPTable table, String value, Font font, int colspan, BaseColor bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setColspan(colspan);
        cell.setBackgroundColor(bgColor);
        cell.setPadding(5);
        table.addCell(cell);
    }
    
    private void addKeyValueRow(PdfPTable table, String key, String value) {
        PdfPCell keyCell = new PdfPCell(new Phrase(key, NORMAL_FONT));
        keyCell.setBorder(Rectangle.NO_BORDER);
        keyCell.setPaddingLeft(10);
        table.addCell(keyCell);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value, NORMAL_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(valueCell);
    }
    
    private void generateGenericPDF(Document document, Object data) throws DocumentException {
        Paragraph content = new Paragraph(data.toString(), NORMAL_FONT);
        document.add(content);
    }
    
    private String formatCurrency(BigDecimal amount) {
        return String.format("$%,.2f", amount);
    }
    
    private String formatNumber(BigDecimal number) {
        return String.format("%,.2f", number);
    }
    
    private BaseColor getStatusColor(String status) {
        switch (status.toUpperCase()) {
            case "GOOD":
            case "ACHIEVED":
            case "LOW":
                return new BaseColor(144, 238, 144); // Light green
            case "WARNING":
            case "MEDIUM":
                return new BaseColor(255, 255, 224); // Light yellow
            case "CRITICAL":
            case "HIGH":
            case "FAILED":
                return new BaseColor(255, 182, 193); // Light red
            default:
                return BaseColor.WHITE;
        }
    }
    
    private BaseColor getSeverityColor(String severity) {
        switch (severity.toUpperCase()) {
            case "LOW":
                return new BaseColor(144, 238, 144); // Light green
            case "MEDIUM":
                return new BaseColor(255, 255, 224); // Light yellow
            case "HIGH":
                return new BaseColor(255, 182, 193); // Light red
            case "CRITICAL":
                return new BaseColor(255, 99, 71); // Tomato
            default:
                return BaseColor.WHITE;
        }
    }
    
    private BaseColor getComplianceStatusColor(String status) {
        switch (status.toUpperCase()) {
            case "COMPLIANT":
                return new BaseColor(144, 238, 144); // Light green
            case "PARTIALLY_COMPLIANT":
                return new BaseColor(255, 255, 224); // Light yellow
            case "NON_COMPLIANT":
                return new BaseColor(255, 182, 193); // Light red
            default:
                return BaseColor.WHITE;
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        executorService.shutdown();
    }
}