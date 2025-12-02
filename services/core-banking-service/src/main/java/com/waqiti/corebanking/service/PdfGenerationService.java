package com.waqiti.corebanking.service;

import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.service.StatementGenerationService.StatementSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF Generation Service
 *
 * Handles PDF generation for statements and reports.
 * Uses iText library for PDF creation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfGenerationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    /**
     * Generate PDF statement for an account
     */
    public byte[] generateStatementPdf(Account account, List<Transaction> transactions,
                                       StatementSummary summary) {

        log.info("Generating PDF statement for account: {}", account.getAccountNumber());

        try {
            // Using a simple HTML-to-PDF approach for now
            // In production, you'd use libraries like iText, Flying Saucer, or similar
            String htmlContent = generateStatementHtml(account, transactions, summary);
            return convertHtmlToPdf(htmlContent);

        } catch (Exception e) {
            log.error("Error generating PDF statement for account: {}", account.getAccountNumber(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    /**
     * Generate HTML content for statement
     */
    private String generateStatementHtml(Account account, List<Transaction> transactions,
                                         StatementSummary summary) {

        StringBuilder html = new StringBuilder();

        // HTML header with CSS
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>Account Statement</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 20px;
                        color: #333;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px solid #0066cc;
                        padding-bottom: 20px;
                        margin-bottom: 30px;
                    }
                    .company-name {
                        font-size: 24px;
                        font-weight: bold;
                        color: #0066cc;
                        margin-bottom: 5px;
                    }
                    .statement-title {
                        font-size: 18px;
                        color: #666;
                    }
                    .account-info {
                        background-color: #f8f9fa;
                        padding: 15px;
                        border-radius: 5px;
                        margin-bottom: 20px;
                    }
                    .account-info table {
                        width: 100%;
                        border-collapse: collapse;
                    }
                    .account-info td {
                        padding: 5px 10px;
                        border: none;
                    }
                    .account-info .label {
                        font-weight: bold;
                        width: 150px;
                    }
                    .summary {
                        background-color: #e8f4f8;
                        padding: 15px;
                        border-radius: 5px;
                        margin-bottom: 20px;
                    }
                    .summary h3 {
                        margin-top: 0;
                        color: #0066cc;
                    }
                    .summary-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 10px;
                    }
                    .summary-item {
                        display: flex;
                        justify-content: space-between;
                        padding: 5px 0;
                    }
                    .summary-item .label {
                        font-weight: bold;
                    }
                    .transactions {
                        margin-top: 20px;
                    }
                    .transactions h3 {
                        color: #0066cc;
                        border-bottom: 1px solid #ccc;
                        padding-bottom: 5px;
                    }
                    .transaction-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-top: 10px;
                    }
                    .transaction-table th {
                        background-color: #0066cc;
                        color: white;
                        padding: 10px;
                        text-align: left;
                        font-weight: bold;
                    }
                    .transaction-table td {
                        padding: 8px 10px;
                        border-bottom: 1px solid #ddd;
                    }
                    .transaction-table tr:nth-child(even) {
                        background-color: #f8f9fa;
                    }
                    .amount-credit {
                        color: #28a745;
                        font-weight: bold;
                    }
                    .amount-debit {
                        color: #dc3545;
                        font-weight: bold;
                    }
                    .footer {
                        margin-top: 40px;
                        padding-top: 20px;
                        border-top: 1px solid #ccc;
                        font-size: 12px;
                        color: #666;
                        text-align: center;
                    }
                    .page-break {
                        page-break-before: always;
                    }
                </style>
            </head>
            <body>
            """);

        // Header
        html.append("""
            <div class="header">
                <div class="company-name">WAQITI</div>
                <div class="statement-title">Account Statement</div>
            </div>
            """);

        // Account Information
        html.append("<div class=\"account-info\">");
        html.append("<table>");
        html.append("<tr><td class=\"label\">Account Holder:</td><td>").append(account.getAccountName()).append("</td></tr>");
        html.append("<tr><td class=\"label\">Account Number:</td><td>").append(account.getAccountNumber()).append("</td></tr>");
        html.append("<tr><td class=\"label\">Account Type:</td><td>").append(account.getAccountType().toString().replace("_", " ")).append("</td></tr>");
        html.append("<tr><td class=\"label\">Currency:</td><td>").append(account.getCurrency()).append("</td></tr>");
        html.append("<tr><td class=\"label\">Statement Date:</td><td>").append(java.time.LocalDateTime.now().format(DATETIME_FORMAT)).append("</td></tr>");
        html.append("</table>");
        html.append("</div>");

        // Summary
        html.append("<div class=\"summary\">");
        html.append("<h3>Statement Summary</h3>");
        html.append("<div class=\"summary-grid\">");

        html.append("<div class=\"summary-item\">");
        html.append("<span class=\"label\">Opening Balance:</span>");
        html.append("<span>").append(formatCurrency(summary.getOpeningBalance(), account.getCurrency())).append("</span>");
        html.append("</div>");

        html.append("<div class=\"summary-item\">");
        html.append("<span class=\"label\">Closing Balance:</span>");
        html.append("<span>").append(formatCurrency(summary.getClosingBalance(), account.getCurrency())).append("</span>");
        html.append("</div>");

        html.append("<div class=\"summary-item\">");
        html.append("<span class=\"label\">Total Credits:</span>");
        html.append("<span class=\"amount-credit\">").append(formatCurrency(summary.getTotalCredits(), account.getCurrency())).append("</span>");
        html.append("</div>");

        html.append("<div class=\"summary-item\">");
        html.append("<span class=\"label\">Total Debits:</span>");
        html.append("<span class=\"amount-debit\">").append(formatCurrency(summary.getTotalDebits(), account.getCurrency())).append("</span>");
        html.append("</div>");

        html.append("<div class=\"summary-item\">");
        html.append("<span class=\"label\">Total Fees:</span>");
        html.append("<span>").append(formatCurrency(summary.getTotalFees(), account.getCurrency())).append("</span>");
        html.append("</div>");

        html.append("<div class=\"summary-item\">");
        html.append("<span class=\"label\">Total Interest:</span>");
        html.append("<span class=\"amount-credit\">").append(formatCurrency(summary.getTotalInterest(), account.getCurrency())).append("</span>");
        html.append("</div>");

        html.append("</div>");
        html.append("</div>");

        // Transactions
        html.append("<div class=\"transactions\">");
        html.append("<h3>Transaction Details (").append(transactions.size()).append(" transactions)</h3>");

        if (!transactions.isEmpty()) {
            html.append("<table class=\"transaction-table\">");
            html.append("<thead>");
            html.append("<tr>");
            html.append("<th>Date</th>");
            html.append("<th>Description</th>");
            html.append("<th>Type</th>");
            html.append("<th>Reference</th>");
            html.append("<th>Amount</th>");
            html.append("<th>Balance</th>");
            html.append("</tr>");
            html.append("</thead>");
            html.append("<tbody>");

            BigDecimal runningBalance = summary.getOpeningBalance();

            for (Transaction transaction : transactions) {
                if (transaction.getStatus() == Transaction.TransactionStatus.COMPLETED) {
                    boolean isDebit = isDebitTransaction(transaction, account.getId());
                    BigDecimal amount = isDebit ? transaction.getAmount().negate() : transaction.getAmount();
                    runningBalance = runningBalance.add(amount);

                    html.append("<tr>");
                    html.append("<td>").append(transaction.getCreatedAt().format(DATE_FORMAT)).append("</td>");
                    html.append("<td>").append(escapeHtml(transaction.getDescription() != null ? transaction.getDescription() : "")).append("</td>");
                    html.append("<td>").append(formatTransactionType(transaction.getType().toString())).append("</td>");
                    html.append("<td>").append(transaction.getReferenceNumber() != null ? transaction.getReferenceNumber() : "").append("</td>");

                    String amountClass = isDebit ? "amount-debit" : "amount-credit";
                    String amountSign = isDebit ? "-" : "+";
                    html.append("<td class=\"").append(amountClass).append("\">")
                            .append(amountSign).append(formatCurrency(transaction.getAmount(), account.getCurrency()))
                            .append("</td>");

                    html.append("<td>").append(formatCurrency(runningBalance, account.getCurrency())).append("</td>");
                    html.append("</tr>");
                }
            }

            html.append("</tbody>");
            html.append("</table>");
        } else {
            html.append("<p>No transactions found for this period.</p>");
        }

        html.append("</div>");

        // Footer
        html.append("<div class=\"footer\">");
        html.append("<p>This statement was generated electronically and is valid without signature.</p>");
        html.append("<p>WAQITI - Digital Payment Platform | Generated on ").append(java.time.LocalDateTime.now().format(DATETIME_FORMAT)).append("</p>");
        html.append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    /**
     * Convert HTML to PDF
     * This is a simplified implementation - in production use proper PDF libraries
     */
    private byte[] convertHtmlToPdf(String htmlContent) {
        try {
            log.info("Converting HTML content to PDF using Flying Saucer renderer");

            // Create proper XHTML document
            String xhtmlContent = createValidXhtml(htmlContent);

            // Create PDF document
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Use Flying Saucer (xhtmlrenderer) for HTML to PDF conversion
            ITextRenderer renderer = new ITextRenderer();

            // Set up document with proper encoding
            renderer.setDocumentFromString(xhtmlContent);
            renderer.layout();

            // Generate PDF
            renderer.createPDF(outputStream);
            renderer.finishPDF();

            byte[] pdfBytes = outputStream.toByteArray();

            log.info("Successfully generated PDF with size: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Failed to convert HTML to PDF", e);

            // Fallback: Generate a simple PDF with error message
            return generateErrorPdf("PDF generation failed: " + e.getMessage());
        }
    }

    private String createValidXhtml(String htmlContent) {
        // Ensure XHTML compliance for Flying Saucer
        String xhtml = htmlContent;

        // Add DOCTYPE and proper structure if missing
        if (!xhtml.contains("<!DOCTYPE")) {
            xhtml = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
                    "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" + xhtml;
        }

        // Ensure html tag has xmlns
        if (!xhtml.contains("xmlns")) {
            xhtml = xhtml.replace("<html", "<html xmlns=\"http://www.w3.org/1999/xhtml\"");
        }

        // Close self-closing tags
        xhtml = xhtml.replaceAll("<img([^>]*[^/])>", "<img$1 />");
        xhtml = xhtml.replaceAll("<br>", "<br />");
        xhtml = xhtml.replaceAll("<hr>", "<hr />");

        return xhtml;
    }

    private byte[] generateErrorPdf(String errorMessage) {
        try {
            String errorHtml = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
                    "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                    "<head><title>Error</title></head>\n" +
                    "<body>\n" +
                    "<h1>Document Generation Error</h1>\n" +
                    "<p>" + escapeHtml(errorMessage) + "</p>\n" +
                    "<p>Please contact support for assistance.</p>\n" +
                    "</body></html>";

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(errorHtml);
            renderer.layout();
            renderer.createPDF(outputStream);
            renderer.finishPDF();

            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate error PDF", e);
            // Return minimal PDF as last resort
            return "Error generating PDF document".getBytes();
        }
    }

    // Helper methods

    private boolean isDebitTransaction(Transaction transaction, java.util.UUID accountId) {
        return accountId.equals(transaction.getSourceAccountId());
    }

    private String formatCurrency(BigDecimal amount, String currency) {
        if (amount == null) {
            return currency + " 0.00";
        }
        return currency + " " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    private String formatTransactionType(String type) {
        return type.replace("_", " ").toLowerCase()
                .replaceAll("\\b\\w", m -> m.group().toUpperCase());
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}