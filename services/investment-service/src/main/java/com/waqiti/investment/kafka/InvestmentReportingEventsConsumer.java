package com.waqiti.investment.kafka;

import com.waqiti.common.events.investment.InvestmentPerformanceEvent;
import com.waqiti.investment.domain.InvestmentReport;
import com.waqiti.investment.domain.PerformanceMetrics;
import com.waqiti.investment.repository.InvestmentReportRepository;
import com.waqiti.investment.repository.PerformanceMetricsRepository;
import com.waqiti.investment.service.PortfolioAnalyticsService;
import com.waqiti.investment.service.InvestmentService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class InvestmentReportingEventsConsumer {

    private final InvestmentReportRepository reportRepository;
    private final PerformanceMetricsRepository metricsRepository;
    private final PortfolioAnalyticsService analyticsService;
    private final InvestmentService investmentService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    // DEPRECATED: In-memory cache - replaced by persistent IdempotencyService
    @Deprecated
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("investment_reporting_processed_total")
            .description("Total number of successfully processed investment reporting events")
            .register(meterRegistry);
        errorCounter = Counter.builder("investment_reporting_errors_total")
            .description("Total number of investment reporting processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("investment_reporting_processing_duration")
            .description("Time taken to process investment reporting events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"investment-reporting-events", "portfolio-performance-reports", "investment-statements", "performance-analytics"},
        groupId = "investment-reporting-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "investment-reporting", fallbackMethod = "handleInvestmentReportingEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleInvestmentReportingEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String reportType = (String) eventData.get("reportType");
        String userId = (String) eventData.get("userId");
        String correlationId = String.format("report-%s-p%d-o%d", reportType, partition, offset);

        // CRITICAL SECURITY: Enhanced idempotency key
        String idempotencyKey = String.format("investment-reporting:%s:%s:%s",
            userId, reportType, eventData.get("timestamp"));
        UUID operationId = UUID.randomUUID();

        try {
            // CRITICAL SECURITY: Persistent idempotency check (survives service restarts)
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate investment reporting event ignored: userId={}, reportType={}, idempotencyKey={}",
                        userId, reportType, idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new investment reporting event: reportType={}, userId={}, topic={}, idempotencyKey={}",
                reportType, userId, topic, idempotencyKey);

            // DEPRECATED: Old in-memory cleanup (will be removed)
            cleanExpiredEntries();

            switch (reportType != null ? reportType.toUpperCase() : "UNKNOWN") {
                case "PERFORMANCE_REPORT":
                    processPerformanceReport(eventData, correlationId);
                    break;

                case "PORTFOLIO_STATEMENT":
                    generatePortfolioStatement(eventData, correlationId);
                    break;

                case "TAX_REPORT":
                    generateTaxReport(eventData, correlationId);
                    break;

                case "COMPLIANCE_REPORT":
                    generateComplianceReport(eventData, correlationId);
                    break;

                case "RISK_ANALYSIS":
                    generateRiskAnalysisReport(eventData, correlationId);
                    break;

                case "ASSET_ALLOCATION_REPORT":
                    generateAssetAllocationReport(eventData, correlationId);
                    break;

                case "DIVIDEND_REPORT":
                    generateDividendReport(eventData, correlationId);
                    break;

                case "TRANSACTION_HISTORY":
                    generateTransactionHistoryReport(eventData, correlationId);
                    break;

                default:
                    log.warn("Unknown investment reporting event type: {}", reportType);
                    processGenericReportingEvent(eventData, correlationId);
                    break;
            }

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("reportType", reportType, "userId", userId,
                       "accountId", (String) eventData.get("accountId"),
                       "correlationId", correlationId, "topic", topic,
                       "status", "COMPLETED"), Duration.ofDays(7));

            // DEPRECATED: Old in-memory tracking (will be removed)
            String eventKey = String.format("%s-%s-%s", userId, reportType, eventData.get("timestamp"));
            markEventAsProcessed(eventKey);

            auditService.logAccountEvent("INVESTMENT_REPORTING_EVENT_PROCESSED",
                (String) eventData.get("accountId"),
                Map.of("reportType", reportType, "userId", userId,
                    "correlationId", correlationId, "topic", topic,
                    "idempotencyKey", idempotencyKey,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("SECURITY: Failed to process investment reporting event: {}", e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed in persistent storage for retry logic
            idempotencyService.failOperation(idempotencyKey, operationId,
                String.format("Investment reporting failed: %s", e.getMessage()));

            // Send fallback event
            kafkaTemplate.send("investment-reporting-fallback-events", Map.of(
                "originalEvent", eventData, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleInvestmentReportingEventFallback(
            Map<String, Object> eventData,
            int partition,
            long offset,
            String topic,
            Acknowledgment acknowledgment,
            Exception ex) {

        String reportType = (String) eventData.get("reportType");
        String correlationId = String.format("report-fallback-%s-p%d-o%d", reportType, partition, offset);

        log.error("Circuit breaker fallback triggered for investment reporting: reportType={}, error={}",
            reportType, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("investment-reporting-dlq", Map.of(
            "originalEvent", eventData,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Investment Reporting Circuit Breaker Triggered",
                String.format("Investment reporting for type %s failed: %s", reportType, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltInvestmentReportingEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String reportType = (String) eventData.get("reportType");
        String correlationId = String.format("dlt-report-%s-%d", reportType, System.currentTimeMillis());

        log.error("Dead letter topic handler - Investment reporting permanently failed: reportType={}, topic={}, error={}",
            reportType, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAccountEvent("INVESTMENT_REPORTING_DLT_EVENT",
            (String) eventData.get("accountId"),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "reportType", reportType, "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Investment Reporting Dead Letter Event",
                String.format("Investment reporting for type %s sent to DLT: %s", reportType, exceptionMessage),
                Map.of("reportType", reportType, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processPerformanceReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String reportPeriod = (String) eventData.get("reportPeriod");

        // Generate performance analytics
        Map<String, Object> performanceData = analyticsService.generatePerformanceReport(
            accountId, reportPeriod);

        // Create performance report record
        InvestmentReport report = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("PERFORMANCE")
            .reportPeriod(reportPeriod)
            .generatedAt(LocalDateTime.now())
            .reportData(performanceData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(report);

        // Send notification to user
        notificationService.sendNotification(userId, "Performance Report Generated",
            String.format("Your investment performance report for %s is ready for review.", reportPeriod),
            correlationId);

        // Publish report ready event
        kafkaTemplate.send("investment-report-ready", Map.of(
            "reportId", report.getReportId(),
            "userId", userId,
            "reportType", "PERFORMANCE",
            "reportPeriod", reportPeriod,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Performance report generated: reportId={}, userId={}, period={}",
            report.getReportId(), userId, reportPeriod);
    }

    private void generatePortfolioStatement(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String statementDate = (String) eventData.get("statementDate");

        // Generate portfolio statement
        Map<String, Object> statementData = analyticsService.generatePortfolioStatement(
            accountId, statementDate);

        // Create statement record
        InvestmentReport statement = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("PORTFOLIO_STATEMENT")
            .reportPeriod(statementDate)
            .generatedAt(LocalDateTime.now())
            .reportData(statementData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(statement);

        // Send to document generation service
        kafkaTemplate.send("document-generation", Map.of(
            "documentType", "PORTFOLIO_STATEMENT",
            "reportId", statement.getReportId(),
            "userId", userId,
            "accountId", accountId,
            "templateData", statementData,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Portfolio statement generated: reportId={}, userId={}, date={}",
            statement.getReportId(), userId, statementDate);
    }

    private void generateTaxReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String taxYear = (String) eventData.get("taxYear");

        // Generate tax-related data
        Map<String, Object> taxData = analyticsService.generateTaxReport(accountId, taxYear);

        // Create tax report
        InvestmentReport taxReport = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("TAX_REPORT")
            .reportPeriod(taxYear)
            .generatedAt(LocalDateTime.now())
            .reportData(taxData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(taxReport);

        // Send to tax compliance service
        kafkaTemplate.send("tax-compliance-processing", Map.of(
            "reportId", taxReport.getReportId(),
            "userId", userId,
            "accountId", accountId,
            "taxYear", taxYear,
            "taxData", taxData,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Tax report generated: reportId={}, userId={}, taxYear={}",
            taxReport.getReportId(), userId, taxYear);
    }

    private void generateComplianceReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String reportingPeriod = (String) eventData.get("reportingPeriod");

        // Generate compliance data
        Map<String, Object> complianceData = investmentService.generateComplianceReport(
            accountId, reportingPeriod);

        // Create compliance report
        InvestmentReport complianceReport = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("COMPLIANCE")
            .reportPeriod(reportingPeriod)
            .generatedAt(LocalDateTime.now())
            .reportData(complianceData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(complianceReport);

        // Send to regulatory reporting
        kafkaTemplate.send("regulatory-reporting", Map.of(
            "reportId", complianceReport.getReportId(),
            "accountId", accountId,
            "reportType", "INVESTMENT_COMPLIANCE",
            "reportingPeriod", reportingPeriod,
            "complianceData", complianceData,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Compliance report generated: reportId={}, accountId={}, period={}",
            complianceReport.getReportId(), accountId, reportingPeriod);
    }

    private void generateRiskAnalysisReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String analysisDate = (String) eventData.get("analysisDate");

        // Generate risk analysis
        Map<String, Object> riskData = analyticsService.generateRiskAnalysis(accountId, analysisDate);

        // Create risk analysis report
        InvestmentReport riskReport = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("RISK_ANALYSIS")
            .reportPeriod(analysisDate)
            .generatedAt(LocalDateTime.now())
            .reportData(riskData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(riskReport);

        // Send notification for high risk portfolios
        if (isHighRiskPortfolio(riskData)) {
            notificationService.sendNotification(userId, "Portfolio Risk Alert",
                "Your portfolio risk analysis shows elevated risk levels. Please review your asset allocation.",
                correlationId);
        }

        log.info("Risk analysis report generated: reportId={}, userId={}, date={}",
            riskReport.getReportId(), userId, analysisDate);
    }

    private void generateAssetAllocationReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");

        // Generate asset allocation analysis
        Map<String, Object> allocationData = analyticsService.generateAssetAllocationReport(accountId);

        // Create asset allocation report
        InvestmentReport allocationReport = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("ASSET_ALLOCATION")
            .reportPeriod(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
            .generatedAt(LocalDateTime.now())
            .reportData(allocationData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(allocationReport);

        log.info("Asset allocation report generated: reportId={}, userId={}",
            allocationReport.getReportId(), userId);
    }

    private void generateDividendReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String reportPeriod = (String) eventData.get("reportPeriod");

        // Generate dividend summary
        Map<String, Object> dividendData = analyticsService.generateDividendReport(accountId, reportPeriod);

        // Create dividend report
        InvestmentReport dividendReport = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("DIVIDEND_REPORT")
            .reportPeriod(reportPeriod)
            .generatedAt(LocalDateTime.now())
            .reportData(dividendData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(dividendReport);

        log.info("Dividend report generated: reportId={}, userId={}, period={}",
            dividendReport.getReportId(), userId, reportPeriod);
    }

    private void generateTransactionHistoryReport(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String accountId = (String) eventData.get("accountId");
        String fromDate = (String) eventData.get("fromDate");
        String toDate = (String) eventData.get("toDate");

        // Generate transaction history
        Map<String, Object> transactionData = analyticsService.generateTransactionHistory(
            accountId, fromDate, toDate);

        // Create transaction history report
        InvestmentReport transactionReport = InvestmentReport.builder()
            .reportId(UUID.randomUUID().toString())
            .userId(userId)
            .accountId(accountId)
            .reportType("TRANSACTION_HISTORY")
            .reportPeriod(fromDate + " to " + toDate)
            .generatedAt(LocalDateTime.now())
            .reportData(transactionData)
            .status("GENERATED")
            .correlationId(correlationId)
            .build();

        reportRepository.save(transactionReport);

        log.info("Transaction history report generated: reportId={}, userId={}, period={} to {}",
            transactionReport.getReportId(), userId, fromDate, toDate);
    }

    private void processGenericReportingEvent(Map<String, Object> eventData, String correlationId) {
        String userId = (String) eventData.get("userId");
        String reportType = (String) eventData.get("reportType");

        log.info("Processing generic reporting event: userId={}, reportType={}", userId, reportType);

        // Store the event for manual processing
        auditService.logAccountEvent("GENERIC_REPORTING_EVENT",
            (String) eventData.get("accountId"),
            Map.of("reportType", reportType, "eventData", eventData,
                "correlationId", correlationId, "requiresManualProcessing", true,
                "timestamp", Instant.now()));
    }

    private boolean isHighRiskPortfolio(Map<String, Object> riskData) {
        Object riskScore = riskData.get("overallRiskScore");
        if (riskScore instanceof Number) {
            return ((Number) riskScore).doubleValue() > 7.5; // Risk score out of 10
        }
        return false;
    }
}