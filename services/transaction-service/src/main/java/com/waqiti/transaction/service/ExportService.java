package com.waqiti.transaction.service;

import com.waqiti.transaction.entity.Transaction;
import com.waqiti.transaction.dto.ExportFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting transaction data in various formats
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Export transactions in specified format
     */
    public byte[] exportTransactions(List<Transaction> transactions, ExportFormat format) {
        log.info("Exporting {} transactions in {} format", transactions.size(), format);

        try {
            switch (format) {
                case CSV:
                    return exportToCsv(transactions);
                case JSON:
                    return exportToJson(transactions);
                case XML:
                    return exportToXml(transactions);
                default:
                    throw new IllegalArgumentException("Unsupported export format: " + format);
            }
        } catch (Exception e) {
            log.error("Failed to export transactions in {} format", format, e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    private byte[] exportToCsv(List<Transaction> transactions) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

        // Write CSV header
        writer.write("Transaction ID,Type,Status,From Wallet,To Wallet,Amount,Currency,Description,Created At,Updated At\n");

        // Write transaction data
        for (Transaction transaction : transactions) {
            writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                escapeForCsv(transaction.getId().toString()),
                escapeForCsv(transaction.getType().toString()),
                escapeForCsv(transaction.getStatus().toString()),
                escapeForCsv(transaction.getFromWalletId() != null ? transaction.getFromWalletId().toString() : ""),
                escapeForCsv(transaction.getToWalletId() != null ? transaction.getToWalletId().toString() : ""),
                transaction.getAmount().toString(),
                escapeForCsv(transaction.getCurrency()),
                escapeForCsv(transaction.getDescription() != null ? transaction.getDescription() : ""),
                transaction.getCreatedAt().format(TIMESTAMP_FORMATTER),
                transaction.getUpdatedAt().format(TIMESTAMP_FORMATTER)
            ));
        }

        writer.flush();
        writer.close();
        
        log.debug("CSV export completed: {} bytes", baos.size());
        return baos.toByteArray();
    }

    private byte[] exportToJson(List<Transaction> transactions) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

        writer.write("[\n");
        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            
            writer.write("  {\n");
            writer.write(String.format("    \"transactionId\": \"%s\",\n", transaction.getId()));
            writer.write(String.format("    \"type\": \"%s\",\n", transaction.getType()));
            writer.write(String.format("    \"status\": \"%s\",\n", transaction.getStatus()));
            writer.write(String.format("    \"fromWalletId\": \"%s\",\n", 
                transaction.getFromWalletId() != null ? transaction.getFromWalletId() : ""));
            writer.write(String.format("    \"toWalletId\": \"%s\",\n", 
                transaction.getToWalletId() != null ? transaction.getToWalletId() : ""));
            writer.write(String.format("    \"amount\": %s,\n", transaction.getAmount()));
            writer.write(String.format("    \"currency\": \"%s\",\n", transaction.getCurrency()));
            writer.write(String.format("    \"description\": \"%s\",\n", 
                escapeForJson(transaction.getDescription() != null ? transaction.getDescription() : "")));
            writer.write(String.format("    \"createdAt\": \"%s\",\n", 
                transaction.getCreatedAt().format(TIMESTAMP_FORMATTER)));
            writer.write(String.format("    \"updatedAt\": \"%s\"\n", 
                transaction.getUpdatedAt().format(TIMESTAMP_FORMATTER)));
            writer.write("  }");
            
            if (i < transactions.size() - 1) {
                writer.write(",");
            }
            writer.write("\n");
        }
        writer.write("]\n");

        writer.flush();
        writer.close();
        
        log.debug("JSON export completed: {} bytes", baos.size());
        return baos.toByteArray();
    }

    private byte[] exportToXml(List<Transaction> transactions) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        writer.write("<transactions>\n");

        for (Transaction transaction : transactions) {
            writer.write("  <transaction>\n");
            writer.write(String.format("    <transactionId>%s</transactionId>\n", 
                escapeForXml(transaction.getId().toString())));
            writer.write(String.format("    <type>%s</type>\n", 
                escapeForXml(transaction.getType().toString())));
            writer.write(String.format("    <status>%s</status>\n", 
                escapeForXml(transaction.getStatus().toString())));
            writer.write(String.format("    <fromWalletId>%s</fromWalletId>\n", 
                escapeForXml(transaction.getFromWalletId() != null ? transaction.getFromWalletId().toString() : "")));
            writer.write(String.format("    <toWalletId>%s</toWalletId>\n", 
                escapeForXml(transaction.getToWalletId() != null ? transaction.getToWalletId().toString() : "")));
            writer.write(String.format("    <amount>%s</amount>\n", transaction.getAmount()));
            writer.write(String.format("    <currency>%s</currency>\n", 
                escapeForXml(transaction.getCurrency())));
            writer.write(String.format("    <description>%s</description>\n", 
                escapeForXml(transaction.getDescription() != null ? transaction.getDescription() : "")));
            writer.write(String.format("    <createdAt>%s</createdAt>\n", 
                transaction.getCreatedAt().format(TIMESTAMP_FORMATTER)));
            writer.write(String.format("    <updatedAt>%s</updatedAt>\n", 
                transaction.getUpdatedAt().format(TIMESTAMP_FORMATTER)));
            writer.write("  </transaction>\n");
        }

        writer.write("</transactions>\n");

        writer.flush();
        writer.close();
        
        log.debug("XML export completed: {} bytes", baos.size());
        return baos.toByteArray();
    }

    private String escapeForCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    private String escapeForJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private String escapeForXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}