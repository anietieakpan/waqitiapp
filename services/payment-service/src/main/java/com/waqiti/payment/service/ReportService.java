package com.waqiti.payment.service;

import com.waqiti.payment.model.BatchReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready service for generating batch payment reports
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Async
    public CompletableFuture<String> generateBatchReport(BatchReport report) {
        log.info("Generating batch report for batchId: {}, total payments: {}, success rate: {}%",
            report.getBatchId(), report.getTotalPayments(), report.getSuccessRate());

        try {
            // Generate CSV report
            String csvReport = generateCsvReport(report);

            // Generate JSON summary
            String jsonSummary = generateJsonSummary(report);

            // Publish report generated event
            kafkaTemplate.send("batch-reports", report.getBatchId(), java.util.Map.of(
                "batchId", report.getBatchId(),
                "reportType", "BATCH_COMPLETION",
                "format", "CSV",
                "generatedAt", report.getGeneratedAt(),
                "summary", jsonSummary
            ));

            log.info("Batch report generated successfully for batchId: {}", report.getBatchId());
            return CompletableFuture.completedFuture(csvReport);

        } catch (Exception e) {
            log.error("Failed to generate batch report for batchId: {}", report.getBatchId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private String generateCsvReport(BatchReport report) {
        StringBuilder csv = new StringBuilder();

        // Header
        csv.append("Batch Payment Report\n");
        csv.append("Batch ID,").append(report.getBatchId()).append("\n");
        csv.append("Generated At,").append(report.getGeneratedAt().toString()).append("\n");
        csv.append("\n");

        // Summary
        csv.append("Summary\n");
        csv.append("Total Payments,").append(report.getTotalPayments()).append("\n");
        csv.append("Successful Payments,").append(report.getSuccessfulPayments()).append("\n");
        csv.append("Failed Payments,").append(report.getFailedPayments()).append("\n");
        csv.append("Success Rate,").append(String.format("%.2f%%", report.getSuccessRate())).append("\n");
        csv.append("Total Amount,").append(report.getTotalAmount()).append("\n");
        csv.append("Processed Amount,").append(report.getProcessedAmount()).append("\n");
        csv.append("Processing Time,").append(report.getProcessingTime()).append("ms\n");
        csv.append("\n");

        // Errors section
        if (report.getErrors() != null && !report.getErrors().isEmpty()) {
            csv.append("Errors\n");
            csv.append("Payment ID,Error Code,Error Message,Timestamp\n");

            report.getErrors().forEach(error -> {
                csv.append(escape(error.getPaymentId())).append(",");
                csv.append(escape(error.getErrorCode())).append(",");
                csv.append(escape(error.getErrorMessage())).append(",");
                csv.append(error.getTimestamp()).append("\n");
            });
        }

        return csv.toString();
    }

    private String generateJsonSummary(BatchReport report) {
        return String.format(
            "{\"batchId\":\"%s\",\"totalPayments\":%d,\"successfulPayments\":%d," +
            "\"failedPayments\":%d,\"successRate\":%.2f,\"totalAmount\":\"%s\"," +
            "\"processedAmount\":\"%s\",\"processingTime\":%d}",
            report.getBatchId(),
            report.getTotalPayments(),
            report.getSuccessfulPayments(),
            report.getFailedPayments(),
            report.getSuccessRate(),
            report.getTotalAmount(),
            report.getProcessedAmount(),
            report.getProcessingTime()
        );
    }

    private String escape(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
